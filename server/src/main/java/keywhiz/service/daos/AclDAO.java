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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import keywhiz.KeywhizConfig;
import keywhiz.KeywhizConfig.RowHmacCheck;
import keywhiz.api.ApiDate;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import keywhiz.api.model.SecretContent;
import keywhiz.api.model.SecretSeries;
import keywhiz.api.model.SecretSeriesAndContent;
import keywhiz.jooq.tables.records.SecretsRecord;
import keywhiz.log.AuditLog;
import keywhiz.log.Event;
import keywhiz.log.EventTag;
import keywhiz.service.config.Readonly;
import keywhiz.service.crypto.RowHmacGenerator;
import keywhiz.service.daos.ClientDAO.ClientDAOFactory;
import keywhiz.service.daos.GroupDAO.GroupDAOFactory;
import keywhiz.service.daos.SecretContentDAO.SecretContentDAOFactory;
import keywhiz.service.daos.SecretSeriesDAO.SecretSeriesDAOFactory;
import keywhiz.service.daos.SecretSeriesMapper.SecretSeriesMapperFactory;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectQuery;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static keywhiz.jooq.tables.Accessgrants.ACCESSGRANTS;
import static keywhiz.jooq.tables.Clients.CLIENTS;
import static keywhiz.jooq.tables.Groups.GROUPS;
import static keywhiz.jooq.tables.Memberships.MEMBERSHIPS;
import static keywhiz.jooq.tables.Secrets.SECRETS;
import static keywhiz.jooq.tables.SecretsContent.SECRETS_CONTENT;

public class AclDAO {
  private static final Logger logger = LoggerFactory.getLogger(AclDAO.class);

  private final DSLContext dslContext;
  private final ClientDAOFactory clientDAOFactory;
  private final GroupDAOFactory groupDAOFactory;
  private final SecretContentDAOFactory secretContentDAOFactory;
  private final SecretSeriesDAOFactory secretSeriesDAOFactory;
  private final ClientMapper clientMapper;
  private final GroupMapper groupMapper;
  private final SecretSeriesMapperFactory secretSeriesMapperFactory;
  private final SecretContentMapper secretContentMapper;
  private final RowHmacGenerator rowHmacGenerator;
  private final KeywhizConfig config;

  private AclDAO(DSLContext dslContext, ClientDAOFactory clientDAOFactory, GroupDAOFactory groupDAOFactory,
                 SecretContentDAOFactory secretContentDAOFactory, SecretSeriesDAOFactory secretSeriesDAOFactory,
                 ClientMapper clientMapper, GroupMapper groupMapper, SecretSeriesMapperFactory secretSeriesMapperFactory,
                 SecretContentMapper secretContentMapper, RowHmacGenerator rowHmacGenerator,
                 KeywhizConfig config) {
    this.dslContext = dslContext;
    this.clientDAOFactory = clientDAOFactory;
    this.groupDAOFactory = groupDAOFactory;
    this.secretContentDAOFactory = secretContentDAOFactory;
    this.secretSeriesDAOFactory = secretSeriesDAOFactory;
    this.clientMapper = clientMapper;
    this.groupMapper = groupMapper;
    this.secretSeriesMapperFactory = secretSeriesMapperFactory;
    this.secretContentMapper = secretContentMapper;
    this.rowHmacGenerator = rowHmacGenerator;
    this.config = config;
  }

