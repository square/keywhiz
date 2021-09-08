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
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import keywhiz.KeywhizConfig;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.SanitizedSecretWithGroups;
import keywhiz.api.model.SanitizedSecretWithGroupsListAndCursor;
import keywhiz.api.model.Secret;
import keywhiz.api.model.SecretRetrievalCursor;
import keywhiz.api.model.SecretSeriesAndContent;
import keywhiz.service.crypto.ContentCryptographer;
import keywhiz.service.crypto.ContentEncodingException;
import keywhiz.service.crypto.SecretTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static keywhiz.api.model.SanitizedSecretWithGroups.fromSecretSeriesAndContentAndGroups;

public class SecretController {
  private static final Logger logger = LoggerFactory.getLogger(SecretController.class);
  private final SecretTransformer transformer;
  private final ContentCryptographer cryptographer;
  private final SecretDAO secretDAO;
  private final AclDAO aclDAO;
  private final KeywhizConfig config;

  public SecretController(SecretTransformer transformer, ContentCryptographer cryptographer,
      SecretDAO secretDAO, AclDAO aclDAO, KeywhizConfig config) {
    this.transformer = transformer;
    this.cryptographer = cryptographer;
    this.secretDAO = secretDAO;
    this.aclDAO = aclDAO;
    this.config = config;
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
   * @param names of secrets series to look up secrets by.
   * @return all existing secrets matching criteria.
   */
  public List<Secret> getSecretsByName(List<String> names) {
    return secretDAO.getSecretsByName(names).stream().map(transformer::transform).collect(toList());
  }

  /**
   * @param group limit results to secrets assigned to this group.
   * @return all existing secrets matching criteria.
   * */
  public List<Secret> getSecretsForGroup(Group group) {
    return secretDAO.getSecrets(null, group, null, null, null).stream()
        .map(transformer::transform)
        .collect(toList());
  }

  /**
   * @param expireMaxTime timestamp for farthest expiry to include
   * @param group limit results to secrets assigned to this group, if provided.
   * @return all existing sanitized secrets matching criteria.
   * */
  public List<SanitizedSecret> getSanitizedSecrets(@Nullable Long expireMaxTime, @Nullable Group group) {
    return secretDAO.getSecrets(expireMaxTime, group, null, null, null).stream()
        .map(SanitizedSecret::fromSecretSeriesAndContent)
        .collect(toList());
  }

  /**
   * @param expireMaxTime timestamp for farthest expiry to include
   * @return all existing sanitized secrets and their groups matching criteria.
   * */
  public List<SanitizedSecretWithGroups> getSanitizedSecretsWithGroups(@Nullable Long expireMaxTime) {
    ImmutableList<SecretSeriesAndContent> secrets = secretDAO.getSecrets(expireMaxTime, null,
        null, null, null);

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

  /**
   * @param expireMinTime timestamp for closest expiry to include (may be overridden by cursor)
   * @param expireMaxTime timestamp for farthest expiry to include
   * @param limit         limit on number of results to return
   * @param cursor        cursor to be used to enforce pagination
   * @return all existing sanitized secrets and their groups matching criteria.
   */
  public SanitizedSecretWithGroupsListAndCursor getSanitizedSecretsWithGroupsAndCursor(
      @Nullable Long expireMinTime,
      @Nullable Long expireMaxTime,
      @Nullable Integer limit,
      @Nullable SecretRetrievalCursor cursor) {
    // Retrieve secrets based on the cursor (if provided).
    ImmutableList<SecretSeriesAndContent> secrets;

    // Retrieve one additional record to detect when information is missing
    Integer updatedLimit = null;
    if (limit != null) {
      updatedLimit = limit + 1;
    }

    if (cursor == null) {
      secrets = secretDAO.getSecrets(expireMaxTime, null, expireMinTime, null, updatedLimit);
    } else {
      secrets = secretDAO.getSecrets(expireMaxTime, null, cursor.expiry(), cursor.name(),
          updatedLimit);
    }

    // Set the cursor and strip the final record from the secrets if necessary
    SecretRetrievalCursor newCursor = null;
    if (limit != null && secrets.size() > limit) {
      // The name and expiry in the new cursor will be the first entry in the next set of results
      newCursor = SecretRetrievalCursor.of(secrets.get(limit).series().name(),
          secrets.get(limit).content().expiry());
      // Trim the last record from the list
      secrets = secrets.subList(0, limit);
    }

    Set<Long> secretIds = secrets.stream().map(s -> s.series().id()).collect(toSet());

    Map<Long, List<Group>> groupsForSecrets = aclDAO.getGroupsForSecrets(secretIds);

    List<SanitizedSecretWithGroups> secretsWithGroups = secrets.stream().map(s -> {
      List<Group> groups = groupsForSecrets.get(s.series().id());
      if (groups == null) {
        groups = ImmutableList.of();
      }
      return fromSecretSeriesAndContentAndGroups(s, groups);
    }).collect(toList());

    try {
      return SanitizedSecretWithGroupsListAndCursor.of(secretsWithGroups,
          SecretRetrievalCursor.toUrlEncodedString(newCursor));
    } catch (Exception e) {
      logger.warn("Unable to encode cursor to string (cursor: {}): {}", newCursor, e.getMessage());
      // The cursor is malformed; return what information could be gathered
      return SanitizedSecretWithGroupsListAndCursor.of(secretsWithGroups, null);
    }
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
    validateSecretSize(secret);
    checkArgument(!creator.isEmpty());
    String hmac = cryptographer.computeHmac(secret.getBytes(UTF_8), "hmackey"); // Compute HMAC on base64 encoded data
    if (hmac == null) {
      throw new ContentEncodingException("Error encoding content for SecretBuilder!");
    }
    String encryptedSecret = cryptographer.encryptionKeyDerivedFrom(name).encrypt(secret);
    return new SecretBuilder(transformer, secretDAO, name, encryptedSecret, hmac, creator, expiry);
  }

  private void validateSecretSize(String base64EncodedSecret) {
    if (config.getMaximumSecretSizeInBytesInclusive() == null) {
      return;
    }

    int rawSecretLength = Base64.getDecoder().decode(base64EncodedSecret).length;

    if (rawSecretLength > config.getMaximumSecretSizeInBytesInclusive()) {
      throw new IllegalArgumentException(
          String.format(
              "Secret is too large. Secret size %,d bytes exceeds maximum size of %,d bytes.",
              rawSecretLength,
              config.getMaximumSecretSizeInBytesInclusive()));
    }
  }


  /** Builder to generate new secret series or versions with. */
  public static class SecretBuilder {
    private final SecretTransformer transformer;
    private final SecretDAO secretDAO;
    private final String name;
    private final String encryptedSecret;
    private final String hmac;
    private final String creator;
    private String ownerName;
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
     * Supply an optional owner of the secret.
     * @param ownerName owner of secret
     * @return the builder
     */
    public SecretBuilder withOwnerName(String ownerName) {
      this.ownerName = ownerName;
      return this;
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
        secretDAO.createSecret(
            name,
            ownerName,
            encryptedSecret,
            hmac,
            creator,
            metadata,
            expiry,
            description,
            type,
            generationOptions);
        return transformer.transform(secretDAO.getSecretByName(name).get());
    }

    public Secret createOrUpdate() {
      secretDAO.createOrUpdateSecret(
          name,
          ownerName,
          encryptedSecret,
          hmac,
          creator,
          metadata,
          expiry,
          description,
          type,
          generationOptions);
      return transformer.transform(secretDAO.getSecretByName(name).get());
    }
  }
}
