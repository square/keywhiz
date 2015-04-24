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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import keywhiz.api.model.SecretContent;
import keywhiz.api.model.SecretSeries;
import keywhiz.api.model.SecretSeriesAndContent;
import keywhiz.jooq.tables.records.SecretsRecord;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static keywhiz.jooq.tables.Accessgrants.ACCESSGRANTS;
import static keywhiz.jooq.tables.Clients.CLIENTS;
import static keywhiz.jooq.tables.Groups.GROUPS;
import static keywhiz.jooq.tables.Memberships.MEMBERSHIPS;
import static keywhiz.jooq.tables.Secrets.SECRETS;
import static keywhiz.jooq.tables.SecretsContent.SECRETS_CONTENT;

public class AclDAO {
  private static final Logger logger = LoggerFactory.getLogger(AclDAO.class);

  private final DSLContext dslContext;
  private final ClientDAO clientDAO;
  private final GroupDAO groupDAO;
  private final SecretContentDAO secretContentDAO;
  private final SecretSeriesDAO secretSeriesDAO;
  private final ObjectMapper mapper;

  @Inject
  public AclDAO(DSLContext dslContext, ClientDAO clientDAO, GroupDAO groupDAO,
      SecretContentDAO secretContentDAO, SecretSeriesDAO secretSeriesDAO, ObjectMapper mapper) {
    this.dslContext = dslContext;
    this.clientDAO = clientDAO;
    this.groupDAO = groupDAO;
    this.secretContentDAO = secretContentDAO;
    this.secretSeriesDAO = secretSeriesDAO;
    this.mapper = mapper;
  }

  public void findAndAllowAccess(long secretId, long groupId) {
    dslContext.transaction(configuration -> {
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

      allowAccess(secretId, groupId);
    });
  }

  public void findAndRevokeAccess(long secretId, long groupId) {
    dslContext.transaction(configuration -> {
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

      revokeAccess(secretId, groupId);
    });
  }

  public void findAndEnrollClient(long clientId, long groupId) {
    dslContext.transaction(configuration -> {
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

      enrollClient(clientId, groupId);
    });
  }

  public void findAndEvictClient(long clientId, long groupId) {
    dslContext.transaction(configuration -> {
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

      evictClient(clientId, groupId);
    });
  }

  public ImmutableSet<SanitizedSecret> getSanitizedSecretsFor(Group group) {
    checkNotNull(group);

    ImmutableSet.Builder<SanitizedSecret> set = ImmutableSet.builder();

    for (SecretSeries series : getSecretSeriesFor(group)) {
      for (SecretContent content : secretContentDAO.getSecretContentsBySecretId(series.getId())) {
        SecretSeriesAndContent seriesAndContent = SecretSeriesAndContent.of(series, content);
        set.add(SanitizedSecret.fromSecretSeriesAndContent(seriesAndContent));
      }
    }

    return set.build();
  }

  public Set<Group> getGroupsFor(Secret secret) {
    List<Group> r = dslContext
        .select()
        .from(GROUPS)
        .join(ACCESSGRANTS).on(GROUPS.ID.eq(ACCESSGRANTS.GROUPID))
        .join(SECRETS).on(ACCESSGRANTS.SECRETID.eq(SECRETS.ID))
        .where(SECRETS.NAME.eq(secret.getName()))
        .fetchInto(GROUPS)
        .map(new GroupMapper());
    return new HashSet<>(r);
  }

  public Set<Group> getGroupsFor(Client client) {
    List<Group> r = dslContext
        .select()
        .from(GROUPS)
        .join(MEMBERSHIPS).on(GROUPS.ID.eq(MEMBERSHIPS.GROUPID))
        .join(CLIENTS).on(CLIENTS.ID.eq(MEMBERSHIPS.CLIENTID))
        .where(CLIENTS.NAME.eq(client.getName()))
        .fetchInto(GROUPS)
        .map(new GroupMapper());
    return new HashSet<>(r);
  }

  public Set<Client> getClientsFor(Group group) {
    List<Client> r = dslContext
        .select()
        .from(CLIENTS)
        .join(MEMBERSHIPS).on(CLIENTS.ID.eq(MEMBERSHIPS.CLIENTID))
        .join(GROUPS).on(GROUPS.ID.eq(MEMBERSHIPS.GROUPID))
        .where(GROUPS.NAME.eq(group.getName()))
        .fetchInto(CLIENTS)
        .map(new ClientMapper());
    return new HashSet<>(r);
  }

  public ImmutableSet<SanitizedSecret> getSanitizedSecretsFor(Client client) {
    checkNotNull(client);
    return dslContext.transactionResult(configuration -> {
      ImmutableSet.Builder<SanitizedSecret> sanitizedSet = ImmutableSet.builder();

      for (SecretSeries series : getSecretSeriesFor(client)) {
        for (SecretContent content : secretContentDAO.getSecretContentsBySecretId(series.getId())) {
          SecretSeriesAndContent seriesAndContent = SecretSeriesAndContent.of(series, content);
          sanitizedSet.add(SanitizedSecret.fromSecretSeriesAndContent(seriesAndContent));
        }
      }
      return sanitizedSet.build();
    });
  }

