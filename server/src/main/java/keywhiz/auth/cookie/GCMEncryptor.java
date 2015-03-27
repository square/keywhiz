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
package keywhiz.auth.cookie;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.primitives.Bytes;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static javax.crypto.Cipher.DECRYPT_MODE;
import static javax.crypto.Cipher.ENCRYPT_MODE;

/**
 * Encrypt data using an AES key, GCM mode
 */
public class GCMEncryptor {
  private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
  private static final String KEY_ALGORITHM = "AES";
  private static final int TAG_BITS = 128;
  private static final boolean ENCRYPT = true;
  private static final boolean DECRYPT = false;
  private static final int NONCE_LENGTH = 12;

  private final byte[] key;
  private final SecureRandom secureRandom;

  /**
   * Creates new encryptor.
   *
   * @param key key of at least 128-bits.
   * @param secureRandom source of strong randomness.
   */
  public GCMEncryptor(byte[] key, SecureRandom secureRandom) {
    checkArgument(key.length >= 16, "GCM key expected to be 128-bits or greater.");
    this.key = Arrays.copyOf(key, key.length);
    this.secureRandom = checkNotNull(secureRandom);
  }

  public synchronized byte[] encrypt(byte[] plaintext) throws AEADBadTagException {
    byte[] nonce = new byte[NONCE_LENGTH];
    secureRandom.nextBytes(nonce);

    return Bytes.concat(nonce, gcm(ENCRYPT, plaintext, nonce));
  }

  public byte[] decrypt(byte[] ciphertext) throws AEADBadTagException {
    return gcm(DECRYPT, ciphertextWithoutNonce(ciphertext), getNonce(ciphertext));
  }

  @VisibleForTesting
  static byte[] getNonce(byte[] ciphertext) {
    return Arrays.copyOfRange(ciphertext, 0, NONCE_LENGTH);
  }

  private static byte[] ciphertextWithoutNonce(byte[] ciphertext) {
    return Arrays.copyOfRange(ciphertext, NONCE_LENGTH, ciphertext.length);
  }

  private byte[] gcm(boolean encrypt, byte[] input, byte[] nonce) throws AEADBadTagException {
    try {
      Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
      SecretKey secretKey = new SecretKeySpec(key, KEY_ALGORITHM);

      GCMParameterSpec gcmParameters = new GCMParameterSpec(TAG_BITS, nonce);
      cipher.init(encrypt ? ENCRYPT_MODE : DECRYPT_MODE, secretKey, gcmParameters);
      return cipher.doFinal(input);
    } catch (NoSuchAlgorithmException | NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException e) {
      Throwables.propagateIfInstanceOf(e, AEADBadTagException.class);
      throw Throwables.propagate(e);
    }
  }
}
