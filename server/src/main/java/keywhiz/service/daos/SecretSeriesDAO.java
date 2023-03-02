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
import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import keywhiz.api.model.Group;
import keywhiz.api.model.SecretSeries;
import keywhiz.jooq.tables.records.DeletedSecretsRecord;
import keywhiz.jooq.tables.records.SecretsContentRecord;
import keywhiz.jooq.tables.records.SecretsRecord;
import keywhiz.service.config.Readonly;
import keywhiz.service.crypto.RowHmacGenerator;
import org.joda.time.DateTime;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectQuery;
import org.jooq.impl.DSL;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkNotNull;
import static keywhiz.jooq.tables.Accessgrants.ACCESSGRANTS;
import static keywhiz.jooq.tables.DeletedAccessgrants.DELETED_ACCESSGRANTS;
import static keywhiz.jooq.tables.Groups.GROUPS;
import static keywhiz.jooq.tables.Secrets.SECRETS;
import static keywhiz.jooq.tables.DeletedSecrets.DELETED_SECRETS;
import static keywhiz.jooq.tables.SecretsContent.SECRETS_CONTENT;
import static org.jooq.impl.DSL.currentTimestamp;
import static org.jooq.impl.DSL.select;

/**
 * Interacts with 'secrets' table and actions on {@link SecretSeries} entities.
 */
public class SecretSeriesDAO {
  private final DSLContext dslContext;
  private final ObjectMapper mapper;
  private final SecretSeriesMapper secretSeriesMapper;
  private final RowHmacGenerator rowHmacGenerator;

  private SecretSeriesDAO(
      DSLContext dslContext,
      ObjectMapper mapper,
      SecretSeriesMapper secretSeriesMapper,
      RowHmacGenerator rowHmacGenerator) {
    this.dslContext = dslContext;
    this.mapper = mapper;
    this.secretSeriesMapper = secretSeriesMapper;
    this.rowHmacGenerator = rowHmacGenerator;
  }

  public boolean secretSeriesExists(String name) {
    return dslContext.fetchExists(SECRETS, SECRETS.NAME.eq(name));
  }

  long createSecretSeries(
      String name,
      Long ownerId,
      String creator,
      String description,
      @Nullable String type,
      @Nullable Map<String, String> generationOptions,
      long now) {
    long generatedId = rowHmacGenerator.getNextLongSecure();
    return createSecretSeries(
        generatedId,
        name,
        ownerId,
        creator,
        description,
        type,
        generationOptions,
        now);
  }

  @VisibleForTesting
  long createSecretSeries(
      long id,
      String name,
      Long ownerId,
      String creator,
      String description,
      @Nullable String type,
      @Nullable Map<String, String> generationOptions,
      long now) {
    SecretsRecord r = dslContext.newRecord(SECRETS);

    String rowHmac = computeRowHmac(id, name);

    r.setId(id);
    r.setName(name);
    r.setOwner(ownerId);
    r.setDescription(description);
    r.setCreatedby(creator);
    r.setCreatedat(now);
    r.setUpdatedby(creator);
    r.setUpdatedat(now);
    r.setType(type);
    r.setRowHmac(rowHmac);
    r.setOptions(getOptionsField(generationOptions));

    r.store();

    return r.getId();
  }

  private String getOptionsField(
      @Nullable Map<String, String> generationOptions
  ) {
    if (generationOptions == null) {
      return "{}";
    }
    try {
      return mapper.writeValueAsString(generationOptions);
    } catch (JsonProcessingException e) {
      // Serialization of a Map<String, String> should never fail.
      throw Throwables.propagate(e);
    }
  }

  void updateSecretSeries(
      long secretId,
      String name,
      Long ownerId,
      String creator,
      String description,
      @Nullable String type,
      @Nullable Map<String, String> generationOptions,
      long now) {

    if (generationOptions == null) {
      generationOptions = ImmutableMap.of();
    }

    try {
      String rowHmac = computeRowHmac(secretId, name);

      dslContext.update(SECRETS)
          .set(SECRETS.NAME, name)
          .set(SECRETS.OWNER, ownerId)
          .set(SECRETS.DESCRIPTION, description)
          .set(SECRETS.UPDATEDBY, creator)
          .set(SECRETS.UPDATEDAT, now)
          .set(SECRETS.TYPE, type)
          .set(SECRETS.OPTIONS, mapper.writeValueAsString(generationOptions))
          .set(SECRETS.ROW_HMAC, rowHmac)
          .where(SECRETS.ID.eq(secretId))
          .execute();
    } catch (JsonProcessingException e) {
      // Serialization of a Map<String, String> can never fail.
      throw Throwables.propagate(e);
    }
  }