  public void findAndAllowAccess(long secretId, long groupId, AuditLog auditLog, String user, Map<String, String> extraInfo) {
    dslContext.transaction(configuration -> {
      GroupDAO groupDAO = groupDAOFactory.using(configuration);
      SecretSeriesDAO secretSeriesDAO = secretSeriesDAOFactory.using(configuration);

      Optional<Group> group = groupDAO.getGroupById(groupId);
      if (!group.isPresent()) {
        logger.info("Failure to allow access groupId {}, secretId {}: groupId not found.", groupId,
            secretId);
        throw new IllegalStateException(format("GroupId %d doesn't exist.", groupId));
      }

      Optional<SecretSeries> secret = secretSeriesDAO.getSecretSeriesById(secretId);
      if (!secret.isPresent()) {
        logger.info("Failure to allow access groupId {}, secretId {}: secretId not found.", groupId,
            secretId);
        throw new IllegalStateException(format("SecretId %d doesn't exist.", secretId));
      }

      allowAccess(configuration, secretId, groupId);

      extraInfo.put("group", group.get().getName());
      extraInfo.put("secret added", secret.get().name());
      auditLog.recordEvent(new Event(Instant.now(), EventTag.CHANGEACL_GROUP_SECRET, user, group.get().getName(), extraInfo));
    });
  }

  public void findAndRevokeAccess(long secretId, long groupId, AuditLog auditLog, String user, Map<String, String> extraInfo) {
    dslContext.transaction(configuration -> {
      GroupDAO groupDAO = groupDAOFactory.using(configuration);
      SecretSeriesDAO secretSeriesDAO = secretSeriesDAOFactory.using(configuration);

      Optional<Group> group = groupDAO.getGroupById(groupId);
      if (!group.isPresent()) {
        logger.info("Failure to revoke access groupId {}, secretId {}: groupId not found.", groupId,
            secretId);
        throw new IllegalStateException(format("GroupId %d doesn't exist.", groupId));
      }

      Optional<SecretSeries> secret = secretSeriesDAO.getSecretSeriesById(secretId);
      if (!secret.isPresent()) {
        logger.info("Failure to revoke access groupId {}, secretId {}: secretId not found.",
            groupId, secretId);
        throw new IllegalStateException(format("SecretId %d doesn't exist.", secretId));
      }

      revokeAccess(configuration, secretId, groupId);

      extraInfo.put("group", group.get().getName());
      extraInfo.put("secret removed", secret.get().name());
      auditLog.recordEvent(new Event(Instant.now(), EventTag.CHANGEACL_GROUP_SECRET, user, group.get().getName(), extraInfo));
    });
  }

  public void findAndEnrollClient(long clientId, long groupId, AuditLog auditLog, String user, Map<String, String> extraInfo) {
    dslContext.transaction(configuration -> {
      ClientDAO clientDAO = clientDAOFactory.using(configuration);
      GroupDAO groupDAO = groupDAOFactory.using(configuration);

      Optional<Client> client = clientDAO.getClientById(clientId);
      if (!client.isPresent()) {
        logger.info("Failure to enroll membership clientId {}, groupId {}: clientId not found.",
            clientId, groupId);
        throw new IllegalStateException(format("ClientId %d doesn't exist.", clientId));
      }

      Optional<Group> group = groupDAO.getGroupById(groupId);
      if (!group.isPresent()) {
        logger.info("Failure to enroll membership clientId {}, groupId {}: groupId not found.",
            clientId, groupId);
        throw new IllegalStateException(format("GroupId %d doesn't exist.", groupId));
      }

      enrollClient(configuration, clientId, groupId);

      extraInfo.put("group", group.get().getName());
      extraInfo.put("client added", client.get().getName());
      auditLog.recordEvent(new Event(Instant.now(), EventTag.CHANGEACL_GROUP_CLIENT, user, group.get().getName(), extraInfo));
    });
  }

