package org.thoughtcrime.securesms.yggdrasil;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.IntentUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class YggdrasilForegroundService extends Service {

  private static final String TAG = "YggdrasilFgService";
  private static final int NOTIFICATION_ID = 314827;

  public static final String ACTION_START = "org.thoughtcrime.securesms.yggdrasil.START";
  public static final String ACTION_STOP = "org.thoughtcrime.securesms.yggdrasil.STOP";

  private PowerManager.WakeLock wakeLock;
  private WifiManager.WifiLock wifiLock;

  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  public static void startSelf(Context context) {
    Intent intent = new Intent(context, YggdrasilForegroundService.class);
    intent.setAction(ACTION_START);
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent);
      } else {
        context.startService(intent);
      }
    } catch (Exception e) {
      Log.e(TAG, "Error starting service", e);
    }
  }

  public static void stopSelf(Context context) {
    Intent intent = new Intent(context, YggdrasilForegroundService.class);
    intent.setAction(ACTION_STOP);
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent);
      } else {
        context.startService(intent);
      }
    } catch (Exception e) {
      Log.e(TAG, "Error sending stop to service", e);
    }
  }

  @Override
  public void onCreate() {
    super.onCreate();
    Log.i(TAG, "onCreate");
    createNotificationChannel();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent == null) {
      Log.i(TAG, "onStartCommand: intent=null (restarted by system) — restarting Yggdrasil");
      acquireLocks();
      ensureRunning();
      return START_STICKY;
    }

    String action = intent.getAction();
    Log.i(TAG, "onStartCommand: action=" + action);

    if (ACTION_START.equals(action)) {
      acquireLocks();
      ensureRunning();
      startForeground(NOTIFICATION_ID, createNotification());
    } else if (ACTION_STOP.equals(action)) {
      ensureStopped();
      releaseLocks();
      stopForeground(true);
      stopSelf();
    }

    return START_STICKY;
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onDestroy() {
    Log.i(TAG, "onDestroy");
    releaseLocks();
    executor.shutdownNow();
    super.onDestroy();
  }

  @Override
  public void onTimeout(int startId, int fgsType) {
    stopSelf();
  }

  private void acquireLocks() {
    acquireWakeLock();
    acquireWifiLock();
  }

  private void releaseLocks() {
    releaseWakeLock();
    releaseWifiLock();
  }

  private void acquireWakeLock() {
    if (wakeLock != null && wakeLock.isHeld()) return;
    try {
      PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
      wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "yggdrasil:network");
      wakeLock.acquire();
      Log.i(TAG, "WakeLock acquired");
    } catch (Exception e) {
      Log.e(TAG, "Failed to acquire WakeLock", e);
    }
  }

  private void releaseWakeLock() {
    try {
      if (wakeLock != null && wakeLock.isHeld()) {
        wakeLock.release();
        Log.i(TAG, "WakeLock released");
      }
    } catch (Exception e) {
      Log.e(TAG, "Error releasing WakeLock", e);
    }
    wakeLock = null;
  }

  private void acquireWifiLock() {
    if (wifiLock != null && wifiLock.isHeld()) return;
    try {
      WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
      if (wm != null) {
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "yggdrasil:wifi");
        wifiLock.acquire();
        Log.i(TAG, "WifiLock acquired");
      }
    } catch (Exception e) {
      Log.e(TAG, "Failed to acquire WifiLock", e);
    }
  }

  private void releaseWifiLock() {
    try {
      if (wifiLock != null && wifiLock.isHeld()) {
        wifiLock.release();
        Log.i(TAG, "WifiLock released");
      }
    } catch (Exception e) {
      Log.e(TAG, "Error releasing WifiLock", e);
    }
    wifiLock = null;
  }



  private void ensureRunning() {
    if (!YggdrasilManager.isRunning()) {
      executor.execute(() -> {
        YggdrasilManager.init(this);
        YggdrasilManager.start();
      });
    }
  }

  private void ensureStopped() {
    executor.execute(() -> {
      if (YggdrasilManager.isRunning()) {
        YggdrasilManager.stop();
      }
    });
  }

  private Notification createNotification() {
    Intent intent = new Intent(this, org.thoughtcrime.securesms.ConversationListActivity.class);
    PendingIntent contentIntent =
        PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | IntentUtils.FLAG_MUTABLE());

    NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CH_YGGDRASIL);

    builder.setContentTitle(getString(R.string.app_name));
    builder.setContentText(getString(R.string.yggdrasil_notification_text));
    builder.setPriority(NotificationCompat.PRIORITY_MIN);
    builder.setWhen(0);
    builder.setContentIntent(contentIntent);
    builder.setSmallIcon(R.drawable.notification_permanent);
    builder.setOngoing(true);

    return builder.build();
  }

  private static boolean chCreated = false;
  public static final String CH_YGGDRASIL = "yggdrasil_fg";

  private void createNotificationChannel() {
    if (chCreated) return;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      chCreated = true;
      NotificationChannel channel =
          new NotificationChannel(
              CH_YGGDRASIL,
              getString(R.string.yggdrasil_notification_channel_name),
              NotificationManager.IMPORTANCE_MIN);
      channel.setDescription(getString(R.string.yggdrasil_notification_channel_desc));
      channel.setShowBadge(false);
      NotificationManager nm = getSystemService(NotificationManager.class);
      nm.createNotificationChannel(channel);
    }
  }
}
