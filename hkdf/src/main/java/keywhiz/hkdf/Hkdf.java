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
package keywhiz.hkdf;

import com.sun.crypto.provider.SunJCE;

import javax.annotation.Nullable;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.SecureRandom;

import static java.util.Objects.requireNonNull;

/**
 * HMAC-based Extract-and-Expand Key Derivation Function (HKDF) from
 * <a href="http://tools.ietf.org/html/rfc5869">RFC-5869</a>. HKDF is a standard means to generate
 * a derived key of arbitrary length.
 *
 * <pre>{@code
 * // Instantiate an Hkdf object with a hash function.
 * Hkdf hkdf = new Hkdf(Hash.SHA256);
 *
 * // Using some protected input keying material (IKM), extract a pseudo-random key (PRK) with a
 * // random salt. Remember to store the salt so the key can be derived again.
 * SecretKey prk = hkdf.extract(Hkdf.randomSalt(), ikm);
 *
 * // Expand the prk with some information related to your data and the length of the output key.
 * SecretKey derivedKey = hkdf.expand(prk, "id: 5".getBytes(StandardCharsets.UTF_8), 32);
 * }</pre>
 *
 * HKDF is a generic means for generating derived keys. In some cases, you may want to use it in a
 * different manner. Consult the RFC for security considerations, when to omit a salt, skipping the
 * extraction step, etc.
 */
public class Hkdf {
  private static Hash DEFAULT_HASH = Hash.SHA256;
  private static Provider DEFAULT_PROVIDER = new SunJCE();

  private final Hash hash;
  private final Provider provider;

  private Hkdf(Hash hash, Provider provider) {
    this.hash = hash;
    this.provider = provider;
  }

  /**
   * @return Hkdf constructed with default hash and derivation provider
   */
  public static Hkdf usingDefaults() {
    return new Hkdf(DEFAULT_HASH, DEFAULT_PROVIDER);
  }

  /**
   * @param hash Supported hash function constant
   * @return Hkdf constructed with a specific hash function
   */
  public static Hkdf usingHash(Hash hash) {
    return new Hkdf(requireNonNull(hash), DEFAULT_PROVIDER);
  }

  /**
   * @param provider provider for key derivation, particularly useful when using HSMs
   * @return Hkdf constructed with a specific JCE provider for key derivation
   */
  public static Hkdf usingProvider(Provider provider) {
    return new Hkdf(DEFAULT_HASH, requireNonNull(provider));
  }

  /**
   * HKDF-Extract(salt, IKM) -&gt; PRK
   *
   * @param salt optional salt value (a non-secret random value); if not provided, it is set to a string of HashLen zeros.
   * @param ikm input keying material
   * @return a pseudorandom key (of HashLen bytes)
   */
  public SecretKey extract(@Nullable SecretKey salt, byte[] ikm) {
    requireNonNull(ikm, "ikm must not be null");
    if (salt == null) {
      salt = new SecretKeySpec(new byte[hash.getByteLength()], hash.getAlgorithm());
    }

    Mac mac = initMac(salt);
    byte[] keyBytes = mac.doFinal(ikm);
    return new SecretKeySpec(keyBytes, hash.getAlgorithm());
  }

  /**
   * HKDF-Expand(PRK, info, L) -&gt; OKM
   *
   * @param key a pseudorandom key of at least HashLen bytes (usually, the output from the extract step)
   * @param info context and application specific information (can be empty)
   * @param outputLength length of output keying material in bytes (&lt;= 255*HashLen)
   * @return output keying material
   */
  public byte[] expand(SecretKey key, @Nullable byte[] info, int outputLength) {
    requireNonNull(key, "key must not be null");
    if (outputLength < 1) {
      throw new IllegalArgumentException("outputLength must be positive");
    }
    int hashLen = hash.getByteLength();
    if (outputLength > 255 * hashLen) {
      throw new IllegalArgumentException("outputLength must be less than or equal to 255*HashLen");
    }

    if (info == null) {
      info = new byte[0];
    }

    /*
    The output OKM is calculated as follows:

      N = ceil(L/HashLen)
      T = T(1) | T(2) | T(3) | ... | T(N)
      OKM = first L bytes of T

    where:
      T(0) = empty string (zero length)
      T(1) = HMAC-Hash(PRK, T(0) | info | 0x01)
      T(2) = HMAC-Hash(PRK, T(1) | info | 0x02)
      T(3) = HMAC-Hash(PRK, T(2) | info | 0x03)
      ...
     */
    int n = (outputLength % hashLen == 0) ?
        outputLength / hashLen :
        (outputLength / hashLen) + 1;

    byte[] hashRound = new byte[0];

    ByteBuffer generatedBytes = ByteBuffer.allocate(Math.multiplyExact(n, hashLen));
    Mac mac = initMac(key);
    for (int roundNum = 1; roundNum <= n; roundNum++) {
      mac.reset();
      ByteBuffer t = ByteBuffer.allocate(hashRound.length + info.length + 1);
      t.put(hashRound);
      t.put(info);
      t.put((byte)roundNum);
      hashRound = mac.doFinal(t.array());
      generatedBytes.put(hashRound);
    }

    byte[] result = new byte[outputLength];
    generatedBytes.rewind();
    generatedBytes.get(result, 0, outputLength);
    return result;
  }

  /**
   * Generates a random salt value to be used with {@link #extract(javax.crypto.SecretKey, byte[])}.
   *
   * @return randomly generated SecretKey to use for PRK extraction.
   */
  public SecretKey randomSalt() {
    SecureRandom random = new SecureRandom();
    byte[] randBytes = new byte[hash.getByteLength()];
    random.nextBytes(randBytes);
    return new SecretKeySpec(randBytes, hash.getAlgorithm());
  }

  private Mac initMac(SecretKey key) {
    Mac mac;
    try {
      mac = Mac.getInstance(hash.getAlgorithm(), provider);
      mac.init(key);
      return mac;
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    } catch (InvalidKeyException e) {
      throw new IllegalArgumentException(e);
    }
  }
}
