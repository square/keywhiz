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
import keywhiz.service.config.Readonly;
import keywhiz.service.daos.SecretContentDAO.SecretContentDAOFactory;
import keywhiz.service.daos.SecretSeriesDAO.SecretSeriesDAOFactory;
import org.jooq.Configuration;
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
  private final DSLContext dslContext;
  private final SecretContentDAOFactory secretContentDAOFactory;
  private final SecretSeriesDAOFactory secretSeriesDAOFactory;

  private SecretDAO(DSLContext dslContext, SecretContentDAOFactory secretContentDAOFactory,
      SecretSeriesDAOFactory secretSeriesDAOFactory) {
    this.dslContext = dslContext;
    this.secretContentDAOFactory = secretContentDAOFactory;
    this.secretSeriesDAOFactory = secretSeriesDAOFactory;
  }

  @VisibleForTesting
  public long createSecret(String name, String encryptedSecret, String version,
      String creator, Map<String, String> metadata, String description, @Nullable String type,
      @Nullable Map<String, String> generationOptions) {
    // TODO(jlfwong): Should the description be updated...?

    return dslContext.transactionResult(configuration -> {
      SecretContentDAO secretContentDAO = secretContentDAOFactory.using(configuration);
      SecretSeriesDAO secretSeriesDAO = secretSeriesDAOFactory.using(configuration);

      Optional<SecretSeries> secretSeries = secretSeriesDAO.getSecretSeriesByName(name);
      long secretId;
      if (secretSeries.isPresent()) {
        secretId = secretSeries.get().getId();
      } else {
        secretId = secretSeriesDAO.createSecretSeries(name, creator, description, type,
            generationOptions);
      }

      secretContentDAO.createSecretContent(secretId, encryptedSecret, version, creator,
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
      SecretContentDAO secretContentDAO = secretContentDAOFactory.using(configuration);
      SecretSeriesDAO secretSeriesDAO = secretSeriesDAOFactory.using(configuration);

      ImmutableList.Builder<SecretSeriesAndContent> secretsBuilder = ImmutableList.builder();

      Optional<SecretSeries> series = secretSeriesDAO.getSecretSeriesById(secretId);
      if (series.isPresent()) {
        ImmutableList<SecretContent> contents =
            secretContentDAO.getSecretContentsBySecretId(secretId);
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

    return dslContext.<Optional<SecretSeriesAndContent>>transactionResult(configuration -> {
      SecretContentDAO secretContentDAO = secretContentDAOFactory.using(configuration);
      SecretSeriesDAO secretSeriesDAO = secretSeriesDAOFactory.using(configuration);

      Optional<SecretSeries> series = secretSeriesDAO.getSecretSeriesById(secretId);
      if (!series.isPresent()) {
        return Optional.empty();
      }

      Optional<SecretContent> content =
          secretContentDAO.getSecretContentBySecretIdAndVersion(secretId, version);
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

    return dslContext.<ImmutableList<String>>transactionResult(configuration -> {
      SecretContentDAO secretContentDAO = secretContentDAOFactory.using(configuration);
      SecretSeriesDAO secretSeriesDAO = secretSeriesDAOFactory.using(configuration);

      Optional<SecretSeries> series = secretSeriesDAO.getSecretSeriesByName(name);
      if (!series.isPresent()) {
        return ImmutableList.of();
      }

      return secretContentDAO.getVersionFromSecretId(series.get().getId());
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

    Optional<SecretSeries> secretSeries = secretSeriesDAO.getSecretSeriesByName(name);
    if (!secretSeries.isPresent()) {
      return Optional.empty();
    }

    Optional<SecretContent> secretContent =
        secretContentDAO.getSecretContentBySecretIdAndVersion(secretSeries.get().getId(), version);
    if (!secretContent.isPresent()) {
      return Optional.empty();
    }

    return Optional.of(SecretSeriesAndContent.of(secretSeries.get(), secretContent.get()));
  }

  /** @return all existing secrets. */
  public ImmutableList<SecretSeriesAndContent> getSecrets() {
    return dslContext.transactionResult(configuration -> {
      SecretContentDAO secretContentDAO = secretContentDAOFactory.using(configuration);
      SecretSeriesDAO secretSeriesDAO = secretSeriesDAOFactory.using(configuration);

      ImmutableList.Builder<SecretSeriesAndContent> secretsBuilder = ImmutableList.builder();

      secretSeriesDAO.getSecretSeries()
          .forEach((series) -> secretContentDAO.getSecretContentsBySecretId(series.getId())
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
  public void deleteSecretsByName(String name) {
    checkArgument(!name.isEmpty());

    dslContext.transaction(configuration -> {
      SecretSeriesDAO secretSeriesDAO = secretSeriesDAOFactory.using(configuration);
      secretSeriesDAO.deleteSecretSeriesByName(name);
    });
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
      SecretContentDAO secretContentDAO = secretContentDAOFactory.using(configuration);
      SecretSeriesDAO secretSeriesDAO = secretSeriesDAOFactory.using(configuration);

      Optional<SecretSeries> secretSeries = secretSeriesDAO.getSecretSeriesByName(name);
      if (!secretSeries.isPresent()) {
        return;
      }

      secretContentDAO.deleteSecretContentBySecretIdAndVersion(secretSeries.get().getId(),
          version);

      long seriesId = secretSeries.get().getId();
      if (secretContentDAO.getSecretContentsBySecretId(seriesId).isEmpty()) {
        secretSeriesDAO.deleteSecretSeriesById(seriesId);
      }
    });
  }

  public static class SecretDAOFactory implements DAOFactory<SecretDAO> {
    private final DSLContext jooq;
    private final DSLContext readonlyJooq;
    private final SecretContentDAOFactory secretContentDAOFactory;
    private final SecretSeriesDAOFactory secretSeriesDAOFactory;

    @Inject public SecretDAOFactory(DSLContext jooq, @Readonly DSLContext readonlyJooq,
        SecretContentDAOFactory secretContentDAOFactory,
        SecretSeriesDAOFactory secretSeriesDAOFactory) {
      this.jooq = jooq;
      this.readonlyJooq = readonlyJooq;
      this.secretContentDAOFactory = secretContentDAOFactory;
      this.secretSeriesDAOFactory = secretSeriesDAOFactory;
    }

    @Override public SecretDAO readwrite() {
      return new SecretDAO(jooq, secretContentDAOFactory, secretSeriesDAOFactory);
    }

    @Override public SecretDAO readonly() {
      return new SecretDAO(readonlyJooq, secretContentDAOFactory, secretSeriesDAOFactory);
    }

    @Override public SecretDAO using(Configuration configuration) {
      DSLContext dslContext = DSL.using(checkNotNull(configuration));
      return new SecretDAO(dslContext, secretContentDAOFactory, secretSeriesDAOFactory);
    }
  }
}
