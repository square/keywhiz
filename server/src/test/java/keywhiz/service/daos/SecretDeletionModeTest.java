package keywhiz.service.daos;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class SecretDeletionModeTest {
  @Test
  public void valueOfIgnoreCaseAcceptsUppercase() {
    assertEquals(SecretDeletionMode.SOFT, SecretDeletionMode.valueOfIgnoreCase("SOFT"));
  }

  @Test
  public void valueOfIgnoreCaseAcceptsLowercase() {
    assertEquals(SecretDeletionMode.SOFT, SecretDeletionMode.valueOfIgnoreCase("soft"));
  }

  @Test
  public void valueOfIgnoreCaseRejectsUnknownValue() {
    assertThrows(IllegalArgumentException.class, () -> SecretDeletionMode.valueOfIgnoreCase("foo"));
  }
}
