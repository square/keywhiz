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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import keywhiz.api.automation.v2.PartialUpdateSecretRequestV2;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import keywhiz.api.model.SecretContent;
import keywhiz.api.model.SecretSeries;
import keywhiz.api.model.SecretSeriesAndContent;
import keywhiz.jooq.tables.Secrets;
import keywhiz.service.config.Readonly;
import keywhiz.service.crypto.ContentCryptographer;
import keywhiz.service.crypto.ContentEncodingException;
import keywhiz.service.daos.SecretContentDAO.SecretContentDAOFactory;
import keywhiz.service.daos.SecretSeriesDAO.SecretSeriesDAOFactory;
import keywhiz.service.exceptions.ConflictException;
import keywhiz.service.permissions.Action;
import keywhiz.service.permissions.PermissionCheck;
import org.joda.time.DateTime;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static keywhiz.jooq.tables.Secrets.SECRETS;

/**
 * Primary class to interact with {@link Secret}s.
 *
 * Does not map to a table itself, but utilizes both {@link SecretSeriesDAO} and {@link
 * SecretContentDAO} to provide a more usable API.
 */
public class SecretDAO {
  private final DSLContext dslContext;
  private final SecretContentDAOFactory secretContentDAOFactory;
  private final SecretSeriesDAOFactory secretSeriesDAOFactory;
  private final GroupDAO.GroupDAOFactory groupDAOFactory;
  private final ContentCryptographer cryptographer;
  private final PermissionCheck permissionCheck;

  // this is the maximum length of a secret name, so that it will still fit in the 255 char limit
  // of the database field if it is deleted and auto-renamed
  private static final int SECRET_NAME_MAX_LENGTH = 195;

  // this is the maximum number of rows that will be deleted per-transaction by the endpoint
  // to permanently remove secrets
  private static final int MAX_ROWS_REMOVED_PER_TRANSACTION = 1000;

  public SecretDAO(
      DSLContext dslContext,
      SecretContentDAOFactory secretContentDAOFactory,
      SecretSeriesDAOFactory secretSeriesDAOFactory,
      GroupDAO.GroupDAOFactory groupDAOFactory,
      ContentCryptographer cryptographer,
      PermissionCheck permissionCheck) {
    this.dslContext = dslContext;
    this.secretContentDAOFactory = secretContentDAOFactory;
    this.secretSeriesDAOFactory = secretSeriesDAOFactory;
    this.groupDAOFactory = groupDAOFactory;
    this.cryptographer = cryptographer;
    this.permissionCheck = permissionCheck;
  }

  @VisibleForTesting
  public long createSecret(
      String name,
      String ownerName,
      String encryptedSecret,
      String hmac,
      String creator,
      Map<String, String> metadata,
      long expiry,
      String description,
      @Nullable String type,
      @Nullable Map<String, String> generationOptions) {

    return dslContext.transactionResult(configuration -> {
      // disallow use of a leading period in secret names
      // check is here because this is where all APIs converge on secret creation
      if (name.startsWith(".")) {
        throw new BadRequestException(format("secret cannot be created with name `%s` - secret "
            + "names cannot begin with a period", name));
      }

      // enforce a shorter max length than the db to ensure secrets renamed on deletion still fit
      if (name.length() > SECRET_NAME_MAX_LENGTH) {
        throw new BadRequestException(format("secret cannot be created with name `%s` - secret "
            + "names must be %d characters or less", name, SECRET_NAME_MAX_LENGTH));
      }

      long now = OffsetDateTime.now().toEpochSecond();

      SecretContentDAO secretContentDAO = secretContentDAOFactory.using(configuration);
      SecretSeriesDAO secretSeriesDAO = secretSeriesDAOFactory.using(configuration);

      Long ownerId = getOwnerId(configuration, ownerName);

      Optional<SecretSeries> secretSeries = secretSeriesDAO.getSecretSeriesByName(name);
      long secretId;
      if (secretSeries.isPresent()) {
        SecretSeries secretSeries1 = secretSeries.get();
        if (secretSeries1.currentVersion().isPresent()) {
          throw new DataAccessException(format("secret already present: %s", name));
        } else {
          // Unreachable unless the implementation of getSecretSeriesByName is changed
          throw new IllegalStateException(
              format("secret %s retrieved without current version set", name));
        }
      } else {
        secretId = secretSeriesDAO.createSecretSeries(
            name,
            ownerId,
            creator,
            description,
            type,
            generationOptions,
            now);
      }

      long secretContentId = secretContentDAO.createSecretContent(secretId, encryptedSecret, hmac,
          creator, metadata, expiry, now);
      secretSeriesDAO.setCurrentVersion(secretId, secretContentId, creator, now);

      return secretId;
    });
  }

