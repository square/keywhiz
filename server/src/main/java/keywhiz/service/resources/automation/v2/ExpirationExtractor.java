package keywhiz.service.resources.automation.v2;

import com.google.common.base.Throwables;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Iterator;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.jcajce.JcaPGPPublicKeyRingCollection;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

/** Helper class to extract expirations from secrets contents (best effort) */
public final class ExpirationExtractor {
  private ExpirationExtractor() {}

  public static Instant expirationFromOpenPGP(byte[] content) {
    JcaPGPPublicKeyRingCollection collection;
    try {
      collection = new JcaPGPPublicKeyRingCollection(new ByteArrayInputStream(content));
    } catch (IOException | PGPException e) {
      // Unable to parse
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

  public static Instant expirationFromEncodedCertificateChain(byte[] content) {
    PemReader reader = new PemReader(new InputStreamReader(new ByteArrayInputStream(content)));

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

  public static Instant expirationFromRawCertificate(byte[] content) {
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
      return null;
    }

    return cert.getNotAfter().toInstant();
  }
}
