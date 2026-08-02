package org.thoughtcrime.securesms.yggdrasil;

import static org.assertj.core.api.Assertions.assertThat;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class YggdrasilRemotePeerServiceTest {

  @Test
  public void parsesAllPeerUrisAndCountryNames() throws Exception {
    String json =
        "{"
            + "\"united-states.md\":{\"tls://one.example:443\":{\"key\":\"ignored\"}},"
            + "\"germany.md\":{\"tcp://two.example:1234\":{}}"
            + "}";

    List<YggdrasilRemotePeer> peers = YggdrasilRemotePeerService.parsePublicNodesJson(json);

    assertThat(peers).hasSize(2);
    assertThat(findPeer(peers, "tls://one.example:443").country).isEqualTo("United States");
    assertThat(findPeer(peers, "tcp://two.example:1234").country).isEqualTo("Germany");
  }

  @Test
  public void sortsPeersByRttAndPlacesFailuresLast() {
    List<YggdrasilRemotePeer> peers =
        new ArrayList<>(
            Arrays.asList(
                new YggdrasilRemotePeer("tls://slow:1", "", 200L, 1L),
                new YggdrasilRemotePeer("tls://failed:2", "", null, 1L),
                new YggdrasilRemotePeer("tls://fast:3", "", 20L, 1L),
                new YggdrasilRemotePeer("tls://middle:4", "", 80L, 1L)));

    YggdrasilRemotePeerService.sortPeers(peers);

    List<String> uris = new ArrayList<>();
    for (YggdrasilRemotePeer peer : peers) {
      uris.add(peer.uri);
    }
    assertThat(uris)
        .containsExactly("tls://fast:3", "tls://middle:4", "tls://slow:1", "tls://failed:2");
  }

  @Test
  public void reportsOneCompletionForEveryPeer() {
    Context context =
        new ContextWrapper(null) {
          @Override
          public Context getApplicationContext() {
            return this;
          }

          @Override
          public SharedPreferences getSharedPreferences(String name, int mode) {
            return null;
          }
        };

    List<YggdrasilRemotePeer> peers =
        Arrays.asList(
            new YggdrasilRemotePeer("not-a-peer-1", ""),
            new YggdrasilRemotePeer("not-a-peer-2", ""),
            new YggdrasilRemotePeer("not-a-peer-3", ""));
    AtomicInteger completions = new AtomicInteger();

    List<YggdrasilRemotePeer> results =
        new YggdrasilRemotePeerService(context)
            .checkPeers(
                peers,
                new YggdrasilRemotePeerService.RttProgressListener() {
                  @Override
                  public boolean isCancelled() {
                    return false;
                  }

                  @Override
                  public void onProgress(int checked, int total) {
                    completions.incrementAndGet();
                  }
                });

    assertThat(results).hasSize(peers.size());
    assertThat(completions.get()).isEqualTo(peers.size());
  }

  private static YggdrasilRemotePeer findPeer(List<YggdrasilRemotePeer> peers, String uri) {
    for (YggdrasilRemotePeer peer : peers) {
      if (uri.equals(peer.uri)) return peer;
    }
    throw new AssertionError("Peer not found: " + uri);
  }
}