  @VisibleForTesting
  public long createOrUpdateSecret(
      String name,
      String owner,
      String encryptedSecret,
      String hmac,
      String creator,
      Map<String, String> metadata,
      long expiry,
      String description,
      @Nullable String type,
      @Nullable Map<String, String> generationOptions) {
    // SecretController should have already checked that the contents are not empty
    return dslContext.transactionResult(configuration -> {
      long now = OffsetDateTime.now().toEpochSecond();

      SecretContentDAO secretContentDAO = secretContentDAOFactory.using(configuration);
      SecretSeriesDAO secretSeriesDAO = secretSeriesDAOFactory.using(configuration);

      Long ownerId = getOwnerId(configuration, owner);

      Optional<SecretSeries> secretSeries = secretSeriesDAO.getSecretSeriesByName(name);
      long secretId;
      if (secretSeries.isPresent()) {
        SecretSeries secretSeries1 = secretSeries.get();
        secretId = secretSeries1.id();

        Long effectiveOwnerId = ownerId != null
            ? ownerId
            : getOwnerId(configuration, secretSeries1.owner());

        secretSeriesDAO.updateSecretSeries(
            secretId,
            name,
            effectiveOwnerId,
            creator,
            description,
            type,
            generationOptions,
            now);
      } else {
        secretId = secretSeriesDAO.createSecretSeries(
            name,
            ownerId,
            creator,
            description,
            type,
            generationOptions,
            now);
      }

      long secretContentId = secretContentDAO.createSecretContent(secretId, encryptedSecret, hmac,
          creator, metadata, expiry, now);
      secretSeriesDAO.setCurrentVersion(secretId, secretContentId, creator, now);

      return secretId;
    });
  }

  @VisibleForTesting
  public long partialUpdateSecret(String name, String creator,
      PartialUpdateSecretRequestV2 request) {
    return dslContext.transactionResult(configuration -> {
      long now = OffsetDateTime.now().toEpochSecond();

      SecretContentDAO secretContentDAO = secretContentDAOFactory.using(configuration);
      SecretSeriesDAO secretSeriesDAO = secretSeriesDAOFactory.using(configuration);

      // Get the current version of the secret, throwing exceptions if it is not found
      SecretSeries secretSeries = secretSeriesDAO.getSecretSeriesByName(name).orElseThrow(
          NotFoundException::new);
      Long currentVersion = secretSeries.currentVersion().orElseThrow(NotFoundException::new);
      SecretContent secretContent =
          secretContentDAO.getSecretContentById(currentVersion).orElseThrow(NotFoundException::new);

      long secretId = secretSeries.id();

      // Set the fields to the original series and current version's values or the request values if provided
      String description = request.descriptionPresent()
          ? request.description()
          : secretSeries.description();
      String type = request.typePresent()
          ? request.type()
          : secretSeries.type().orElse("");
      ImmutableMap<String, String> metadata = request.metadataPresent()
          ? request.metadata()
          : secretContent.metadata();
      Long expiry = request.expiryPresent()
          ? request.expiry()
          : secretContent.expiry();

      String owner = request.ownerPresent()
          ? request.owner()
          : secretSeries.owner();
      Long ownerId = getOwnerId(configuration, owner);

      String encryptedContent = secretContent.encryptedContent();
      String hmac = secretContent.hmac();
      // Mirrors hmac-creation in SecretController
      if (request.contentPresent()) {
        checkArgument(!request.content().isEmpty());

        hmac = cryptographer.computeHmac(
            request.content().getBytes(UTF_8), "hmackey"); // Compute HMAC on base64 encoded data
        if (hmac == null) {
          throw new ContentEncodingException("Error encoding content for SecretBuilder!");
        }
        encryptedContent = cryptographer.encryptionKeyDerivedFrom(name).encrypt(request.content());
      }

      secretSeriesDAO.updateSecretSeries(
          secretId,
          name,
          ownerId,
          creator,
          description,
          type,
          secretSeries.generationOptions(),
          now);

      long secretContentId = secretContentDAO.createSecretContent(secretId, encryptedContent, hmac,
          creator, metadata, expiry, now);
      secretSeriesDAO.setCurrentVersion(secretId, secretContentId, creator, now);

      return secretId;
    });
  }

