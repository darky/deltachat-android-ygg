package org.thoughtcrime.securesms.yggdrasil;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.ActionBar;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.thoughtcrime.securesms.BaseActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;

public class YggdrasilSettingsActivity extends BaseActionBarActivity {

  private TextView statusText;
  private TextView ipv6Text;
  private TextView pubkeyText;
  private TextView peerCountText;
  private TextView remotePeersStatus;
  private Button toggleBtn;
  private Button fetchRemotePeersBtn;
  private Button checkRemotePeersBtn;
  private ProgressBar remotePeersProgress;
  private LinearLayout remotePeersList;
  private Runnable statusListener;

  private final ExecutorService remotePeerExecutor = Executors.newSingleThreadExecutor();
  private YggdrasilRemotePeerService remotePeerService;
  private Future<?> remoteOperation;
  private List<YggdrasilRemotePeer> remotePeers = new ArrayList<>();
  private volatile int remoteOperationGeneration;
  private volatile boolean destroyed;
  private boolean remotePeerOperationBusy;
  private boolean rttCheckRunning;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    setContentView(R.layout.yggdrasil_settings_activity);

    YggdrasilManager.initializeStorage(this);
    remotePeerService = new YggdrasilRemotePeerService(this);

    ViewUtil.applyWindowInsets(findViewById(R.id.content_container), true, true, true, true);

    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setTitle(R.string.yggdrasil);
      actionBar.setDisplayHomeAsUpEnabled(true);
    }

    statusText = findViewById(R.id.yggdrasil_status_text);
    ipv6Text = findViewById(R.id.yggdrasil_ipv6);
    pubkeyText = findViewById(R.id.yggdrasil_pubkey);
    peerCountText = findViewById(R.id.yggdrasil_peer_count);
    toggleBtn = findViewById(R.id.yggdrasil_toggle);
    Button peersBtn = findViewById(R.id.yggdrasil_peers_btn);
    fetchRemotePeersBtn = findViewById(R.id.yggdrasil_fetch_remote_peers);
    checkRemotePeersBtn = findViewById(R.id.yggdrasil_check_remote_peers);
    remotePeersProgress = findViewById(R.id.yggdrasil_remote_peers_progress);
    remotePeersStatus = findViewById(R.id.yggdrasil_remote_peers_status);
    remotePeersList = findViewById(R.id.yggdrasil_remote_peers_list);
    Button pfBtn = findViewById(R.id.yggdrasil_port_forwarding_btn);

    toggleBtn.setOnClickListener(
        v -> {
          if (YggdrasilManager.isRunning()) {
            YggdrasilForegroundService.stopSelf(this);
          } else {
            YggdrasilForegroundService.startSelf(this);
          }
          updateUi();
        });

    peersBtn.setOnClickListener(v -> startActivity(YggdrasilPeersActivity.createIntent(this)));

    fetchRemotePeersBtn.setOnClickListener(v -> fetchRemotePeers());
    checkRemotePeersBtn.setOnClickListener(v -> checkRemotePeers());

    pfBtn.setOnClickListener(
        v -> startActivity(YggdrasilPortForwardingActivity.createIntent(this)));

    statusListener = this::updateUi;
    YggdrasilManager.addStatusListener(statusListener);
    loadCachedRemotePeers();
    updateUi();
  }

  @Override
  public void onDestroy() {
    destroyed = true;
    remoteOperationGeneration++;
    if (remoteOperation != null) remoteOperation.cancel(true);
    remotePeerExecutor.shutdownNow();
    YggdrasilManager.removeStatusListener(statusListener);
    super.onDestroy();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      finish();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  private void updateUi() {
    boolean running = YggdrasilManager.isRunning();
    toggleBtn.setText(running ? R.string.yggdrasil_stop : R.string.yggdrasil_start);
    statusText.setText(
        running ? R.string.yggdrasil_status_running : R.string.yggdrasil_status_stopped);

    if (running) {
      String addr = YggdrasilManager.getAddress();
      ipv6Text.setText(
          addr != null && !addr.isEmpty() ? getString(R.string.yggdrasil_ipv6_label, addr) : "");
      String pk = YggdrasilManager.getPublicKey();
      pubkeyText.setText(
          pk != null && !pk.isEmpty()
              ? getString(
                  R.string.yggdrasil_pubkey_label,
                  pk.substring(0, Math.min(pk.length(), 16)) + "...")
              : "");
      peerCountText.setText(
          getString(R.string.yggdrasil_peers_count, YggdrasilManager.getPeerCount()));
    } else {
      ipv6Text.setText("");
      pubkeyText.setText("");
      peerCountText.setText("");
    }
  }

  private void loadCachedRemotePeers() {
    final int generation = remoteOperationGeneration;
    remotePeerExecutor.execute(
        () -> {
          List<YggdrasilRemotePeer> cachedPeers = remotePeerService.loadCachedPeers();
          runOnRemotePeersUi(
              generation,
              () -> {
                if (!remotePeers.isEmpty()) return;
                remotePeers = cachedPeers;
                renderRemotePeers();
                if (!cachedPeers.isEmpty()) {
                  remotePeersStatus.setText(
                      getString(R.string.yggdrasil_remote_peers_cached, cachedPeers.size()));
                }
              });
        });
  }

  private void fetchRemotePeers() {
    if (remotePeerOperationBusy) return;

    final int generation = ++remoteOperationGeneration;
    rttCheckRunning = false;
    setRemotePeersBusy(true, true);
    remotePeersStatus.setText(R.string.yggdrasil_remote_peers_fetching);

    remoteOperation =
        remotePeerExecutor.submit(
            () -> {
              try {
                List<YggdrasilRemotePeer> fetchedPeers = remotePeerService.fetchRemotePeers();
                remotePeerService.saveCachedPeers(fetchedPeers);
                runOnRemotePeersUi(
                    generation,
                    () -> {
                      remotePeers = fetchedPeers;
                      renderRemotePeers();
                      remotePeersStatus.setText(
                          getString(R.string.yggdrasil_remote_peers_fetched, fetchedPeers.size()));
                    });
              } catch (Exception e) {
                runOnRemotePeersUi(
                    generation,
                    () ->
                        remotePeersStatus.setText(
                            getString(
                                R.string.yggdrasil_remote_peers_fetch_failed, errorMessage(e))));
              } finally {
                runOnRemotePeersUi(
                    generation,
                    () -> {
                      remoteOperation = null;
                      setRemotePeersBusy(false, false);
                    });
              }
            });
  }

  private void checkRemotePeers() {
    if (rttCheckRunning) {
      cancelRemotePeerCheck();
      return;
    }
    if (remotePeerOperationBusy || remotePeers.isEmpty()) return;

    final int generation = ++remoteOperationGeneration;
    final List<YggdrasilRemotePeer> peersToCheck = new ArrayList<>();
    for (YggdrasilRemotePeer peer : remotePeers) {
      peersToCheck.add(peer.withoutRtt());
    }

    rttCheckRunning = true;
    setRemotePeersBusy(true, false);
    remotePeersProgress.setIndeterminate(false);
    remotePeersProgress.setProgress(0);
    remotePeersStatus.setText(
        getString(R.string.yggdrasil_remote_peers_checking, 0, peersToCheck.size()));

    remoteOperation =
        remotePeerExecutor.submit(
            () -> {
              try {
                List<YggdrasilRemotePeer> sortedPeers =
                    remotePeerService.checkPeers(
                        peersToCheck,
                        new YggdrasilRemotePeerService.RttProgressListener() {
                          @Override
                          public boolean isCancelled() {
                            return Thread.currentThread().isInterrupted()
                                || destroyed
                                || generation != remoteOperationGeneration;
                          }

                          @Override
                          public void onProgress(int checked, int total) {
                            runOnRemotePeersUi(
                                generation,
                                () -> {
                                  remotePeersProgress.setProgress(checked * 100 / total);
                                  remotePeersStatus.setText(
                                      getString(
                                          R.string.yggdrasil_remote_peers_checking,
                                          checked,
                                          total));
                                });
                          }
                        });

                runOnRemotePeersUi(
                    generation,
                    () -> {
                      remotePeers = sortedPeers;
                      renderRemotePeers();
                      remotePeersStatus.setText(
                          getString(R.string.yggdrasil_remote_peers_checked, sortedPeers.size()));
                    });
              } catch (CancellationException ignored) {
                // The activity is being destroyed or a newer operation superseded this one.
              } catch (Exception e) {
                runOnRemotePeersUi(
                    generation,
                    () ->
                        remotePeersStatus.setText(
                            getString(
                                R.string.yggdrasil_remote_peers_check_failed, errorMessage(e))));
              } finally {
                runOnRemotePeersUi(
                    generation,
                    () -> {
                      remoteOperation = null;
                      rttCheckRunning = false;
                      setRemotePeersBusy(false, false);
                    });
              }
            });
  }

  private void cancelRemotePeerCheck() {
    if (!rttCheckRunning) return;

    remoteOperationGeneration++;
    if (remoteOperation != null) remoteOperation.cancel(true);
    remoteOperation = null;
    rttCheckRunning = false;
    remotePeerOperationBusy = false;
    remotePeersProgress.setVisibility(View.GONE);
    remotePeersProgress.setProgress(0);
    checkRemotePeersBtn.setText(R.string.yggdrasil_check_rtt);
    checkRemotePeersBtn.setEnabled(!remotePeers.isEmpty());
    fetchRemotePeersBtn.setEnabled(true);
    remotePeersStatus.setText(R.string.yggdrasil_remote_peers_cancelled);
  }

  private void renderRemotePeers() {
    remotePeersList.removeAllViews();

    Set<String> configuredPeers = new HashSet<>();
    for (YggdrasilManager.PeerEntry peer : YggdrasilManager.getAllPeers()) {
      configuredPeers.add(peer.uri);
    }

    for (YggdrasilRemotePeer peer : remotePeers) {
      addRemotePeerView(peer, configuredPeers.contains(peer.uri));
    }

    checkRemotePeersBtn.setText(
        rttCheckRunning ? R.string.yggdrasil_cancel_rtt : R.string.yggdrasil_check_rtt);
    checkRemotePeersBtn.setEnabled(
        !remotePeers.isEmpty() && (!remotePeerOperationBusy || rttCheckRunning));
  }

  private void addRemotePeerView(YggdrasilRemotePeer peer, boolean alreadyAdded) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setPadding(0, 8, 0, 8);

    LinearLayout details = new LinearLayout(this);
    details.setOrientation(LinearLayout.VERTICAL);
    details.setLayoutParams(
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

    TextView uriText = new TextView(this);
    uriText.setText(peer.uri);
    uriText.setTextSize(14);

    TextView metadataText = new TextView(this);
    metadataText.setText(buildRemotePeerMetadata(peer));
    metadataText.setTextSize(13);

    details.addView(uriText);
    details.addView(metadataText);

    Button addButton = new Button(this);
    addButton.setText(
        alreadyAdded ? R.string.yggdrasil_remote_peer_added : R.string.yggdrasil_add_peer);
    addButton.setEnabled(!alreadyAdded);
    addButton.setOnClickListener(
        v -> {
          YggdrasilManager.addPeer(peer.uri);
          v.setEnabled(false);
          ((Button) v).setText(R.string.yggdrasil_remote_peer_added);
        });

    row.addView(details);
    row.addView(addButton);
    remotePeersList.addView(row);
  }

  private String buildRemotePeerMetadata(YggdrasilRemotePeer peer) {
    StringBuilder metadata = new StringBuilder(peer.country);
    if (metadata.length() > 0) metadata.append("  |  ");

    if (peer.rtt != null) {
      metadata.append(getString(R.string.yggdrasil_remote_peer_rtt, peer.rtt));
    } else if (peer.lastChecked != null) {
      metadata.append(getString(R.string.yggdrasil_remote_peer_unreachable));
    } else {
      metadata.append(getString(R.string.yggdrasil_remote_peer_not_checked));
    }
    return metadata.toString();
  }

  private void setRemotePeersBusy(boolean busy, boolean indeterminate) {
    remotePeerOperationBusy = busy;
    remotePeersProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
    remotePeersProgress.setIndeterminate(indeterminate);
    if (!busy) remotePeersProgress.setProgress(0);
    fetchRemotePeersBtn.setEnabled(!busy);
    checkRemotePeersBtn.setText(
        busy && rttCheckRunning ? R.string.yggdrasil_cancel_rtt : R.string.yggdrasil_check_rtt);
    checkRemotePeersBtn.setEnabled(busy ? rttCheckRunning : !remotePeers.isEmpty());
  }

  private void runOnRemotePeersUi(int generation, Runnable action) {
    Util.runOnMain(
        () -> {
          if (!destroyed && generation == remoteOperationGeneration) action.run();
        });
  }

  private String errorMessage(Exception exception) {
    String message = exception.getMessage();
    return message == null || message.isEmpty() ? exception.getClass().getSimpleName() : message;
  }
}