  public int setExpiration(long secretContentId, Instant expiration) {
    return dslContext.transactionResult(configuration -> {
      SecretsContentRecord content = dslContext.select(SECRETS_CONTENT.EXPIRY)
          .from(SECRETS_CONTENT)
          .where(SECRETS_CONTENT.ID.eq(secretContentId))
          .forUpdate()
          .fetchOneInto(SecretsContentRecord.class);

      if (content == null) {
        return 0;
      }

      Long currentExpiry = content.getExpiry();
      long epochSeconds = expiration.getEpochSecond();

      Long updatedExpiry = (currentExpiry == null || currentExpiry == 0)
          ? epochSeconds
          : Math.min(currentExpiry, epochSeconds);

      int contentsUpdated = dslContext.update(SECRETS_CONTENT)
          .set(SECRETS_CONTENT.EXPIRY, updatedExpiry)
          .where(SECRETS_CONTENT.ID.eq(secretContentId))
          .execute();

      int secretsUpdated = dslContext.update(SECRETS)
          .set(SECRETS.EXPIRY, updatedExpiry)
          .where(SECRETS.CURRENT.eq(secretContentId))
          .execute();

      return contentsUpdated + secretsUpdated;
    });
  }

  public int setRowHmacByName(String secretName, String hmac) {
    return dslContext.update(SECRETS)
        .set(SECRETS.ROW_HMAC, hmac)
        .where(SECRETS.NAME.eq(secretName))
        .execute();
  }

  public int setHmac(long secretContentId, String hmac) {
    return dslContext.update(SECRETS_CONTENT)
        .set(SECRETS_CONTENT.CONTENT_HMAC, hmac)
        .where(SECRETS_CONTENT.ID.eq(secretContentId))
        .execute();
  }

  public int setCurrentVersion(long secretId, long secretContentId, String updater, long now) {
    SecretsContentRecord r = dslContext
        .select(
            SECRETS_CONTENT.SECRETID,
            SECRETS_CONTENT.EXPIRY)
        .from(SECRETS_CONTENT)
        .where(SECRETS_CONTENT.ID.eq(secretContentId))
        .fetchOneInto(SECRETS_CONTENT);

    if (r == null) {
      throw new BadRequestException(
          String.format("The requested version %d is not a known version of this secret",
              secretContentId));
    }

    long checkId = r.getSecretid();
    if (checkId != secretId) {
      throw new IllegalStateException(String.format(
          "tried to reset secret with id %d to version %d, but this version is not associated with this secret",
          secretId, secretContentId));
    }

    return dslContext.update(SECRETS)
        .set(SECRETS.CURRENT, secretContentId)
        .set(SECRETS.EXPIRY, r.getExpiry())
        .set(SECRETS.UPDATEDBY, updater)
        .set(SECRETS.UPDATEDAT, now)
        .where(SECRETS.ID.eq(secretId))
        .execute();
  }

  public Optional<SecretSeries> getSecretSeriesById(long id) {
    SecretsRecord r = getSecretSeriesRecordById(id);
    return Optional.ofNullable(r).map(secretSeriesMapper::map);
  }

  @VisibleForTesting
  SecretsRecord getSecretSeriesRecordById(long id) {
    return dslContext.fetchOne(SECRETS, SECRETS.ID.eq(id).and(SECRETS.CURRENT.isNotNull()));
  }

  public Optional<SecretSeries> getDeletedSecretSeriesById(long id) {
    Optional<SecretSeries> fromDeletedTable = getDeletedSecretSeriesFromDeletedSecretsTable(id);
    if (fromDeletedTable.isPresent()) {
      return fromDeletedTable;
    }
    return getDeletedSecretSeriesFromMainSecretsTable(id);
  }

  private Optional<SecretSeries> getDeletedSecretSeriesFromMainSecretsTable(long id) {
    SecretsRecord r =
        dslContext.fetchOne(SECRETS, SECRETS.ID.eq(id).and(SECRETS.CURRENT.isNull()));
    return Optional.ofNullable(r).map(secretSeriesMapper::map);
  }

  private Optional<SecretSeries> getDeletedSecretSeriesFromDeletedSecretsTable(long id) {
    DeletedSecretsRecord r =
        dslContext.fetchOne(DELETED_SECRETS, DELETED_SECRETS.ID.eq(id));
    return Optional.ofNullable(r).map(secretSeriesMapper::map);
  }

