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

import com.google.common.io.BaseEncoding;
import java.util.Arrays;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * See <a href="http://tools.ietf.org/html/rfc5869">RFC-5869</a> for standard test vectors.
 */
@RunWith(Parameterized.class)
public class HkdfRfcVectorsTest {
  private static final BaseEncoding HEX = BaseEncoding.base16().lowerCase();

  private final Hash hash;
  private final PossibleBytes salt;
  private final String ikm;
  private final PossibleBytes info;
  private final int outputLen;
  private final String expectedPrk;
  private final String expectedOkm;

  public HkdfRfcVectorsTest(@SuppressWarnings("unused") String name, Hash hash, PossibleBytes salt,
      String ikm, PossibleBytes info, int outputLen, String expectedPrk, String expectedOkm) {
    this.hash = hash;
    this.salt = salt;
    this.ikm = ikm;
    this.info = info;
    this.outputLen = outputLen;
    this.expectedPrk = expectedPrk;
    this.expectedOkm = expectedOkm;
  }

  @Parameterized.Parameters(name = "{index}: {0}")
  public static Iterable<Object[]> data() {
    return Arrays.asList(new Object[][] {
        {
            "Basic test case with SHA-256",
            Hash.SHA256,
            PossibleBytes.of("000102030405060708090a0b0c"),
            "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b",
            PossibleBytes.of("f0f1f2f3f4f5f6f7f8f9"),
            42,
            "077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5",
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"
        },
        {
            "SHA-256 and longer inputs/outputs",
            Hash.SHA256,
            PossibleBytes.of("606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9fa0a1a2a3a4a5a6a7a8a9aaabacadaeaf"),
            "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f404142434445464748494a4b4c4d4e4f",
            PossibleBytes.of("b0b1b2b3b4b5b6b7b8b9babbbcbdbebfc0c1c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2d3d4d5d6d7d8d9dadbdcdddedfe0e1e2e3e4e5e6e7e8e9eaebecedeeeff0f1f2f3f4f5f6f7f8f9fafbfcfdfeff"),
            82,
            "06a6b88c5853361a06104c9ceb35b45cef760014904671014a193f40c15fc244",
            "b11e398dc80327a1c8e7f78c596a49344f012eda2d4efad8a050cc4c19afa97c59045a99cac7827271cb41c65e590e09da3275600c2f09b8367793a9aca3db71cc30c58179ec3e87c14c01d5c1f3434f1d87"
        },
        {
            "SHA-256 and zero-length salt/info",
            Hash.SHA256,
            PossibleBytes.NULL,
            "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b",
            PossibleBytes.EMPTY,
            42,
            "19ef24a32c717b167f33a91d6f648bdf96596776afdb6377ac434c1c293ccb04",
            "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8"
        },
        {
            "SHA-256 and null salt/info",
            Hash.SHA256,
            PossibleBytes.NULL,
            "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b",
            PossibleBytes.NULL,
            42,
            "19ef24a32c717b167f33a91d6f648bdf96596776afdb6377ac434c1c293ccb04",
            "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8"
        },
        {
            "Basic test case with SHA-1",
            Hash.SHA1,
            PossibleBytes.of("000102030405060708090a0b0c"),
            "0b0b0b0b0b0b0b0b0b0b0b",
            PossibleBytes.of("f0f1f2f3f4f5f6f7f8f9"),
            42,
            "9b6c18c432a7bf8f0e71c8eb88f4b30baa2ba243",
            "085a01ea1b10f36933068b56efa5ad81a4f14b822f5b091568a9cdd4f155fda2c22e422478d305f3f896"
        },
        {
            "SHA-1 and longer inputs/outputs",
            Hash.SHA1,
            PossibleBytes.of("606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9fa0a1a2a3a4a5a6a7a8a9aaabacadaeaf"),
            "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f404142434445464748494a4b4c4d4e4f",
            PossibleBytes.of("b0b1b2b3b4b5b6b7b8b9babbbcbdbebfc0c1c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2d3d4d5d6d7d8d9dadbdcdddedfe0e1e2e3e4e5e6e7e8e9eaebecedeeeff0f1f2f3f4f5f6f7f8f9fafbfcfdfeff"),
            82,
            "8adae09a2a307059478d309b26c4115a224cfaf6",
            "0bd770a74d1160f7c9f12cd5912a06ebff6adcae899d92191fe4305673ba2ffe8fa3f1a4e5ad79f3f334b3b202b2173c486ea37ce3d397ed034c7f9dfeb15c5e927336d0441f4c4300e2cff0d0900b52d3b4"
        },
        {
            "SHA-1 and zero-length salt/info",
            Hash.SHA1,
            PossibleBytes.NULL,
            "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b",
            PossibleBytes.EMPTY,
            42,
            "da8c8a73c7fa77288ec6f5e7c297786aa0d32d01",
            "0ac1af7002b3d761d1e55298da9d0506b9ae52057220a306e07b6b87e8df21d0ea00033de03984d34918"
        },
        {
            "SHA-1 and null salt/info",
            Hash.SHA1,
            PossibleBytes.NULL,
            "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b",
            PossibleBytes.NULL,
            42,
            "da8c8a73c7fa77288ec6f5e7c297786aa0d32d01",
            "0ac1af7002b3d761d1e55298da9d0506b9ae52057220a306e07b6b87e8df21d0ea00033de03984d34918"
        },
        {
            "SHA-1, salt not provided (defaults to HashLen zero octets), zero-length info",
            Hash.SHA1,
            PossibleBytes.NULL,
            "0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c",
            PossibleBytes.EMPTY,
            42,
            "2adccada18779e7c2077ad2eb19d3f3e731385dd",
            "2c91117204d745f3500d636a62f64f0ab3bae548aa53d423b0d1f27ebba6f5e5673a081d70cce7acfc48"
        }
    });
  }

  @Test public void testVector() throws Exception {
    Hkdf hkdf = Hkdf.usingHash(hash);
    SecretKey saltKey = salt.isNull() ? null : new SecretKeySpec(salt.get(), hash.getAlgorithm());
    SecretKey prk = hkdf.extract(saltKey, HEX.decode(ikm));
    byte[] okm = hkdf.expand(prk, info.get(), outputLen);

    assertThat(prk.getEncoded()).containsExactly(HEX.decode(expectedPrk));
    assertThat(okm).containsExactly(HEX.decode(expectedOkm));
  }

  private static class PossibleBytes {
    public static final PossibleBytes NULL = new PossibleBytes(null);
    public static final PossibleBytes EMPTY = new PossibleBytes(new byte[0]);

    public static PossibleBytes of(String hex) {
      return new PossibleBytes(HEX.decode(hex));
    }

    private final byte[] bytes;

    private PossibleBytes(byte[] bytes) {
      this.bytes = bytes;
    }

    public byte[] get() {
      return bytes;
    }

    public boolean isNull() {
      return bytes == null;
    }
  }
}
