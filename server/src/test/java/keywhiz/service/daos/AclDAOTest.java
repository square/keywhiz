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
import com.google.common.collect.Iterables;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.inject.Inject;
import keywhiz.KeywhizConfig;
import keywhiz.KeywhizTestRunner;
import keywhiz.api.ApiDate;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import keywhiz.api.model.SecretSeries;
import keywhiz.service.daos.ClientDAO.ClientDAOFactory;
import keywhiz.service.daos.GroupDAO.GroupDAOFactory;
import keywhiz.service.daos.SecretDAO.SecretDAOFactory;
import keywhiz.service.daos.SecretSeriesDAO.SecretSeriesDAOFactory;
import org.jooq.DSLContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static keywhiz.jooq.tables.Accessgrants.ACCESSGRANTS;
import static keywhiz.jooq.tables.Clients.CLIENTS;
import static keywhiz.jooq.tables.Memberships.MEMBERSHIPS;
import static keywhiz.jooq.tables.Secrets.SECRETS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assert.assertEquals;

@RunWith(KeywhizTestRunner.class)
public class AclDAOTest {
  @Inject DSLContext jooqContext;

  @Inject SecretSeriesDAOFactory secretSeriesDAOFactory;
  @Inject SecretDAOFactory secretDAOFactory;
  @Inject ClientDAOFactory clientDAOFactory;
  @Inject GroupDAOFactory groupDAOFactory;
  @Inject AclDAO.AclDAOFactory aclDAOFactory;
  @Inject KeywhizConfig keywhizConfig;

  Client client1, client2;
  Group group1, group2, group3;
  Secret secret1, secret2;
  ClientDAO clientDAO;
  GroupDAO groupDAO;
  SecretSeriesDAO secretSeriesDAO;
  AclDAO aclDAO;

  @Before public void setUp() {
    secretSeriesDAO = secretSeriesDAOFactory.readwrite();
    clientDAO = clientDAOFactory.readwrite();
    groupDAO = groupDAOFactory.readwrite();
    aclDAO = aclDAOFactory.readwrite();

    long id = clientDAO.createClient("client1", "creator", "", null);
    client1 = clientDAO.getClientById(id).get();

    id = clientDAO.createClient("client2", "creator", "", null);
    client2 = clientDAO.getClientById(id).get();

    id = groupDAO.createGroup("group1", "creator", "", ImmutableMap.of());
    group1 = groupDAO.getGroupById(id).get();

    id = groupDAO.createGroup("group2", "creator", "", ImmutableMap.of());
    group2 = groupDAO.getGroupById(id).get();

    id = groupDAO.createGroup("group3", "creator", "", ImmutableMap.of());
    group3 = groupDAO.getGroupById(id).get();

    SecretFixtures secretFixtures = SecretFixtures.using(secretDAOFactory.readwrite());
    secret1 = secretFixtures.createSecret("secret1", "c2VjcmV0MQ==", "group1");
    secret2 = secretFixtures.createSecret("secret2", "c2VjcmV0Mg==", "group1");
  }

  @Test public void listingRenamedSecretDoesNotFailHmacCheck() {
    aclDAO.enrollClient(jooqContext.configuration(), client1.getId(), group1.getId());
    aclDAO.allowAccess(jooqContext.configuration(), secret1.getId(), group1.getId());

    String newName = UUID.randomUUID().toString();
    secretSeriesDAO.renameSecretSeriesById(
        secret1.getId(),
        newName,
        client1.getName(),
        ApiDate.now().toEpochSecond());

    KeywhizConfig.RowHmacCheck originalHmacCheck = keywhizConfig.getRowHmacCheck();
    try {
      keywhizConfig.setRowHmacCheck(KeywhizConfig.RowHmacCheck.ENFORCED);
      aclDAO.getSanitizedSecretsFor(client1);
    } finally {
      keywhizConfig.setRowHmacCheck(originalHmacCheck);
    }
  }

