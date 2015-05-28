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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import keywhiz.service.crypto.ContentCryptographer;
import keywhiz.service.crypto.SecretTransformer;
import org.jooq.DSLContext;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toList;

public class SecretController {
  private final SecretTransformer transformer;
  private final ContentCryptographer cryptographer;
  private final SecretDAO secretDAO;
  private final DSLContext dslContext;

  public SecretController(SecretTransformer transformer, ContentCryptographer cryptographer,
      DSLContext dslContext, SecretDAO secretDAO) {
    this.transformer = transformer;
    this.cryptographer = cryptographer;
    this.dslContext = dslContext;
    this.secretDAO = secretDAO;
  }

  /**
   * @param secretId external secret series id to look up secrets by.
   * @return all Secrets with given id. May be empty or include multiple versions.
   */
  public List<Secret> getSecretsById(long secretId) {
    return transformer.transform(secretDAO.getSecretsById(dslContext, secretId));
  }

  /**
   * @param secretId external secret series id to look up secrets by.
   * @param version specific version of secret. May be empty.
   * @return Secret matching input parameters or Optional.absent().
   */
  public Optional<Secret> getSecretByIdAndVersion(long secretId, String version) {
    return secretDAO.getSecretByIdAndVersion(dslContext, secretId, version)
        .map(transformer::transform);
  }

  /**
   * @param name of secret series to look up secrets by.
   * @param version specific version of secret. May be empty.
   * @return Secret matching input parameters or Optional.absent().
   */
  public Optional<Secret> getSecretByNameAndVersion(String name, String version) {
    return secretDAO.getSecretByNameAndVersion(dslContext, name, version)
        .map(transformer::transform);
  }

  /** @return all existing sanitized secrets. */
  public List<SanitizedSecret> getSanitizedSecrets() {
    return secretDAO.getSecrets(dslContext).stream()
        .map(SanitizedSecret::fromSecretSeriesAndContent)
        .collect(toList());
  }

  /** @return all versions for this secret name. */
  public List<String> getVersionsForName(String name) {
    checkArgument(!name.isEmpty());
    return secretDAO.getVersionsForSecretName(dslContext, name);
  }

  public SecretBuilder builder(String name, String secret, String creator) {
    checkArgument(!name.isEmpty());
    checkArgument(!secret.isEmpty());
    checkArgument(!creator.isEmpty());
    String encryptedSecret = cryptographer.encryptionKeyDerivedFrom(name).encrypt(secret);
    return new SecretBuilder(transformer, dslContext, secretDAO, name, encryptedSecret, creator);
  }

  /** Builder to generate new secret series or versions with. */
  public static class SecretBuilder {
    private final SecretTransformer transformer;
    private final DSLContext dslContext;
    private final SecretDAO secretDAO;
    private final String name;
    private final String encryptedSecret;
    private final String creator;
    private String description = "";
    private Map<String, String> metadata = ImmutableMap.of();
    private String version = "";
    private String type;
    private Map<String, String> generationOptions = ImmutableMap.of();

    /**
     * @param transformer
     * @param secretDAO
     * @param name of secret series.
     * @param encryptedSecret encrypted content of secret version
     * @param creator username responsible for creating this secret version.
     */
    private SecretBuilder(SecretTransformer transformer, DSLContext dslContext, SecretDAO secretDAO,
        String name, String encryptedSecret, String creator) {
      this.transformer = transformer;
      this.dslContext = dslContext;
      this.secretDAO = secretDAO;
      this.name = name;
      this.encryptedSecret = encryptedSecret;
      this.creator = creator;
    }

    /**
     * Supply an optional description of the secret.
     * @param description description of secret
     * @return the builder
     */
    public SecretBuilder withDescription(String description) {
      this.description = checkNotNull(description);
      return this;
    }

    /**
     * Supply optional map of metadata properties for the secret.
     * @param metadata metadata of secret
     * @return the builder
     */
    public SecretBuilder withMetadata(Map<String, String> metadata) {
      this.metadata = checkNotNull(metadata);
      return this;
    }

    /**
     * Supply an optional version of the secret, otherwise the default '' is used.
     * @param version version of secret
     * @return the builder
     */
    public SecretBuilder withVersion(String version) {
      this.version = checkNotNull(version);
      return this;
    }

    /**
     * Supply a secret type, otherwise the default '' is used.
     * @param type type of secret
     * @return the builder
     */
    public SecretBuilder withType(String type) {
      this.type = checkNotNull(type);
      return this;
    }

    /**
     * Supply a map of options used to generate the secret.
     * @param generationOptions map of settings from the generator to persist
     * @return the builder
     */
    public SecretBuilder withGenerationOptions(Map<String, String> generationOptions) {
      this.generationOptions = checkNotNull(generationOptions);
      return this;
    }

    /**
     * Finalizes creation of a new secret.
     *
     * @return an instance of the newly created secret.
     */
    public Secret build() {
        secretDAO.createSecret(dslContext, name, encryptedSecret, version, creator, metadata,
            description, type, generationOptions);
        return transformer.transform(secretDAO.getSecretByNameAndVersion(dslContext, name, version)
            .get());
    }
  }
}