  public Optional<SecretSeries> getSecretSeriesByName(String name) {
    SecretsRecord r =
        dslContext.fetchOne(SECRETS, SECRETS.NAME.eq(name).and(SECRETS.CURRENT.isNotNull()));
    return Optional.ofNullable(r).map(secretSeriesMapper::map);
  }

  public List<SecretSeries> getSecretSeriesByDeletedName(String name) {
    List<SecretSeries> fromDeletedSecretsTable =
        getDeletedSecretSeriesFromDeletedSecretsTable(name);
    Set<Long> idsFromDeletedSecretsTable =
        fromDeletedSecretsTable.stream().map(SecretSeries::id).collect(Collectors.toSet());
    return Stream.concat(fromDeletedSecretsTable.stream(),
            getDeletedSecretSeriesFromMainSecretsTable(name).stream()
                // If a secret series exists in both tables, only include the copy from `deleted_secrets`
                // rather than the copy from `secrets` since it contains more information.
                .filter(secretSeries -> !idsFromDeletedSecretsTable.contains(secretSeries.id())))
        .collect(Collectors.toList());
  }

  private List<SecretSeries> getDeletedSecretSeriesFromMainSecretsTable(String name) {
    String lookup = "." + name + ".%";
    return dslContext.fetch(SECRETS, SECRETS.NAME.like(lookup).and(SECRETS.CURRENT.isNull())).map(secretSeriesMapper::map);
  }

  private List<SecretSeries> getDeletedSecretSeriesFromDeletedSecretsTable(String name) {
    return dslContext.fetch(
        DELETED_SECRETS,
        DELETED_SECRETS.NAME.eq(name)
    ).map(secretSeriesMapper::map);
  }

  public List<SecretSeries> getMultipleSecretSeriesByName(List<String> names) {
    return dslContext.fetch(SECRETS, SECRETS.NAME.in(names).and(SECRETS.CURRENT.isNotNull())).map(secretSeriesMapper::map);
  }

  public List<String> listExpiringSecretNames(Instant notAfterInclusive) {
    List<String> expiringSecretNames = dslContext
        .select()
        .from(SECRETS)
        .where(SECRETS.CURRENT.isNotNull())
        .and(SECRETS.EXPIRY.greaterOrEqual(Instant.now().getEpochSecond()))
        .and(SECRETS.EXPIRY.lessThan(notAfterInclusive.getEpochSecond()))
        .fetch(SECRETS.NAME);
    return ImmutableList.copyOf(expiringSecretNames);
  }

  public ImmutableList<SecretSeries> getSecretSeries(@Nullable Long expireMaxTime,
      @Nullable Group group, @Nullable Long expireMinTime, @Nullable String minName,
      @Nullable Integer limit) {

    SelectQuery<Record> select = dslContext
          .select(SECRETS.fields())
          .from(SECRETS)
          .where(SECRETS.CURRENT.isNotNull())
          .getQuery();
    select.addOrderBy(SECRETS.EXPIRY.asc(), SECRETS.NAME.asc());

    // Set an upper bound on expiration dates
    if (expireMaxTime != null && expireMaxTime > 0) {
      // Set a lower bound of "now" on the expiration only if it isn't configured separately
      if (expireMinTime == null || expireMinTime == 0) {
        long now = System.currentTimeMillis() / 1000L;
        select.addConditions(SECRETS.EXPIRY.greaterOrEqual(now));
      }
      select.addConditions(SECRETS.EXPIRY.lessThan(expireMaxTime));
    }

    if (expireMinTime != null && expireMinTime > 0) {
      // set a lower bound on expiration dates, using the secret name as a tiebreaker
      select.addConditions(SECRETS.EXPIRY.greaterThan(expireMinTime)
          .or(SECRETS.EXPIRY.eq(expireMinTime)
              .and(SECRETS.NAME.greaterOrEqual(minName))));
    }

    if (group != null) {
      select.addJoin(ACCESSGRANTS, SECRETS.ID.eq(ACCESSGRANTS.SECRETID));
      select.addJoin(GROUPS, GROUPS.ID.eq(ACCESSGRANTS.GROUPID));
      select.addConditions(GROUPS.NAME.eq(group.getName()));
    }

    if (limit != null && limit >= 0) {
      select.addLimit(limit);
    }

    List<SecretSeries> r = select.fetchInto(SECRETS).map(secretSeriesMapper);
    return ImmutableList.copyOf(r);
  }

