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

import com.sun.crypto.provider.SunJCE;
import java.security.Provider;
import java.security.Security;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import keywhiz.FakeRandom;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.google.common.io.BaseEncoding.base16;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Base64.getEncoder;
import static org.assertj.core.api.Assertions.assertThat;

public class ContentCryptographerTest {
  private static final SecretKey BASE_KEY = new SecretKeySpec(
      base16().lowerCase().decode("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), "AES");
  private static final Provider BC = new BouncyCastleProvider();

  ContentCryptographer cryptographer;

  @BeforeClass public static void setUpOnce() throws Exception {
    if (Security.getProvider(BC.getName()) == null) {
      Security.addProvider(BC);
    }
  }

  @Before public void setUp() throws Exception {
    cryptographer = new ContentCryptographer(BASE_KEY, new SunJCE(), BC, FakeRandom.create());
  }

  @Test public void encryptDecrypt() throws Exception {
    String input = "Hello World";
    String inputBase64 = getEncoder().encodeToString(input.getBytes(UTF_8));

    String crypted = cryptographer
        .encryptionKeyDerivedFrom("secret_filename.gpg")
        .encrypt(inputBase64);
    assertThat(crypted).isNotEmpty().isNotEqualTo(inputBase64);
    String outputBase64 = cryptographer.decrypt(crypted);
    assertThat(outputBase64).isEqualTo(inputBase64);
  }
}
