package keywhiz.service.crypto;

import com.google.inject.Provides;
import javax.crypto.SecretKey;
import javax.inject.Inject;
import javax.inject.Singleton;

import static java.nio.charset.StandardCharsets.UTF_8;

public class RowHmacGenerator {
  private final ContentCryptographer cryptographer;
  private final SecretKey hmacKey;

  @Inject private RowHmacGenerator(ContentCryptographer cryptographer) {
    this.cryptographer = cryptographer;
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

  public long getNextLongSecure() {
    return cryptographer.getNextLongSecure();
  }

  @Provides @Singleton
  public RowHmacGenerator getInstance() {
    return new RowHmacGenerator(cryptographer);
  }

  @Provides @Singleton
  public static RowHmacGenerator getInstance(ContentCryptographer cryptographer) {
    return new RowHmacGenerator(cryptographer);
  }
}