  @Test public void allowsAccess() {
    int before = accessGrantsTableSize();
    aclDAO.allowAccess(jooqContext.configuration(), secret2.getId(), group1.getId());
    assertThat(accessGrantsTableSize()).isEqualTo(before + 1);
  }

  @Test public void allowsAccessOnlyOnce() {
    int before = accessGrantsTableSize();
    aclDAO.allowAccess(jooqContext.configuration(), secret2.getId(), group1.getId());
    aclDAO.allowAccess(jooqContext.configuration(), secret2.getId(), group1.getId()); // no effect
    assertThat(accessGrantsTableSize()).isEqualTo(before + 1);
  }

  @Test public void revokesAccess() {
    aclDAO.allowAccess(jooqContext.configuration(), secret2.getId(), group1.getId());
    int before = accessGrantsTableSize();

    aclDAO.revokeAccess(jooqContext.configuration(), secret2.getId(), group2.getId());
    assertThat(accessGrantsTableSize()).isEqualTo(before);

    aclDAO.revokeAccess(jooqContext.configuration(), secret2.getId(), group1.getId());
    assertThat(accessGrantsTableSize()).isEqualTo(before - 1);
  }

  @Test public void accessGrantsHasReferentialIntegrity() {
    aclDAO.allowAccess(jooqContext.configuration(), secret1.getId(), group1.getId());
    aclDAO.allowAccess(jooqContext.configuration(), secret2.getId(), group2.getId());
    int before = accessGrantsTableSize();

    groupDAO.deleteGroup(group1);
    assertThat(accessGrantsTableSize()).isEqualTo(before - 1);

    secretSeriesDAO.deleteSecretSeriesById(secret2.getId());
    assertThat(accessGrantsTableSize()).isEqualTo(before - 2);
  }

  @Test public void enrollsClients() {
    int before = membershipsTableSize();
    aclDAO.enrollClient(jooqContext.configuration(), client1.getId(), group2.getId());
    assertThat(membershipsTableSize()).isEqualTo(before + 1);
  }

  @Test public void enrollsClientsOnlyOnce() {
    int before = membershipsTableSize();
    aclDAO.enrollClient(jooqContext.configuration(), client1.getId(), group2.getId());
    aclDAO.enrollClient(jooqContext.configuration(), client1.getId(), group2.getId());
    assertThat(membershipsTableSize()).isEqualTo(before + 1);
  }

  @Test public void evictsClient() {
    aclDAO.enrollClient(jooqContext.configuration(), client1.getId(), group2.getId());
    int before = membershipsTableSize();

    aclDAO.evictClient(jooqContext.configuration(), client2.getId(), group2.getId());
    assertThat(membershipsTableSize()).isEqualTo(before);

    aclDAO.evictClient(jooqContext.configuration(), client1.getId(), group2.getId());
    assertThat(membershipsTableSize()).isEqualTo(before - 1);
  }

  @Test public void membershipsHasReferentialIntegrity() {
    aclDAO.enrollClient(jooqContext.configuration(), client1.getId(), group1.getId());
    aclDAO.enrollClient(jooqContext.configuration(), client2.getId(), group2.getId());
    int before = membershipsTableSize();

    groupDAO.deleteGroup(group1);
    assertThat(membershipsTableSize()).isEqualTo(before - 1);

    clientDAO.deleteClient(client2);
    assertThat(membershipsTableSize()).isEqualTo(before - 2);
  }

  @Test public void getsSanitizedSecretsForGroup() {
    SanitizedSecret sanitizedSecret1 = SanitizedSecret.fromSecret(secret1);
    SanitizedSecret sanitizedSecret2 = SanitizedSecret.fromSecret(secret2);

    aclDAO.allowAccess(jooqContext.configuration(), secret2.getId(), group1.getId());
    Set<SanitizedSecret> secrets = aclDAO.getSanitizedSecretsFor(group1);
    assertThat(Iterables.getOnlyElement(secrets)).isEqualToIgnoringGivenFields(sanitizedSecret2, "id", "version");

    aclDAO.allowAccess(jooqContext.configuration(), secret1.getId(), group1.getId());
    secrets = aclDAO.getSanitizedSecretsFor(group1);
    assertThat(secrets).hasSize(2).doesNotHaveDuplicates();

    for (SanitizedSecret secret : secrets) {
      if (secret.name().equals(secret1.getName())) {
        assertThat(secret).isEqualToIgnoringGivenFields(sanitizedSecret1, "id", "version");
      } else {
        assertThat(secret).isEqualToIgnoringGivenFields(sanitizedSecret2, "id", "version");
      }
    }
  }

