package org.thoughtcrime.securesms.bcrypt;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.ActionBar;
import com.google.android.material.textfield.TextInputEditText;
import org.mindrot.jbcrypt.BCrypt;
import org.thoughtcrime.securesms.BaseActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ViewUtil;

public class BcryptActivity extends BaseActionBarActivity {

  private TextInputEditText passwordEdit;
  private TextInputEditText roundsEdit;
  private TextView resultLabel;
  private TextView resultText;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    setContentView(R.layout.bcrypt_activity);

    ViewUtil.applyWindowInsets(findViewById(R.id.content_container), true, true, true, true);

    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setTitle(R.string.bcrypt);
      actionBar.setDisplayHomeAsUpEnabled(true);
    }

    passwordEdit = findViewById(R.id.bcrypt_password);
    roundsEdit = findViewById(R.id.bcrypt_rounds);
    Button generateBtn = findViewById(R.id.bcrypt_generate);
    resultLabel = findViewById(R.id.bcrypt_result_label);
    resultText = findViewById(R.id.bcrypt_result);

    generateBtn.setOnClickListener(
        v -> {
          String password = passwordEdit.getText().toString();
          String roundsStr = roundsEdit.getText().toString();

          if (password.isEmpty()) {
            return;
          }

          int rounds;
          try {
            rounds = Integer.parseInt(roundsStr);
          } catch (NumberFormatException e) {
            rounds = 14;
          }
          if (rounds < 4) rounds = 4;
          if (rounds > 30) rounds = 30;

          String hash = BCrypt.hashpw(password, BCrypt.gensalt(rounds));

          resultLabel.setVisibility(View.VISIBLE);
          resultText.setText(hash);
          resultText.setVisibility(View.VISIBLE);
        });
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      finish();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }
}