  public boolean setExpiration(String name, Instant expiration) {
    return dslContext.transactionResult(configuration -> {
      SecretSeriesDAO secretSeriesDAO = secretSeriesDAOFactory.using(configuration);

      Optional<SecretSeries> secretSeries = secretSeriesDAO.getSecretSeriesByName(name);
      if (secretSeries.isPresent()) {
        Optional<Long> currentVersion = secretSeries.get().currentVersion();
        if (currentVersion.isPresent()) {
          return secretSeriesDAO.setExpiration(currentVersion.get(), expiration) > 0;
        }
      }
      return false;
    });
  }

  /**
   * @param secretId external secret series id to look up secrets by.
   * @return Secret matching input parameters or Optional.absent().
   */
  public Optional<SecretSeriesAndContent> getSecretById(long secretId) {
    return dslContext.<Optional<SecretSeriesAndContent>>transactionResult(configuration -> {
      SecretContentDAO secretContentDAO = secretContentDAOFactory.using(configuration);
      SecretSeriesDAO secretSeriesDAO = secretSeriesDAOFactory.using(configuration);

      Optional<SecretSeries> series = secretSeriesDAO.getSecretSeriesById(secretId);
      if (series.isPresent() && series.get().currentVersion().isPresent()) {
        long secretContentId = series.get().currentVersion().get();
        Optional<SecretContent> contents = secretContentDAO.getSecretContentById(secretContentId);
        if (!contents.isPresent()) {
          throw new IllegalStateException(
              format("failed to fetch secret %d, content %d not found.", secretId,
                  secretContentId));
        }
        return Optional.of(SecretSeriesAndContent.of(series.get(), contents.get()));
      }
      return Optional.empty();
    });
  }

  /**
   * @param name of secret series to look up secrets by.
   * @return Secret matching input parameters or Optional.absent().
   */
  public Optional<SecretSeriesAndContent> getSecretByName(String name) {
    checkArgument(!name.isEmpty());

    // In the past, the two data fetches below were wrapped in a transaction. The transaction was
    // removed because jOOQ transactions doesn't play well with MySQL readonly connections
    // (see https://github.com/jOOQ/jOOQ/issues/3955).
    //
    // A possible work around is to write a transaction manager (see http://git.io/vkuFM)
    //
    // Removing the transaction however seems to be simpler and safe. The first data fetch's
    // secret.id is used for the second data fetch.
    //
    // A third way to work around this issue is to write a SQL join. Jooq makes it relatively easy,
    // but such joins hurt code re-use.
    SecretContentDAO secretContentDAO = secretContentDAOFactory.using(dslContext.configuration());
    SecretSeriesDAO secretSeriesDAO = secretSeriesDAOFactory.using(dslContext.configuration());

    Optional<SecretSeries> series = secretSeriesDAO.getSecretSeriesByName(name);
    if (series.isPresent() && series.get().currentVersion().isPresent()) {
      long secretContentId = series.get().currentVersion().get();
      Optional<SecretContent> secretContent =
          secretContentDAO.getSecretContentById(secretContentId);
      if (!secretContent.isPresent()) {
        return Optional.empty();
      }

      return Optional.of(SecretSeriesAndContent.of(series.get(), secretContent.get()));
    }
    return Optional.empty();
  }

  /**
   * @param names of secrets series to look up secrets by.
   * @return Secrets matching input parameters.
   */
  public List<SecretSeriesAndContent> getSecretsByName(List<String> names) {
    checkArgument(!names.isEmpty());

    SecretContentDAO secretContentDAO = secretContentDAOFactory.using(dslContext.configuration());
    SecretSeriesDAO secretSeriesDAO = secretSeriesDAOFactory.using(dslContext.configuration());

    List<SecretSeries> multipleSeries = secretSeriesDAO.getMultipleSecretSeriesByName(names);

    List<SecretSeriesAndContent> ret = new ArrayList<SecretSeriesAndContent>();

    for (SecretSeries series : multipleSeries) {
      if (series.currentVersion().isPresent()) {
        long secretContentId = series.currentVersion().get();
        Optional<SecretContent> secretContent =
                secretContentDAO.getSecretContentById(secretContentId);
        if (secretContent.isPresent()) {
          ret.add(SecretSeriesAndContent.of(series, secretContent.get()));
        } else {
          throw new NotFoundException("Secret not found.");
        }
      }
    }
    return ret;
  }

