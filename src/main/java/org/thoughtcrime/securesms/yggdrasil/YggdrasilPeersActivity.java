package org.thoughtcrime.securesms.yggdrasil;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;

import org.thoughtcrime.securesms.BaseActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.List;

public class YggdrasilPeersActivity extends BaseActionBarActivity {

  private LinearLayout listContainer;
  private TextView headerText;

  public static Intent createIntent(Context context) {
    return new Intent(context, YggdrasilPeersActivity.class);
  }

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    setContentView(R.layout.yggdrasil_peers_activity);

    ViewUtil.applyWindowInsets(findViewById(R.id.content_container), true, true, true, true);

    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setTitle(R.string.yggdrasil_peers);
      actionBar.setDisplayHomeAsUpEnabled(true);
    }

    headerText = findViewById(R.id.yggdrasil_peers_header);
    listContainer = findViewById(R.id.yggdrasil_peers_list);
    Button addBtn = findViewById(R.id.yggdrasil_add_peer);

    addBtn.setOnClickListener(v -> showAddDialog());
    loadPeers();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      finish();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  private void loadPeers() {
    Util.runOnAnyBackgroundThread(
        () -> {
          List<YggdrasilManager.PeerEntry> peers = YggdrasilManager.getAllPeers();
          Util.runOnMain(
              () -> {
                listContainer.removeAllViews();
                headerText.setText(getString(R.string.yggdrasil_peers_header, peers.size()));
                for (YggdrasilManager.PeerEntry peer : peers) {
                  addPeerView(peer);
                }
              });
        });
  }

  private void addPeerView(YggdrasilManager.PeerEntry peer) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setPadding(0, 8, 0, 8);

    CheckBox checkBox = new CheckBox(this);
    checkBox.setChecked(peer.active);

    TextView tv = new TextView(this);
    tv.setText(peer.uri);
    tv.setTextSize(16);
    tv.setPadding(16, 0, 0, 0);
    tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

    row.addView(checkBox);
    row.addView(tv);

    checkBox.setOnCheckedChangeListener(
        (buttonView, isChecked) -> {
          YggdrasilManager.setPeerActive(peer.uri, isChecked);
        });

    row.setOnLongClickListener(
        v -> {
          showDeleteDialog(peer.uri);
          return true;
        });

    listContainer.addView(row);
  }

  private void showAddDialog() {
    View view = getLayoutInflater().inflate(R.layout.single_line_input, null);
    EditText inputField = view.findViewById(R.id.input_field);
    inputField.setHint("tls://host:port");

    new AlertDialog.Builder(this)
        .setTitle(R.string.yggdrasil_add_peer)
        .setMessage(R.string.yggdrasil_add_peer_explain)
        .setView(view)
        .setPositiveButton(
            R.string.ok,
            (dialog, which) -> {
              String uri = inputField.getText().toString().trim();
              if (!uri.isEmpty()) {
                YggdrasilManager.addPeer(uri);
                loadPeers();
              }
            })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void showDeleteDialog(String peer) {
    new AlertDialog.Builder(this)
        .setTitle(R.string.yggdrasil_remove_peer)
        .setMessage(getString(R.string.yggdrasil_remove_peer_explain, peer))
        .setPositiveButton(
            R.string.delete,
            (dialog, which) -> {
              YggdrasilManager.removePeer(peer);
              loadPeers();
            })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }
}
