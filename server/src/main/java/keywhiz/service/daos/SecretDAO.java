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
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import keywhiz.api.model.*;
import keywhiz.jooq.tables.Secrets;
import keywhiz.service.config.Readonly;
import keywhiz.service.daos.SecretContentDAO.SecretContentDAOFactory;
import keywhiz.service.daos.SecretSeriesDAO.SecretSeriesDAOFactory;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
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

  private SecretDAO(DSLContext dslContext, SecretContentDAOFactory secretContentDAOFactory,
      SecretSeriesDAOFactory secretSeriesDAOFactory) {
    this.dslContext = dslContext;
    this.secretContentDAOFactory = secretContentDAOFactory;
    this.secretSeriesDAOFactory = secretSeriesDAOFactory;
  }

  @VisibleForTesting
  public long createSecret(String name, String encryptedSecret,
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

      long secretContentId = secretContentDAO.createSecretContent(secretId, encryptedSecret, creator, metadata, expiry);
      secretSeriesDAO.setCurrentVersion(secretId, secretContentId);

      return secretId;
    });
  }

  @VisibleForTesting
  public long createOrUpdateSecret(String name, String encryptedSecret, String creator, Map<String, String> metadata,
                                   long expiry, String description, @Nullable String type,
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

      long secretContentId = secretContentDAO.createSecretContent(secretId, encryptedSecret, creator, metadata, expiry);
      secretSeriesDAO.setCurrentVersion(secretId, secretContentId);

      return secretId;
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
