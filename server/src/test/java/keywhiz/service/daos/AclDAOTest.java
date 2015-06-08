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
import com.google.common.collect.Iterables;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import keywhiz.TestDBRule;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import keywhiz.api.model.SecretSeries;
import keywhiz.api.model.VersionGenerator;
import keywhiz.service.config.Readonly;
import keywhiz.service.daos.ClientDAO.ClientDAOFactory;
import keywhiz.service.daos.GroupDAO.GroupDAOFactory;
import keywhiz.service.daos.SecretDAO.SecretDAOFactory;
import keywhiz.service.daos.SecretSeriesDAO.SecretSeriesDAOFactory;
import org.jooq.DSLContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static keywhiz.jooq.tables.Accessgrants.ACCESSGRANTS;
import static keywhiz.jooq.tables.Clients.CLIENTS;
import static keywhiz.jooq.tables.Groups.GROUPS;
import static keywhiz.jooq.tables.Memberships.MEMBERSHIPS;
import static keywhiz.jooq.tables.Secrets.SECRETS;
import static keywhiz.jooq.tables.SecretsContent.SECRETS_CONTENT;
import static org.assertj.core.api.Assertions.assertThat;

public class AclDAOTest {
  @Rule public final TestDBRule testDBRule = new TestDBRule();

  @Bind @SuppressWarnings("unused") ObjectMapper objectMapper = new ObjectMapper();
  @Bind DSLContext jooqContext;
  @Bind @Readonly DSLContext jooqReadonlyContext;

  @Inject SecretSeriesDAOFactory secretSeriesDAOFactory;
  @Inject SecretDAOFactory secretDAOFactory;
  @Inject ClientDAOFactory clientDAOFactory;
  @Inject GroupDAOFactory groupDAOFactory;
  @Inject AclDAO.AclDAOFactory aclDAOFactory;

  Client client1, client2;
  Group group1, group2, group3;
  Secret secret1, secret2;
  ClientDAO clientDAO;
  GroupDAO groupDAO;
  SecretSeriesDAO secretSeriesDAO;
  AclDAO aclDAO;

  @Before public void setUp() {
    jooqContext = jooqReadonlyContext = testDBRule.jooqContext();
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);

    secretSeriesDAO = secretSeriesDAOFactory.readwrite();
    clientDAO = clientDAOFactory.readwrite();
    groupDAO = groupDAOFactory.readwrite();
    aclDAO = aclDAOFactory.readwrite();

    jooqContext.delete(CLIENTS).execute();
    jooqContext.delete(GROUPS).execute();
    jooqContext.delete(SECRETS).execute();
    jooqContext.delete(SECRETS_CONTENT).execute();

    long id = clientDAO.createClient("client1", "creator", Optional.empty());
    client1 = clientDAO.getClientById(id).get();

    id = clientDAO.createClient("client2", "creator", Optional.empty());
    client2 = clientDAO.getClientById(id).get();

    id = groupDAO.createGroup("group1", "creator", Optional.empty());
    group1 = groupDAO.getGroupById(id).get();

    id = groupDAO.createGroup("group2", "creator", Optional.empty());
    group2 = groupDAO.getGroupById(id).get();

    id = groupDAO.createGroup("group3", "creator", Optional.empty());
    group3 = groupDAO.getGroupById(id).get();

