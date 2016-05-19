package keywhiz.utility;

import com.google.common.base.Splitter;
import com.google.common.io.BaseEncoding;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableEntryException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import keywhiz.KeywhizConfig;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * We re-issued an intermediate certificate. Instead of re-issuing all the leaf certificates, we
 * decided to search & replace the old intermediate with the new one.
 *
 * This code looks for the old intermediate and replaces it with the new one. The code handles
 * JCEKS, PKCS12 and concatenated PEM files.
 *
 * It might all sound crazy, but we plan to re-use some of this to track expiry. Killing two birds
 * with one stone.
 */
public class ReplaceIntermediateCertificate {
  List<String> passwords;
  X509Certificate oldCertificate;
  X509Certificate newCertificate;

  @Inject public ReplaceIntermediateCertificate(KeywhizConfig config) throws IOException, CertificateException {
    byte[] oldCertificate = Files.readAllBytes(new File(config.replaceIntermediateCertificate.oldCertificate).toPath());
    this.oldCertificate = fromBytes(oldCertificate);

    byte[] newCertificate = Files.readAllBytes(new File(config.replaceIntermediateCertificate.newCertificate).toPath());
    this.newCertificate = fromBytes(newCertificate);

    passwords = config.replaceIntermediateCertificate.passwords;
  }

  private X509Certificate fromBytes(byte[] data) throws CertificateException {
    CertificateFactory cf = CertificateFactory.getInstance("X.509");
    return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(data));
  }

  public @Nullable String process(String data, KeyStoreType type) throws IOException,
      NoSuchAlgorithmException, CertificateException, KeyStoreException, UnrecoverableEntryException {
    byte[] out = process(BaseEncoding.base64().decode(data), type);
    if (out != null) {
      return BaseEncoding.base64().encode(out);
    }
    return null;
  }

  public @Nullable byte[] process(byte[] data, KeyStoreType type) throws IOException,
      NoSuchAlgorithmException, CertificateException, KeyStoreException, UnrecoverableEntryException {

    if (type == KeyStoreType.JCEKS || type == KeyStoreType.P12) {
      KeyStore keyStore;
      if (type == KeyStoreType.JCEKS) {
        keyStore = KeyStore.getInstance("JCEKS");
      } else {
        keyStore = KeyStore.getInstance("PKCS12");
      }

      for (String password : passwords) {
        try {
          keyStore.load(new ByteArrayInputStream(data), password.toCharArray());
          if (update(password, keyStore)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            keyStore.store(out, password.toCharArray());
            return out.toByteArray();
          }
          return null;
        } catch (IOException e) {
          if (!(e.getCause() instanceof UnrecoverableKeyException)) {
            throw e;
          }
        }
      }
      throw new RuntimeException("failed to decrypt keystore");
    } else {
      // We assume every certificate in the file is a x509 certificate.
      boolean changed = false;
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      PemReader pemReader = new PemReader(new InputStreamReader(new ByteArrayInputStream(data), UTF_8));
      while (true) {
        PemObject pem = pemReader.readPemObject();
        if (pem == null) {
          break;
        }
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate c = cf.generateCertificate(new ByteArrayInputStream(pem.getContent()));
        if (oldCertificate.equals(c)) {
          changed = true;
          out.write(toPem(newCertificate).getBytes(UTF_8));
        } else {
          out.write(toPem(c).getBytes(UTF_8));
        }
      }
      if (changed) {
        return out.toByteArray();
      }
      return null;
    }
  }

  /**
   * Encode a certificate in PEM format.
   * @param certificate Certificate to PEM-encode.
   * @return PEM-encoded string with certificate and private key sections.
   */
  private String toPem(Certificate certificate) {
    checkNotNull(certificate);

    String cert;
    try {
      cert = BaseEncoding.base64().encode(certificate.getEncoded());
    } catch (CertificateEncodingException e) {
      throw new AssertionError(e);
    }

    StringBuilder sb = new StringBuilder();
    sb.append("-----BEGIN CERTIFICATE-----\n");
    for (String line : Splitter.fixedLength(64).split(cert)) {
      sb.append(line).append("\n");
    }
    sb.append("-----END CERTIFICATE-----\n");
    return sb.toString();
  }

  /**
   * Iterates through each alias in the keystore and updates the keystore if a certificate
   * needs to be replaced.
   *
   * @return true if the keystore was modified.
   */
  private boolean update(String password, KeyStore keyStore) throws KeyStoreException, NoSuchAlgorithmException,
      UnrecoverableEntryException {
    boolean r = false;
    Set<String> aliases = new HashSet<>(Collections.list(keyStore.aliases()));
    for (String alias : aliases) {
      Certificate[] chain = keyStore.getCertificateChain(alias);
      if ((chain != null) && updateChain(chain)) {
        r = true;
        PrivateKey key = (PrivateKey)keyStore.getKey(alias, password.toCharArray());
        KeyStore.Entry privateKeyEntry = new KeyStore.PrivateKeyEntry(key, chain);
        keyStore.setEntry(alias, privateKeyEntry, new KeyStore.PasswordProtection(password.toCharArray()));
      }
    }
    return r;
  }

  /**
   * Iterates through each certificate in the chain. Updates the keystore if a certificate
   * needs to be replaced.
   *
   * @return true if the keystore was modified.
   */
  private boolean updateChain(Certificate[] chain) {
    boolean r = false;
    for (int i = 0; i < chain.length; i++) {
      X509Certificate certificate = (X509Certificate) chain[i];

      if (oldCertificate.equals(certificate)) {
        chain[i] = newCertificate;
        r = true;
      }
    }
    return r;
  }

  public enum KeyStoreType {
    JCEKS,
    P12,
    PEM
  }
}
