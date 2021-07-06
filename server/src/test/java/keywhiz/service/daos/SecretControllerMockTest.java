package keywhiz.service.daos;

import java.util.Base64;
import javax.swing.text.AbstractDocument;
import keywhiz.KeywhizConfig;
import keywhiz.service.crypto.ContentCryptographer;
import keywhiz.service.crypto.SecretTransformer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SecretControllerMockTest {
  private static final Long MAXIMUM_SECRET_SIZE_IN_BYTES_INCLUSIVE = 100L;
  private static final long NO_EXPIRY = 0;
  private static final Long NO_MAXIMUM_SECRET_SIZE = null;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void rejectsSecretWithSizeGreaterThanMaximumSecretSize() {
    byte[] secret = new byte[(int) (long) MAXIMUM_SECRET_SIZE_IN_BYTES_INCLUSIVE + 1];

    SecretController controller = createController(MAXIMUM_SECRET_SIZE_IN_BYTES_INCLUSIVE);

    thrown.expect(IllegalArgumentException.class);

    createSecret(controller, secret);
  }

  @Test
  public void acceptsSecretWithSizeEqualToMaximumSize() {
    byte[] secret = new byte[(int) (long) MAXIMUM_SECRET_SIZE_IN_BYTES_INCLUSIVE];

    SecretController controller = createController(MAXIMUM_SECRET_SIZE_IN_BYTES_INCLUSIVE);

    createSecret(controller, secret);
  }

  @Test
  public void acceptsSecretWithSizeLessThanMaximumSize() {
    byte[] secret = new byte[(int) (long) MAXIMUM_SECRET_SIZE_IN_BYTES_INCLUSIVE - 1];

    SecretController controller = createController(MAXIMUM_SECRET_SIZE_IN_BYTES_INCLUSIVE);

    createSecret(controller, secret);
  }

  @Test
  public void acceptsLargeSecretIfNoMaximumSizeSpecified() {
    // 65KB
    byte[] secret = new byte[65 * (int) Math.pow(2, 10)];

    SecretController controller = createController(NO_MAXIMUM_SECRET_SIZE);

    createSecret(controller, secret);
  }

  private void createSecret(SecretController controller, byte[] secret) {
    String base64EncodedSecret = Base64.getEncoder().encodeToString(secret);
    controller.builder("name", base64EncodedSecret, "creator", NO_EXPIRY);
  }

  private SecretController createController(Long maximumSecretSizeInBytesInclusive) {
    KeywhizConfig config = new KeywhizConfig();
    config.setMaximumSecretSizeInBytesInclusive(maximumSecretSizeInBytesInclusive);

    ContentCryptographer cryptographer = mock(ContentCryptographer.class);
    when(cryptographer.computeHmac(any(), any())).thenReturn("hmac");
    when(cryptographer.encryptionKeyDerivedFrom(any()))
        .thenReturn(mock(ContentCryptographer.Encrypter.class));

    return new SecretController(
        mock(SecretTransformer.class),
        cryptographer,
        mock(SecretDAO.class),
        mock(AclDAO.class),
        config);
  }
}
