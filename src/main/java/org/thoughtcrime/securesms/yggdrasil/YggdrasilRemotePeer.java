package org.thoughtcrime.securesms.yggdrasil;

/** A peer advertised by the public Yggdrasil peer list. */
public final class YggdrasilRemotePeer {
  public final String uri;
  public final String country;
  public final Long rtt;
  public final Long lastChecked;

  public YggdrasilRemotePeer(String uri, String country) {
    this(uri, country, null, null);
  }

  public YggdrasilRemotePeer(String uri, String country, Long rtt, Long lastChecked) {
    this.uri = uri;
    this.country = country;
    this.rtt = rtt;
    this.lastChecked = lastChecked;
  }

  public YggdrasilRemotePeer withoutRtt() {
    return new YggdrasilRemotePeer(uri, country);
  }

  public YggdrasilRemotePeer withRtt(Long value) {
    return new YggdrasilRemotePeer(uri, country, value, System.currentTimeMillis());
  }
}