  public ImmutableList<SecretSeries> getSecretSeriesBatched(int idx, int num, boolean newestFirst) {
    SelectQuery<Record> select = dslContext
        .select()
        .from(SECRETS)
        .join(SECRETS_CONTENT)
        .on(SECRETS.CURRENT.equal(SECRETS_CONTENT.ID))
        .where(SECRETS.CURRENT.isNotNull())
        .getQuery();
    if (newestFirst) {
      select.addOrderBy(SECRETS.CREATEDAT.desc());
    } else {
      select.addOrderBy(SECRETS.CREATEDAT.asc());
    }
    select.addLimit(idx, num);

    List<SecretSeries> r = select.fetchInto(SECRETS).map(secretSeriesMapper);
    return ImmutableList.copyOf(r);
  }

  public void hardDeleteSecretSeriesByName(String name) {
    dslContext.transaction(configuration -> {
      DSLContext dslContext = DSL.using(configuration);

      SecretsRecord record = dslContext.select()
          .from(SECRETS)
          .where(SECRETS.NAME.eq(name))
          .forUpdate()
          .fetchOneInto(SECRETS);

      hardDeleteSecretSeries(dslContext, record);
    });
  }

  public void hardDeleteSecretSeriesById(Long id) {
    dslContext.transaction(configuration -> {
      DSLContext dslContext = DSL.using(configuration);

      SecretsRecord record = dslContext.select()
          .from(SECRETS)
          .where(SECRETS.ID.eq(id))
          .forUpdate()
          .fetchOneInto(SECRETS);

      hardDeleteSecretSeries(dslContext, record);
    });
  }

  private static void hardDeleteSecretSeries(DSLContext dslContext, SecretsRecord record) {
    if (record == null) {
      return;
    }

    dslContext.deleteFrom(SECRETS_CONTENT)
        .where(SECRETS_CONTENT.SECRETID.eq(record.getId()))
        .execute();
    dslContext.deleteFrom(SECRETS)
        .where(SECRETS.ID.eq(record.getId()))
        .execute();
    dslContext.deleteFrom(ACCESSGRANTS)
        .where(ACCESSGRANTS.SECRETID.eq(record.getId()))
        .execute();
  }

  public void softDeleteSecretSeriesByName(String name) {
    dslContext.transaction(configuration -> {
      // find the record and lock it until this transaction is complete
      SecretsRecord record = DSL.using(configuration)
          .select()
          .from(SECRETS)
          .where(SECRETS.NAME.eq(name).and(SECRETS.CURRENT.isNotNull()))
          .forUpdate()
          .fetchOneInto(SECRETS);

      softDeleteSecretSeries(DSL.using(configuration), record);
    });
  }

  public void softDeleteSecretSeriesById(long id) {
    dslContext.transaction(configuration -> {
      // find the record and lock it until this transaction is complete
      SecretsRecord record = DSL.using(configuration)
          .select()
          .from(SECRETS)
          .where(SECRETS.ID.eq(id).and(SECRETS.CURRENT.isNotNull()))
          .forUpdate()
          .fetchOneInto(SECRETS);

      softDeleteSecretSeries(DSL.using(configuration), record);
    });
  }

  private static void softDeleteSecretSeries(DSLContext dslContext, SecretsRecord record) {
    if (record == null) {
      return;
    }

    long now = OffsetDateTime.now().toEpochSecond();

    dslContext
        .insertInto(DELETED_SECRETS)
        .columns(DELETED_SECRETS.fields())
        .select(select(Arrays.stream(DELETED_SECRETS.fields())
            .map(SECRETS::field)
            .collect(Collectors.toList())).from(SECRETS).where(SECRETS.ID.eq(record.getId())))
            .execute();

    dslContext
        .update(SECRETS)
        .set(SECRETS.NAME, transformNameForDeletion(record.getName()))
        .set(SECRETS.CURRENT, (Long) null)
        .set(SECRETS.UPDATEDAT, now)
        .where(SECRETS.ID.eq(record.getId()))
        .execute();

    dslContext
        .insertInto(DELETED_ACCESSGRANTS)
        .columns(DELETED_ACCESSGRANTS.fields())
        .select(select(
            Arrays.stream(DELETED_ACCESSGRANTS.fields())
                .map(ACCESSGRANTS::field)
                .collect(Collectors.toList()))
            .from(ACCESSGRANTS)
            .where(ACCESSGRANTS.SECRETID.eq(record.getId())))
        .execute();

    dslContext
        .delete(ACCESSGRANTS)
        .where(ACCESSGRANTS.SECRETID.eq(record.getId()))
        .execute();
  }

