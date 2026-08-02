package org.thoughtcrime.securesms.yggdrasil;

import android.content.Context;
import android.content.SharedPreferences;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import link.yggdrasil.yggstack.mobile.Mobile;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Fetches public peers and measures their connection latency. */
public final class YggdrasilRemotePeerService {
  private static final String PRIMARY_URL =
      "https://publicpeers.neilalexander.dev/publicnodes.json";
  private static final String MIRROR_URL = "https://peers.yggdrasil.link/publicnodes.json";
  private static final String CACHE_PREFS = "yggdrasil_remote_peers";
  private static final String CACHE_KEY = "peers";
  private static final int FETCH_TIMEOUT_MS = 10_000;
  private static final int CONNECT_TIMEOUT_MS = 3_000;
  private static final int MAX_CONCURRENT_CHECKS = 24;
  private static final ObjectMapper JSON = new ObjectMapper();

  private final SharedPreferences cache;

  public YggdrasilRemotePeerService(Context context) {
    cache = context.getApplicationContext().getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE);
  }

  public interface RttProgressListener {
    boolean isCancelled();

    void onProgress(int checked, int total);
  }

  public List<YggdrasilRemotePeer> loadCachedPeers() {
    List<YggdrasilRemotePeer> peers = new ArrayList<>();
    String json = cache.getString(CACHE_KEY, "[]");
    try {
      JSONArray array = new JSONArray(json);
      for (int i = 0; i < array.length(); i++) {
        JSONObject object = array.optJSONObject(i);
        if (object == null) continue;
        String uri = object.optString("uri", "").trim();
        if (uri.isEmpty()) continue;
        peers.add(new YggdrasilRemotePeer(uri, object.optString("country", "")));
      }
    } catch (JSONException ignored) {
      // Ignore an invalid cache and let the user fetch a fresh list.
    }
    return peers;
  }

  public void saveCachedPeers(List<YggdrasilRemotePeer> peers) {
    JSONArray array = new JSONArray();
    for (YggdrasilRemotePeer peer : peers) {
      JSONObject object = new JSONObject();
      try {
        object.put("uri", peer.uri);
        object.put("country", peer.country);
        array.put(object);
      } catch (JSONException ignored) {
        // Skip a malformed cache entry.
      }
    }
    cache.edit().putString(CACHE_KEY, array.toString()).apply();
  }

  public List<YggdrasilRemotePeer> fetchRemotePeers() throws IOException {
    Exception lastError = null;
    String[] endpoints = {PRIMARY_URL, MIRROR_URL};

    for (String endpoint : endpoints) {
      try {
        return parsePublicNodesJson(fetchUrl(endpoint));
      } catch (IOException e) {
        lastError = e;
      }
    }

    throw new IOException("Failed to fetch public peers from both sources", lastError);
  }

  public List<YggdrasilRemotePeer> checkPeers(
      List<YggdrasilRemotePeer> peers, RttProgressListener listener) {
    if (peers.isEmpty()) return new ArrayList<>();

    int workerCount = Math.min(MAX_CONCURRENT_CHECKS, peers.size());
    ExecutorService executor =
        Executors.newFixedThreadPool(workerCount, daemonThreadFactory("ygg-peer-check"));
    ExecutorService resolverExecutor =
        Executors.newFixedThreadPool(workerCount, daemonThreadFactory("ygg-peer-dns"));
    CompletionService<YggdrasilRemotePeer> completion = new ExecutorCompletionService<>(executor);

    try {
      for (YggdrasilRemotePeer peer : peers) {
        completion.submit(() -> checkPeer(peer, resolverExecutor));
      }

      List<YggdrasilRemotePeer> results = new ArrayList<>();
      for (int checked = 1; checked <= peers.size(); checked++) {
        if (listener != null && listener.isCancelled()) {
          throw new CancellationException();
        }

        Future<YggdrasilRemotePeer> future =
            completion.poll(CONNECT_TIMEOUT_MS + 1_000L, TimeUnit.MILLISECONDS);
        if (future == null) {
          throw new TimeoutException("Peer RTT worker timed out");
        }
        YggdrasilRemotePeer result;
        try {
          result = future.get();
        } catch (ExecutionException e) {
          throw new RuntimeException(e.getCause());
        }

        results.add(result);
        sortPeers(results);
        if (listener != null) {
          listener.onProgress(checked, peers.size());
        }
      }

      sortPeers(results);
      return results;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CancellationException();
    } catch (TimeoutException e) {
      throw new RuntimeException(e);
    } finally {
      shutdownAndAwait(executor);
      shutdownAndAwait(resolverExecutor);
    }
  }

  static List<YggdrasilRemotePeer> parsePublicNodesJson(String jsonString) throws IOException {
    List<YggdrasilRemotePeer> peers = new ArrayList<>();
    JsonNode root = JSON.readTree(jsonString);
    if (root == null || !root.isObject()) {
      throw new IOException("Public peer list is not a JSON object");
    }

    Iterator<Map.Entry<String, JsonNode>> countryFiles = root.fields();
    while (countryFiles.hasNext()) {
      Map.Entry<String, JsonNode> countryEntry = countryFiles.next();
      String countryFile = countryEntry.getKey();
      JsonNode value = countryEntry.getValue();
      if (!value.isObject()) continue;

      String country = formatCountry(countryFile);
      Iterator<String> peerUris = value.fieldNames();
      while (peerUris.hasNext()) {
        String uri = peerUris.next();
        if (!uri.trim().isEmpty()) {
          peers.add(new YggdrasilRemotePeer(uri, country));
        }
      }
    }

    return peers;
  }

  static String formatCountry(String countryFile) {
    String country =
        countryFile.endsWith(".md")
            ? countryFile.substring(0, countryFile.length() - 3)
            : countryFile;
    String[] parts = country.split("-");
    StringBuilder formatted = new StringBuilder();
    for (String part : parts) {
      if (part.isEmpty()) continue;
      if (formatted.length() > 0) formatted.append(' ');
      formatted.append(Character.toUpperCase(part.charAt(0)));
      if (part.length() > 1) formatted.append(part.substring(1));
    }
    return formatted.toString();
  }

  private String fetchUrl(String endpoint) throws IOException {
    HttpURLConnection connection = null;
    try {
      connection = (HttpURLConnection) new URL(endpoint).openConnection();
      connection.setConnectTimeout(FETCH_TIMEOUT_MS);
      connection.setReadTimeout(FETCH_TIMEOUT_MS);
      connection.setRequestMethod("GET");
      connection.setUseCaches(false);

      if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
        throw new IOException("HTTP " + connection.getResponseCode());
      }

      StringBuilder response = new StringBuilder();
      try (BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          response.append(line);
        }
      }
      return response.toString();
    } finally {
      if (connection != null) connection.disconnect();
    }
  }

  private YggdrasilRemotePeer checkPeer(
      YggdrasilRemotePeer peer, ExecutorService resolverExecutor) {
    long start = System.nanoTime();
    long deadline = start + TimeUnit.MILLISECONDS.toNanos(CONNECT_TIMEOUT_MS);

    try {
      String protocol = extractProtocol(peer.uri);
      if ("quic".equals(protocol)) {
        return peer.withRtt(checkQuicPeer(peer.uri, deadline, resolverExecutor));
      }

      HostPort hostPort = parseHostPort(peer.uri);
      InetAddress[] addresses = resolveHost(hostPort.host, deadline, resolverExecutor);
      for (InetAddress address : addresses) {
        try (Socket socket = new Socket()) {
          socket.connect(
              new InetSocketAddress(address, hostPort.port), remainingTimeoutMillis(deadline));
          long rtt = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
          return peer.withRtt(rtt);
        } catch (IOException ignored) {
          // Try the next resolved address while the per-peer deadline remains.
        }
      }
      return peer.withRtt(null);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return peer.withRtt(null);
    } catch (Exception e) {
      return peer.withRtt(null);
    }
  }

  private InetAddress[] resolveHost(String host, long deadline, ExecutorService resolverExecutor)
      throws Exception {
    Future<InetAddress[]> resolution =
        resolverExecutor.submit(() -> InetAddress.getAllByName(host));
    try {
      return resolution.get(remainingTimeoutMillis(deadline), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      resolution.cancel(true);
      throw e;
    }
  }

  private Long checkQuicPeer(String uri, long deadline, ExecutorService resolverExecutor)
      throws Exception {
    Future<Long> check = resolverExecutor.submit(() -> Mobile.checkQUICPeer(uri));
    try {
      long rtt = check.get(remainingTimeoutMillis(deadline), TimeUnit.MILLISECONDS);
      return rtt > 0 ? rtt : null;
    } catch (TimeoutException e) {
      check.cancel(true);
      throw e;
    } catch (InterruptedException e) {
      check.cancel(true);
      throw e;
    }
  }

  private static int remainingTimeoutMillis(long deadline) throws TimeoutException {
    long remainingNanos = deadline - System.nanoTime();
    if (remainingNanos <= 0) throw new TimeoutException("Peer RTT deadline exceeded");
    long remainingMillis = TimeUnit.NANOSECONDS.toMillis(remainingNanos);
    return (int) Math.max(1, Math.min(Integer.MAX_VALUE, remainingMillis));
  }

  private static String extractProtocol(String uriString) throws Exception {
    URI uri = new URI(uriString.split("\\?", 2)[0]);
    return uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
  }

  private static HostPort parseHostPort(String uriString) throws Exception {
    String cleanUri = uriString.split("\\?", 2)[0];
    URI uri = new URI(cleanUri);
    String authority = uri.getRawAuthority();
    if (authority == null) throw new IllegalArgumentException("Missing peer authority");

    int userInfoEnd = authority.lastIndexOf('@');
    if (userInfoEnd >= 0) authority = authority.substring(userInfoEnd + 1);

    String host;
    int port;
    if (authority.startsWith("[")) {
      int closingBracket = authority.indexOf(']');
      if (closingBracket < 0
          || closingBracket + 2 > authority.length()
          || authority.charAt(closingBracket + 1) != ':') {
        throw new IllegalArgumentException("Invalid IPv6 peer authority");
      }
      host = authority.substring(1, closingBracket);
      port = Integer.parseInt(authority.substring(closingBracket + 2));
    } else {
      int colon = authority.lastIndexOf(':');
      if (colon <= 0 || colon == authority.length() - 1) {
        throw new IllegalArgumentException("Invalid peer authority");
      }
      host = authority.substring(0, colon);
      port = Integer.parseInt(authority.substring(colon + 1));
    }

    if (host.isEmpty() || port < 1 || port > 65535) {
      throw new IllegalArgumentException("Invalid peer host or port");
    }
    return new HostPort(host, port);
  }

  static void sortPeers(List<YggdrasilRemotePeer> peers) {
    Collections.sort(
        peers,
        new Comparator<YggdrasilRemotePeer>() {
          @Override
          public int compare(YggdrasilRemotePeer left, YggdrasilRemotePeer right) {
            if (left.rtt == null && right.rtt != null) return 1;
            if (left.rtt != null && right.rtt == null) return -1;
            if (left.rtt == null) return left.uri.compareTo(right.uri);
            int byRtt = Long.compare(left.rtt, right.rtt);
            return byRtt != 0 ? byRtt : left.uri.compareTo(right.uri);
          }
        });
  }

  private static ThreadFactory daemonThreadFactory(String name) {
    return runnable -> {
      Thread thread = new Thread(runnable, name);
      thread.setDaemon(true);
      return thread;
    };
  }

  private static void shutdownAndAwait(ExecutorService executor) {
    executor.shutdownNow();
    try {
      executor.awaitTermination(1, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static final class HostPort {
    private final String host;
    private final int port;

    private HostPort(String host, int port) {
      this.host = host;
      this.port = port;
    }
  }
}