  /**
   * @param expireMaxTime the maximum expiration date for secrets to return (exclusive)
   * @param group the group secrets returned must be assigned to
   * @param expireMinTime the minimum expiration date for secrets to return (inclusive)
   * @param minName the minimum name (alphabetically) that will be returned for secrets
   *                expiring on expireMinTime (inclusive)
   * @param limit the maximum number of secrets to return
   *               which to start the list of returned secrets
   * @return list of secrets. can limit/sort by expiry, and for group if given
   */
  public ImmutableList<SecretSeriesAndContent> getSecrets(@Nullable Long expireMaxTime,
      @Nullable Group group, @Nullable Long expireMinTime, @Nullable String minName,
      @Nullable Integer limit) {
    return dslContext.transactionResult(configuration -> {
      SecretContentDAO secretContentDAO = secretContentDAOFactory.using(configuration);
      SecretSeriesDAO secretSeriesDAO = secretSeriesDAOFactory.using(configuration);

      ImmutableList.Builder<SecretSeriesAndContent> secretsBuilder = ImmutableList.builder();

      for (SecretSeries series : secretSeriesDAO.getSecretSeries(expireMaxTime, group,
          expireMinTime, minName, limit)) {
        SecretContent content =
            secretContentDAO.getSecretContentById(series.currentVersion().get()).get();
        SecretSeriesAndContent seriesAndContent = SecretSeriesAndContent.of(series, content);
        secretsBuilder.add(seriesAndContent);
      }

      return secretsBuilder.build();
    });
  }

  /**
   * @return A list of id, name
   */
  public ImmutableList<SimpleEntry<Long, String>> getSecretsNameOnly() {
    List<SimpleEntry<Long, String>> results = dslContext.select(SECRETS.ID, SECRETS.NAME)
        .from(SECRETS)
        .where(SECRETS.CURRENT.isNotNull())
        .fetchInto(Secrets.SECRETS)
        .map(r -> new SimpleEntry<>(r.getId(), r.getName()));
    return ImmutableList.copyOf(results);
  }

  /**
   * @param idx the first index to select in a list of secrets sorted by creation time
   * @param num the number of secrets after idx to select in the list of secrets
   * @param newestFirst if true, order the secrets from newest creation time to oldest
   * @return A list of secrets
   */
  public ImmutableList<SecretSeriesAndContent> getSecretsBatched(int idx, int num,
      boolean newestFirst) {
    return dslContext.transactionResult(configuration -> {
      SecretContentDAO secretContentDAO = secretContentDAOFactory.using(configuration);
      SecretSeriesDAO secretSeriesDAO = secretSeriesDAOFactory.using(configuration);

      ImmutableList.Builder<SecretSeriesAndContent> secretsBuilder = ImmutableList.builder();

      for (SecretSeries series : secretSeriesDAO.getSecretSeriesBatched(idx, num, newestFirst)) {
        SecretContent content =
            secretContentDAO.getSecretContentById(series.currentVersion().get()).get();
        SecretSeriesAndContent seriesAndContent = SecretSeriesAndContent.of(series, content);
        secretsBuilder.add(seriesAndContent);
      }

      return secretsBuilder.build();
    });
  }

  /**
   * @param name of secret series to look up secrets by.
   * @param versionIdx the first index to select in a list of versions sorted by creation time
   * @param numVersions the number of versions after versionIdx to select in the list of versions
   * @return Versions of a secret matching input parameters or Optional.absent().
   */
  public Optional<ImmutableList<SanitizedSecret>> getSecretVersionsByName(String name,
      int versionIdx, int numVersions) {
    checkArgument(!name.isEmpty());
    checkArgument(versionIdx >= 0);
    checkArgument(numVersions >= 0);

    SecretContentDAO secretContentDAO = secretContentDAOFactory.using(dslContext.configuration());
    SecretSeriesDAO secretSeriesDAO = secretSeriesDAOFactory.using(dslContext.configuration());

    Optional<SecretSeries> series = secretSeriesDAO.getSecretSeriesByName(name);
    if (series.isPresent()) {
      SecretSeries s = series.get();
      long secretId = s.id();
      Optional<ImmutableList<SecretContent>> contents =
          secretContentDAO.getSecretVersionsBySecretId(secretId, versionIdx, numVersions);
      if (contents.isPresent()) {
        ImmutableList.Builder<SanitizedSecret> b = new ImmutableList.Builder<>();
        b.addAll(contents.get()
            .stream()
            .map(c -> SanitizedSecret.fromSecretSeriesAndContent(SecretSeriesAndContent.of(s, c)))
            .collect(toList()));

        return Optional.of(b.build());
      }
    }

    return Optional.empty();
  }

