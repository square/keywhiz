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
import java.time.Instant;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.NotFoundException;
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
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static keywhiz.jooq.tables.Secrets.SECRETS;

/**
 * Primary class to interact with {@link Secret}s.
 *
 * Does not map to a table itself, but utilizes both {@link SecretSeriesDAO} and
 * {@link SecretContentDAO} to provide a more usable API.
 */
public class SecretDAO {
  private final DSLContext dslContext;
  private final SecretContentDAOFactory secretContentDAOFactory;
  private final SecretSeriesDAOFactory secretSeriesDAOFactory;
  private final ContentCryptographer cryptographer;

  private SecretDAO(DSLContext dslContext, SecretContentDAOFactory secretContentDAOFactory,
      SecretSeriesDAOFactory secretSeriesDAOFactory, ContentCryptographer cryptographer) {
    this.dslContext = dslContext;
    this.secretContentDAOFactory = secretContentDAOFactory;
    this.secretSeriesDAOFactory = secretSeriesDAOFactory;
    this.cryptographer = cryptographer;
  }

  @VisibleForTesting
  public long createSecret(String name, String encryptedSecret, String hmac,
      String creator, Map<String, String> metadata, long expiry, String description, @Nullable String type,
      @Nullable Map<String, String> generationOptions) {
    return dslContext.transactionResult(configuration -> {
      SecretContentDAO secretContentDAO = secretContentDAOFactory.using(configuration);
      SecretSeriesDAO secretSeriesDAO = secretSeriesDAOFactory.using(configuration);

      Optional<SecretSeries> secretSeries = secretSeriesDAO.getSecretSeriesByName(name);
      long secretId;
      if (secretSeries.isPresent()) {
        SecretSeries secretSeries1 = secretSeries.get();
        if (secretSeries1.currentVersion().isPresent()) {
          throw new DataAccessException(format("secret already present: %s", name));
        }
        secretId = secretSeries1.id();
        secretSeriesDAO.updateSecretSeries(secretId, name, creator, description, type, generationOptions);
      } else {
        secretId = secretSeriesDAO.createSecretSeries(name, creator, description, type,
            generationOptions);
      }

      long secretContentId = secretContentDAO.createSecretContent(secretId, encryptedSecret, hmac, creator, metadata, expiry);
      secretSeriesDAO.setCurrentVersion(secretId, secretContentId);

      return secretId;
    });
  }

  @VisibleForTesting
  public long createOrUpdateSecret(String name, String encryptedSecret, String hmac, String creator,
      Map<String, String> metadata, long expiry, String description, @Nullable String type,
      @Nullable Map<String, String> generationOptions) {
    return dslContext.transactionResult(configuration -> {
      SecretContentDAO secretContentDAO = secretContentDAOFactory.using(configuration);
      SecretSeriesDAO secretSeriesDAO = secretSeriesDAOFactory.using(configuration);

      Optional<SecretSeries> secretSeries = secretSeriesDAO.getSecretSeriesByName(name);
      long secretId;
      if (secretSeries.isPresent()) {
        SecretSeries secretSeries1 = secretSeries.get();
        secretId = secretSeries1.id();
        secretSeriesDAO.updateSecretSeries(secretId, name, creator, description, type, generationOptions);
      } else {
        secretId = secretSeriesDAO.createSecretSeries(name, creator, description, type, generationOptions);
      }

      long secretContentId = secretContentDAO.createSecretContent(secretId, encryptedSecret, hmac, creator, metadata, expiry);
      secretSeriesDAO.setCurrentVersion(secretId, secretContentId);

      return secretId;
    });
  }