  public Set<Client> getClientsFor(Secret secret) {
    List<Client> r = dslContext
        .select()
        .from(CLIENTS)
        .join(MEMBERSHIPS).on(CLIENTS.ID.eq(MEMBERSHIPS.CLIENTID))
        .join(ACCESSGRANTS).on(MEMBERSHIPS.GROUPID.eq(ACCESSGRANTS.GROUPID))
        .join(SECRETS).on(SECRETS.ID.eq(ACCESSGRANTS.SECRETID))
        .where(SECRETS.NAME.eq(secret.getName()))
        .fetchInto(CLIENTS)
        .map(new ClientMapper());
    return new HashSet<>(r);
  }

  public Optional<SanitizedSecret> getSanitizedSecretFor(Client client, String name, String version) {
    checkNotNull(client);
    checkArgument(!name.isEmpty());
    checkNotNull(version);

    return dslContext.<Optional<SanitizedSecret>>transactionResult(configuration -> {
      Optional<SecretSeries> secretSeries = getSecretSeriesFor(client, name);
      if (!secretSeries.isPresent()) {
        return Optional.empty();
      }

      Optional<SecretContent> secretContent =
          secretContentDAO.getSecretContentBySecretIdAndVersion(
              secretSeries.get().getId(), version);
      if (!secretContent.isPresent()) {
        return Optional.empty();
      }

      SecretSeriesAndContent seriesAndContent = SecretSeriesAndContent.of(
          secretSeries.get(), secretContent.get());
      return Optional.of(SanitizedSecret.fromSecretSeriesAndContent(seriesAndContent));
    });
  }

  protected void allowAccess(long secretId, long groupId) {
    OffsetDateTime now = OffsetDateTime.now();

    dslContext
        .insertInto(ACCESSGRANTS)
        .set(ACCESSGRANTS.SECRETID, Math.toIntExact(secretId))
        .set(ACCESSGRANTS.GROUPID, Math.toIntExact(groupId))
        .set(ACCESSGRANTS.CREATEDAT, now)
        .set(ACCESSGRANTS.UPDATEDAT, now)
        .execute();
  }

  protected void revokeAccess(long secretId, long groupId) {
    dslContext
        .delete(ACCESSGRANTS)
        .where(ACCESSGRANTS.SECRETID.eq(Math.toIntExact(secretId))
            .and(ACCESSGRANTS.GROUPID.eq(Math.toIntExact(groupId))))
        .execute();
  }

  protected void enrollClient(long clientId, long groupId) {
    OffsetDateTime now = OffsetDateTime.now();

    dslContext
        .insertInto(MEMBERSHIPS)
        .set(MEMBERSHIPS.GROUPID, Math.toIntExact(groupId))
        .set(MEMBERSHIPS.CLIENTID, Math.toIntExact(clientId))
        .set(ACCESSGRANTS.CREATEDAT, now)
        .set(ACCESSGRANTS.UPDATEDAT, now)
        .execute();
  }

  protected void evictClient(long clientId, long groupId) {
    dslContext
        .delete(MEMBERSHIPS)
        .where(MEMBERSHIPS.CLIENTID.eq(Math.toIntExact(clientId))
            .and(MEMBERSHIPS.GROUPID.eq(Math.toIntExact(groupId))))
        .execute();
  }

  protected ImmutableSet<SecretSeries> getSecretSeriesFor(Group group) {
    List<SecretSeries> r = dslContext
        .select()
        .from(SECRETS)
        .join(ACCESSGRANTS).on(SECRETS.ID.eq(ACCESSGRANTS.SECRETID))
        .join(GROUPS).on(GROUPS.ID.eq(ACCESSGRANTS.GROUPID))
        .where(GROUPS.NAME.eq(group.getName()))
        .fetchInto(SECRETS)
        .map(new SecretSeriesMapper(mapper));
    return ImmutableSet.copyOf(r);

  }

  protected ImmutableSet<SecretSeries> getSecretSeriesFor(Client client) {
    List<SecretSeries> r = dslContext
        .select()
        .from(SECRETS)
        .join(ACCESSGRANTS).on(SECRETS.ID.eq(ACCESSGRANTS.SECRETID))
        .join(MEMBERSHIPS).on(ACCESSGRANTS.GROUPID.eq(MEMBERSHIPS.GROUPID))
        .join(CLIENTS).on(CLIENTS.ID.eq(MEMBERSHIPS.CLIENTID))
        .where(CLIENTS.NAME.eq(client.getName()))
        .fetchInto(SECRETS)
        .map(new SecretSeriesMapper(mapper));
    return ImmutableSet.copyOf(r);
  }

  /**
   * @param client client to access secrets
   * @param name name of SecretSeries
   * @return Optional.absent() when secret unauthorized or not found.
   * The query doesn't distinguish between these cases. If result absent, a followup call on clients
   * table should be used to determine the exception.
   */
  protected Optional<SecretSeries> getSecretSeriesFor(Client client, String name) {
    SecretsRecord r = dslContext
        .select()
        .from(SECRETS)
        .join(SECRETS_CONTENT).on(SECRETS.ID.eq(SECRETS_CONTENT.SECRETID))
        .join(ACCESSGRANTS).on(SECRETS.ID.eq(ACCESSGRANTS.SECRETID))
        .join(MEMBERSHIPS).on(ACCESSGRANTS.GROUPID.eq(MEMBERSHIPS.GROUPID))
        .join(CLIENTS).on(CLIENTS.ID.eq(MEMBERSHIPS.CLIENTID))
        .where(SECRETS.NAME.eq(name).and(CLIENTS.NAME.eq(client.getName())))
        .fetchOneInto(SECRETS);

    return Optional.ofNullable(r).map(
        rec -> new SecretSeriesMapper(mapper).map(rec));
  }
}
