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
import keywhiz.api.model.Secret;
import keywhiz.api.model.SecretContent;
import keywhiz.api.model.SecretSeries;
import keywhiz.api.model.SecretSeriesAndContent;
import org.skife.jdbi.v2.sqlobject.CreateSqlObject;
import org.skife.jdbi.v2.sqlobject.Transaction;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Primary class to interact with {@link Secret}s.
 *
 * Does not map to a table itself, but utilizes both {@link SecretSeriesDAO} and
 * {@link SecretContentDAO} to provide a more usable API.
 */
public abstract class SecretDAO implements Transactional<SecretDAO> {
  @CreateSqlObject protected abstract SecretContentDAO createSecretContentDAO();
  @CreateSqlObject protected abstract SecretSeriesDAO createSecretSeriesDAO();

  @Transaction
  @VisibleForTesting
  public long createSecret(String name, String encryptedSecret, String version,
      String creator, Map<String, String> metadata, String description, @Nullable String type,
      @Nullable Map<String, String> generationOptions) {
    // TODO(jlfwong): Should the description be updated...?
    long secretId;
    Optional<SecretSeries> secretSeries = createSecretSeriesDAO().getSecretSeriesByName(name);
    if (secretSeries.isPresent()) {
      secretId = secretSeries.get().getId();
    } else {
      secretId = createSecretSeriesDAO()
          .createSecretSeries(name, creator, description, type, generationOptions);
    }

    createSecretContentDAO().createSecretContent(secretId, encryptedSecret, version, creator, metadata);
    return secretId;
  }

  /**
   * @param secretId external secret series id to look up secrets by.
   * @return all Secrets with given id. May be empty or include multiple versions.
   */
  @Transaction
  public ImmutableList<SecretSeriesAndContent> getSecretsById(long secretId) {
    ImmutableList.Builder<SecretSeriesAndContent> secretsBuilder = ImmutableList.builder();

    Optional<SecretSeries> series = createSecretSeriesDAO().getSecretSeriesById(secretId);
    if (series.isPresent()) {
      ImmutableList<SecretContent> contents = createSecretContentDAO().getSecretContentsBySecretId(secretId);
      for (SecretContent content : contents) {
        secretsBuilder.add(SecretSeriesAndContent.of(series.get(), content));
      }
    }

    return secretsBuilder.build();
  }

  /**
   * @param secretId external secret series id to look up secrets by.
   * @param version specific version of secret. May be empty.
   * @return Secret matching input parameters or Optional.absent().
   */
  @Transaction
  public Optional<SecretSeriesAndContent> getSecretByIdAndVersion(long secretId, String version) {
    checkNotNull(version);
    Optional<SecretSeries> series = createSecretSeriesDAO().getSecretSeriesById(secretId);
    if (!series.isPresent()) {
      return Optional.empty();
    }

    Optional<SecretContent> content =
        createSecretContentDAO().getSecretContentBySecretIdAndVersion(secretId, version);
    if (!content.isPresent()) {
      return Optional.empty();
    }

    return Optional.of(SecretSeriesAndContent.of(series.get(), content.get()));
  }

  /**
   * @param name external secret series name to look up versions by
   * @return List of versions tied to the parameter secret name.
   */
  @Transaction
  public ImmutableList<String> getVersionsForSecretName(String name) {
    checkNotNull(name);
    Optional<SecretSeries> series = createSecretSeriesDAO().getSecretSeriesByName(name);
    if (!series.isPresent()) {
      return ImmutableList.of();
    }

    return createSecretContentDAO().getVersionFromSecretId(series.get().getId());
  }

  /**
   * @param name external secret series name to look up versions by
   * @return Latest secret version for secret name.
   */
  @Transaction
  public Optional<String> getLatestVersion(String name) {
    checkNotNull(name);
    Optional<SecretSeries> series = createSecretSeriesDAO().getSecretSeriesByName(name);
    if (!series.isPresent()) {
      return Optional.empty();
    }

    ImmutableList<String> versions = createSecretContentDAO().getVersionFromSecretId(series.get().getId());
    if (versions.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(versions.get(0));
  }

  /**
   * @param name of secret series to look up secrets by.
   * @param version specific version of secret. May be empty.
   * @return Secret matching input parameters or Optional.absent().
   */
  @Transaction
  public Optional<SecretSeriesAndContent> getSecretByNameAndVersion(String name, String version) {
    checkArgument(!name.isEmpty());
    checkNotNull(version);

    Optional<SecretSeries> secretSeries = createSecretSeriesDAO().getSecretSeriesByName(name);
    if (!secretSeries.isPresent()) {
      return Optional.empty();
    }

    SecretContentDAO secretContentDAO = createSecretContentDAO();
    Optional<SecretContent> secretContent =
        secretContentDAO.getSecretContentBySecretIdAndVersion(secretSeries.get().getId(), version);

    if (!secretContent.isPresent()) {
      return Optional.empty();
    }

    return Optional.of(SecretSeriesAndContent.of(secretSeries.get(), secretContent.get()));
  }

  /** @return all existing secrets. */
  @Transaction
  public ImmutableList<SecretSeriesAndContent> getSecrets() {
    SecretContentDAO secretContentDAO = createSecretContentDAO();
    ImmutableList.Builder<SecretSeriesAndContent> secretsBuilder = ImmutableList.builder();

    createSecretSeriesDAO().getSecretSeries().forEach(
        (series) -> secretContentDAO.getSecretContentsBySecretId(series.getId()).forEach(
            (content) -> secretsBuilder.add(SecretSeriesAndContent.of(series, content))));

    return secretsBuilder.build();
  }

  /** @return all existing secrets series (one series shared across versions). */
  @Transaction
  public ImmutableList<SecretSeries> getSecretSeries() {
    return createSecretSeriesDAO().getSecretSeries();
  }

  /**
   * Deletes the series and all associated version of the given secret series name.
   *
   * @param name of secret series to delete.
   */
  public void deleteSecretsByName(String name) {
    checkArgument(!name.isEmpty());
    createSecretSeriesDAO().deleteSecretSeriesByName(name);
  }

  /**
   * Deletes a specific version in a secret series.
   *
   * @param name of secret series to delete from.
   * @param version of secret to specifically delete.
   */
  @Transaction
  public void deleteSecretByNameAndVersion(String name, String version) {
    checkArgument(!name.isEmpty());
    checkNotNull(version);

    SecretSeriesDAO secretSeriesDAO = createSecretSeriesDAO();
    Optional<SecretSeries> secretSeries = secretSeriesDAO.getSecretSeriesByName(name);
    if (!secretSeries.isPresent()) {
      return;
    }

    SecretContentDAO secretContentDAO = createSecretContentDAO();
    secretContentDAO.deleteSecretContentBySecretIdAndVersion(secretSeries.get().getId(), version);

    long seriesId = secretSeries.get().getId();
    if (secretContentDAO.getSecretContentsBySecretId(seriesId).isEmpty()) {
      secretSeriesDAO.deleteSecretSeriesById(seriesId);
    }
  }
}
