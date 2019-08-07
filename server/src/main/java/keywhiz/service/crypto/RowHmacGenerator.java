package keywhiz.service.crypto;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import javax.crypto.SecretKey;
import javax.inject.Inject;
import javax.inject.Singleton;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Each call to ContentCryptographer.deriveKey would be a call to Provider, which is inefficient
 * if using an HSM. Instead of derive the key once and reuse the derived key with a Singleton
 */

@Singleton
public class RowHmacGenerator {
  private final ContentCryptographer cryptographer;
  private final SecretKey hmacKey;
  private final SecureRandom random;

  @Inject private RowHmacGenerator(ContentCryptographer cryptographer, SecureRandom random) {
    this.cryptographer = cryptographer;
    this.random = random;
    this.hmacKey = cryptographer.deriveKey(32, "row_hmac");
  }

  public String computeRowHmac(String table, String name, long id) {
    String hmacContent = table + "|" + name + "|" + id;
    return cryptographer.computeHmacWithSecretKey(hmacContent.getBytes(UTF_8), hmacKey);
  }

  public String computeRowHmac(String table, long id1, long id2) {
    String hmacContent = table + "|" + id1 + "|" + id2;
    return cryptographer.computeHmacWithSecretKey(hmacContent.getBytes(UTF_8), hmacKey);
  }

  public String computeRowHmac(String table, String content, String metadata, long id) {
    String hmacContent = table + "|" + content + "|" + metadata + "|" + id;
    return cryptographer.computeHmacWithSecretKey(hmacContent.getBytes(UTF_8), hmacKey);
  }

  /**
   * The random long generated with random.nextLong only uses 48 bits of randomness,
   * meaning it will not return all possible long values. Instead we generate a long from 8
   * random bytes.
   */
  public long getNextLongSecure() {
    byte[] generateIdBytes = new byte[8];
    random.nextBytes(generateIdBytes);
    ByteBuffer generateIdByteBuffer = ByteBuffer.wrap(generateIdBytes);
    return generateIdByteBuffer.getLong();
  }
}