  /**
   *
   * @param secretId, the secret's id
   * @param versionIdx the first index to select in a list of versions sorted by creation time
   * @param numVersions the number of versions after versionIdx to select in the list of versions
   * @return all versions of a deleted secret, including the secret's content for each version,
   * matching input parameters or Optional.absent().
   */
  public Optional<ImmutableList<SecretSeriesAndContent>> getDeletedSecretVersionsBySecretId(long secretId,
      int versionIdx, int numVersions) {
    checkArgument(versionIdx >= 0);
    checkArgument(numVersions >= 0);

    SecretContentDAO secretContentDAO = secretContentDAOFactory.using(dslContext.configuration());
    SecretSeriesDAO secretSeriesDAO = secretSeriesDAOFactory.using(dslContext.configuration());

    Optional<SecretSeries> series = secretSeriesDAO.getDeletedSecretSeriesById(secretId);
    if (series.isPresent()) {
      SecretSeries s = series.get();
      Optional<ImmutableList<SecretContent>> contents =
          secretContentDAO.getSecretVersionsBySecretId(secretId, versionIdx, numVersions);
      if (contents.isPresent()) {
        ImmutableList.Builder<SecretSeriesAndContent> b = new ImmutableList.Builder<>();
        b.addAll(contents.get()
            .stream()
            .map(c ->SecretSeriesAndContent.of(s, c))
            .collect(toList()));

        return Optional.of(b.build());
      }
    }

    return Optional.empty();
  }

  public List<SecretSeries> getSecretsWithDeletedName(String name) {
    SecretSeriesDAO secretSeriesDAO = secretSeriesDAOFactory.using(dslContext.configuration());

    return secretSeriesDAO.getSecretSeriesByDeletedName(name);
  }

  /**
   * @param name of secret series for which to reset secret version
   * @param versionId The identifier for the desired current version
   * @param updater the user to be linked to this update
   * @throws NotFoundException if secret not found
   */
  public void setCurrentSecretVersionByName(String name, long versionId, String updater) {
    checkArgument(!name.isEmpty());

    SecretSeriesDAO secretSeriesDAO = secretSeriesDAOFactory.using(dslContext.configuration());
    SecretSeries series = secretSeriesDAO.getSecretSeriesByName(name).orElseThrow(
        NotFoundException::new);
    secretSeriesDAO.setCurrentVersion(series.id(), versionId, updater,
        OffsetDateTime.now().toEpochSecond());
  }

  /**
   * Deletes the series and all associated version of the given secret series name.
   *
   * @param name of secret series to delete.
   */
  public void deleteSecretsByName(String name) {
    checkArgument(!name.isEmpty());

    secretSeriesDAOFactory.using(dslContext.configuration())
        .deleteSecretSeriesByName(name);
  }

  /**
   * Renames the secret, specified by the secret id, to the name provided
   * We check to make sure there are no other secrets that have the same name - if so,
   * we throw an exception to prevent multiple secrets from having the same name
   * @param secretId
   * @param name
   */
  public void renameSecretById(long secretId, String name, String creator) {
    checkArgument(!name.isEmpty());
    Optional<SecretSeries> secretSeriesWithName =
        secretSeriesDAOFactory.using(dslContext.configuration()).getSecretSeriesByName(name);
    if(secretSeriesWithName.isPresent()) {
      throw new ConflictException(
          String.format("name %s already used by an existing secret in keywhiz", name));
    }

    secretSeriesDAOFactory.using(dslContext.configuration())
        .renameSecretSeriesById(secretId, name, creator, OffsetDateTime.now().toEpochSecond());
  }

  /**
   * Updates the Secret Content ID for the given Secret
   *
   */
  public void setCurrentSecretVersionBySecretId(long secretId, long secretContentId, String updater) {
    secretSeriesDAOFactory.using(dslContext.configuration())
        .setCurrentVersion(secretId, secretContentId, updater,
        OffsetDateTime.now().toEpochSecond());
  }

  /**
   * @return the total number of deleted secrets.
   */
  public int countDeletedSecrets() {
    return secretSeriesDAOFactory.using(dslContext.configuration())
        .countDeletedSecretSeries();
  }

  /**
   * @param deleteBefore the cutoff date; secrets deleted before this date will be counted
   * @return the number of secrets deleted before the specified cutoff.
   */
  public int countSecretsDeletedBeforeDate(DateTime deleteBefore) {
    checkArgument(deleteBefore != null);

    // identify the secrets deleted before this date
    return secretSeriesDAOFactory.using(dslContext.configuration())
        .getIdsForSecretSeriesDeletedBeforeDate(deleteBefore)
        .size();
  }