  public void findAndEvictClient(long clientId, long groupId, AuditLog auditLog, String user, Map<String, String> extraInfo) {
    dslContext.transaction(configuration -> {
      ClientDAO clientDAO = clientDAOFactory.using(configuration);
      GroupDAO groupDAO = groupDAOFactory.using(configuration);

      Optional<Client> client = clientDAO.getClientById(clientId);
      if (!client.isPresent()) {
        logger.info("Failure to evict membership clientId {}, groupId {}: clientId not found.",
            clientId, groupId);
        throw new IllegalStateException(format("ClientId %d doesn't exist.", clientId));
      }

      Optional<Group> group = groupDAO.getGroupById(groupId);
      if (!group.isPresent()) {
        logger.info("Failure to evict membership clientId {}, groupId {}: groupId not found.",
            clientId, groupId);
        throw new IllegalStateException(format("GroupId %d doesn't exist.", groupId));
      }

      evictClient(configuration, clientId, groupId);

      extraInfo.put("group", group.get().getName());
      extraInfo.put("client removed", client.get().getName());
      auditLog.recordEvent(new Event(Instant.now(), EventTag.CHANGEACL_GROUP_CLIENT, user, group.get().getName(), extraInfo));
    });
  }

  public ImmutableSet<SanitizedSecret> getSanitizedSecretsFor(Group group) {
    return getSanitizedSecretsFor(group, false);
  }

  public ImmutableSet<SanitizedSecret> getSanitizedSecretsFor(Group group, boolean owned) {
    checkNotNull(group);

    ImmutableSet.Builder<SanitizedSecret> set = ImmutableSet.builder();

    return dslContext.transactionResult(configuration -> {
      SecretContentDAO secretContentDAO = secretContentDAOFactory.using(configuration);

      Set<SecretSeries> seriesSet;
      if (owned) {
        seriesSet = getOwnedSecretSeriesFor(configuration, group);
      } else {
        seriesSet = getSecretSeriesFor(configuration, group);
      }

      for (SecretSeries series : seriesSet) {
        SecretContent content = secretContentDAO.getSecretContentById(series.currentVersion().get()).get();
        SecretSeriesAndContent seriesAndContent = SecretSeriesAndContent.of(series, content);
        set.add(SanitizedSecret.fromSecretSeriesAndContent(seriesAndContent));
      }

      return set.build();
    });
  }

  public Set<Group> getGroupsFor(Secret secret) {
    List<Group> r = dslContext
        .select(GROUPS.fields())
        .from(GROUPS)
        .join(ACCESSGRANTS).on(GROUPS.ID.eq(ACCESSGRANTS.GROUPID))
        .join(SECRETS).on(ACCESSGRANTS.SECRETID.eq(SECRETS.ID))
        .where(SECRETS.NAME.eq(secret.getName()))
        .fetchInto(GROUPS)
        .map(groupMapper);
    return new HashSet<>(r);
  }

  public Set<Group> getGroupsFor(Client client) {
    List<Group> r = dslContext
        .select(GROUPS.fields())
        .from(GROUPS)
        .join(MEMBERSHIPS).on(GROUPS.ID.eq(MEMBERSHIPS.GROUPID))
        .join(CLIENTS).on(CLIENTS.ID.eq(MEMBERSHIPS.CLIENTID))
        .where(CLIENTS.NAME.eq(client.getName()))
        .fetchInto(GROUPS)
        .map(groupMapper);
    return new HashSet<>(r);
  }

  public Set<Client> getClientsFor(Group group) {
    List<Client> r = dslContext
        .select(CLIENTS.fields())
        .from(CLIENTS)
        .join(MEMBERSHIPS).on(CLIENTS.ID.eq(MEMBERSHIPS.CLIENTID))
        .join(GROUPS).on(GROUPS.ID.eq(MEMBERSHIPS.GROUPID))
        .where(GROUPS.NAME.eq(group.getName()))
        .fetchInto(CLIENTS)
        .map(clientMapper);
    return new HashSet<>(r);
  }

