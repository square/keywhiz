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
import org.jooq.impl.DSL;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Primary class to interact with {@link Secret}s.
 *
 * Does not map to a table itself, but utilizes both {@link SecretSeriesDAO} and
 * {@link SecretContentDAO} to provide a more usable API.
 */
public class SecretDAO {
  private final SecretContentDAO secretContentDAO;
  private final SecretSeriesDAO secretSeriesDAO;

  @Inject
  public SecretDAO(SecretContentDAO secretContentDAO, SecretSeriesDAO secretSeriesDAO) {
    this.secretContentDAO = secretContentDAO;
    this.secretSeriesDAO = secretSeriesDAO;
  }

  @VisibleForTesting
  public long createSecret(DSLContext dslContext, String name, String encryptedSecret,
      String version, String creator, Map<String, String> metadata, String description,
      @Nullable String type, @Nullable Map<String, String> generationOptions) {
    checkNotNull(dslContext);

    // TODO(jlfwong): Should the description be updated...?

    return dslContext.transactionResult(configuration -> {
      DSLContext innerDslContext = DSL.using(configuration);

      Optional<SecretSeries> secretSeries = secretSeriesDAO.getSecretSeriesByName(innerDslContext,
          name);
      long secretId;
      if (secretSeries.isPresent()) {
        secretId = secretSeries.get().getId();
      } else {
        secretId = secretSeriesDAO.createSecretSeries(innerDslContext, name, creator, description,
            type, generationOptions);
      }

      secretContentDAO.createSecretContent(innerDslContext, secretId, encryptedSecret, version,
          creator, metadata);
      return secretId;
    });
  }

  /**
   * @param secretId external secret series id to look up secrets by.
   * @return all Secrets with given id. May be empty or include multiple versions.
   */
  public ImmutableList<SecretSeriesAndContent> getSecretsById(DSLContext dslContext,
      long secretId) {
    checkNotNull(dslContext);

    return dslContext.transactionResult(configuration -> {
      DSLContext innerDslContext = DSL.using(configuration);

      ImmutableList.Builder<SecretSeriesAndContent> secretsBuilder = ImmutableList.builder();

      Optional<SecretSeries> series = secretSeriesDAO.getSecretSeriesById(innerDslContext,
          secretId);
      if (series.isPresent()) {
        ImmutableList<SecretContent> contents =
            secretContentDAO.getSecretContentsBySecretId(innerDslContext, secretId);
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
  public Optional<SecretSeriesAndContent> getSecretByIdAndVersion(DSLContext dslContext,
      long secretId, String version) {
    checkNotNull(dslContext);
    checkNotNull(version);

    return dslContext.<Optional<SecretSeriesAndContent>>transactionResult(configuration -> {
      DSLContext innerDslContext = DSL.using(configuration);

      Optional<SecretSeries> series = secretSeriesDAO.getSecretSeriesById(innerDslContext,
          secretId);
      if (!series.isPresent()) {
        return Optional.empty();
      }

      Optional<SecretContent> content =
          secretContentDAO.getSecretContentBySecretIdAndVersion(innerDslContext, secretId, version);
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
  public ImmutableList<String> getVersionsForSecretName(DSLContext dslContext, String name) {
    checkNotNull(dslContext);
    checkNotNull(name);

    return dslContext.<ImmutableList<String>>transactionResult(configuration -> {
      DSLContext innerDslContext = DSL.using(configuration);

      Optional<SecretSeries> series = secretSeriesDAO.getSecretSeriesByName(innerDslContext, name);
      if (!series.isPresent()) {
        return ImmutableList.of();
      }

      return secretContentDAO.getVersionFromSecretId(innerDslContext, series.get().getId());
    });
  }

  /**
   * @param name of secret series to look up secrets by.
   * @param version specific version of secret. May be empty.
   * @return Secret matching input parameters or Optional.absent().
   */
  public Optional<SecretSeriesAndContent> getSecretByNameAndVersion(DSLContext dslContext,
      String name, String version) {
    checkNotNull(dslContext);
    checkArgument(!name.isEmpty());
    checkNotNull(version);

    return dslContext.<Optional<SecretSeriesAndContent>>transactionResult(configuration -> {
      DSLContext innerDslContext = DSL.using(configuration);

      Optional<SecretSeries> secretSeries = secretSeriesDAO.getSecretSeriesByName(innerDslContext,
          name);
      if (!secretSeries.isPresent()) {
        return Optional.empty();
      }

      Optional<SecretContent> secretContent =
          secretContentDAO.getSecretContentBySecretIdAndVersion(innerDslContext,
              secretSeries.get().getId(), version);
      if (!secretContent.isPresent()) {
        return Optional.empty();
      }

      return Optional.of(SecretSeriesAndContent.of(secretSeries.get(), secretContent.get()));
    });
  }

  /** @return all existing secrets. */
  public ImmutableList<SecretSeriesAndContent> getSecrets(DSLContext dslContext) {
    checkNotNull(dslContext);

    return dslContext.transactionResult(configuration -> {
      DSLContext innerDslContext = DSL.using(configuration);

      ImmutableList.Builder<SecretSeriesAndContent> secretsBuilder = ImmutableList.builder();

      secretSeriesDAO.getSecretSeries(innerDslContext)
          .forEach((series) -> secretContentDAO.getSecretContentsBySecretId(innerDslContext,
              series.getId())
              .forEach(
                  (content) -> secretsBuilder.add(SecretSeriesAndContent.of(series, content))));

      return secretsBuilder.build();
    });
  }

  /**
   * Deletes the series and all associated version of the given secret series name.
   *
   * @param name of secret series to delete.
   */
  public void deleteSecretsByName(DSLContext dslContext, String name) {
    checkNotNull(dslContext);
    checkArgument(!name.isEmpty());

    secretSeriesDAO.deleteSecretSeriesByName(dslContext, name);
  }

  /**
   * Deletes a specific version in a secret series.
   *
   * @param name of secret series to delete from.
   * @param version of secret to specifically delete.
   */
  public void deleteSecretByNameAndVersion(DSLContext dslContext, String name, String version) {
    checkNotNull(dslContext);
    checkArgument(!name.isEmpty());
    checkNotNull(version);

    dslContext.transaction(configuration -> {
      DSLContext innerDslContext = DSL.using(configuration);

      Optional<SecretSeries> secretSeries = secretSeriesDAO.getSecretSeriesByName(innerDslContext,
          name);
      if (!secretSeries.isPresent()) {
        return;
      }

      secretContentDAO.deleteSecretContentBySecretIdAndVersion(innerDslContext,
          secretSeries.get().getId(), version);

      long seriesId = secretSeries.get().getId();
      if (secretContentDAO.getSecretContentsBySecretId(innerDslContext, seriesId).isEmpty()) {
        secretSeriesDAO.deleteSecretSeriesById(innerDslContext, seriesId);
      }
    });
  }
}
