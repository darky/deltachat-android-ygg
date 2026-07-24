package org.thoughtcrime.securesms.bcrypt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mindrot.jbcrypt.BCrypt;

@RunWith(JUnit4.class)
public class BcryptTest {

  @Test
  public void hashPasswordWithDefaultRounds() {
    String hash = BCrypt.hashpw("password", BCrypt.gensalt());
    assertThat(hash).startsWith("$2a$10$");
  }

  @Test
  public void hashPasswordWith14Rounds() {
    String hash = BCrypt.hashpw("password", BCrypt.gensalt(14));
    assertThat(hash).startsWith("$2a$14$");
    assertThat(BCrypt.checkpw("password", hash)).isTrue();
    assertThat(BCrypt.checkpw("wrong", hash)).isFalse();
  }

  @Test
  public void differentHashesForSamePassword() {
    String hash1 = BCrypt.hashpw("password", BCrypt.gensalt(8));
    String hash2 = BCrypt.hashpw("password", BCrypt.gensalt(8));
    assertThat(hash1).isNotEqualTo(hash2);
  }

  @Test
  public void emptyPassword() {
    String hash = BCrypt.hashpw("", BCrypt.gensalt(8));
    assertThat(hash).startsWith("$2a$08$");
    assertThat(BCrypt.checkpw("", hash)).isTrue();
  }

  @Test
  public void unicodePassword() {
    String password = "caf\u00e9\u2603\u00f1";
    String hash = BCrypt.hashpw(password, BCrypt.gensalt(8));
    assertThat(BCrypt.checkpw(password, hash)).isTrue();
    assertThat(BCrypt.checkpw("cafe", hash)).isFalse();
  }

  @Test
  public void longPassword() {
    String password = "A".repeat(72);
    String hash = BCrypt.hashpw(password, BCrypt.gensalt(8));
    assertThat(BCrypt.checkpw(password, hash)).isTrue();
  }
}