  public ImmutableSet<SanitizedSecret> getSanitizedSecretsFor(Client client) {
    checkNotNull(client);

    ImmutableSet.Builder<SanitizedSecret> sanitizedSet = ImmutableSet.builder();

    SelectQuery<Record> query = dslContext.select(SECRETS.fields())
        .from(SECRETS)
        .join(ACCESSGRANTS).on(SECRETS.ID.eq(ACCESSGRANTS.SECRETID))
        .join(MEMBERSHIPS).on(ACCESSGRANTS.GROUPID.eq(MEMBERSHIPS.GROUPID))
        .join(CLIENTS).on(CLIENTS.ID.eq(MEMBERSHIPS.CLIENTID))
        .join(SECRETS_CONTENT).on(SECRETS_CONTENT.ID.eq(SECRETS.CURRENT))
        .where(CLIENTS.NAME.eq(client.getName()).and(SECRETS.CURRENT.isNotNull()))
        .getQuery();
    query.addSelect(SECRETS_CONTENT.CONTENT_HMAC);
    query.addSelect(SECRETS_CONTENT.CREATEDAT);
    query.addSelect(SECRETS_CONTENT.CREATEDBY);
    query.addSelect(SECRETS_CONTENT.METADATA);
    query.addSelect(SECRETS_CONTENT.EXPIRY);
    query.addSelect(ACCESSGRANTS.ROW_HMAC);
    query.addSelect(MEMBERSHIPS.ROW_HMAC);
    query.addSelect(MEMBERSHIPS.GROUPID);
    query.addSelect(CLIENTS.ROW_HMAC);
    query.addSelect(SECRETS.ROW_HMAC);
    query.fetch()
        .map(row -> processSanitizedSecretRow(row, client))
        .forEach(sanitizedSet::add);

    return sanitizedSet.build();
  }

  public Set<Client> getClientsFor(Secret secret) {
    List<Client> r = dslContext
        .select(CLIENTS.fields())
        .from(CLIENTS)
        .join(MEMBERSHIPS).on(CLIENTS.ID.eq(MEMBERSHIPS.CLIENTID))
        .join(ACCESSGRANTS).on(MEMBERSHIPS.GROUPID.eq(ACCESSGRANTS.GROUPID))
        .join(SECRETS).on(SECRETS.ID.eq(ACCESSGRANTS.SECRETID))
        .where(SECRETS.NAME.eq(secret.getName()))
        .fetchInto(CLIENTS)
        .map(clientMapper);
    return new HashSet<>(r);
  }

  public Optional<SanitizedSecret> getSanitizedSecretFor(Client client, String secretName) {
    checkNotNull(client);
    checkArgument(!secretName.isEmpty());

    SelectQuery<Record> query = dslContext.select(SECRETS.fields())
        .from(SECRETS)
        .join(ACCESSGRANTS).on(SECRETS.ID.eq(ACCESSGRANTS.SECRETID))
        .join(MEMBERSHIPS).on(ACCESSGRANTS.GROUPID.eq(MEMBERSHIPS.GROUPID))
        .join(CLIENTS).on(CLIENTS.ID.eq(MEMBERSHIPS.CLIENTID))
        .join(SECRETS_CONTENT).on(SECRETS_CONTENT.ID.eq(SECRETS.CURRENT))
        .where(CLIENTS.NAME.eq(client.getName())
            .and(SECRETS.CURRENT.isNotNull())
            .and(SECRETS.NAME.eq(secretName)))
        .limit(1)
        .getQuery();
    query.addSelect(SECRETS_CONTENT.CONTENT_HMAC);
    query.addSelect(SECRETS_CONTENT.CREATEDAT);
    query.addSelect(SECRETS_CONTENT.CREATEDBY);
    query.addSelect(SECRETS_CONTENT.METADATA);
    query.addSelect(SECRETS_CONTENT.EXPIRY);
    query.addSelect(ACCESSGRANTS.ROW_HMAC);
    query.addSelect(MEMBERSHIPS.ROW_HMAC);
    query.addSelect(MEMBERSHIPS.GROUPID);
    query.addSelect(CLIENTS.ROW_HMAC);
    query.addSelect(SECRETS.ROW_HMAC);
    return Optional.ofNullable(query.fetchOne())
        .map(row -> processSanitizedSecretRow(row, client));
  }