  @Test public void getOwnedSanitizedSecretsForGroupWithSecrets() {
    SanitizedSecret sanitizedSecret1 = SanitizedSecret.fromSecret(secret1);
    SanitizedSecret sanitizedSecret2 = SanitizedSecret.fromSecret(secret2);

    Set<SanitizedSecret> secrets = aclDAO.getSanitizedSecretsFor(group1, true);
    assertEquals(Set.of(sanitizedSecret1, sanitizedSecret2), secrets);
  }

  @Test public void getOwnedSanitizedSecretsForGroupWithoutSecrets() {
    Set<SanitizedSecret> secrets = aclDAO.getSanitizedSecretsFor(group2, true);
    assertThat(secrets).isEmpty();
  }

  @Test public void getGroupsForSecret() {
    aclDAO.allowAccess(jooqContext.configuration(), secret1.getId(), group2.getId());
    assertThat(aclDAO.getGroupsFor(secret1)).containsOnly(group2);

    aclDAO.allowAccess(jooqContext.configuration(), secret1.getId(), group1.getId());
    assertThat(aclDAO.getGroupsFor(secret1)).containsOnly(group1, group2);
  }

  @Test public void getGroupsForSecrets() {
    aclDAO.allowAccess(jooqContext.configuration(), secret1.getId(), group2.getId());
    aclDAO.allowAccess(jooqContext.configuration(), secret1.getId(), group1.getId());
    aclDAO.allowAccess(jooqContext.configuration(), secret2.getId(), group2.getId());
    aclDAO.allowAccess(jooqContext.configuration(), secret2.getId(), group1.getId());
    Map<Long, List<Group>> groupsForSecrets =
        aclDAO.getGroupsForSecrets(ImmutableSet.of(secret1.getId(), secret2.getId()));

    assertThat(groupsForSecrets.size()).isEqualTo(2);
    assertThat(groupsForSecrets.get(secret1.getId())).containsOnly(group1, group2);
    assertThat(groupsForSecrets.get(secret2.getId())).containsOnly(group1, group2);
  }

  @Test public void getGroupsForClient() {
    aclDAO.enrollClient(jooqContext.configuration(), client1.getId(), group2.getId());
    assertThat(aclDAO.getGroupsFor(client1)).containsOnly(group2);

    aclDAO.enrollClient(jooqContext.configuration(), client1.getId(), group1.getId());
    assertThat(aclDAO.getGroupsFor(client1)).containsOnly(group1, group2);
  }

  @Test public void getClientsForGroup() {
    aclDAO.enrollClient(jooqContext.configuration(), client2.getId(), group1.getId());
    assertThat(aclDAO.getClientsFor(group1)).containsOnly(client2);

    aclDAO.enrollClient(jooqContext.configuration(), client1.getId(), group1.getId());
    assertThat(aclDAO.getClientsFor(group1)).containsOnly(client1, client2);
  }

