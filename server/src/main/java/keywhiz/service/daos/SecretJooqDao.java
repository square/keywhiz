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
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import keywhiz.api.model.Secret;
import keywhiz.api.model.SecretContent;
import keywhiz.api.model.SecretSeries;
import keywhiz.api.model.SecretSeriesAndContent;
import org.jooq.DSLContext;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Primary class to interact with {@link Secret}s.
 *
 * Does not map to a table itself, but utilizes both {@link SecretSeriesJooqDao} and
 * {@link SecretContentJooqDao} to provide a more usable API.
 */
public class SecretJooqDao {
  private final DSLContext dslContext;
  private SecretContentJooqDao secretContentJooqDao;
  private SecretSeriesJooqDao secretSeriesJooqDao;

  @Inject
  public SecretJooqDao(DSLContext dslContext, SecretContentJooqDao secretContentJooqDao,
      SecretSeriesJooqDao secretSeriesJooqDao) {
    this.dslContext = dslContext;
    this.secretContentJooqDao = secretContentJooqDao;
    this.secretSeriesJooqDao = secretSeriesJooqDao;
  }

  @VisibleForTesting
  public long createSecret(String name, String encryptedSecret, String version,
      String creator, Map<String, String> metadata, String description, @Nullable String type,
      @Nullable Map<String, String> generationOptions) {
    // TODO(jlfwong): Should the description be updated...?

    return dslContext.transactionResult(configuration -> {
      Optional<SecretSeries> secretSeries = secretSeriesJooqDao.getSecretSeriesByName(name);
      long secretId;
      if (secretSeries.isPresent()) {
        secretId = secretSeries.get().getId();
      } else {
        secretId = secretSeriesJooqDao.createSecretSeries(name, creator, description, type,
            generationOptions);
      }

      secretContentJooqDao.createSecretContent(secretId, encryptedSecret, version, creator,
          metadata);
      return secretId;
    });
  }

  /**
   * @param secretId external secret series id to look up secrets by.
   * @return all Secrets with given id. May be empty or include multiple versions.
   */
  public ImmutableList<SecretSeriesAndContent> getSecretsById(long secretId) {
    return dslContext.transactionResult(configuration -> {
      ImmutableList.Builder<SecretSeriesAndContent> secretsBuilder = ImmutableList.builder();

      Optional<SecretSeries> series = secretSeriesJooqDao.getSecretSeriesById(secretId);
      if (series.isPresent()) {
        ImmutableList<SecretContent> contents = secretContentJooqDao.getSecretContentsBySecretId(secretId);
        for (SecretContent content : contents) {
          secretsBuilder.add(SecretSeriesAndContent.of(series.get(), content));
        }
      }

      return secretsBuilder.build();
    });
  }

  /**
   * @param secretId external secret series id to look up secrets by.
   * @param version specific version of secret. May be empty.
   * @return Secret matching input parameters or Optional.absent().
   */
  public Optional<SecretSeriesAndContent> getSecretByIdAndVersion(long secretId, String version) {
    checkNotNull(version);

    // Cast to fix issue with mvn + java8 (not showing up in Intellij).
    return dslContext.<Optional<SecretSeriesAndContent>>transactionResult(configuration -> {
      Optional<SecretSeries> series = secretSeriesJooqDao.getSecretSeriesById(secretId);
      if (!series.isPresent()) {
        return Optional.empty();
      }

      Optional<SecretContent> content =
          secretContentJooqDao.getSecretContentBySecretIdAndVersion(secretId, version);
      if (!content.isPresent()) {
        return Optional.empty();
      }

      return Optional.of(SecretSeriesAndContent.of(series.get(), content.get()));
    });
  }

  /**
   * @param name external secret series name to look up versions by
   * @return List of versions tied to the parameter secret name.
   */
  public ImmutableList<String> getVersionsForSecretName(String name) {
    checkNotNull(name);

    // Cast to fix issue with mvn + java8 (not showing up in Intellij).
    return dslContext.<ImmutableList<String>>transactionResult(configuration -> {
      Optional<SecretSeries> series = secretSeriesJooqDao.getSecretSeriesByName(name);
      if (!series.isPresent()) {
        return ImmutableList.of();
      }

      return secretContentJooqDao.getVersionFromSecretId(series.get().getId());
    });
  }

  /**
   * @param name of secret series to look up secrets by.
   * @param version specific version of secret. May be empty.
   * @return Secret matching input parameters or Optional.absent().
   */
  public Optional<SecretSeriesAndContent> getSecretByNameAndVersion(String name, String version) {
    checkArgument(!name.isEmpty());
    checkNotNull(version);

    // Cast to fix issue with mvn + java8 (not showing up in Intellij).
    return dslContext.<Optional<SecretSeriesAndContent>>transactionResult(configuration -> {
      Optional<SecretSeries> secretSeries = secretSeriesJooqDao.getSecretSeriesByName(name);
      if (!secretSeries.isPresent()) {
        return Optional.empty();
      }

      Optional<SecretContent> secretContent =
          secretContentJooqDao.getSecretContentBySecretIdAndVersion(secretSeries.get().getId(),
              version);
      if (!secretContent.isPresent()) {
        return Optional.empty();
      }

      return Optional.of(SecretSeriesAndContent.of(secretSeries.get(), secretContent.get()));
    });
  }

  /** @return all existing secrets. */
  public ImmutableList<SecretSeriesAndContent> getSecrets() {
    return dslContext.transactionResult(configuration -> {
      ImmutableList.Builder<SecretSeriesAndContent> secretsBuilder = ImmutableList.builder();

      secretSeriesJooqDao.getSecretSeries()
          .forEach((series) -> secretContentJooqDao.getSecretContentsBySecretId(series.getId())
              .forEach((content) -> secretsBuilder.add(SecretSeriesAndContent.of(series, content))));

      return secretsBuilder.build();
    });
  }

  /**
   * Deletes the series and all associated version of the given secret series name.
   *
   * @param name of secret series to delete.
   */
  public void deleteSecretsByName(String name) {
    checkArgument(!name.isEmpty());
    secretSeriesJooqDao.deleteSecretSeriesByName(name);
  }

  /**
   * Deletes a specific version in a secret series.
   *
   * @param name of secret series to delete from.
   * @param version of secret to specifically delete.
   */
  public void deleteSecretByNameAndVersion(String name, String version) {
    checkArgument(!name.isEmpty());
    checkNotNull(version);

    dslContext.transaction(configuration -> {
      Optional<SecretSeries> secretSeries = secretSeriesJooqDao.getSecretSeriesByName(name);
      if (!secretSeries.isPresent()) {
        return;
      }

      secretContentJooqDao.deleteSecretContentBySecretIdAndVersion(secretSeries.get().getId(),
          version);

      long seriesId = secretSeries.get().getId();
      if (secretContentJooqDao.getSecretContentsBySecretId(seriesId).isEmpty()) {
        secretSeriesJooqDao.deleteSecretSeriesById(seriesId);
      }
    });
  }
}