  public List<SanitizedSecret> getBatchSanitizedSecretsFor(Client client, List<String> secretNames) {
    checkNotNull(client);
    checkArgument(!secretNames.isEmpty());

    SelectQuery<Record> query = dslContext.select(SECRETS.fields())
            .from(SECRETS)
            .join(ACCESSGRANTS).on(SECRETS.ID.eq(ACCESSGRANTS.SECRETID))
            .join(MEMBERSHIPS).on(ACCESSGRANTS.GROUPID.eq(MEMBERSHIPS.GROUPID))
            .join(CLIENTS).on(CLIENTS.ID.eq(MEMBERSHIPS.CLIENTID))
            .join(SECRETS_CONTENT).on(SECRETS_CONTENT.ID.eq(SECRETS.CURRENT))
            .where(CLIENTS.NAME.eq(client.getName())
                    .and(SECRETS.CURRENT.isNotNull())
                    .and(SECRETS.NAME.in(secretNames)))
            .getQuery();
    query.addSelect(SECRETS_CONTENT.CONTENT_HMAC);
    query.addSelect(SECRETS_CONTENT.CREATEDAT);
    query.addSelect(SECRETS_CONTENT.CREATEDBY);
    query.addSelect(SECRETS_CONTENT.METADATA);
    query.addSelect(SECRETS_CONTENT.EXPIRY);
    query.addSelect(ACCESSGRANTS.ROW_HMAC);
    query.addSelect(MEMBERSHIPS.ROW_HMAC);
    query.addSelect(MEMBERSHIPS.GROUPID);
    query.addSelect(CLIENTS.ROW_HMAC);
    query.addSelect(SECRETS.ROW_HMAC);

    return query.fetch().map(row -> processSanitizedSecretRow(row, client));
  }

  private SanitizedSecret processSanitizedSecretRow(Record row, Client client) {
    boolean rowHmacLog = config.getRowHmacCheck() == RowHmacCheck.DISABLED_BUT_LOG;
    boolean rowHmacFail = config.getRowHmacCheck() == RowHmacCheck.ENFORCED;

    SecretSeries series = secretSeriesMapperFactory.using(dslContext).map(row.into(SECRETS));

    String secretHmac = rowHmacGenerator.computeRowHmac(
        SECRETS.getName(), List.of(row.getValue(SECRETS.NAME), row.getValue(SECRETS.ID))
    );
    if (!RowHmacGenerator.compareHmacs(secretHmac, row.getValue(SECRETS.ROW_HMAC))) {
      String errorMessage = String.format(
          "Secret HMAC verification failed for secret: %s", row.getValue(SECRETS.NAME));
      if (rowHmacLog) {
        logger.warn(errorMessage);
      }
      if (rowHmacFail) {
        throw new AssertionError(errorMessage);
      }
    }

    String clientHmac = rowHmacGenerator.computeRowHmac(
        CLIENTS.getName(), List.of(client.getName(), client.getId())
    );
    if (!RowHmacGenerator.compareHmacs(clientHmac, row.getValue(CLIENTS.ROW_HMAC))) {
      String errorMessage = String.format(
          "Client HMAC verification failed for client: %s", client.getName());
      if (rowHmacLog) {
        logger.warn(errorMessage);
      }
      if (rowHmacFail) {
        throw new AssertionError(errorMessage);
      }
    }

    String membershipsHmac = rowHmacGenerator.computeRowHmac(
        MEMBERSHIPS.getName(), List.of(client.getId(), row.getValue(MEMBERSHIPS.GROUPID)));
    if (!RowHmacGenerator.compareHmacs(membershipsHmac, row.getValue(MEMBERSHIPS.ROW_HMAC))) {
      String errorMessage = String.format(
          "Memberships HMAC verification failed for clientId: %d in groupId: %d",
          client.getId(), row.getValue(MEMBERSHIPS.GROUPID));
      if (rowHmacLog) {
        logger.warn(errorMessage);
      }
      if (rowHmacFail) {
        throw new AssertionError(errorMessage);
      }
    }

    String accessgrantsHmac = rowHmacGenerator.computeRowHmac(
        ACCESSGRANTS.getName(),
        List.of(row.getValue(MEMBERSHIPS.GROUPID), row.getValue(SECRETS.ID)));
    if (!RowHmacGenerator.compareHmacs(accessgrantsHmac, row.getValue(ACCESSGRANTS.ROW_HMAC))) {
      String errorMessage = String.format(
          "Access Grants HMAC verification failed for groupId: %d in secretId: %d",
          row.getValue(MEMBERSHIPS.GROUPID), row.getValue(SECRETS.ID));
      if (rowHmacLog) {
        logger.warn(errorMessage);
      }
      if (rowHmacFail) {
        throw new AssertionError(errorMessage);
      }
    }

    return SanitizedSecret.of(
        series.id(),
        series.name(),
        series.owner(),
        series.description(),
        row.getValue(SECRETS_CONTENT.CONTENT_HMAC),
        series.createdAt(),
        series.createdBy(),
        series.updatedAt(),
        series.updatedBy(),
        secretContentMapper.tryToReadMapFromMetadata(row.getValue(SECRETS_CONTENT.METADATA)),
        series.type().orElse(null),
        series.generationOptions(),
        row.getValue(SECRETS_CONTENT.EXPIRY),
        series.currentVersion().orElse(null),
        new ApiDate(row.getValue(SECRETS_CONTENT.CREATEDAT)),
        row.getValue(SECRETS_CONTENT.CREATEDBY));
  }

