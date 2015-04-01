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

import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import java.util.Set;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import keywhiz.api.model.SecretContent;
import keywhiz.api.model.SecretSeries;
import keywhiz.api.model.SecretSeriesAndContent;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.CreateSqlObject;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.Transaction;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.customizers.SingleValueResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

@RegisterMapper({SecretSeriesMapper.class, GroupMapper.class, ClientMapper.class})
public abstract class AclDAO {
  private static final Logger logger = LoggerFactory.getLogger(AclDAO.class);

  // Creates objects with same underlying connection.
  @CreateSqlObject protected abstract ClientDAO createClientDAO();
  @CreateSqlObject protected abstract GroupDAO createGroupDAO();
  @CreateSqlObject protected abstract SecretContentDAO createSecretContentDAO();
  @CreateSqlObject protected abstract SecretSeriesDAO createSecretSeriesDAO();

  @Transaction
  public void findAndAllowAccess(long secretId, long groupId) {
    Optional<Group> group = createGroupDAO().getGroupById(groupId);
    if (!group.isPresent()) {
      logger.info("Failure to allow access groupId {}, secretId {}: groupId not found.", groupId, secretId);
      throw new IllegalStateException(format("GroupId %d doesn't exist.", groupId));
    }

    Optional<SecretSeries> secret = createSecretSeriesDAO().getSecretSeriesById(secretId);
    if (!secret.isPresent()) {
      logger.info("Failure to allow access groupId {}, secretId {}: secretId not found.", groupId, secretId);
      throw new IllegalStateException(format("SecretId %d doesn't exist.", secretId));
    }

    allowAccess(secretId, groupId);
  }

  @Transaction
  public void findAndRevokeAccess(long secretId, long groupId) {
    Optional<Group> group = createGroupDAO().getGroupById(groupId);
    if (!group.isPresent()) {
      logger.info("Failure to revoke access groupId {}, secretId {}: groupId not found.", groupId, secretId);
      throw new IllegalStateException(format("GroupId %d doesn't exist.", groupId));
    }

    Optional<SecretSeries> secret = createSecretSeriesDAO().getSecretSeriesById(secretId);
    if (!secret.isPresent()) {
      logger.info("Failure to revoke access groupId {}, secretId {}: secretId not found.", groupId, secretId);
      throw new IllegalStateException(format("SecretId %d doesn't exist.", secretId));
    }

    revokeAccess(secretId, groupId);
  }

  @Transaction
  public void findAndEnrollClient(long clientId, long groupId) {
    Optional<Client> client = createClientDAO().getClientById(clientId);
    if (!client.isPresent()) {
      logger.info("Failure to enroll membership clientId {}, groupId {}: clientId not found.", clientId, groupId);
      throw new IllegalStateException(format("ClientId %d doesn't exist.", clientId));
    }

    Optional<Group> group = createGroupDAO().getGroupById(groupId);
    if (!group.isPresent()) {
      logger.info("Failure to enroll membership clientId {}, groupId {}: groupId not found.", clientId, groupId);
      throw new IllegalStateException(format("GroupId %d doesn't exist.", groupId));
    }

    enrollClient(clientId, groupId);
  }

  @Transaction
  public void findAndEvictClient(long clientId, long groupId) {
    Optional<Client> client = createClientDAO().getClientById(clientId);
    if (!client.isPresent()) {
      logger.info("Failure to evict membership clientId {}, groupId {}: clientId not found.", clientId, groupId);
      throw new IllegalStateException(format("ClientId %d doesn't exist.", clientId));
    }

    Optional<Group> group = createGroupDAO().getGroupById(groupId);
    if (!group.isPresent()) {
      logger.info("Failure to evict membership clientId {}, groupId {}: groupId not found.", clientId, groupId);
      throw new IllegalStateException(format("GroupId %d doesn't exist.", groupId));
    }

    evictClient(clientId, groupId);
  }

  public ImmutableSet<SanitizedSecret> getSanitizedSecretsFor(Group group) {
    checkNotNull(group);

    ImmutableSet.Builder<SanitizedSecret> set = ImmutableSet.builder();
    SecretContentDAO secretContentDAO = createSecretContentDAO();

    for (SecretSeries series : getSecretSeriesFor(group)) {
      for (SecretContent content : secretContentDAO.getSecretContentsBySecretId(series.getId())) {
        SecretSeriesAndContent seriesAndContent = SecretSeriesAndContent.of(series, content);
        set.add(SanitizedSecret.fromSecretSeriesAndContent(seriesAndContent));
      }
    }

    return set.build();
  }

  @SqlQuery("SELECT groups.id, groups.name, groups.description, groups.createdAt, groups.createdBy, " +
            "groups.updatedAt, groups.updatedBy FROM groups " +
            "JOIN accessGrants ON groups.id = accessGrants.groupId " +
            "JOIN secrets ON accessGrants.secretId = secrets.id " +
            "WHERE secrets.name = :name")
  public abstract Set<Group> getGroupsFor(@BindSecret Secret secret);

  @SqlQuery("SELECT groups.id, groups.name, groups.description, groups.createdAt, groups.createdBy, " +
            "groups.updatedAt, groups.updatedBy FROM groups " +
            "JOIN memberships ON groups.id = memberships.groupId " +
            "JOIN clients ON clients.id = memberships.clientId " +
            "WHERE clients.name = :name")
  public abstract Set<Group> getGroupsFor(@BindBean Client client);

