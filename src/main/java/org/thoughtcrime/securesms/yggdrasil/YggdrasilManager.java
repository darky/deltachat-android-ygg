package org.thoughtcrime.securesms.yggdrasil;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
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
    "tls://45.95.202.21:443",
    "tls://45.147.200.202:443",
    "tls://95.217.35.92:1337",
    "tls://62.210.85.80:39575"
  };

  private static Yggstack instance;
  private static boolean running;
  private static Context appContext;

  private static final CopyOnWriteArrayList<Runnable> statusListeners = new CopyOnWriteArrayList<>();

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
    try {
      return instance != null ? instance.getPeers() : "[]";
    } catch (Exception e) {
      return "[]";
    }
  }

  public static int getPeerCount() {
    try {
      if (instance == null) return 0;
      String json = instance.getPeers();
      if (json == null || json.equals("[]") || json.equals("null")) return 0;
      int count = 0;
      for (int i = 0; i < json.length(); i++) {
        if (json.charAt(i) == '\"') count++;
      }
      return count / 2;
    } catch (Exception e) {
      return 0;
    }
  }

  public static synchronized void init(Context context) {
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
    for (String peer : DEFAULT_PEERS) {
      arr.put(peer);
    }
    SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    prefs.edit().putString(KEY_PEERS, arr.toString()).apply();
  }

  private static void addPeersFromPrefs(Context context) {
    SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    String peersJson = prefs.getString(KEY_PEERS, null);
    if (peersJson == null) return;
    try {
      JSONArray arr = new JSONArray(peersJson);
      for (int i = 0; i < arr.length(); i++) {
        String peer = arr.optString(i, null);
        if (peer != null && !peer.isEmpty()) {
          instance.addPeer(peer);
        }
      }
    } catch (Exception e) {
      Log.e(TAG, "Failed to load peers from prefs", e);
    }
  }

  private static void persistPeers() {
    if (appContext == null) return;
    try {
      String json = instance.getPeers();
      SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
      prefs.edit().putString(KEY_PEERS, json).apply();
    } catch (Exception e) {
      Log.e(TAG, "Failed to persist peers", e);
    }
  }

  public static synchronized void start() {
    if (running) return;
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
      instance.stop();
    } catch (Exception e) {
      Log.e(TAG, "Error stopping Yggdrasil", e);
    }
    running = false;
    Log.i(TAG, "Yggdrasil stopped");
    notifyStatusChanged();
  }

  public static void addPeer(String uri) {
    try {
      instance.addPeer(uri);
      if (running) {
        instance.addLivePeer(uri);
      }
      persistPeers();
    } catch (Exception e) {
      Log.e(TAG, "Failed to add peer " + uri, e);
    }
  }

  public static void removePeer(String uri) {
    try {
      instance.removePeer(uri);
      if (running) {
        instance.removeLivePeer(uri);
      }
      persistPeers();
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
}