  public Map<Long, List<Group>> getGroupsForSecrets(Set<Long> secretIdList) {
    Map<Long, Group> groupMap = dslContext.select().from(GROUPS)
        .join(ACCESSGRANTS).on(ACCESSGRANTS.GROUPID.eq(GROUPS.ID))
        .join(SECRETS).on(ACCESSGRANTS.SECRETID.eq(SECRETS.ID))
        .where(SECRETS.ID.in(secretIdList))
        .fetchInto(GROUPS).map(groupMapper).stream().collect(Collectors.toMap(Group::getId, g -> g, (g1, g2) -> g1));

    Map<Long, List<Long>> secretsIdGroupsIdMap = dslContext.select().from(GROUPS)
        .join(ACCESSGRANTS).on(ACCESSGRANTS.GROUPID.eq(GROUPS.ID))
        .join(SECRETS).on(ACCESSGRANTS.SECRETID.eq(SECRETS.ID))
        .where(SECRETS.ID.in(secretIdList))
        .fetch().intoGroups(SECRETS.ID, GROUPS.ID);

    ImmutableMap.Builder<Long, List<Group>> builder = ImmutableMap.builder();
    for (Map.Entry<Long, List<Long>> entry : secretsIdGroupsIdMap.entrySet()) {
      List<Group> groupList = entry.getValue().stream().map(groupMap::get).collect(toList());
      builder.put(entry.getKey(), groupList);
    }
    return builder.build();
  }

  protected void allowAccess(Configuration configuration, long secretId, long groupId) {
    long now = OffsetDateTime.now().toEpochSecond();

    boolean assigned = 0 < DSL.using(configuration)
        .fetchCount(ACCESSGRANTS,
            ACCESSGRANTS.SECRETID.eq(secretId).and(
            ACCESSGRANTS.GROUPID.eq(groupId)));
    if (assigned) {
      return;
    }

    String verificationHmac = rowHmacGenerator.computeRowHmac(
        ACCESSGRANTS.getName(), List.of(groupId, secretId));

    DSL.using(configuration)
        .insertInto(ACCESSGRANTS)
        .set(ACCESSGRANTS.SECRETID, secretId)
        .set(ACCESSGRANTS.GROUPID, groupId)
        .set(ACCESSGRANTS.CREATEDAT, now)
        .set(ACCESSGRANTS.UPDATEDAT, now)
        .set(ACCESSGRANTS.ROW_HMAC, verificationHmac)
        .execute();
  }

