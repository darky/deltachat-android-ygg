package org.thoughtcrime.securesms.yggdrasil;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import link.yggdrasil.yggstack.mobile.Mobile;
import link.yggdrasil.yggstack.mobile.Yggstack;

public class YggdrasilManager {

  private static final String TAG = "YggdrasilManager";
  private static final String PREFS_NAME = "yggdrasil";
  private static final String KEY_CONFIG = "config";
  private static final String KEY_PEERS = "peers";
  private static final String KEY_MAPPINGS = "mappings";

  private static final String[] DEFAULT_PEERS = {
    "tls://37.192.232.33:442",
    "tls://45.147.200.202:443",
    "tls://95.217.35.92:1337",
    "tls://62.210.85.80:39575"
  };

  private static Yggstack instance;
  private static boolean running;
  private static Context appContext;

  private static final CopyOnWriteArrayList<Runnable> statusListeners = new CopyOnWriteArrayList<>();

  public static class PeerEntry {
    public final String uri;
    public boolean active;

    public PeerEntry(String uri, boolean active) {
      this.uri = uri;
      this.active = active;
    }

    public static PeerEntry fromJson(JSONObject obj) {
      return new PeerEntry(obj.optString("uri", ""), obj.optBoolean("active", false));
    }

    public JSONObject toJson() {
      try {
        JSONObject obj = new JSONObject();
        obj.put("uri", uri);
        obj.put("active", active);
        return obj;
      } catch (JSONException e) {
        return new JSONObject();
      }
    }
  }

  public static void addStatusListener(Runnable l) {
    statusListeners.add(l);
  }

  public static void removeStatusListener(Runnable l) {
    statusListeners.remove(l);
  }

  private static void notifyStatusChanged() {
    for (Runnable l : statusListeners) {
      l.run();
    }
  }

  public static boolean isRunning() {
    return running;
  }

  public static String getAddress() {
    try {
      return instance != null ? instance.getAddress() : "";
    } catch (Exception e) {
      return "";
    }
  }

  public static String getPublicKey() {
    try {
      return instance != null ? instance.getPublicKey() : "";
    } catch (Exception e) {
      return "";
    }
  }

