package org.thoughtcrime.securesms.yggdrasil;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;

import org.thoughtcrime.securesms.BaseActionBarActivity;
import org.thoughtcrime.securesms.R;

public class YggdrasilSettingsActivity extends BaseActionBarActivity {

  private TextView statusText;
  private TextView ipv6Text;
  private TextView pubkeyText;
  private TextView peerCountText;
  private Button toggleBtn;
  private Runnable statusListener;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    setContentView(R.layout.yggdrasil_settings_activity);

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
    Button pfBtn = findViewById(R.id.yggdrasil_port_forwarding_btn);

    toggleBtn.setOnClickListener(
        v -> {
          if (YggdrasilManager.isRunning()) {
            YggdrasilManager.stop();
          } else {
            YggdrasilManager.start();
          }
          updateUi();
        });

    peersBtn.setOnClickListener(
        v -> startActivity(YggdrasilPeersActivity.createIntent(this)));

    pfBtn.setOnClickListener(
        v -> startActivity(YggdrasilPortForwardingActivity.createIntent(this)));

    statusListener = this::updateUi;
    YggdrasilManager.addStatusListener(statusListener);
    updateUi();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    YggdrasilManager.removeStatusListener(statusListener);
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
    statusText.setText(running ? R.string.yggdrasil_status_running : R.string.yggdrasil_status_stopped);

    if (running) {
      String addr = YggdrasilManager.getAddress();
      ipv6Text.setText(addr != null && !addr.isEmpty()
          ? getString(R.string.yggdrasil_ipv6_label, addr) : "");
      String pk = YggdrasilManager.getPublicKey();
      pubkeyText.setText(pk != null && !pk.isEmpty()
          ? getString(R.string.yggdrasil_pubkey_label, pk.substring(0, Math.min(pk.length(), 16)) + "...")
          : "");
      peerCountText.setText(getString(R.string.yggdrasil_peers_count, YggdrasilManager.getPeerCount()));
    } else {
      ipv6Text.setText("");
      pubkeyText.setText("");
      peerCountText.setText("");
    }
  }
}
