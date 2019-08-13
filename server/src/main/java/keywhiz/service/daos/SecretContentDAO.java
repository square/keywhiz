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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import keywhiz.KeywhizConfig;
import keywhiz.KeywhizConfig.RowHmacCheck;
import keywhiz.api.model.SecretContent;
import keywhiz.jooq.tables.records.SecretsContentRecord;
import keywhiz.jooq.tables.records.SecretsRecord;
import keywhiz.service.config.Readonly;
import keywhiz.service.crypto.RowHmacGenerator;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static keywhiz.jooq.tables.Secrets.SECRETS;
import static keywhiz.jooq.tables.SecretsContent.SECRETS_CONTENT;

/**
 * Interacts with 'secrets_content' table and actions on {@link SecretContent} entities.
 */
public class SecretContentDAO {
  private static final Logger logger = LoggerFactory.getLogger(AclDAO.class);
  // Number of old contents we keep around before we prune
  @VisibleForTesting static final int PRUNE_CUTOFF_ITEMS = 10;

  // Cut-off time after which we prune old, unreferenced contents
  @VisibleForTesting static final int PRUNE_CUTOFF_DAYS = 15;

  private final DSLContext dslContext;
  private final ObjectMapper mapper;
  private final SecretContentMapper secretContentMapper;
  private final RowHmacGenerator rowHmacGenerator;
  private final KeywhizConfig config;

  private SecretContentDAO(DSLContext dslContext, ObjectMapper mapper,
      SecretContentMapper secretContentMapper, RowHmacGenerator rowHmacGenerator,
      KeywhizConfig config) {
    this.dslContext = dslContext;
    this.mapper = mapper;
    this.secretContentMapper = secretContentMapper;
    this.rowHmacGenerator = rowHmacGenerator;
    this.config = config;
  }

  public long createSecretContent(long secretId, String encryptedContent, String hmac,
      String creator, Map<String, String> metadata, long expiry, long now) {
    SecretsContentRecord r = dslContext.newRecord(SECRETS_CONTENT);

    String jsonMetadata;
    try {
      jsonMetadata = mapper.writeValueAsString(metadata);
    } catch (JsonProcessingException e) {
      // Serialization of a Map<String, String> can never fail.
      throw Throwables.propagate(e);
    }

    long generatedId = rowHmacGenerator.getNextLongSecure();
    String rowHmac = rowHmacGenerator.computeRowHmac(
        SECRETS_CONTENT.getName(), List.of(encryptedContent, jsonMetadata, generatedId));

    r.setId(generatedId);
    r.setSecretid(secretId);
    r.setEncryptedContent(encryptedContent);
    r.setContentHmac(hmac);
    r.setCreatedby(creator);
    r.setCreatedat(now);
    r.setUpdatedby(creator);
    r.setUpdatedat(now);
    r.setMetadata(jsonMetadata);
    r.setExpiry(expiry);
    r.setRowHmac(rowHmac);
    r.store();

    pruneOldContents(secretId);

    return r.getId();
  }

  /**
   * Prune old secret contents from the database, for the given secret id. Whenever a new secret
   * content entry is added for a secret series, we check the database for really old content
   * entries and clean them out to prevent the database from growing too large.
   */
  @VisibleForTesting void pruneOldContents(long secretId) {
    // Fetch current version number
    SecretsRecord secret =
        dslContext.select(SECRETS.CURRENT)
            .from(SECRETS)
            .where(SECRETS.ID.eq(secretId))
            .fetchOneInto(SecretsRecord.class);

    if (secret == null || secret.getCurrent() == null) {
      // No current secret assigned (secret just being created), let's not prune right now.
      return;
    }

    // Select everything older than cutoff for possible pruning
    long cutoff = OffsetDateTime.now().minusDays(PRUNE_CUTOFF_DAYS).toEpochSecond();

    List<Long> records =
        dslContext.select(SECRETS_CONTENT.ID)
            .from(SECRETS_CONTENT)
            .where(SECRETS_CONTENT.SECRETID.eq(secretId))
            .and(SECRETS_CONTENT.CREATEDAT.lt(cutoff))
            .and(SECRETS_CONTENT.ID.ne(secret.getCurrent()))
            .orderBy(SECRETS_CONTENT.CREATEDAT.desc())
            .fetch(SECRETS_CONTENT.ID);

    // Always keep last X items, prune otherwise
    if (records.size() > PRUNE_CUTOFF_ITEMS) {
      for (long id : records.subList(PRUNE_CUTOFF_ITEMS, records.size())) {
        dslContext.deleteFrom(SECRETS_CONTENT).where(SECRETS_CONTENT.ID.eq(id)).execute();
      }
    }
  }

