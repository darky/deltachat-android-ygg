package org.thoughtcrime.securesms.yggdrasil;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;

import org.thoughtcrime.securesms.BaseActionBarActivity;
import org.thoughtcrime.securesms.R;

import java.util.ArrayList;

public class YggdrasilPortForwardingActivity extends BaseActionBarActivity {

  private ArrayAdapter<String> adapter;
  private TextView headerText;
  private final ArrayList<String> mappings = new ArrayList<>();

  public static Intent createIntent(Context context) {
    return new Intent(context, YggdrasilPortForwardingActivity.class);
  }

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    setContentView(R.layout.yggdrasil_port_forwarding_activity);

    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setTitle(R.string.yggdrasil_port_forwarding);
      actionBar.setDisplayHomeAsUpEnabled(true);
    }

    headerText = findViewById(R.id.yggdrasil_pf_header);
    ListView listView = findViewById(R.id.yggdrasil_pf_list);
    Button addBtn = findViewById(R.id.yggdrasil_add_pf);

    adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, mappings);
    listView.setAdapter(adapter);

    listView.setOnItemLongClickListener(
        (parent, view, position, id) -> {
          String item = adapter.getItem(position);
          if (item != null) showDeleteMappingDialog(item);
          return true;
        });

    addBtn.setOnClickListener(v -> showAddMappingDialog());
    loadMappings();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      finish();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  private void loadMappings() {
    mappings.clear();
    mappings.addAll(YggdrasilManager.getSavedMappings());
    adapter.notifyDataSetChanged();
    headerText.setText(getString(R.string.yggdrasil_pf_header, mappings.size()));
  }

  private void showAddMappingDialog() {
    View view = getLayoutInflater().inflate(R.layout.yggdrasil_pf_dialog, null);
    EditText localPortInput = view.findViewById(R.id.yggdrasil_pf_local_port);
    EditText remoteHostInput = view.findViewById(R.id.yggdrasil_pf_remote_host);
    EditText remotePortInput = view.findViewById(R.id.yggdrasil_pf_remote_port);

    new AlertDialog.Builder(this)
        .setTitle(R.string.yggdrasil_add_pf)
        .setMessage(R.string.yggdrasil_add_pf_explain)
        .setView(view)
        .setPositiveButton(
            R.string.ok,
            (dialog, which) -> {
              try {
                int localPort = Integer.parseInt(localPortInput.getText().toString().trim());
                String remoteHost = remoteHostInput.getText().toString().trim();
                int remotePort = Integer.parseInt(remotePortInput.getText().toString().trim());

                String localAddr = "127.0.0.1:" + localPort;
                String remoteAddr = "[" + remoteHost + "]:" + remotePort;
                YggdrasilManager.addLocalTCPMapping(localAddr, remoteAddr);

                mappings.add(localAddr + " -> [" + remoteHost + "]:" + remotePort);
                adapter.notifyDataSetChanged();
                headerText.setText(getString(R.string.yggdrasil_pf_header, mappings.size()));
              } catch (NumberFormatException e) {
                // ignore invalid input
              }
            })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void showDeleteMappingDialog(String mapping) {
    new AlertDialog.Builder(this)
        .setTitle(R.string.yggdrasil_remove_pf)
        .setMessage(getString(R.string.yggdrasil_remove_pf_explain, mapping))
        .setPositiveButton(
            R.string.delete,
            (dialog, which) -> {
              try {
                String[] parts = mapping.split(" -> ");
                String localAddr = parts[0].trim();
                String remoteAddr = parts[1].trim();
                YggdrasilManager.removeLocalTCPMapping(localAddr, remoteAddr);
                mappings.remove(mapping);
                adapter.notifyDataSetChanged();
                headerText.setText(getString(R.string.yggdrasil_pf_header, mappings.size()));
              } catch (Exception e) {
                // ignore
              }
            })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }
}
