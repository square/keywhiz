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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.SanitizedSecretWithGroups;
import keywhiz.api.model.Secret;
import keywhiz.api.model.SecretSeriesAndContent;
import keywhiz.service.crypto.ContentCryptographer;
import keywhiz.service.crypto.ContentEncodingException;
import keywhiz.service.crypto.SecretTransformer;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static keywhiz.api.model.SanitizedSecretWithGroups.fromSecretSeriesAndContentAndGroups;

public class SecretController {
  private final SecretTransformer transformer;
  private final ContentCryptographer cryptographer;
  private final SecretDAO secretDAO;
  private final AclDAO aclDAO;

  public SecretController(SecretTransformer transformer, ContentCryptographer cryptographer,
      SecretDAO secretDAO, AclDAO aclDAO) {
    this.transformer = transformer;
    this.cryptographer = cryptographer;
    this.secretDAO = secretDAO;
    this.aclDAO = aclDAO;
  }

  /**
   * @param secretId external secret series id to look up secrets by.
   * @return Secret matching input parameters or Optional.absent().
   */
  public Optional<Secret> getSecretById(long secretId) {
    return secretDAO.getSecretById(secretId).map(transformer::transform);
  }

  /**
   * @param name of secret series to look up secrets by.
   * @return Secret matching input parameters or Optional.absent().
   */
  public Optional<Secret> getSecretByName(String name) {
    return secretDAO.getSecretByName(name).map(transformer::transform);
  }

  /**
   * @param expireMaxTime timestamp for farthest expiry to include
   * @param group limit results to secrets assigned to this group, if provided.
   * @return all existing sanitized secrets matching criteria.
   * */
  public List<SanitizedSecret> getSanitizedSecrets(@Nullable Long expireMaxTime, Group group) {
    return secretDAO.getSecrets(expireMaxTime, group).stream()
        .map(SanitizedSecret::fromSecretSeriesAndContent)
        .collect(toList());
  }
  
  /**
   * @param expireMaxTime timestamp for farthest expiry to include
   * @return all existing sanitized secrets and their groups matching criteria.
   * */
  public List<SanitizedSecretWithGroups> getExpiringSanitizedSecrets(@Nullable Long expireMaxTime) {
    ImmutableList<SecretSeriesAndContent> secrets = secretDAO.getSecrets(expireMaxTime, null);

    Map<Long, SecretSeriesAndContent> secretIds = secrets.stream()
        .collect(toMap(s -> s.series().id(), s -> s));

    Map<Long, List<Group>> groupsForSecrets = aclDAO.getGroupsForSecrets(secretIds.keySet());


    return secrets.stream().map(s -> {
      List<Group> groups = groupsForSecrets.get(s.series().id());
      if (groups == null) {
        groups = ImmutableList.of();
      }
      return fromSecretSeriesAndContentAndGroups(s, groups);
    }).collect(toList());
  }

  /** @return names of all existing sanitized secrets. */
  public List<SanitizedSecret> getSecretsNameOnly() {
    return secretDAO.getSecretsNameOnly()
        .stream()
        .map(s -> SanitizedSecret.of(s.getKey(), s.getValue()))
        .collect(toList());
  }

  /**
   * @param idx the first index to select in a list of secrets sorted by creation time
   * @param num the number of secrets after idx to select in the list of secrets
   * @param newestFirst if true, order the secrets from newest creation time to oldest
   * @return A list of secret names
   */
  public List<SanitizedSecret> getSecretsBatched(int idx, int num, boolean newestFirst) {
    checkArgument(idx >= 0, "Index must be positive when getting batched secret names!");
    checkArgument(num >= 0, "Num must be positive when getting batched secret names!");
    return secretDAO.getSecretsBatched(idx, num, newestFirst).stream()
        .map(SanitizedSecret::fromSecretSeriesAndContent)
        .collect(toList());
  }

  public SecretBuilder builder(String name, String secret, String creator, long expiry) {
    checkArgument(!name.isEmpty());
    checkArgument(!secret.isEmpty());
    checkArgument(!creator.isEmpty());
    String hmac = cryptographer.computeHmac(secret.getBytes(UTF_8)); // Compute HMAC on base64 encoded data
    if (hmac == null) {
      throw new ContentEncodingException("Error encoding content for SecretBuilder!");
    }
    String encryptedSecret = cryptographer.encryptionKeyDerivedFrom(name).encrypt(secret);
    return new SecretBuilder(transformer, secretDAO, name, encryptedSecret, hmac, creator, expiry);
  }

  /** Builder to generate new secret series or versions with. */
  public static class SecretBuilder {
    private final SecretTransformer transformer;
    private final SecretDAO secretDAO;
    private final String name;
    private final String encryptedSecret;
    private final String hmac;
    private final String creator;
    private String description = "";
    private Map<String, String> metadata = ImmutableMap.of();
    private long expiry = 0;
    private String type;
    private Map<String, String> generationOptions = ImmutableMap.of();

    /**
     * @param transformer
     * @param secretDAO
     * @param name of secret series.
     * @param encryptedSecret encrypted content of secret version
     * @param creator username responsible for creating this secret version.
     */
    private SecretBuilder(SecretTransformer transformer, SecretDAO secretDAO, String name, String encryptedSecret,
        String hmac, String creator, long expiry) {
      this.transformer = transformer;
      this.secretDAO = secretDAO;
      this.name = name;
      this.encryptedSecret = encryptedSecret;
      this.hmac = hmac;
      this.creator = creator;
      this.expiry = expiry;
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
     * Supply a secret type, otherwise the default '' is used.
     * @param type type of secret
     * @return the builder
     */
    public SecretBuilder withType(String type) {
      this.type = checkNotNull(type);
      return this;
    }

    /**
     * Finalizes creation of a new secret.
     *
     * @return an instance of the newly created secret.
     */
    public Secret create() {
        secretDAO.createSecret(name, encryptedSecret, hmac, creator, metadata, expiry, description, type,
            generationOptions);
        return transformer.transform(secretDAO.getSecretByName(name).get());
    }

    public Secret createOrUpdate() {
      secretDAO.createOrUpdateSecret(name, encryptedSecret, hmac, creator, metadata, expiry, description, type,
          generationOptions);
      return transformer.transform(secretDAO.getSecretByName(name).get());
    }
  }
}
