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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import keywhiz.api.model.SecretSeries;
import keywhiz.jooq.tables.records.SecretsRecord;
import keywhiz.service.config.Readonly;
import keywhiz.service.config.ShadowWrite;
import keywhiz.shadow_write.jooq.tables.Secrets;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static keywhiz.jooq.tables.Accessgrants.ACCESSGRANTS;
import static keywhiz.jooq.tables.Secrets.SECRETS;
import static keywhiz.jooq.tables.SecretsContent.SECRETS_CONTENT;

/**
 * Interacts with 'secrets' table and actions on {@link SecretSeries} entities.
 */
public class SecretSeriesDAO {
  private static final Logger logger = LoggerFactory.getLogger(SecretSeriesDAO.class);

  private final DSLContext dslContext;
  private final ObjectMapper mapper;
  private final SecretSeriesMapper secretSeriesMapper;
  private final DSLContext shadowWriteDslContext;

  private SecretSeriesDAO(DSLContext dslContext, ObjectMapper mapper,
      SecretSeriesMapper secretSeriesMapper,
      DSLContext shadowWriteDslContext) {
    this.dslContext = dslContext;
    this.mapper = mapper;
    this.secretSeriesMapper = secretSeriesMapper;
    this.shadowWriteDslContext = shadowWriteDslContext;
  }

  long createSecretSeries(String name, String creator, String description, @Nullable String type,
      @Nullable Map<String, String> generationOptions) {
    SecretsRecord r =  dslContext.newRecord(SECRETS);

    long now = OffsetDateTime.now().toEpochSecond();

    r.setName(name);
    r.setDescription(description);
    r.setCreatedby(creator);
    r.setCreatedat(now);
    r.setUpdatedby(creator);
    r.setUpdatedat(now);
    r.setType(type);
    String options;
    if (generationOptions != null) {
      try {
        options = mapper.writeValueAsString(generationOptions);
      } catch (JsonProcessingException e) {
        // Serialization of a Map<String, String> can never fail.
        throw Throwables.propagate(e);
      }
    } else {
      options = "{}";
    }
    r.setOptions(options);
    r.store();

    long id = r.getId();

    try {
      keywhiz.shadow_write.jooq.tables.records.SecretsRecord shadowR = shadowWriteDslContext.newRecord(
          Secrets.SECRETS);
      shadowR.setId(id);
      shadowR.setName(name);
      shadowR.setDescription(description);
      shadowR.setCreatedby(creator);
      shadowR.setCreatedat(now);
      shadowR.setUpdatedby(creator);
      shadowR.setUpdatedat(now);
      shadowR.setType(type);
      shadowR.setOptions(options);
      shadowR.store();
    } catch (DataAccessException e) {
      logger.error("shadowWrite: failure to create secret secretId {}", e, id);
    }

    return id;
  }

  public Optional<SecretSeries> getSecretSeriesById(long id) {
    SecretsRecord r = dslContext.fetchOne(SECRETS, SECRETS.ID.eq(id));
    return Optional.ofNullable(r).map(secretSeriesMapper::map);
  }

  public Optional<SecretSeries> getSecretSeriesByName(String name) {
    SecretsRecord r = dslContext.fetchOne(SECRETS, SECRETS.NAME.eq(name));
    return Optional.ofNullable(r).map(secretSeriesMapper::map);
  }

  public ImmutableList<SecretSeries> getSecretSeries() {
    List<SecretSeries> r = dslContext
        .selectFrom(SECRETS)
        .fetch()
        .map(secretSeriesMapper);

    return ImmutableList.copyOf(r);
  }