  protected void revokeAccess(Configuration configuration, long secretId, long groupId) {
    DSL.using(configuration)
        .delete(ACCESSGRANTS)
        .where(ACCESSGRANTS.SECRETID.eq(secretId)
            .and(ACCESSGRANTS.GROUPID.eq(groupId)))
        .execute();
  }

  protected void enrollClient(Configuration configuration, long clientId, long groupId) {
    long now = OffsetDateTime.now().toEpochSecond();

    boolean enrolled = 0 < DSL.using(configuration)
        .fetchCount(MEMBERSHIPS,
            MEMBERSHIPS.GROUPID.eq(groupId).and(
            MEMBERSHIPS.CLIENTID.eq(clientId)));
    if (enrolled) {
      return;
    }

    String verificationHmac = rowHmacGenerator.computeRowHmac(
        MEMBERSHIPS.getName(), List.of(clientId, groupId));

    DSL.using(configuration)
        .insertInto(MEMBERSHIPS)
        .set(MEMBERSHIPS.GROUPID, groupId)
        .set(MEMBERSHIPS.CLIENTID, clientId)
        .set(MEMBERSHIPS.CREATEDAT, now)
        .set(MEMBERSHIPS.UPDATEDAT, now)
        .set(MEMBERSHIPS.ROW_HMAC, verificationHmac)
        .execute();
  }


  protected void evictClient(Configuration configuration, long clientId, long groupId) {
    DSL.using(configuration)
        .delete(MEMBERSHIPS)
        .where(MEMBERSHIPS.CLIENTID.eq(clientId)
            .and(MEMBERSHIPS.GROUPID.eq(groupId)))
        .execute();
  }

  public ImmutableSet<SecretSeries> getSecretSeriesFor(Group group) {
    checkNotNull(group);

    return dslContext.transactionResult(configuration -> getSecretSeriesFor(configuration, group));
  }

  protected ImmutableSet<SecretSeries> getSecretSeriesFor(Configuration configuration, Group group) {
    List<SecretSeries> r = DSL.using(configuration)
        .select(SECRETS.fields())
        .from(SECRETS)
        .join(ACCESSGRANTS).on(SECRETS.ID.eq(ACCESSGRANTS.SECRETID))
        .join(GROUPS).on(GROUPS.ID.eq(ACCESSGRANTS.GROUPID))
        .where(GROUPS.NAME.eq(group.getName()).and(SECRETS.CURRENT.isNotNull()))
        .fetchInto(SECRETS)
        .map(secretSeriesMapperFactory.using(configuration.dsl()));
    return ImmutableSet.copyOf(r);
  }

  protected ImmutableSet<SecretSeries> getOwnedSecretSeriesFor(Configuration configuration, Group group) {
    List<SecretSeries> r = DSL.using(configuration)
        .select(SECRETS.fields())
        .from(SECRETS)
        .where(SECRETS.OWNER.eq(group.getId()))
        .and(SECRETS.CURRENT.isNotNull())
        .fetchInto(SECRETS)
        .map(secretSeriesMapperFactory.using(configuration.dsl()));
    return ImmutableSet.copyOf(r);
  }