  @Test public void getSanitizedSecretsForClient() {
    assertThat(aclDAO.getSanitizedSecretsFor(client2)).isEmpty();

    aclDAO.enrollClient(jooqContext.configuration(), client2.getId(), group2.getId());
    aclDAO.allowAccess(jooqContext.configuration(), secret2.getId(), group2.getId());
    Set<SanitizedSecret> secrets = aclDAO.getSanitizedSecretsFor(client2);
    assertThat(Iterables.getOnlyElement(secrets))
        .isEqualToIgnoringGivenFields(SanitizedSecret.fromSecret(secret2), "id", "version");

    aclDAO.allowAccess(jooqContext.configuration(), secret1.getId(), group2.getId());
    secrets = aclDAO.getSanitizedSecretsFor(client2);
    assertThat(secrets).hasSize(2).doesNotHaveDuplicates();

    for (SanitizedSecret secret : secrets) {
      if (secret.name().equals(secret1.getName())) {
        assertThat(secret).isEqualToIgnoringGivenFields(SanitizedSecret.fromSecret(secret1), "id", "version");
      } else {
        assertThat(secret).isEqualToIgnoringGivenFields(SanitizedSecret.fromSecret(secret2), "id", "version");
      }
    }

    aclDAO.evictClient(jooqContext.configuration(), client2.getId(), group2.getId());
    assertThat(aclDAO.getSanitizedSecretsFor(client2)).isEmpty();
  }

  @Test public void getClientsForSecret() {
    assertThat(aclDAO.getClientsFor(secret2)).isEmpty();

    aclDAO.allowAccess(jooqContext.configuration(), secret2.getId(), group2.getId());
    aclDAO.enrollClient(jooqContext.configuration(), client2.getId(), group2.getId());
    assertThat(aclDAO.getClientsFor(secret2)).containsOnly(client2);

    aclDAO.enrollClient(jooqContext.configuration(), client1.getId(), group2.getId());
    assertThat(aclDAO.getClientsFor(secret2)).containsOnly(client1, client2);

    aclDAO.revokeAccess(jooqContext.configuration(), secret2.getId(), group2.getId());
    assertThat(aclDAO.getClientsFor(secret2)).isEmpty();
  }

  @Test public void getSecretSeriesForWhenUnauthorized() throws Exception {
    assertThat(aclDAO.getSecretSeriesFor(jooqContext.configuration(), client1, secret1.getName()))
        .isEmpty();
  }

  @Test public void getSecretSeriesForWhenMissing() throws Exception {
    assertThat(aclDAO.getSecretSeriesFor(jooqContext.configuration(), client1, "non-existent"))
        .isEmpty();
  }

  @Test public void getSecretSeriesFor() throws Exception {
    SecretSeries secretSeries1 = secretSeriesDAO.getSecretSeriesById(secret1.getId()).get();

    aclDAO.enrollClient(jooqContext.configuration(), client2.getId(), group1.getId());
    aclDAO.enrollClient(jooqContext.configuration(), client2.getId(), group3.getId());
    aclDAO.enrollClient(jooqContext.configuration(), client2.getId(), group2.getId());
    aclDAO.allowAccess(jooqContext.configuration(), secret1.getId(), group1.getId());
    aclDAO.allowAccess(jooqContext.configuration(), secret1.getId(), group2.getId());

    SecretSeries secretSeries = aclDAO.getSecretSeriesFor(jooqContext.configuration(), client2, secret1.getName())
        .orElseThrow(RuntimeException::new);
    assertThat(secretSeries).isEqualToIgnoringGivenFields(secretSeries1, "id");

    aclDAO.evictClient(jooqContext.configuration(), client2.getId(), group1.getId());
    aclDAO.evictClient(jooqContext.configuration(), client2.getId(), group2.getId());
    assertThat(aclDAO.getSecretSeriesFor(jooqContext.configuration(), client2, secret1.getName()))
        .isEmpty();

    aclDAO.allowAccess(jooqContext.configuration(), secret1.getId(), group3.getId());

    secretSeries = aclDAO.getSecretSeriesFor(jooqContext.configuration(), client2, secret1.getName())
        .orElseThrow(RuntimeException::new);
    assertThat(secretSeries).isEqualToIgnoringGivenFields(secretSeries1, "id");
  }

  @Test public void getSecretForWhenUnauthorized() throws Exception {
    assertThat(aclDAO.getSanitizedSecretFor(client1, secret1.getName()))
        .isEmpty();
  }

  @Test public void getSecretForWhenMissing() throws Exception {
    assertThat(aclDAO.getSanitizedSecretFor(client1, "non-existent")).isEmpty();
  }

