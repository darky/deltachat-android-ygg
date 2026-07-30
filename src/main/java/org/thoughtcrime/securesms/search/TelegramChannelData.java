package org.thoughtcrime.securesms.search;

import androidx.annotation.NonNull;

public class TelegramChannelData {

  public static final int TELEGRAM_CHANNEL_VIRTUAL_ID_BASE = -1000;

  private final @NonNull String channelName;
  private final int virtualId;

  public TelegramChannelData(@NonNull String channelName, int index) {
    this.channelName = channelName;
    this.virtualId = TELEGRAM_CHANNEL_VIRTUAL_ID_BASE - index;
  }

  @NonNull
  public String getChannelName() {
    return channelName;
  }

  @NonNull
  public String getDisplayTitle() {
    return channelName;
  }

  @NonNull
  public String getDisplaySubtitle() {
    return "https://t.me/s/" + channelName;
  }

  public int getVirtualId() {
    return virtualId;
  }

  public static boolean isTelegramChannelId(long chatId) {
    return chatId <= TELEGRAM_CHANNEL_VIRTUAL_ID_BASE && chatId > TELEGRAM_CHANNEL_VIRTUAL_ID_BASE - 1000;
  }

  public static int getChannelIndex(long chatId) {
    return TELEGRAM_CHANNEL_VIRTUAL_ID_BASE - (int) chatId;
  }
}