    SecretFixtures secretFixtures = SecretFixtures.using(secretDAOFactory.readwrite());
    secret1 = secretFixtures.createSecret("secret1", "c2VjcmV0MQ==", VersionGenerator.now().toHex());
    secret2 = secretFixtures.createSecret("secret2", "c2VjcmV0Mg==");
  }

  @Test public void allowsAccess() {
    int before = accessGrantsTableSize();
    aclDAO.allowAccess(jooqContext.configuration(), secret2.getId(), group1.getId());
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
    assertThat(Iterables.getOnlyElement(secrets)).isEqualToIgnoringGivenFields(sanitizedSecret2, "id");

    aclDAO.allowAccess(jooqContext.configuration(), secret1.getId(), group1.getId());
    secrets = aclDAO.getSanitizedSecretsFor(group1);
    assertThat(secrets).hasSize(2).doesNotHaveDuplicates();

    for (SanitizedSecret secret : secrets) {
      if (secret.name().equals(secret1.getName())) {
        assertThat(secret).isEqualToIgnoringGivenFields(sanitizedSecret1, "id");
      } else {
        assertThat(secret).isEqualToIgnoringGivenFields(sanitizedSecret2, "id");
      }
    }
  }

  @Test public void getGroupsForSecret() {
    aclDAO.allowAccess(jooqContext.configuration(), secret1.getId(), group2.getId());
    assertThat(aclDAO.getGroupsFor(secret1)).containsOnly(group2);

    aclDAO.allowAccess(jooqContext.configuration(), secret1.getId(), group1.getId());
    assertThat(aclDAO.getGroupsFor(secret1)).containsOnly(group1, group2);
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
        .isEqualToIgnoringGivenFields(SanitizedSecret.fromSecret(secret2), "id");

    aclDAO.allowAccess(jooqContext.configuration(), secret1.getId(), group2.getId());
    secrets = aclDAO.getSanitizedSecretsFor(client2);
    assertThat(secrets).hasSize(2).doesNotHaveDuplicates();

    for (SanitizedSecret secret : secrets) {
      if (secret.name().equals(secret1.getName())) {
        assertThat(secret).isEqualToIgnoringGivenFields(SanitizedSecret.fromSecret(secret1), "id");
      } else {
        assertThat(secret).isEqualToIgnoringGivenFields(SanitizedSecret.fromSecret(secret2), "id");
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
    assertThat(aclDAO.getSecretSeriesFor(jooqContext.configuration(), client1, secret1.getName())).isEmpty();
  }

  @Test public void getSecretSeriesForWhenMissing() throws Exception {
    assertThat(aclDAO.getSecretSeriesFor(jooqContext.configuration(), client1, "non-existent")).isEmpty();
  }

  @Test public void getSecretSeriesFor() throws Exception {
    SecretSeries secretSeries1 = secretSeriesDAO.getSecretSeriesById(secret1.getId()).get();

    aclDAO.enrollClient(jooqContext.configuration(), client2.getId(), group1.getId());
    aclDAO.enrollClient(jooqContext.configuration(), client2.getId(), group3.getId());
    aclDAO.allowAccess(jooqContext.configuration(), secret1.getId(), group1.getId());

    SecretSeries secretSeries = aclDAO.getSecretSeriesFor(jooqContext.configuration(), client2, secret1.getName())
        .orElseThrow(RuntimeException::new);
    assertThat(secretSeries).isEqualToIgnoringGivenFields(secretSeries1, "id");

    aclDAO.evictClient(jooqContext.configuration(), client2.getId(), group1.getId());
    assertThat(aclDAO.getSecretSeriesFor(jooqContext.configuration(), client2, secret1.getName())).isEmpty();

    aclDAO.allowAccess(jooqContext.configuration(), secret1.getId(), group3.getId());

    secretSeries = aclDAO.getSecretSeriesFor(jooqContext.configuration(), client2, secret1.getName())
        .orElseThrow(RuntimeException::new);
    assertThat(secretSeries).isEqualToIgnoringGivenFields(secretSeries1, "id");
  }

  @Test public void getSecretForWhenUnauthorized() throws Exception {
    assertThat(aclDAO.getSanitizedSecretFor(client1, secret1.getName(), secret1.getVersion())).isEmpty();
  }

  @Test public void getSecretForWhenMissing() throws Exception {
    assertThat(aclDAO.getSanitizedSecretFor(client1, "non-existent", "")).isEmpty();
  }

  @Test public void getSecretFor() throws Exception {
    SanitizedSecret sanitizedSecret1 = SanitizedSecret.fromSecret(secret1);

    aclDAO.enrollClient(jooqContext.configuration(), client2.getId(), group1.getId());
    aclDAO.enrollClient(jooqContext.configuration(), client2.getId(), group3.getId());

    aclDAO.allowAccess(jooqContext.configuration(), secret1.getId(), group1.getId());

    SanitizedSecret secret = aclDAO.getSanitizedSecretFor(client2, sanitizedSecret1.name(), sanitizedSecret1.version())
        .orElseThrow(RuntimeException::new);
    assertThat(secret).isEqualToIgnoringGivenFields(sanitizedSecret1, "id");

    aclDAO.evictClient(jooqContext.configuration(), client2.getId(), group1.getId());
    Optional<SanitizedSecret> missingSecret =
        aclDAO.getSanitizedSecretFor(client2, sanitizedSecret1.name(), sanitizedSecret1.version());
    assertThat(missingSecret).isEmpty();

    aclDAO.allowAccess(jooqContext.configuration(), sanitizedSecret1.id(), group3.getId());

    secret = aclDAO.getSanitizedSecretFor(client2, sanitizedSecret1.name(), sanitizedSecret1.version())
        .orElseThrow(RuntimeException::new);
    assertThat(secret).isEqualToIgnoringGivenFields(sanitizedSecret1, "id");
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

  private int accessGrantsTableSize() {
    return jooqContext.fetchCount(ACCESSGRANTS);
  }

  private int membershipsTableSize() {
    return jooqContext.fetchCount(MEMBERSHIPS);
  }
}