  @Test public void getSecretFor() throws Exception {
    SanitizedSecret sanitizedSecret1 = SanitizedSecret.fromSecret(secret1);

    aclDAO.enrollClient(jooqContext.configuration(), client2.getId(), group1.getId());
    aclDAO.enrollClient(jooqContext.configuration(), client2.getId(), group3.getId());

    aclDAO.allowAccess(jooqContext.configuration(), secret1.getId(), group1.getId());

    SanitizedSecret secret = aclDAO.getSanitizedSecretFor(client2, sanitizedSecret1.name())
        .orElseThrow(RuntimeException::new);
    assertThat(secret).isEqualToIgnoringGivenFields(sanitizedSecret1, "id", "version");

    aclDAO.evictClient(jooqContext.configuration(), client2.getId(), group1.getId());
    Optional<SanitizedSecret> missingSecret =
        aclDAO.getSanitizedSecretFor(client2, sanitizedSecret1.name());
    assertThat(missingSecret).isEmpty();

    aclDAO.allowAccess(jooqContext.configuration(), sanitizedSecret1.id(), group3.getId());

    secret = aclDAO.getSanitizedSecretFor(client2, sanitizedSecret1.name())
        .orElseThrow(RuntimeException::new);
    assertThat(secret).isEqualToIgnoringGivenFields(sanitizedSecret1, "id", "version");
  }

  @Test public void getSecretsReturnsDistinct() {
    // client1 has two paths to secret1
    aclDAO.enrollClient(jooqContext.configuration(), client1.getId(), group1.getId());
    aclDAO.enrollClient(jooqContext.configuration(), client1.getId(), group2.getId());
    aclDAO.allowAccess(jooqContext.configuration(), secret1.getId(), group1.getId());
    aclDAO.allowAccess(jooqContext.configuration(), secret1.getId(), group2.getId());

    Set<SanitizedSecret> secret = aclDAO.getSanitizedSecretsFor(client1);
    assertThat(secret).hasSize(1);
  }

  @Test public void modifySecretGroup() {
    aclDAO.enrollClient(jooqContext.configuration(), client1.getId(), group1.getId());
    aclDAO.enrollClient(jooqContext.configuration(), client2.getId(), group2.getId());
    aclDAO.allowAccess(jooqContext.configuration(), secret1.getId(), group1.getId());

    jooqContext.update(ACCESSGRANTS)
        .set(ACCESSGRANTS.GROUPID, group2.getId())
        .where(ACCESSGRANTS.GROUPID.eq(group1.getId()))
        .execute();

    String errorMessage = String.format(
        "Access Grants HMAC verification failed for groupId: %d in secretId: %d",
        group2.getId(), secret1.getId());
    assertThatExceptionOfType(AssertionError.class).isThrownBy(() -> {
      aclDAO.getSanitizedSecretsFor(client2);
    }).withMessage(errorMessage);

    assertThatExceptionOfType(AssertionError.class).isThrownBy(() -> {
      aclDAO.getSanitizedSecretFor(client2, secret1.getName());
    }).withMessage(errorMessage);
  }

  @Test public void modifyClientGroup() {
    aclDAO.enrollClient(jooqContext.configuration(), client1.getId(), group1.getId());
    aclDAO.enrollClient(jooqContext.configuration(), client2.getId(), group2.getId());
    aclDAO.allowAccess(jooqContext.configuration(), secret1.getId(), group1.getId());

    jooqContext.update(MEMBERSHIPS)
        .set(MEMBERSHIPS.CLIENTID, client2.getId())
        .where(MEMBERSHIPS.GROUPID.eq(group1.getId()))
        .execute();

    String errorMessage = String.format(
        "Memberships HMAC verification failed for clientId: %d in groupId: %d",
        client2.getId(), group1.getId());
    assertThatExceptionOfType(AssertionError.class).isThrownBy(() -> {
      aclDAO.getSanitizedSecretsFor(client2);
    }).withMessage(errorMessage);

    assertThatExceptionOfType(AssertionError.class).isThrownBy(() -> {
      aclDAO.getSanitizedSecretFor(client2, secret1.getName());
    }).withMessage(errorMessage);
  }

