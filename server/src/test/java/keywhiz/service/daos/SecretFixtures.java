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
import keywhiz.service.crypto.CryptoFixtures;
import keywhiz.service.crypto.SecretTransformer;

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
   * Create a new secret without a version.
   *
   * @param name secret name
   * @param content secret content
   * @return created secret model
   */
  public Secret createSecret(String name, String content) {
    return createSecret(name, content, "");
  }

  /**
   * Create a new secret.
   *
   * @param name secret name
   * @param content secret content
   * @param version secret version
   * @return created secret model
   */
  public Secret createSecret(String name, String content, String version) {
    String encryptedContent = cryptographer.encryptionKeyDerivedFrom(name).encrypt(content);
    long id =
        secretDAO.createSecret(name, encryptedContent, version, "creator", ImmutableMap.of(),
            "", null, ImmutableMap.of());
    return transformer.transform(secretDAO.getSecretByIdAndVersion(id, version).get());
  }
}