  @VisibleForTesting
  public long partialUpdateSecret(String name, String creator, PartialUpdateSecretRequestV2 request) {
    return dslContext.transactionResult(configuration -> {
      SecretContentDAO secretContentDAO = secretContentDAOFactory.using(configuration);
      SecretSeriesDAO secretSeriesDAO = secretSeriesDAOFactory.using(configuration);

      // Get the current version of the secret, throwing exceptions if it is not found
      SecretSeries secretSeries = secretSeriesDAO.getSecretSeriesByName(name).orElseThrow(
          NotFoundException::new);
      Long currentVersion = secretSeries.currentVersion().orElseThrow(NotFoundException::new);
      SecretContent secretContent = secretContentDAO.getSecretContentById(currentVersion).orElseThrow(NotFoundException::new);

      long secretId = secretSeries.id();

      // Set the fields to the original series and current version's values or the request values if provided
      String description = request.descriptionPresent() ? request.description() : secretSeries.description();
      String type = request.typePresent() ? request.type() : secretSeries.type().orElse("");
      ImmutableMap<String, String> metadata = request.metadataPresent() ? request.metadata() : secretContent.metadata();
      Long expiry = request.expiryPresent() ? request.expiry() : secretContent.expiry();
      String encryptedContent = secretContent.encryptedContent();
      String hmac = secretContent.hmac();
      // Mirrors hmac-creation in SecretController
      if (request.contentPresent()) {
        hmac = cryptographer.computeHmac(request.content().getBytes(UTF_8)); // Compute HMAC on base64 encoded data
        if (hmac == null) {
          throw new ContentEncodingException("Error encoding content for SecretBuilder!");
        }
        encryptedContent = cryptographer.encryptionKeyDerivedFrom(name).encrypt(request.content());
      }

      secretSeriesDAO.updateSecretSeries(secretId, name, creator, description, type, secretSeries.generationOptions());

      long secretContentId = secretContentDAO.createSecretContent(secretId, encryptedContent, hmac, creator, metadata, expiry);
      secretSeriesDAO.setCurrentVersion(secretId, secretContentId);

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
    return dslContext.<Optional<SecretSeriesAndContent>>transactionResult(configuration ->  {
      SecretContentDAO secretContentDAO = secretContentDAOFactory.using(configuration);
      SecretSeriesDAO secretSeriesDAO = secretSeriesDAOFactory.using(configuration);

      Optional<SecretSeries> series = secretSeriesDAO.getSecretSeriesById(secretId);
      if (series.isPresent() && series.get().currentVersion().isPresent()) {
        long secretContentId = series.get().currentVersion().get();
        Optional<SecretContent> contents = secretContentDAO.getSecretContentById(secretContentId);
        if (!contents.isPresent()) {
          throw new IllegalStateException(format("failed to fetch secret %d, content %d not found.", secretId, secretContentId));
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
      Optional<SecretContent> secretContent = secretContentDAO.getSecretContentById(secretContentId);
      if (!secretContent.isPresent()) {
        return Optional.empty();
      }

      return Optional.of(SecretSeriesAndContent.of(series.get(), secretContent.get()));
    }
    return Optional.empty();
  }

  /** @return list of secrets. can limit/sort by expiry, and for group if given */
  public ImmutableList<SecretSeriesAndContent> getSecrets(@Nullable Long expireMaxTime, Group group) {
    return dslContext.transactionResult(configuration -> {
      SecretContentDAO secretContentDAO = secretContentDAOFactory.using(configuration);
      SecretSeriesDAO secretSeriesDAO = secretSeriesDAOFactory.using(configuration);

      ImmutableList.Builder<SecretSeriesAndContent> secretsBuilder = ImmutableList.builder();

      for (SecretSeries series : secretSeriesDAO.getSecretSeries(expireMaxTime, group)) {
        SecretContent content = secretContentDAO.getSecretContentById(series.currentVersion().get()).get();
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
  public ImmutableList<SecretSeriesAndContent> getSecretsBatched(int idx, int num, boolean newestFirst) {
    return dslContext.transactionResult(configuration -> {
      SecretContentDAO secretContentDAO = secretContentDAOFactory.using(configuration);
      SecretSeriesDAO secretSeriesDAO = secretSeriesDAOFactory.using(configuration);

      ImmutableList.Builder<SecretSeriesAndContent> secretsBuilder = ImmutableList.builder();

      for (SecretSeries series : secretSeriesDAO.getSecretSeriesBatched(idx, num, newestFirst)) {
        SecretContent content = secretContentDAO.getSecretContentById(series.currentVersion().get()).get();
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
      int versionIdx,
      int numVersions) {
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
   * @param name of secret series for which to reset secret version
   * @param versionId The identifier for the desired current version
   * @throws NotFoundException if secret not found
   */
  public void setCurrentSecretVersionByName(String name, long versionId) {
    checkArgument(!name.isEmpty());
    checkArgument(versionId >= 0);

    SecretSeriesDAO secretSeriesDAO = secretSeriesDAOFactory.using(dslContext.configuration());
    SecretSeries series = secretSeriesDAO.getSecretSeriesByName(name).orElseThrow(
        NotFoundException::new);
    secretSeriesDAO.setCurrentVersion(series.id(), versionId);
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

  public static class SecretDAOFactory implements DAOFactory<SecretDAO> {
    private final DSLContext jooq;
    private final DSLContext readonlyJooq;
    private final SecretContentDAOFactory secretContentDAOFactory;
    private final SecretSeriesDAOFactory secretSeriesDAOFactory;
    private final ContentCryptographer cryptographer;

    @Inject public SecretDAOFactory(DSLContext jooq, @Readonly DSLContext readonlyJooq,
        SecretContentDAOFactory secretContentDAOFactory,
        SecretSeriesDAOFactory secretSeriesDAOFactory,
        ContentCryptographer cryptographer) {
      this.jooq = jooq;
      this.readonlyJooq = readonlyJooq;
      this.secretContentDAOFactory = secretContentDAOFactory;
      this.secretSeriesDAOFactory = secretSeriesDAOFactory;
      this.cryptographer = cryptographer;
    }

    @Override public SecretDAO readwrite() {
      return new SecretDAO(jooq, secretContentDAOFactory, secretSeriesDAOFactory, cryptographer);
    }

    @Override public SecretDAO readonly() {
      return new SecretDAO(readonlyJooq, secretContentDAOFactory, secretSeriesDAOFactory, cryptographer);
    }

    @Override public SecretDAO using(Configuration configuration) {
      DSLContext dslContext = DSL.using(checkNotNull(configuration));
      return new SecretDAO(dslContext, secretContentDAOFactory, secretSeriesDAOFactory, cryptographer);
    }
  }
}