  @Test public void modifyClientId() {
    aclDAO.enrollClient(jooqContext.configuration(), client1.getId(), group1.getId());
    aclDAO.enrollClient(jooqContext.configuration(), client2.getId(), group2.getId());
    aclDAO.allowAccess(jooqContext.configuration(), secret1.getId(), group1.getId());


    jooqContext.update(CLIENTS)
        .set(CLIENTS.ID, 1337L)
        .where(CLIENTS.ID.eq(client1.getId()))
        .execute();

    jooqContext.update(CLIENTS)
        .set(CLIENTS.ID, client1.getId())
        .where(CLIENTS.ID.eq(client2.getId()))
        .execute();

    Client maliciousClient = clientDAO.getClientByName(client2.getName()).orElseThrow();

    String errorMessage = String.format(
        "Client HMAC verification failed for client: %s", client2.getName());
    assertThatExceptionOfType(AssertionError.class).isThrownBy(() -> {
      aclDAO.getSanitizedSecretsFor(maliciousClient);
    }).withMessage(errorMessage);

    assertThatExceptionOfType(AssertionError.class).isThrownBy(() -> {
      aclDAO.getSanitizedSecretFor(maliciousClient, secret1.getName());
    }).withMessage(errorMessage);
  }

  @Test public void modifySecretId() {
    aclDAO.enrollClient(jooqContext.configuration(), client1.getId(), group1.getId());
    aclDAO.enrollClient(jooqContext.configuration(), client2.getId(), group2.getId());
    aclDAO.allowAccess(jooqContext.configuration(), secret1.getId(), group1.getId());
    aclDAO.allowAccess(jooqContext.configuration(), secret2.getId(), group2.getId());

    jooqContext.update(SECRETS)
        .set(SECRETS.ID, 1337L)
        .where(SECRETS.ID.eq(secret2.getId()))
        .execute();

    jooqContext.update(SECRETS)
        .set(SECRETS.ID, secret2.getId())
        .where(SECRETS.ID.eq(secret1.getId()))
        .execute();

    String errorMessage = String.format(
        "Secret HMAC verification failed for secret: %s", secret1.getName());
    assertThatExceptionOfType(AssertionError.class).isThrownBy(() -> {
      aclDAO.getSanitizedSecretsFor(client2);
    }).withMessage(errorMessage);

    assertThatExceptionOfType(AssertionError.class).isThrownBy(() -> {
      aclDAO.getSanitizedSecretFor(client2, secret1.getName());
    }).withMessage(errorMessage);
  }

  private int accessGrantsTableSize() {
    return jooqContext.fetchCount(ACCESSGRANTS);
  }

  private int membershipsTableSize() {
    return jooqContext.fetchCount(MEMBERSHIPS);
  }

  @Test public void getBatchSanitizedSecretsSingle() {
    aclDAO.enrollClient(jooqContext.configuration(), client1.getId(), group1.getId());
    aclDAO.allowAccess(jooqContext.configuration(), secret1.getId(), group1.getId());

    List<SanitizedSecret> secrets = aclDAO.getBatchSanitizedSecretsFor(client1, List.of(secret1.getName()));
    assertThat(secrets).hasSize(1);
    assertThat(secrets).contains(SanitizedSecret.fromSecret(secret1));
  }

  @Test public void getBatchSanitizedSecretsMultiple() {
    aclDAO.enrollClient(jooqContext.configuration(), client1.getId(), group1.getId());
    aclDAO.allowAccess(jooqContext.configuration(), secret1.getId(), group1.getId());
    aclDAO.allowAccess(jooqContext.configuration(), secret2.getId(), group1.getId());

    List<SanitizedSecret> secrets = aclDAO.getBatchSanitizedSecretsFor(client1, List.of(secret1.getName(), secret2.getName()));
    assertThat(secrets).hasSize(2);
    assertThat(secrets).contains(SanitizedSecret.fromSecret(secret1));
    assertThat(secrets).contains(SanitizedSecret.fromSecret(secret2));
  }
}
