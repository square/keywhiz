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
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import javax.crypto.SecretKey;
import javax.inject.Qualifier;
import javax.inject.Singleton;
import keywhiz.service.config.KeyStoreConfig;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** Guice module for configuring cryptography objects. */
public class CryptoModule extends AbstractModule {
  private static final Logger logger = LoggerFactory.getLogger(CryptoModule.class);

  private final Provider bcProvider = new BouncyCastleProvider();

  private final String derivationProviderClass;
  private final KeyStoreConfig keyStoreConfig;

  // TODO: These values can be read from KeywhizConfig directly once the CLI uses a proper API.
  public CryptoModule(String derivationProviderClass, KeyStoreConfig keyStoreConfig) {
    this.derivationProviderClass = derivationProviderClass;
    this.keyStoreConfig = keyStoreConfig;
  }

  @Override protected void configure() {}

  @Provides @Derivation @Singleton SecretKey baseDerivationKey(@Derivation Provider provider) {
    String alias = keyStoreConfig.alias();
    char[] password = keyStoreConfig.resolvedPassword().toCharArray();

    KeyStore keyStore;
    try (InputStream inputStream = keyStoreConfig.openPath()) {
      keyStore = KeyStore.getInstance(keyStoreConfig.type(), provider);
      keyStore.load(inputStream, password);
      return (SecretKey) keyStore.getKey(alias, password);
    } catch (CertificateException | IOException | KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException e) {
      logger.error("Error loading base derivation key: {}", e.getMessage(), e);
      throw Throwables.propagate(e);
    }
  }

  // TODO: As of jdk 1.8.0_25, there is a severe perf bug with GHASH used in AES-GCM. Switch back to
  // a java native crypto provider instead of BouncyCastle once fixed.
  @Provides @Encryption @Singleton Provider encryptionProvider() {
    if (Security.getProvider(bcProvider.getName()) == null) {
      logger.debug("Registering new crypto provider {}", bcProvider.getName());
      Security.addProvider(bcProvider);
    }
    return bcProvider;
  }

  /**
   * Sometimes a different provider is preferable for key derivation. In particular, when an HSM
   * holds the base derivation key.
   *
   * @return the security provider to use strictly for key derivation.
   */
  @Provides @Derivation @Singleton Provider derivationProvider() {
    try {
      Provider provider = (Provider) Class.forName(derivationProviderClass).newInstance();
      if (Security.getProvider(provider.getName()) == null) {
        logger.debug("Registering new crypto provider {}", provider.getName());
        Security.addProvider(provider);
      }
      return provider;
    } catch (InstantiationException | ClassNotFoundException | IllegalAccessException e) {
      logger.error("Error instantiating derivation provider: {}", e.getMessage(), e);
      throw Throwables.propagate(e);
    }
  }

  /** Denotes objects used for encryption/decryption. */
  @Qualifier @Retention(RUNTIME) public @interface Encryption {}

  /** Denotes objects used for key derivation. */
  @Qualifier @Retention(RUNTIME) public @interface Derivation {}
}
