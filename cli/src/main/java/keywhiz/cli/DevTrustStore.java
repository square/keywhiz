package keywhiz.cli;

import com.google.common.io.Resources;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public class DevTrustStore {
  private static final String store = "dev_and_test_truststore.p12";
  private static final String password = "ponies";

  /**
   * @return KeyStore which is embedded in the resources. This KeyStore works out of the box with
   * the server and is useful for development purpose. Don't use it in production.
   */
  public KeyStore getTrustStore() {
    return keyStoreFromResource(store, password);
  }

  /**
   * TODO: create a common sub-module and clean things up
   * (https://github.com/square/keywhiz/issues/93)
   */
  private static KeyStore keyStoreFromResource(String path, String password) {
    KeyStore keyStore;
    try (InputStream stream = Resources.getResource(path).openStream()) {
      keyStore = KeyStore.getInstance("PKCS12");
      keyStore.load(stream, password.toCharArray());
    } catch (IOException | NoSuchAlgorithmException | CertificateException | KeyStoreException e) {
      throw new AssertionError(e);
    }
    return keyStore;
  }
}