  public void deleteSecretSeriesByName(String name) {
    shadowWriteDslContext.transaction(shadowWriteConfiguration -> {
      Long id = dslContext.transactionResult(configuration -> {
        SecretsRecord r = DSL.using(configuration).fetchOne(SECRETS, SECRETS.NAME.eq(name));
        if (r != null) {
          DSL.using(configuration)
              .delete(SECRETS)
              .where(SECRETS.ID.eq(r.getId()))
              .execute();
          DSL.using(configuration)
              .delete(SECRETS_CONTENT)
              .where(SECRETS_CONTENT.SECRETID.eq(r.getId()))
              .execute();
          DSL.using(configuration)
              .delete(ACCESSGRANTS)
              .where(ACCESSGRANTS.SECRETID.eq(r.getId()))
              .execute();
          return r.getId();
        }
        return null;
      });
      if (id != null) {
        try {
          DSL.using(shadowWriteConfiguration)
              .delete(SECRETS)
              .where(SECRETS.ID.eq(id))
              .execute();
          DSL.using(shadowWriteConfiguration)
              .delete(SECRETS_CONTENT)
              .where(SECRETS_CONTENT.SECRETID.eq(id))
              .execute();
          DSL.using(shadowWriteConfiguration)
              .delete(ACCESSGRANTS)
              .where(ACCESSGRANTS.SECRETID.eq(id))
              .execute();
        } catch (DataAccessException e) {
          logger.error("shadowWrite: failure to delete secret by name secretId {}", e, id);
        }
      }
    });
  }

  public void deleteSecretSeriesById(long id) {
    shadowWriteDslContext.transaction(shadowWriteConfiguration -> {
      dslContext.transaction(configuration -> {
        DSL.using(configuration)
            .delete(SECRETS)
            .where(SECRETS.ID.eq(id))
            .execute();
        DSL.using(configuration)
            .delete(SECRETS_CONTENT)
            .where(SECRETS_CONTENT.SECRETID.eq(id))
            .execute();
        DSL.using(configuration)
            .delete(ACCESSGRANTS)
            .where(ACCESSGRANTS.SECRETID.eq(id))
            .execute();
      });
      try {
        DSL.using(shadowWriteConfiguration)
            .delete(SECRETS)
            .where(SECRETS.ID.eq(id))
            .execute();
        DSL.using(shadowWriteConfiguration)
            .delete(SECRETS_CONTENT)
            .where(SECRETS_CONTENT.SECRETID.eq(id))
            .execute();
        DSL.using(shadowWriteConfiguration)
            .delete(ACCESSGRANTS)
            .where(ACCESSGRANTS.SECRETID.eq(id))
            .execute();
      } catch (DataAccessException e) {
        logger.error("shadowWrite: failure to delete secret by id secretId {}", e, id);
      }
    });
  }

  public static class SecretSeriesDAOFactory implements DAOFactory<SecretSeriesDAO> {
    private final DSLContext jooq;
    private final DSLContext readonlyJooq;
    private final ObjectMapper objectMapper;
    private final SecretSeriesMapper secretSeriesMapper;
    private final DSLContext shadowWriteJooq;

    @Inject public SecretSeriesDAOFactory(DSLContext jooq, @Readonly DSLContext readonlyJooq,
        ObjectMapper objectMapper, SecretSeriesMapper secretSeriesMapper,
        @ShadowWrite DSLContext shadowWriteJooq) {
      this.jooq = jooq;
      this.readonlyJooq = readonlyJooq;
      this.objectMapper = objectMapper;
      this.secretSeriesMapper = secretSeriesMapper;
      this.shadowWriteJooq = shadowWriteJooq;
    }

    @Override public SecretSeriesDAO readwrite() {
      return new SecretSeriesDAO(jooq, objectMapper, secretSeriesMapper, shadowWriteJooq);
    }

    @Override public SecretSeriesDAO readonly() {
      return new SecretSeriesDAO(readonlyJooq, objectMapper, secretSeriesMapper, null);
    }

    @Override public SecretSeriesDAO using(Configuration configuration,
        Configuration shadowWriteConfiguration) {
      DSLContext dslContext = DSL.using(checkNotNull(configuration));
      DSLContext shadowWriteDslContext = null;
      if (shadowWriteConfiguration != null) {
        shadowWriteDslContext = DSL.using(checkNotNull(shadowWriteConfiguration));
      }
      return new SecretSeriesDAO(dslContext, objectMapper, secretSeriesMapper, shadowWriteDslContext);
    }
  }
}
