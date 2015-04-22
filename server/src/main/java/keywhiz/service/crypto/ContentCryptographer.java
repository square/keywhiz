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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.base.Throwables;
import io.dropwizard.jackson.Jackson;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Base64.Encoder;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;
import keywhiz.auth.Subtles;
import keywhiz.hkdf.Hkdf;
import keywhiz.service.crypto.CryptoModule.Derivation;
import keywhiz.service.crypto.CryptoModule.Encryption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Base64.getDecoder;
import static java.util.Base64.getEncoder;
import static javax.crypto.Cipher.DECRYPT_MODE;
import static javax.crypto.Cipher.ENCRYPT_MODE;

/**
 * Cryptographer which encrypts/decrypts secret content.
 *
 * Encryption keys are derived using a provided info tag. Encrypted content is serialized as JSON
 * with the necessary parameters for decryption.
 */
public class ContentCryptographer {
  private static final Logger logger = LoggerFactory.getLogger(ContentCryptographer.class);
  private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
  private static final String KEY_ALGORITHM = "AES";
  private static final int TAG_BITS = 128;
  private static final int NONCE_BYTES = 12;
  private static final ObjectMapper MAPPER = Jackson.newObjectMapper();

  private final SecretKey key;
  private final Provider derivationProvider;
  private final Provider encryptionProvider;
  private final SecureRandom random;

  @Inject public ContentCryptographer(@Derivation SecretKey key,
      @Derivation Provider derivationProvider,
      @Encryption Provider encryptionProvider, SecureRandom random) {
    this.key = key;
    this.derivationProvider = derivationProvider;
    this.encryptionProvider = encryptionProvider;
    this.random = random;
  }

  public class Encrypter {
    private final String derivationInfo;

    private Encrypter(String derivationInfo) {
      this.derivationInfo = derivationInfo;
    }

    /**
     * Encrypts content under a derived key.
     *
     * @param plaintextBase64 plaintext content to encrypt, which is expected to be base64-encoded
     * @return serialized JSON containing ciphertext and parameters necessary for decryption
     */
    public String encrypt(String plaintextBase64) {
      Base64.Decoder decoder = getDecoder();
      final byte[] plaintext = decoder.decode(plaintextBase64);

      byte[] nonce = new byte[NONCE_BYTES];
      random.nextBytes(nonce);

      byte[] ciphertext = gcm(Mode.ENCRYPT, derivationInfo, nonce, plaintext);
      Crypted crypted = Crypted.of(derivationInfo, ciphertext, nonce);
      String encryptedJson;
      try {
        encryptedJson = MAPPER.writeValueAsString(crypted);
      } catch (JsonProcessingException e) {
        throw Throwables.propagate(e);
      }

      if (!Subtles.secureCompare(decoder.decode(decrypt(encryptedJson)), plaintext)) {
        logger.warn("Decryption of (just encrypted) data does not match original! [name={}]",
            derivationInfo);
      }

      return encryptedJson;
    }
  }

  /**
   * Builds an encrypter using key derived from the provided secret name.
   *
   * @param secretName non-empty secret name used for key derivation
   * @return encrypter capable of operating on plaintext data
   */
  public Encrypter encryptionKeyDerivedFrom(String secretName) {
    checkArgument(!secretName.isEmpty());
    return new Encrypter(secretName);
  }

  /**
   * Decrypts content previously encrypted by {@link ContentCryptographer}.
   *
   * @param ciphertextJson JSON from prior {@link Encrypter#encrypt} call
   * @return original base64 plaintext without padding
   */
  public String decrypt(String ciphertextJson) {
    Crypted crypted;
    try {
      crypted = MAPPER.readValue(ciphertextJson, Crypted.class);
    } catch (IOException e) {
      throw new IllegalArgumentException("Cannot deserialize Crypted json", e);
    }

    byte[] plaintext = gcm(Mode.DECRYPT, crypted.derivationInfo(), crypted.ivBytes(), crypted.contentBytes());
    return getEncoder().encodeToString(plaintext);
  }

  private SecretKey deriveKey(int blockSize, String info) {
    Hkdf hkdf = Hkdf.usingProvider(derivationProvider);
    byte[] infoBytes = info.getBytes(UTF_8);
    byte[] derivedKeyBytes = hkdf.expand(key, infoBytes, blockSize);
    return new SecretKeySpec(derivedKeyBytes, KEY_ALGORITHM);
  }

  private byte[] gcm(Mode mode, String info, byte[] nonce, byte[] data) {
    try {
      Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM, encryptionProvider);
      SecretKey derivedKey = deriveKey(cipher.getBlockSize(), info);
      GCMParameterSpec gcmParameters = new GCMParameterSpec(TAG_BITS, nonce);
      cipher.init(mode.cipherMode, derivedKey, gcmParameters);
      return cipher.doFinal(data);
    } catch (IllegalBlockSizeException | InvalidAlgorithmParameterException | NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException | BadPaddingException e) {
      throw Throwables.propagate(e);
    }
  }

  /**
   * Non-public value type representing JSON serialized fields for encrypted data.
   */
  @AutoValue static abstract class Crypted {
    static Crypted of(String info, byte[] content, byte[] iv) {
      Encoder encoder = getEncoder();
      String contentBase64 = encoder.encodeToString(content);
      String ivBase64 = encoder.encodeToString(iv);
      return new AutoValue_ContentCryptographer_Crypted(info, contentBase64, ivBase64);
    }

    @SuppressWarnings("unused")
    @JsonCreator static Crypted fromJson(@JsonProperty("derivationInfo") String derivationInfo,
        @JsonProperty("content") String content, @JsonProperty("iv") String iv) {
      return new AutoValue_ContentCryptographer_Crypted(derivationInfo, content, iv);
    }

    @JsonProperty abstract String derivationInfo();
    @JsonProperty abstract String content();
    @JsonProperty abstract String iv();

    byte[] contentBytes() {
      return getDecoder().decode(content());
    }

    byte[] ivBytes() {
      return getDecoder().decode(iv());
    }

    @Override public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("derivationInfo", derivationInfo())
          .add("content", "REDACTED")
          .add("iv", "REDACTED")
          .toString();
    }
  }

  // Preferable to signify encryption mode as an enum. javax.crypto.Cipher uses ints.
  private static enum Mode {
    ENCRYPT(ENCRYPT_MODE), DECRYPT(DECRYPT_MODE);

    public final int cipherMode;

    Mode(int cipherMode) {
      this.cipherMode = cipherMode;
    }
  }
}
