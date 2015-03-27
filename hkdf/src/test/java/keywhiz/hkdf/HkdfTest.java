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

import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HkdfTest {
  private static final BaseEncoding HEX = BaseEncoding.base16();

  @Test public void randomSalt_correctLength() {
    Hkdf hkdf = Hkdf.usingDefaults();
    SecretKey salt = hkdf.randomSalt();
    assertThat(salt.getEncoded()).hasSize(Hash.SHA256.getByteLength());
  }

  @Test public void extract_nullSaltSameAsZeros() {
    byte[] ikm = HEX.decode("DEADBEEF");
    Hkdf hkdf = Hkdf.usingDefaults();
    byte[] prkFromNullSalt = hkdf.extract(null, ikm).getEncoded();

    SecretKeySpec zeroSalt = new SecretKeySpec(
        HEX.decode(Strings.repeat("00", Hash.SHA256.getByteLength())),
        Hash.SHA256.getAlgorithm());
    byte[] prkFromZeroSalt = hkdf.extract(zeroSalt, ikm).getEncoded();

    assertThat(prkFromNullSalt).isEqualTo(prkFromZeroSalt);
  }

  @Test public void expand_nullInfoSameAsEmpty() {
    Hkdf hkdf = Hkdf.usingDefaults();
    SecretKey key = hkdf.extract(null, HEX.decode("DEADBEE0"));
    byte[] keyNullInfo = hkdf.expand(key, null, 53);
    byte[] keyEmptyInfo = hkdf.expand(key, new byte[0], 53);
    assertThat(keyNullInfo).isEqualTo(keyEmptyInfo);
  }
}