  public void renameSecretSeriesById(long secretId, String name, String creator, long now) {
    String rowHmac = computeRowHmac(secretId, name);
    dslContext.update(SECRETS)
        .set(SECRETS.NAME, name)
        .set(SECRETS.ROW_HMAC, rowHmac)
        .set(SECRETS.UPDATEDBY, creator)
        .set(SECRETS.UPDATEDAT, now)
        .where(SECRETS.ID.eq(secretId))
        .execute();
  }

  /**
   * @return the number of deleted secret series
   */
  public int countDeletedSecretSeries() {
    return dslContext.selectCount()
        .from(SECRETS)
        .where(SECRETS.CURRENT.isNull())
        .fetchOne()
        .value1();
  }

  /**
   * Identify all secret series which were deleted before the given date.
   *
   * @param deleteBefore the cutoff date; secrets deleted before this date will be returned
   * @return IDs for secret series deleted before this date
   */
  public List<Long> getIdsForSecretSeriesDeletedBeforeDate(DateTime deleteBefore) {
    long deleteBeforeSeconds = deleteBefore.getMillis() / 1000;
    return dslContext.select(SECRETS.ID)
        .from(SECRETS)
        .where(SECRETS.CURRENT.isNull())
        .and(SECRETS.UPDATEDAT.le(deleteBeforeSeconds))
        .fetch(SECRETS.ID);
  }

  /**
   * PERMANENTLY REMOVE database records from `secrets` which have the given list of IDs. Does not
   * affect the `secrets_content` table.
   *
   * @param ids the IDs in the `secrets` table to be PERMANENTLY REMOVED
   * @return the number of records which were removed
   */
  public long dangerPermanentlyRemoveRecordsForGivenIDs(List<Long> ids) {
    var deletedCount = new Object(){ long val = 0; };
    dslContext.transaction(configuration -> {
      dslContext.deleteFrom(DELETED_SECRETS)
          .where(DELETED_SECRETS.ID.in(ids))
          .execute();
      deletedCount.val = dslContext.deleteFrom(SECRETS)
          .where(SECRETS.ID.in(ids))
          .execute();
    });
    return deletedCount.val;
  }

  public static class SecretSeriesDAOFactory implements DAOFactory<SecretSeriesDAO> {
    private final DSLContext jooq;
    private final DSLContext readonlyJooq;
    private final ObjectMapper objectMapper;
    private final SecretSeriesMapper.SecretSeriesMapperFactory secretSeriesMapperFactory;
    private final RowHmacGenerator rowHmacGenerator;

    @Inject public SecretSeriesDAOFactory(
        DSLContext jooq,
        @Readonly DSLContext readonlyJooq,
        ObjectMapper objectMapper,
        SecretSeriesMapper.SecretSeriesMapperFactory secretSeriesMapperFactory,
        RowHmacGenerator rowHmacGenerator) {
      this.jooq = jooq;
      this.readonlyJooq = readonlyJooq;
      this.objectMapper = objectMapper;
      this.secretSeriesMapperFactory = secretSeriesMapperFactory;
      this.rowHmacGenerator = rowHmacGenerator;
    }

    @Override public SecretSeriesDAO readwrite() {
      return new SecretSeriesDAO(
          jooq,
          objectMapper,
          secretSeriesMapperFactory.using(jooq),
          rowHmacGenerator);
    }

    @Override public SecretSeriesDAO readonly() {
      return new SecretSeriesDAO(
          readonlyJooq,
          objectMapper,
          secretSeriesMapperFactory.using(readonlyJooq),
          rowHmacGenerator);
    }

    @Override public SecretSeriesDAO using(Configuration configuration) {
      DSLContext dslContext = DSL.using(checkNotNull(configuration));
      return new SecretSeriesDAO(
          dslContext,
          objectMapper,
          secretSeriesMapperFactory.using(dslContext),
          rowHmacGenerator);
    }
  }

  // create a new name for the deleted secret, so that deleted secret names can be reused, while
  // still having a unique constraint on the name field in the DB
  private static String transformNameForDeletion(String name) {
    long now = OffsetDateTime.now().toEpochSecond();
    return String.format(".%s.deleted.%d.%s", name, now, UUID.randomUUID());
  }

  private String computeRowHmac(long secretSeriesId, String secretSeriesName) {
    return rowHmacGenerator.computeRowHmac(
        SECRETS.getName(),
        List.of(
            secretSeriesName,
            secretSeriesId));
  }
}