  @SqlQuery("SELECT clients.id, clients.name, clients.description, clients.createdAt, clients.createdBy, " +
            "clients.updatedAt, clients.updatedBy, clients.enabled, clients.automationAllowed FROM clients " +
            "JOIN memberships ON clients.id = memberships.clientId " +
            "JOIN groups ON groups.id = memberships.groupId " +
            "WHERE groups.name = :name")
  public abstract Set<Client> getClientsFor(@BindBean Group group);

  @Transaction
  public ImmutableSet<SanitizedSecret> getSanitizedSecretsFor(Client client) {
    checkNotNull(client);
    ImmutableSet.Builder<SanitizedSecret> sanitizedSet = ImmutableSet.builder();
    SecretContentDAO secretContentDAO = createSecretContentDAO();

    for (SecretSeries series : getSecretSeriesFor(client)) {
      for (SecretContent content : secretContentDAO.getSecretContentsBySecretId(series.getId())) {
        SecretSeriesAndContent seriesAndContent = SecretSeriesAndContent.of(series, content);
        sanitizedSet.add(SanitizedSecret.fromSecretSeriesAndContent(seriesAndContent));
      }
    }
    return sanitizedSet.build();
  }

  @SqlQuery("SELECT clients.id, clients.name, clients.description, clients.createdAt, clients.createdBy, " +
            "clients.updatedAt, clients.updatedBy, clients.enabled, clients.automationAllowed FROM clients " +
            "JOIN memberships ON clients.id = memberships.clientId " +
            "JOIN accessGrants ON memberships.groupId = accessGrants.groupId " +
            "JOIN secrets ON secrets.id = accessGrants.secretId " +
            "WHERE secrets.name = :name")
  public abstract Set<Client> getClientsFor(@BindSecret Secret secret);

  @Transaction
  public Optional<SanitizedSecret> getSanitizedSecretFor(Client client, String name, String version) {
    checkNotNull(client);
    checkArgument(!name.isEmpty());
    checkNotNull(version);

    Optional<SecretSeries> secretSeries = getSecretSeriesFor(client, name);
    if (!secretSeries.isPresent()) {
      return Optional.empty();
    }

    Optional<SecretContent> secretContent =
        createSecretContentDAO().getSecretContentBySecretIdAndVersion(
            secretSeries.get().getId(), version);
    if (!secretContent.isPresent()) {
      return Optional.empty();
    }

    SecretSeriesAndContent seriesAndContent = SecretSeriesAndContent.of(
        secretSeries.get(), secretContent.get());
    return Optional.of(SanitizedSecret.fromSecretSeriesAndContent(seriesAndContent));
  }

  @SqlUpdate("INSERT INTO accessGrants (secretId, groupId) VALUES (:secretId, :groupId)")
  protected abstract void allowAccess(@Bind("secretId") long secretId, @Bind("groupId") long groupId);

  @SqlUpdate("DELETE FROM accessGrants WHERE secretId = :secretId AND groupId = :groupId")
  protected abstract void revokeAccess(@Bind("secretId") long secretId, @Bind("groupId") long groupId);

  @SqlUpdate("INSERT INTO memberships (groupId, clientId) VALUES (:groupId, :clientId)")
  protected abstract void enrollClient(@Bind("clientId") long clientId, @Bind("groupId") long groupId);

  @SqlUpdate("DELETE FROM memberships WHERE clientId = :clientId AND groupId = :groupId")
  protected abstract void evictClient(@Bind("clientId") long clientId, @Bind("groupId") long groupId);

  @SqlQuery("SELECT secrets.id, secrets.name, secrets.description, " +
      "secrets.createdAt, secrets.createdBy, secrets.updatedAt, secrets.updatedBy, secrets.type, " +
      "secrets.options " +
      "FROM secrets " +
      "JOIN accessGrants ON secrets.id = accessGrants.secretId " +
      "JOIN groups ON groups.id = accessGrants.groupId " +
      "WHERE groups.name = :name")
  protected abstract ImmutableSet<SecretSeries> getSecretSeriesFor(@BindBean Group group);

  @SqlQuery("SELECT secrets.id, secrets.name, secrets.description, " +
      "secrets.createdAt, secrets.createdBy, secrets.updatedAt, secrets.updatedBy, secrets.type, " +
      "secrets.options " +
      "FROM secrets " +
      "JOIN accessGrants ON secrets.id = accessGrants.secretId " +
      "JOIN memberships ON accessGrants.groupId = memberships.groupId " +
      "JOIN clients ON clients.id = memberships.clientId " +
      "WHERE clients.name = :name")
  protected abstract ImmutableSet<SecretSeries> getSecretSeriesFor(@BindBean Client client);

  /**
   * @param client client to access secrets
   * @param name name of SecretSeries
   * @return Optional.absent() when secret unauthorized or not found.
   * The query doesn't distinguish between these cases. If result absent, a followup call on clients
   * table should be used to determine the exception.
   */
  @SingleValueResult(SecretSeries.class)
  @SqlQuery("SELECT secrets.id, secrets.name, secrets.description, " +
            "secrets.createdAt, secrets.createdBy, secrets.updatedAt, secrets.updatedBy, " +
            "secrets.type, secrets.options " +
            "FROM secrets JOIN secrets_content on secrets.id = secrets_content.secretId " +
            "JOIN accessGrants ON secrets.id = accessGrants.secretId " +
            "JOIN memberships ON accessGrants.groupId = memberships.groupId " +
            "JOIN clients ON clients.id = memberships.clientId " +
            "WHERE secrets.name = :name AND clients.name = :c.name")
  protected abstract Optional<SecretSeries> getSecretSeriesFor(@BindBean("c") Client client, @Bind("name") String name);
}
