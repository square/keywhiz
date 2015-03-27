/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package keywhiz.service.crypto;

import com.google.common.base.Throwables;
import com.google.common.io.Resources;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import javax.crypto.SecretKey;
import keywhiz.FakeRandom;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * Static helper methods to make crypto operations easier in testing.
 */
public class CryptoFixtures {
  private static ContentCryptographer cryptographer;

  private CryptoFixtures() {}

  /** @return a content cryptographer initialized with the testing derivation key. */
  public static ContentCryptographer contentCryptographer() {
    if (cryptographer != null) {
      return cryptographer;
    }

    Provider provider = new BouncyCastleProvider();
    if (Security.getProvider(provider.getName()) == null) {
      Security.addProvider(provider);
    }

    SecretKey baseKey;
    char[] password = "CHANGE".toCharArray();
    try (InputStream in = Resources.getResource("derivation.jceks").openStream()) {
      KeyStore keyStore = KeyStore.getInstance("JCEKS");
      keyStore.load(in, password);
      baseKey = (SecretKey) keyStore.getKey("basekey", password);
    } catch (CertificateException | UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException | IOException e) {
      throw Throwables.propagate(e);
    }

    cryptographer = new ContentCryptographer(baseKey, provider, provider, FakeRandom.create());
    return cryptographer;
  }
}
