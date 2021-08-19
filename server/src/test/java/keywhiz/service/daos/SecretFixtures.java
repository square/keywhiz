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

package keywhiz.service.daos;

import com.google.common.collect.ImmutableMap;
import keywhiz.api.model.Secret;
import keywhiz.service.crypto.ContentCryptographer;
import keywhiz.service.crypto.ContentEncodingException;
import keywhiz.service.crypto.CryptoFixtures;
import keywhiz.service.crypto.SecretTransformer;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Helper methods to make secrets, reducing the amount of work for testing.
 */
public class SecretFixtures {
  private final SecretDAO secretDAO;
  private final ContentCryptographer cryptographer;
  private final SecretTransformer transformer;

  private SecretFixtures(SecretDAO secretDAO) {
    this.secretDAO = secretDAO;
    this.cryptographer = CryptoFixtures.contentCryptographer();
    this.transformer = new SecretTransformer(cryptographer);
  }

  /**
   * @return builds a fixture-making object using the given {@link SecretDAO}
   */
  public static SecretFixtures using(SecretDAO secretDAO) {
    return new SecretFixtures(secretDAO);
  }

  /**
   * Create a new secret.
   *
   * @param name secret name
   * @param content secret content
   * @return created secret model
   */
  public Secret createSecret(String name, String content) {
    String hmac = cryptographer.computeHmac(content.getBytes(UTF_8), "hmackey");
    if (hmac == null) {
      throw new ContentEncodingException("Error encoding content in SecretFixture!");
    }
    String encryptedContent = cryptographer.encryptionKeyDerivedFrom(name).encrypt(content);
    long id = secretDAO.createSecret(name, null, encryptedContent, hmac, "creator", ImmutableMap.of(), 0, "", null,
        ImmutableMap.of());
    return transformer.transform(secretDAO.getSecretById(id).get());
  }
}
