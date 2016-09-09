package keywhiz.service.resources.automation.v2;

import com.google.common.base.Throwables;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Iterator;
import javax.annotation.Nullable;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.jcajce.JcaPGPPublicKeyRingCollection;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.list;

/** Helper class to extract expirations from secrets contents (best effort) */
public final class ExpirationExtractor {
  private static final Logger logger = LoggerFactory.getLogger(ExpirationExtractor.class);

  private ExpirationExtractor() {}

  @Nullable public static Instant expirationFromKeystore(String type, String password, byte[] content) {
    KeyStore ks;
    try {
      ks = KeyStore.getInstance(type);
    } catch (KeyStoreException e) {
      // Should never occur (assuming JCE is installed)
      throw Throwables.propagate(e);
    }

    try {
      ks.load(new ByteArrayInputStream(content), password.toCharArray());
    } catch (IOException | NoSuchAlgorithmException | CertificateException e) {
      // Failed to parse
      logger.info("Failed to parse keystore", e);
      return null;
    }

    Instant earliest = null;
    try {
      for (String alias : list(ks.aliases())) {
        Certificate[] chain = ks.getCertificateChain(alias);
        if (chain == null) {
          Certificate certificate = ks.getCertificate(alias);
          if (certificate == null) {
            // No certs in this entry
            continue;
          }
          chain = new Certificate[]{certificate};
        }
        for (Certificate cert : chain) {
          if (cert instanceof X509Certificate) {
            X509Certificate c = (X509Certificate) cert;
            if (earliest == null || c.getNotAfter().toInstant().isBefore(earliest)) {
              earliest = c.getNotAfter().toInstant();
            }
          }
        }
      }
    } catch (KeyStoreException e) {
      // Should never occur (ks was initialized)
      throw Throwables.propagate(e);
    }

    return earliest;
  }

  @Nullable public static Instant expirationFromOpenPGP(byte[] content) {
    JcaPGPPublicKeyRingCollection collection;
    try {
      collection = new JcaPGPPublicKeyRingCollection(new ByteArrayInputStream(content));
    } catch (IOException | PGPException e) {
      // Unable to parse
      logger.info("Failed to parse OpenPGP keyring", e);
      return null;
    }

    Instant earliest = null;

    // Iterate over all key rings in file
    Iterator rings = collection.getKeyRings();
    while (rings.hasNext()) {
      Object ringItem = rings.next();
      if (ringItem instanceof PGPPublicKeyRing) {
        PGPPublicKeyRing ring = (PGPPublicKeyRing) ringItem;

        // Iterate over all keys in ring
        Iterator keys = ring.getPublicKeys();
        while (keys.hasNext()) {
          Object keyItem = keys.next();
          if (keyItem instanceof PGPPublicKey) {
            PGPPublicKey key = (PGPPublicKey) keyItem;

            // Get validity for key (zero means no expiry)
            long validSeconds = key.getValidSeconds();
            if (validSeconds > 0) {
              Instant expiry = key.getCreationTime().toInstant().plusSeconds(validSeconds);
              if (earliest == null || expiry.isBefore(earliest)) {
                earliest = expiry;
              }
            }
          }
        }
      }
    }

    return earliest;
  }

  @Nullable public static Instant expirationFromEncodedCertificateChain(byte[] content) {
    PemReader reader = new PemReader(new InputStreamReader(new ByteArrayInputStream(content), UTF_8));

    PemObject object;
    try {
      object = reader.readPemObject();
    } catch (IOException e) {
      // Should never occur (reading form byte array)
      throw Throwables.propagate(e);
    }

    Instant earliest = null;
    while (object != null) {
      if (object.getType().equals("CERTIFICATE")) {
        Instant expiry = expirationFromRawCertificate(object.getContent());
        if (earliest == null || expiry.isBefore(earliest)) {
          earliest = expiry;
        }
      }

      try {
        object = reader.readPemObject();
      } catch (IOException e) {
        // Should never occur (reading form byte array)
        throw Throwables.propagate(e);
      }
    }

    return earliest;
  }

  @Nullable public static Instant expirationFromRawCertificate(byte[] content) {
    CertificateFactory cf;
    try {
      cf = CertificateFactory.getInstance("X.509");
    } catch (CertificateException e) {
      // Should never happen (X.509 supported by default)
      throw Throwables.propagate(e);
    }

    X509Certificate cert;
    try {
      cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(content));
    } catch (CertificateException e) {
      // Certificate must have been invalid
      logger.info("Failed to parse certificate", e);
      return null;
    }

    return cert.getNotAfter().toInstant();
  }
}
