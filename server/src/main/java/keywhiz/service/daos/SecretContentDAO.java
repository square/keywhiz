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
import javax.inject.Inject;
import keywhiz.api.model.SecretContent;
import keywhiz.jooq.tables.records.SecretsContentRecord;
import keywhiz.service.config.Readonly;
import keywhiz.service.config.ShadowWrite;
import keywhiz.shadow_write.jooq.tables.SecretsContent;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static keywhiz.jooq.tables.SecretsContent.SECRETS_CONTENT;

/**
 * Interacts with 'secrets_content' table and actions on {@link SecretContent} entities.
 */
public class SecretContentDAO {
  private static final Logger logger = LoggerFactory.getLogger(SecretContentDAO.class);

  private final DSLContext dslContext;
  private final ObjectMapper mapper;
  private final SecretContentMapper secretContentMapper;
  private final DSLContext shadowWriteDslContext;

  private SecretContentDAO(DSLContext dslContext, ObjectMapper mapper,
      SecretContentMapper secretContentMapper,
      DSLContext shadowWriteDslContext) {
    this.dslContext = dslContext;
    this.mapper = mapper;
    this.secretContentMapper = secretContentMapper;
    this.shadowWriteDslContext = shadowWriteDslContext;
  }

  public long createSecretContent(long secretId, String encryptedContent, String version,
      String creator, Map<String, String> metadata) {
    SecretsContentRecord r = dslContext.newRecord(SECRETS_CONTENT);

    String jsonMetadata;
    try {
      jsonMetadata = mapper.writeValueAsString(metadata);
    } catch (JsonProcessingException e) {
      // Serialization of a Map<String, String> can never fail.
      throw Throwables.propagate(e);
    }

    long now = OffsetDateTime.now().toEpochSecond();

    r.setSecretid(secretId);
    r.setEncryptedContent(encryptedContent);
    r.setVersion(version);
    r.setCreatedby(creator);
    r.setCreatedat(now);
    r.setUpdatedby(creator);
    r.setUpdatedat(now);
    r.setMetadata(jsonMetadata);
    r.store();

    long id = r.getId();

    try {
      keywhiz.shadow_write.jooq.tables.records.SecretsContentRecord shadowR =
          shadowWriteDslContext.newRecord(SecretsContent.SECRETS_CONTENT);

      shadowR.setId(id);
      shadowR.setSecretid(secretId);
      shadowR.setEncryptedContent(encryptedContent);
      shadowR.setVersion(version);
      shadowR.setCreatedby(creator);
      shadowR.setCreatedat(now);
      shadowR.setUpdatedby(creator);
      shadowR.setUpdatedat(now);
      shadowR.setMetadata(jsonMetadata);
      shadowR.store();
    } catch (DataAccessException e) {
      logger.error("shadowWrite: failure to create secret content secretContentId {}", e, id);
    }

    return id;
  }

  public Optional<SecretContent> getSecretContentById(long id) {
    SecretsContentRecord r = dslContext.fetchOne(SECRETS_CONTENT,
        SECRETS_CONTENT.ID.eq(id));
    return Optional.ofNullable(r).map(secretContentMapper::map);
  }

  public Optional<SecretContent> getSecretContentBySecretIdAndVersion(long secretId,
      String version) {
    SecretsContentRecord r = dslContext.fetchOne(SECRETS_CONTENT,
        SECRETS_CONTENT.SECRETID.eq(secretId)
            .and(SECRETS_CONTENT.VERSION.eq(version)));
    return Optional.ofNullable(r).map(secretContentMapper::map);
  }

  public ImmutableList<SecretContent> getSecretContentsBySecretId(long secretId) {
    List<SecretContent> r = dslContext
        .selectFrom(SECRETS_CONTENT)
        .where(SECRETS_CONTENT.SECRETID.eq(secretId))
        .fetch()
        .map(secretContentMapper);

    return ImmutableList.copyOf(r);
  }

  public void deleteSecretContentBySecretIdAndVersion(long secretId, String version) {
    dslContext
        .delete(SECRETS_CONTENT)
        .where(SECRETS_CONTENT.SECRETID.eq(secretId)
            .and(SECRETS_CONTENT.VERSION.eq(version)))
        .execute();

    try {
      shadowWriteDslContext
          .delete(SECRETS_CONTENT)
          .where(SECRETS_CONTENT.SECRETID.eq(secretId)
              .and(SECRETS_CONTENT.VERSION.eq(version)))
          .execute();
    } catch (DataAccessException e) {
      logger.error("shadowWrite: failure to delete secret content secretContentId {}", e, secretId);
    }
  }

  public ImmutableList<String> getVersionFromSecretId(long secretId) {
    List<String> r = dslContext
        .select(SECRETS_CONTENT.VERSION)
        .from(SECRETS_CONTENT)
        .where(SECRETS_CONTENT.SECRETID.eq(secretId))
        .fetch(SECRETS_CONTENT.VERSION);

    return ImmutableList.copyOf(r);
  }

  public static class SecretContentDAOFactory implements DAOFactory<SecretContentDAO> {
    private final DSLContext jooq;
    private final DSLContext readonlyJooq;
    private final ObjectMapper objectMapper;
    private final SecretContentMapper secretContentMapper;
    private final DSLContext shadowWriteJooq;

    @Inject public SecretContentDAOFactory(DSLContext jooq, @Readonly DSLContext readonlyJooq,
        ObjectMapper objectMapper, SecretContentMapper secretContentMapper,
        @ShadowWrite DSLContext shadowWriteJooq) {
      this.jooq = jooq;
      this.readonlyJooq = readonlyJooq;
      this.objectMapper = objectMapper;
      this.secretContentMapper = secretContentMapper;
      this.shadowWriteJooq = shadowWriteJooq;
    }

    @Override public SecretContentDAO readwrite() {
      return new SecretContentDAO(jooq, objectMapper, secretContentMapper, shadowWriteJooq);
    }

    @Override public SecretContentDAO readonly() {
      return new SecretContentDAO(readonlyJooq, objectMapper, secretContentMapper, null);
    }

    @Override public SecretContentDAO using(Configuration configuration,
        Configuration shadowWriteConfiguration) {
      DSLContext dslContext = DSL.using(checkNotNull(configuration));
      DSLContext shadowWriteDslContext = null;
      if (shadowWriteConfiguration != null) {
        shadowWriteDslContext = DSL.using(checkNotNull(shadowWriteConfiguration));
      }
      return new SecretContentDAO(dslContext, objectMapper, secretContentMapper, shadowWriteDslContext);
    }
  }
}
