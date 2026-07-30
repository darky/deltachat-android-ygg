package org.thoughtcrime.securesms;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class TelegramWebViewActivity extends WebViewActivity {

  public static final String CHANNEL_NAME_EXTRA = "channel_name";

  private String channelName;

  @Override
  protected void onCreate(Bundle state, boolean ready) {
    super.onCreate(state, ready);

    channelName = getIntent().getStringExtra(CHANNEL_NAME_EXTRA);
    if (channelName == null) {
      channelName = "";
    }

    if (getSupportActionBar() != null) {
      getSupportActionBar().setTitle("@" + channelName);
    }

    webView.getSettings().setJavaScriptEnabled(true);
    webView.getSettings().setDomStorageEnabled(true);
    webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

    webView.loadUrl("https://t.me/s/" + channelName);
  }

  @Override
  protected boolean openOnlineUrl(String url) {
    if (url != null && (url.startsWith("https://t.me/") || url.startsWith("http://t.me/"))) {
      return false;
    }
    return super.openOnlineUrl(url);
  }

  public static void open(android.content.Context context, String channelName) {
    Intent intent = new Intent(context, TelegramWebViewActivity.class);
    intent.putExtra(CHANNEL_NAME_EXTRA, channelName);
    context.startActivity(intent);
  }
}
