package org.thoughtcrime.securesms.yggdrasil;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;

import org.json.JSONArray;
import org.thoughtcrime.securesms.BaseActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.ArrayList;
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
          List<String> peers = getPeersList();
          Util.runOnMain(
              () -> {
                listContainer.removeAllViews();
                headerText.setText(getString(R.string.yggdrasil_peers_header, peers.size()));
                for (String peer : peers) {
                  addPeerView(peer);
                }
              });
        });
  }

  private void addPeerView(String peer) {
    TextView tv = new TextView(this);
    tv.setText(peer);
    tv.setPadding(0, 12, 0, 12);
    tv.setTextSize(16);
    tv.setOnLongClickListener(
        v -> {
          showDeleteDialog(peer);
          return true;
        });
    listContainer.addView(tv);
  }

  private List<String> getPeersList() {
    List<String> result = new ArrayList<>();
    try {
      String json = YggdrasilManager.getConfiguredPeers();
      if (json == null || json.equals("[]") || json.equals("null")) return result;
      JSONArray arr = new JSONArray(json);
      for (int i = 0; i < arr.length(); i++) {
        String peer = arr.optString(i, null);
        if (peer != null) result.add(peer);
      }
    } catch (Exception e) {
      // ignore
    }
    return result;
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