  public static String getConfiguredPeers() {
    if (appContext == null) return "[]";
    return appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_PEERS, "[]");
  }

  public static int getPeerCount() {
    return getActivePeerCount();
  }

  public static synchronized void init(Context context) {
    if (instance != null) {
      Log.w(TAG, "init: already initialized, skipping");
      return;
    }
    appContext = context.getApplicationContext();
    instance = Mobile.newYggstack();
    instance.setLogLevel("info");
    instance.setLogCallback(message -> Log.i(TAG, message != null ? message : ""));

    String configJson = loadConfig(context);
    try {
      instance.loadConfigJSON(configJson);
    } catch (Exception e) {
      Log.e(TAG, "Failed to load config", e);
    }
    addPeersFromPrefs(context);
  }

  private static String loadConfig(Context context) {
    SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    String configJson = prefs.getString(KEY_CONFIG, null);
    if (configJson != null) return configJson;

    try {
      configJson = Mobile.generateConfig();
      JSONObject cfg = new JSONObject(configJson);
      cfg.put("MulticastInterfaces", new JSONArray());
      configJson = cfg.toString();
    } catch (Exception e) {
      Log.e(TAG, "Failed to generate config, using empty", e);
      configJson = "{}";
    }
    prefs.edit().putString(KEY_CONFIG, configJson).apply();

    initDefaultPeers(context);
    return configJson;
  }

  private static void initDefaultPeers(Context context) {
    JSONArray arr = new JSONArray();
    boolean first = true;
    for (String peer : DEFAULT_PEERS) {
      try {
        JSONObject obj = new JSONObject();
        obj.put("uri", peer);
        obj.put("active", first);
        arr.put(obj);
        first = false;
      } catch (JSONException e) {
        Log.e(TAG, "Failed to create default peer entry", e);
      }
    }
    SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    prefs.edit().putString(KEY_PEERS, arr.toString()).apply();
  }

  private static void addPeersFromPrefs(Context context) {
    for (PeerEntry entry : getAllPeers()) {
      if (!entry.active) continue;
      try {
        instance.addPeer(entry.uri);
      } catch (Exception e) {
        Log.e(TAG, "Failed to add peer " + entry.uri, e);
      }
    }
  }

  public static List<PeerEntry> getAllPeers() {
    List<PeerEntry> result = new ArrayList<>();
    if (appContext == null) return result;
    String json = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_PEERS, "[]");
    if (json == null || json.equals("[]") || json.equals("null")) return result;
    try {
      JSONArray arr = new JSONArray(json);
      for (int i = 0; i < arr.length(); i++) {
        JSONObject obj = arr.optJSONObject(i);
        if (obj != null) {
          result.add(PeerEntry.fromJson(obj));
        }
      }
    } catch (Exception e) {
      Log.e(TAG, "Failed to parse peers", e);
    }
    return result;
  }

  public static List<String> getActivePeers() {
    List<String> result = new ArrayList<>();
    for (PeerEntry entry : getAllPeers()) {
      if (entry.active) {
        result.add(entry.uri);
      }
    }
    return result;
  }

  public static void setPeerActive(String uri, boolean active) {
    if (appContext == null) return;
    SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    String json = prefs.getString(KEY_PEERS, "[]");
    try {
      JSONArray arr = new JSONArray(json);
      for (int i = 0; i < arr.length(); i++) {
        JSONObject obj = arr.optJSONObject(i);
        if (obj != null && uri.equals(obj.optString("uri", ""))) {
          obj.put("active", active);
          break;
        }
      }
      prefs.edit().putString(KEY_PEERS, arr.toString()).apply();
      if (instance != null) {
        if (active) {
          instance.addPeer(uri);
          if (running) {
            instance.addLivePeer(uri);
          }
        } else {
          if (running) {
            instance.removeLivePeer(uri);
          }
        }
      }
    } catch (Exception e) {
      Log.e(TAG, "Failed to set peer active " + uri, e);
    }
  }

  public static synchronized void start() {
    if (running) return;
    if (instance == null) {
      Log.e(TAG, "start: instance is null, cannot start");
      return;
    }
    try {
      instance.start("", "");
      running = true;
      restoreMappings();
      Log.i(TAG, "Yggdrasil started");
    } catch (Exception e) {
      Log.e(TAG, "Failed to start Yggdrasil", e);
    }
    notifyStatusChanged();
  }

  public static synchronized void stop() {
    if (!running) return;
    try {
      if (instance != null) {
        instance.stop();
      }
    } catch (Exception e) {
      Log.e(TAG, "Error stopping Yggdrasil", e);
    }
    running = false;
    instance = null;
    Log.i(TAG, "Yggdrasil stopped");
    notifyStatusChanged();
  }

  public static void addPeer(String uri) {
    if (appContext == null) return;
    SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    String json = prefs.getString(KEY_PEERS, "[]");
    try {
      JSONArray arr = new JSONArray(json);
      for (int i = 0; i < arr.length(); i++) {
        JSONObject obj = arr.optJSONObject(i);
        if (obj != null && uri.equals(obj.optString("uri", ""))) {
          return;
        }
      }
      JSONObject entry = new JSONObject();
      entry.put("uri", uri);
      entry.put("active", false);
      arr.put(entry);
      prefs.edit().putString(KEY_PEERS, arr.toString()).apply();
      if (instance != null) {
        instance.addPeer(uri);
      }
    } catch (Exception e) {
      Log.e(TAG, "Failed to add peer " + uri, e);
    }
  }

  public static void removePeer(String uri) {
    if (appContext == null) return;
    SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    String json = prefs.getString(KEY_PEERS, "[]");
    try {
      JSONArray arr = new JSONArray(json);
      JSONArray updated = new JSONArray();
      boolean wasActive = false;
      for (int i = 0; i < arr.length(); i++) {
        JSONObject obj = arr.optJSONObject(i);
        if (obj != null && uri.equals(obj.optString("uri", ""))) {
          wasActive = obj.optBoolean("active", false);
        } else {
          updated.put(obj);
        }
      }
      prefs.edit().putString(KEY_PEERS, updated.toString()).apply();
      if (instance != null) {
        if (wasActive && running) {
          instance.removeLivePeer(uri);
        }
        instance.removePeer(uri);
      }
    } catch (Exception e) {
      Log.e(TAG, "Failed to remove peer " + uri, e);
    }
  }

  public static void retryPeersNow() {
    try {
      if (instance != null) instance.retryPeersNow();
    } catch (Exception e) {
      Log.e(TAG, "retryPeersNow error", e);
    }
  }

  public static List<String> getSavedMappings() {
    List<String> result = new ArrayList<>();
    if (appContext == null) return result;
    String json = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_MAPPINGS, null);
    if (json == null) return result;
    try {
      JSONArray arr = new JSONArray(json);
      for (int i = 0; i < arr.length(); i++) {
        JSONObject obj = arr.getJSONObject(i);
        String local = obj.optString("local", "");
        String remote = obj.optString("remote", "");
        if (!local.isEmpty() && !remote.isEmpty()) {
          result.add(local + " -> " + remote);
        }
      }
    } catch (Exception e) {
      Log.e(TAG, "Failed to parse saved mappings", e);
    }
    return result;
  }

  private static void saveMapping(String localAddr, String remoteAddr) {
    if (appContext == null) return;
    SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    String json = prefs.getString(KEY_MAPPINGS, "[]");
    try {
      JSONArray arr = new JSONArray(json);
      JSONObject obj = new JSONObject();
      obj.put("local", localAddr);
      obj.put("remote", remoteAddr);
      arr.put(obj);
      prefs.edit().putString(KEY_MAPPINGS, arr.toString()).apply();
    } catch (Exception e) {
      Log.e(TAG, "saveMapping error", e);
    }
  }

  private static void removeSavedMapping(String localAddr, String remoteAddr) {
    if (appContext == null) return;
    SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    String json = prefs.getString(KEY_MAPPINGS, "[]");
    try {
      JSONArray arr = new JSONArray(json);
      JSONArray updated = new JSONArray();
      for (int i = 0; i < arr.length(); i++) {
        JSONObject obj = arr.getJSONObject(i);
        if (!localAddr.equals(obj.optString("local", ""))
            || !remoteAddr.equals(obj.optString("remote", ""))) {
          updated.put(obj);
        }
      }
      prefs.edit().putString(KEY_MAPPINGS, updated.toString()).apply();
    } catch (Exception e) {
      Log.e(TAG, "removeSavedMapping error", e);
    }
  }

  private static void restoreMappings() {
    if (appContext == null || instance == null) return;
    String json = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_MAPPINGS, null);
    if (json == null) return;
    try {
      JSONArray arr = new JSONArray(json);
      for (int i = 0; i < arr.length(); i++) {
        JSONObject obj = arr.getJSONObject(i);
        String local = obj.optString("local", "");
        String remote = obj.optString("remote", "");
        if (!local.isEmpty() && !remote.isEmpty()) {
          instance.addLocalTCPMapping(local, remote);
        }
      }
      Log.i(TAG, "Restored " + arr.length() + " port mappings");
    } catch (Exception e) {
      Log.e(TAG, "restoreMappings error", e);
    }
  }

  public static void addLocalTCPMapping(String localAddr, String remoteAddr) {
    try {
      instance.addLocalTCPMapping(localAddr, remoteAddr);
      saveMapping(localAddr, remoteAddr);
    } catch (Exception e) {
      Log.e(TAG, "addLocalTCPMapping error", e);
    }
  }

  public static void removeLocalTCPMapping(String localAddr, String remoteAddr) {
    try {
      instance.removeLocalTCPMapping(localAddr, remoteAddr);
      removeSavedMapping(localAddr, remoteAddr);
    } catch (Exception e) {
      Log.e(TAG, "removeLocalTCPMapping error", e);
    }
  }

  public static void clearLocalMappings() {
    try {
      instance.clearLocalMappings();
      if (appContext != null) {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_MAPPINGS).apply();
      }
    } catch (Exception e) {
      Log.e(TAG, "clearLocalMappings error", e);
    }
  }

  public static void retryPeersAndReapplyMappings() {
    retryPeersNow();
    if (running) {
      restoreMappings();
    }
  }

  public static String getActivePeer() {
    List<PeerEntry> peers = getAllPeers();
    for (PeerEntry entry : peers) {
      if (entry.active) return entry.uri;
    }
    return "";
  }

  public static int getActivePeerCount() {
    List<PeerEntry> peers = getAllPeers();
    int count = 0;
    for (PeerEntry entry : peers) {
      if (entry.active) count++;
    }
    return count;
  }
}
