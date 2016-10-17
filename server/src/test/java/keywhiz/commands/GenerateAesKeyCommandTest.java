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

package keywhiz.commands;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Key;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.crypto.SecretKey;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;

public class GenerateAesKeyCommandTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test public void testKeyGeneration() throws Exception {
    char[] password = "CHANGE".toCharArray();
    Path destination = Paths.get(temporaryFolder.getRoot().getPath(), "derivation.jceks");
    int keySize = 128;
    String alias = "baseKey";

    GenerateAesKeyCommand.generate(password, destination, keySize, alias, new SecureRandom());
    assertThat(destination).exists();

    KeyStore keyStore = KeyStore.getInstance("JCEKS");
    try (InputStream in = Files.newInputStream(destination)) {
      keyStore.load(in, password);
    }
    assertThat(keyStore.isKeyEntry(alias)).isTrue();

    Key key = keyStore.getKey(alias, password);
    assertThat(key).isInstanceOf(SecretKey.class);
    SecretKey secretKey = (SecretKey) key;
    assertThat(secretKey.getEncoded()).hasSize(keySize/8);
  }
}
