package org.thoughtcrime.securesms;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import com.google.android.material.textfield.TextInputEditText;
import org.thoughtcrime.securesms.util.Prefs;
import org.thoughtcrime.securesms.util.ViewUtil;

public class TelegramChannelActivity extends PassphraseRequiredActionBarActivity {

  public static final String EXTRA_CHANNEL_NAME = "channel_name";
  public static final String EXTRA_CHANNEL_ADDED = "channel_added";

  private TextInputEditText channelInput;

  @Override
  protected void onCreate(Bundle state, boolean ready) {
    setContentView(R.layout.telegram_channel_activity);

    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setTitle(R.string.add_telegram_channel);
      actionBar.setDisplayHomeAsUpEnabled(true);
      actionBar.setHomeAsUpIndicator(R.drawable.ic_close_white_24dp);
    }

    ViewUtil.applyWindowInsets(findViewById(R.id.content_container));

    channelInput = ViewUtil.findById(this, R.id.channel_text);
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
    menu.clear();
    inflater.inflate(R.menu.telegram_channel, menu);
    super.onPrepareOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    super.onOptionsItemSelected(item);
    int itemId = item.getItemId();
    if (itemId == android.R.id.home) {
      finish();
      return true;
    } else if (itemId == R.id.menu_add_channel) {
      String channelName =
          channelInput.getText() == null ? "" : channelInput.getText().toString().trim();
      if (channelName.isEmpty()) {
        Toast.makeText(this, R.string.login_error_mail, Toast.LENGTH_SHORT).show();
        return true;
      }
      channelName = channelName.replaceAll("^@+", "");
      if (Prefs.hasTelegramChannel(this, channelName)) {
        Toast.makeText(this, "Channel already added", Toast.LENGTH_SHORT).show();
        return true;
      }
      Prefs.addTelegramChannel(this, channelName);
      Toast.makeText(this, getString(R.string.saved), Toast.LENGTH_SHORT).show();
      setResult(RESULT_OK);
      finish();
      return true;
    }
    return false;
  }
}