  /**
   * @param configuration database information
   * @param client client to access secrets
   * @param secretName name of SecretSeries
   * @return Optional.absent() when secret unauthorized or not found.
   * The query doesn't distinguish between these cases. If result absent, a followup call on clients
   * table should be used to determine the exception.
   */
  protected Optional<SecretSeries> getSecretSeriesFor(Configuration configuration, Client client, String secretName) {
    // TODO: We need to set limit(1) because we are using joins. We should probably change the join type.
    SecretsRecord r = DSL.using(configuration)
        .select(SECRETS.fields())
        .from(SECRETS)
        .join(ACCESSGRANTS).on(SECRETS.ID.eq(ACCESSGRANTS.SECRETID))
        .join(MEMBERSHIPS).on(ACCESSGRANTS.GROUPID.eq(MEMBERSHIPS.GROUPID))
        .join(CLIENTS).on(CLIENTS.ID.eq(MEMBERSHIPS.CLIENTID))
        .where(SECRETS.NAME.eq(secretName).and(CLIENTS.NAME.eq(client.getName())).and(SECRETS.CURRENT.isNotNull()))
        .limit(1)
        .fetchOneInto(SECRETS);
    return Optional.ofNullable(r).map(secretSeriesMapperFactory.using(configuration.dsl())::map);
  }

  public static class AclDAOFactory implements DAOFactory<AclDAO> {
    private final DSLContext jooq;
    private final DSLContext readonlyJooq;
    private final ClientDAOFactory clientDAOFactory;
    private final GroupDAOFactory groupDAOFactory;
    private final SecretContentDAOFactory secretContentDAOFactory;
    private final SecretSeriesDAOFactory secretSeriesDAOFactory;
    private final ClientMapper clientMapper;
    private final GroupMapper groupMapper;
    private final SecretSeriesMapper.SecretSeriesMapperFactory secretSeriesMapperFactory;
    private final SecretContentMapper secretContentMapper;
    private final RowHmacGenerator rowHmacGenerator;
    private final KeywhizConfig config;

    @Inject public AclDAOFactory(
        DSLContext jooq,
        @Readonly DSLContext readonlyJooq,
        ClientDAOFactory clientDAOFactory,
        GroupDAOFactory groupDAOFactory,
        SecretContentDAOFactory secretContentDAOFactory,
        SecretSeriesDAOFactory secretSeriesDAOFactory,
        ClientMapper clientMapper,
        GroupMapper groupMapper,
        SecretSeriesMapper.SecretSeriesMapperFactory secretSeriesMapperFactory,
        SecretContentMapper secretContentMapper,
        RowHmacGenerator rowHmacGenerator,
        KeywhizConfig config) {
      this.jooq = jooq;
      this.readonlyJooq = readonlyJooq;
      this.clientDAOFactory = clientDAOFactory;
      this.groupDAOFactory = groupDAOFactory;
      this.secretContentDAOFactory = secretContentDAOFactory;
      this.secretSeriesDAOFactory = secretSeriesDAOFactory;
      this.clientMapper = clientMapper;
      this.groupMapper = groupMapper;
      this.secretSeriesMapperFactory = secretSeriesMapperFactory;
      this.secretContentMapper = secretContentMapper;
      this.rowHmacGenerator = rowHmacGenerator;
      this.config = config;
    }

    @Override public AclDAO readwrite() {
      return new AclDAO(
          jooq,
          clientDAOFactory,
          groupDAOFactory,
          secretContentDAOFactory,
          secretSeriesDAOFactory,
          clientMapper,
          groupMapper,
          secretSeriesMapperFactory,
          secretContentMapper,
          rowHmacGenerator,
          config);
    }

    @Override public AclDAO readonly() {
      return new AclDAO(
          readonlyJooq,
          clientDAOFactory,
          groupDAOFactory,
          secretContentDAOFactory,
          secretSeriesDAOFactory,
          clientMapper,
          groupMapper,
          secretSeriesMapperFactory,
          secretContentMapper,
          rowHmacGenerator,
          config);
    }

    @Override public AclDAO using(Configuration configuration) {
      DSLContext dslContext = DSL.using(checkNotNull(configuration));
      return new AclDAO(
          dslContext,
          clientDAOFactory,
          groupDAOFactory,
          secretContentDAOFactory,
          secretSeriesDAOFactory,
          clientMapper,
          groupMapper,
          secretSeriesMapperFactory,
          secretContentMapper,
          rowHmacGenerator,
          config);
    }
  }
}