  /**
   * Permanently removes the series and all secrets-contents records ("versions") which were deleted
   * before the given date.
   *
   * Unlike the "delete" endpoints above, THIS REMOVAL IS PERMANENT and cannot be undone by editing
   * the database to restore the "current" entries.
   *
   * @param deletedBefore the cutoff date; secrets deleted before this date will be removed from the
   * database
   * @param sleepMillis how many milliseconds to sleep between each batch of removals
   * @throws InterruptedException if interrupted while sleeping between batches
   */
  public void dangerPermanentlyRemoveSecretsDeletedBeforeDate(DateTime deletedBefore,
      int sleepMillis) throws InterruptedException {
    checkArgument(deletedBefore != null);
    SecretSeriesDAO secretSeriesDAO = secretSeriesDAOFactory.using(dslContext.configuration());
    SecretContentDAO secretContentDAO = secretContentDAOFactory.using(dslContext.configuration());

    // identify the secrets deleted before this date
    List<Long> ids = secretSeriesDAO.getIdsForSecretSeriesDeletedBeforeDate(deletedBefore);

    // batch the list of secrets to be removed, to reduce load on the database
    List<List<Long>> partitionedIds = Lists.partition(ids, MAX_ROWS_REMOVED_PER_TRANSACTION);
    for (List<Long> idBatch : partitionedIds) {
      // permanently remove the `secrets_contents` entries originally associated with these secrets
      secretContentDAO.dangerPermanentlyRemoveRecordsForGivenSecretsIDs(idBatch);

      // permanently remove the `secrets` entries for these secrets
      secretSeriesDAO.dangerPermanentlyRemoveRecordsForGivenIDs(idBatch);

      // sleep
      Thread.sleep(sleepMillis);
    }
  }

  private Long getOwnerId(Configuration configuration, String ownerName) {
    if (ownerName == null || ownerName.length() == 0) {
      return null;
    }

    GroupDAO groupDAO = groupDAOFactory.using(configuration);
    Optional<Group> maybeGroup = groupDAO.getGroup(ownerName);

    if (maybeGroup.isEmpty()) {
      throw new IllegalArgumentException(String.format("Unknown owner %s", ownerName));
    }

    return maybeGroup.get().getId();
  }

  public static class SecretDAOFactory implements DAOFactory<SecretDAO> {
    private final DSLContext jooq;
    private final DSLContext readonlyJooq;
    private final SecretContentDAOFactory secretContentDAOFactory;
    private final SecretSeriesDAOFactory secretSeriesDAOFactory;
    private final GroupDAO.GroupDAOFactory groupDAOFactory;
    private final ContentCryptographer cryptographer;
    private final PermissionCheck permissionCheck;

    @Inject public SecretDAOFactory(
        DSLContext jooq,
        @Readonly DSLContext readonlyJooq,
        SecretContentDAOFactory secretContentDAOFactory,
        SecretSeriesDAOFactory secretSeriesDAOFactory,
        GroupDAO.GroupDAOFactory groupDAOFactory,
        ContentCryptographer cryptographer,
        PermissionCheck permissionCheck) {
      this.jooq = jooq;
      this.readonlyJooq = readonlyJooq;
      this.secretContentDAOFactory = secretContentDAOFactory;
      this.secretSeriesDAOFactory = secretSeriesDAOFactory;
      this.groupDAOFactory = groupDAOFactory;
      this.cryptographer = cryptographer;
      this.permissionCheck = permissionCheck;
    }

    @Override public SecretDAO readwrite() {
      return new SecretDAO(
          jooq,
          secretContentDAOFactory,
          secretSeriesDAOFactory,
          groupDAOFactory,
          cryptographer,
          permissionCheck);
    }

    @Override public SecretDAO readonly() {
      return new SecretDAO(
          readonlyJooq,
          secretContentDAOFactory,
          secretSeriesDAOFactory,
          groupDAOFactory,
          cryptographer,
          permissionCheck);
    }

    @Override public SecretDAO using(Configuration configuration) {
      DSLContext dslContext = DSL.using(checkNotNull(configuration));
      return new SecretDAO(
          dslContext,
          secretContentDAOFactory,
          secretSeriesDAOFactory,
          groupDAOFactory,
          cryptographer,
          permissionCheck);
    }
  }
}
