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

package keywhiz.utility;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import keywhiz.KeywhizConfig;
import keywhiz.KeywhizTestRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import static keywhiz.utility.ReplaceIntermediateCertificate.KeyStoreType.JCEKS;
import static keywhiz.utility.ReplaceIntermediateCertificate.KeyStoreType.P12;
import static keywhiz.utility.ReplaceIntermediateCertificate.KeyStoreType.PEM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fixtures for this test were created using the following commands:
 *
 * certstrap init --cn cert1
 * certstrap request-cert --cn cert2
 * certstrap request-cert --cn cert3
 *
 * certstrap sign --intermediate --CA cert1 cert2
 * certstrap sign --CA cert2 cert3
 *
 * mv out/cert2.crt out/cert2-a.crt
 * certstrap sign --intermediate --CA cert1 cert2
 * mv out/cert2.crt out/cert2-b.crt
 *
 * cat cert1.crt cert2-a.crt cert3.crt > a.crt
 * cat cert1.crt cert2-b.crt cert3.crt > b.crt
 *
 * openssl pkcs12 -export -inkey cert3.key -in a.crt -name foo -out a.p12
 * openssl pkcs12 -export -inkey cert3.key -in b.crt -name foo -out b.p12
 *
 * keytool -importkeystore -srckeystore a.p12 -srcstoretype pkcs12 -destkeystore a.jceks -deststoretype JCEKS
 * keytool -importkeystore -srckeystore b.p12 -srcstoretype pkcs12 -destkeystore b.jceks -deststoretype JCEKS
 */
@RunWith(KeywhizTestRunner.class)
public class ReplaceIntermediateCertificateTest {
  @Inject KeywhizConfig keywhizConfig;
  @Inject ReplaceIntermediateCertificate replaceIntermediateCertificate;

  char[] password = "toto1234".toCharArray();

  @Test public void replacesPem() throws Exception {
    byte[] old_bundle = Files.readAllBytes(new File("src/test/resources/fixtures/a.crt").toPath());
    byte[] r = replaceIntermediateCertificate.process(old_bundle, PEM);
    byte[] expected_bundle= Files.readAllBytes(new File("src/test/resources/fixtures/b.crt").toPath());
    assertThat(r).isEqualTo(expected_bundle);

    r = replaceIntermediateCertificate.process(expected_bundle, PEM);
    assertThat(r).isNull();
  }

  private void checkJceksKeyStores(byte[] data1, byte[] data2) throws Exception {
    KeyStore keyStore1 = KeyStore.getInstance("JCEKS");
    keyStore1.load(new ByteArrayInputStream(data1), password);

    KeyStore keyStore2 = KeyStore.getInstance("JCEKS");
    keyStore2.load(new ByteArrayInputStream(data2), password);

    checkKeyStores(keyStore1, keyStore2);
  }

  private void checkPkcs12KeyStores(byte[] data1, byte[] data2) throws Exception {
    KeyStore keyStore1 = KeyStore.getInstance("PKCS12");
    keyStore1.load(new ByteArrayInputStream(data1), password);

    KeyStore keyStore2 = KeyStore.getInstance("PKCS12");
    keyStore2.load(new ByteArrayInputStream(data2), password);

    checkKeyStores(keyStore1, keyStore2);
  }


  private void checkKeyStores(KeyStore keyStore1, KeyStore keyStore2) throws Exception {
    Set<String> aliases1 = new HashSet<>(Collections.list(keyStore1.aliases()));
    Set<String> aliases2 = new HashSet<>(Collections.list(keyStore2.aliases()));

    assertThat(aliases1).isEqualTo(aliases2);

    Enumeration<String> aliases = keyStore1.aliases();
    while (aliases.hasMoreElements()) {
      String alias = aliases.nextElement();
      KeyStore.Entry entry1 = keyStore1.getEntry(alias, new PasswordProtection(password));
      KeyStore.Entry entry2 = keyStore2.getEntry(alias, new PasswordProtection(password));
      assertThat(entry1.toString()).isEqualTo(entry2.toString());
    }
  }

  @Test public void replaceJceks() throws Exception {
    byte[] old_bundle = Files.readAllBytes(new File("src/test/resources/fixtures/a.jceks").toPath());
    byte[] r = replaceIntermediateCertificate.process(old_bundle, JCEKS);
    byte[] expected_bundle= Files.readAllBytes(new File("src/test/resources/fixtures/b.jceks").toPath());

    // Ideally, KeyStore would take the random number generate and date as a dependency. Things get
    // ugly if we mock, so just compare all the entries.
    checkJceksKeyStores(r, expected_bundle);

    r = replaceIntermediateCertificate.process(expected_bundle, JCEKS);
    assertThat(r).isNull();
  }


  @Test public void replacesPkcs12() throws Exception {
    byte[] old_bundle = Files.readAllBytes(new File("src/test/resources/fixtures/a.p12").toPath());
    byte[] r = replaceIntermediateCertificate.process(old_bundle, P12);
    byte[] expected_bundle= Files.readAllBytes(new File("src/test/resources/fixtures/b.p12").toPath());

    // Ideally, KeyStore would take the random number generate and date as a dependency. Things get
    // ugly if we mock, so just compare all the entries.
    checkPkcs12KeyStores(r, expected_bundle);

    r = replaceIntermediateCertificate.process(expected_bundle, P12);
    assertThat(r).isNull();
  }
}
