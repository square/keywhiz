package keywhiz.service.crypto;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
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

  public String computeRowHmac(String table, List<Object> fields) {
    String joinedFields = fields.stream()
        .map(object -> Objects.toString(object, null))
        .collect(Collectors.joining("|"));
    String hmacContent = table + "|" + joinedFields;
    return cryptographer.computeHmacWithSecretKey(hmacContent.getBytes(UTF_8), hmacKey);
  }

  /**
   * Checks whether two HMACs are equal.
   *
   * Uses MessageDigest to prevent timing attacks.
   * */
  public static boolean compareHmacs(String left, String right) {
    return MessageDigest.isEqual(
        left.getBytes(UTF_8),
        right.getBytes(UTF_8)
    );
  }

  /**
   * The random long generated with random.nextLong only uses 48 bits of randomness,
   * meaning it will not return all possible long values. Instead we generate a long from 8
   * random bytes.
   *
   * @return a randomly generated long
   */
  public long getNextLongSecure() {
    byte[] generateIdBytes = new byte[8];
    random.nextBytes(generateIdBytes);
    ByteBuffer generateIdByteBuffer = ByteBuffer.wrap(generateIdBytes);
    return generateIdByteBuffer.getLong();
  }
}