  public Optional<SecretContent> getSecretContentById(long id) {
    SecretsContentRecord r = dslContext.fetchOne(SECRETS_CONTENT, SECRETS_CONTENT.ID.eq(id));
    Optional<SecretContent> result = Optional.ofNullable(r).map(secretContentMapper::map);

    if (result.isEmpty()) {
      return result;
    }

    String rowHmac = rowHmacGenerator.computeRowHmac(
        SECRETS_CONTENT.getName(), List.of(r.getEncryptedContent(), r.getMetadata(), r.getId()));

    if (!rowHmac.equals(r.getRowHmac())) {
      String errorMessage = String.format(
          "Secret Content HMAC verification failed for secretContent: %d", r.getId());
      if (config.getRowHmacCheck() == RowHmacCheck.DISABLED_BUT_LOG) {
        logger.warn(errorMessage);
      }
      if (config.getRowHmacCheck() == RowHmacCheck.ENFORCED) {
        throw new AssertionError(errorMessage);
      }
    }

    return result;
  }

  public Optional<ImmutableList<SecretContent>> getSecretVersionsBySecretId(long id,
      int versionIdx, int numVersions) {
    Result<SecretsContentRecord> r = dslContext.selectFrom(SECRETS_CONTENT)
        .where(SECRETS_CONTENT.SECRETID.eq(id))
        .orderBy(SECRETS_CONTENT.CREATEDAT.desc())
        .limit(versionIdx, numVersions)
        .fetch();

    if (r != null && r.isNotEmpty()) {
      ImmutableList.Builder<SecretContent> b = new ImmutableList.Builder<>();
      b.addAll(r.map(secretContentMapper));
      return Optional.of(b.build());
    } else {
      return Optional.empty();
    }
  }

  /**
   * PERMANENTLY REMOVE database records from `secrets_contents` which are associated with the given
   * list of SECRETS IDs.  (Does not affect the `secrets` table.)
   *
   * @param ids IDs in the `secrets` table; `secrets_contents` records linked to these by
   * `secrets_content.secretid` will be PERMANENTLY REMOVED
   * @returns the number of records which were removed
   */
  public long dangerPermanentlyRemoveRecordsForGivenSecretsIDs(List<Long> ids) {
    return dslContext.deleteFrom(SECRETS_CONTENT)
        .where(SECRETS_CONTENT.SECRETID.in(ids))
        .execute();
  }

  public static class SecretContentDAOFactory implements DAOFactory<SecretContentDAO> {
    private final DSLContext jooq;
    private final DSLContext readonlyJooq;
    private final ObjectMapper objectMapper;
    private final SecretContentMapper secretContentMapper;
    private final RowHmacGenerator rowHmacGenerator;
    private final KeywhizConfig config;

    @Inject public SecretContentDAOFactory(DSLContext jooq, @Readonly DSLContext readonlyJooq,
        ObjectMapper objectMapper, SecretContentMapper secretContentMapper,
        RowHmacGenerator rowHmacGenerator, KeywhizConfig config) {
      this.jooq = jooq;
      this.readonlyJooq = readonlyJooq;
      this.objectMapper = objectMapper;
      this.secretContentMapper = secretContentMapper;
      this.rowHmacGenerator = rowHmacGenerator;
      this.config = config;
    }

    @Override public SecretContentDAO readwrite() {
      return new SecretContentDAO(jooq, objectMapper, secretContentMapper, rowHmacGenerator,
          config);
    }

    @Override public SecretContentDAO readonly() {
      return new SecretContentDAO(readonlyJooq, objectMapper, secretContentMapper, rowHmacGenerator,
          config);
    }

    @Override public SecretContentDAO using(Configuration configuration) {
      DSLContext dslContext = DSL.using(checkNotNull(configuration));
      return new SecretContentDAO(dslContext, objectMapper, secretContentMapper, rowHmacGenerator,
          config);
    }
  }
}
