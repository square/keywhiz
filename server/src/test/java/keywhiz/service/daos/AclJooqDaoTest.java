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

import com.google.common.collect.Iterables;
import java.util.Optional;
import java.util.Set;
import keywhiz.TestDBRule;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import keywhiz.api.model.SecretSeries;
import keywhiz.api.model.VersionGenerator;
import org.jooq.DSLContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.skife.jdbi.v2.DBI;

import static keywhiz.jooq.tables.Accessgrants.ACCESSGRANTS;
import static keywhiz.jooq.tables.Clients.CLIENTS;
import static keywhiz.jooq.tables.Groups.GROUPS;
import static keywhiz.jooq.tables.Memberships.MEMBERSHIPS;
import static keywhiz.jooq.tables.Secrets.SECRETS;
import static keywhiz.jooq.tables.SecretsContent.SECRETS_CONTENT;
import static org.assertj.core.api.Assertions.assertThat;

public class AclJooqDaoTest {
  @Rule public final TestDBRule testDBRule = new TestDBRule();

  Client client1, client2;
  Group group1, group2, group3;
  Secret secret1, secret2;
  ClientDAO clientDAO;
  GroupDAO groupDAO;
  SecretDAO secretDAO;
  SecretSeriesDAO secretSeriesDAO;
  AclJooqDao aclJooqDao;

  @Before
  public void setUp() {
    DSLContext jooqContext = testDBRule.jooqContext();
    jooqContext.delete(CLIENTS).execute();
    jooqContext.delete(GROUPS).execute();
    jooqContext.delete(SECRETS).execute();
    jooqContext.delete(SECRETS_CONTENT).execute();

    DBI dbi = testDBRule.getDbi();

    clientDAO = dbi.onDemand(ClientDAO.class);
    long id = clientDAO.createClient("client1", "creator", Optional.empty());
    client1 = clientDAO.getClientById(id).get();

    id = clientDAO.createClient("client2", "creator", Optional.empty());
    client2 = clientDAO.getClientById(id).get();

    groupDAO = dbi.onDemand(GroupDAO.class);
    id = groupDAO.createGroup("group1", "creator", Optional.empty());
    group1 = groupDAO.getGroupById(id).get();

    id = groupDAO.createGroup("group2", "creator", Optional.empty());
    group2 = groupDAO.getGroupById(id).get();

    id = groupDAO.createGroup("group3", "creator", Optional.empty());
    group3 = groupDAO.getGroupById(id).get();

    secretDAO = dbi.onDemand(SecretDAO.class);
    SecretFixtures secretFixtures = SecretFixtures.using(secretDAO);
    secret1 = secretFixtures.createSecret("secret1", "c2VjcmV0MQ==", VersionGenerator.now().toHex());
    secret2 = secretFixtures.createSecret("secret2", "c2VjcmV0Mg==");

    secretSeriesDAO = dbi.onDemand(SecretSeriesDAO.class);

    AclDeps aclDeps = dbi.onDemand(AclDeps.class);
    aclJooqDao = new AclJooqDao(jooqContext, aclDeps);
  }

  @Test
  public void allowsAccess() {
    int before = accessGrantsTableSize();
    aclJooqDao.allowAccess(secret2.getId(), group1.getId());
    assertThat(accessGrantsTableSize()).isEqualTo(before + 1);
  }

  @Test
  public void revokesAccess() {
    aclJooqDao.allowAccess(secret2.getId(), group1.getId());
    int before = accessGrantsTableSize();

    aclJooqDao.revokeAccess(secret2.getId(), group2.getId());
    assertThat(accessGrantsTableSize()).isEqualTo(before);

    aclJooqDao.revokeAccess(secret2.getId(), group1.getId());
    assertThat(accessGrantsTableSize()).isEqualTo(before - 1);
  }

  @Test
  public void accessGrantsHasReferentialIntegrity() {
    aclJooqDao.allowAccess(secret1.getId(), group1.getId());
    aclJooqDao.allowAccess(secret2.getId(), group2.getId());
    int before = accessGrantsTableSize();

    groupDAO.deleteGroup(group1);
    assertThat(accessGrantsTableSize()).isEqualTo(before - 1);

    secretSeriesDAO.deleteSecretSeriesById(secret2.getId());
    assertThat(accessGrantsTableSize()).isEqualTo(before - 2);
  }

  @Test
  public void enrollsClients() {
    int before = membershipsTableSize();
    aclJooqDao.enrollClient(client1.getId(), group2.getId());
    assertThat(membershipsTableSize()).isEqualTo(before + 1);
  }

  @Test
  public void evictsClient() {
    aclJooqDao.enrollClient(client1.getId(), group2.getId());
    int before = membershipsTableSize();

    aclJooqDao.evictClient(client2.getId(), group2.getId());
    assertThat(membershipsTableSize()).isEqualTo(before);

    aclJooqDao.evictClient(client1.getId(), group2.getId());
    assertThat(membershipsTableSize()).isEqualTo(before - 1);
  }

  @Test
  public void membershipsHasReferentialIntegrity() {
    aclJooqDao.enrollClient(client1.getId(), group1.getId());
    aclJooqDao.enrollClient(client2.getId(), group2.getId());
    int before = membershipsTableSize();

    groupDAO.deleteGroup(group1);
    assertThat(membershipsTableSize()).isEqualTo(before - 1);

    clientDAO.deleteClient(client2);
    assertThat(membershipsTableSize()).isEqualTo(before - 2);
  }

  @Test
  public void getsSanitizedSecretsForGroup() {
    SanitizedSecret sanitizedSecret1 = SanitizedSecret.fromSecret(secret1);
    SanitizedSecret sanitizedSecret2 = SanitizedSecret.fromSecret(secret2);

    aclJooqDao.allowAccess(secret2.getId(), group1.getId());
    Set<SanitizedSecret> secrets = aclJooqDao.getSanitizedSecretsFor(group1);
    assertThat(Iterables.getOnlyElement(secrets)).isEqualToIgnoringGivenFields(sanitizedSecret2, "id");

    aclJooqDao.allowAccess(secret1.getId(), group1.getId());
    secrets = aclJooqDao.getSanitizedSecretsFor(group1);
    assertThat(secrets).hasSize(2).doesNotHaveDuplicates();

    for (SanitizedSecret secret : secrets) {
      if (secret.name().equals(secret1.getName())) {
        assertThat(secret).isEqualToIgnoringGivenFields(sanitizedSecret1, "id");
      } else {
        assertThat(secret).isEqualToIgnoringGivenFields(sanitizedSecret2, "id");
      }
    }
  }

  @Test
  public void getGroupsForSecret() {
    aclJooqDao.allowAccess(secret1.getId(), group2.getId());
    assertThat(aclJooqDao.getGroupsFor(secret1)).containsOnly(group2);

    aclJooqDao.allowAccess(secret1.getId(), group1.getId());
    assertThat(aclJooqDao.getGroupsFor(secret1)).containsOnly(group1, group2);
  }

  @Test
  public void getGroupsForClient() {
    aclJooqDao.enrollClient(client1.getId(), group2.getId());
    assertThat(aclJooqDao.getGroupsFor(client1)).containsOnly(group2);

    aclJooqDao.enrollClient(client1.getId(), group1.getId());
    assertThat(aclJooqDao.getGroupsFor(client1)).containsOnly(group1, group2);
  }

  @Test
  public void getClientsForGroup() {
    aclJooqDao.enrollClient(client2.getId(), group1.getId());
    assertThat(aclJooqDao.getClientsFor(group1)).containsOnly(client2);

    aclJooqDao.enrollClient(client1.getId(), group1.getId());
    assertThat(aclJooqDao.getClientsFor(group1)).containsOnly(client1, client2);
  }

  @Test
  public void getSanitizedSecretsForClient() {
    assertThat(aclJooqDao.getSanitizedSecretsFor(client2)).isEmpty();

    aclJooqDao.enrollClient(client2.getId(), group2.getId());
    aclJooqDao.allowAccess(secret2.getId(), group2.getId());
    Set<SanitizedSecret> secrets = aclJooqDao.getSanitizedSecretsFor(client2);
    assertThat(Iterables.getOnlyElement(secrets))
        .isEqualToIgnoringGivenFields(SanitizedSecret.fromSecret(secret2), "id");

    aclJooqDao.allowAccess(secret1.getId(), group2.getId());
    secrets = aclJooqDao.getSanitizedSecretsFor(client2);
    assertThat(secrets).hasSize(2).doesNotHaveDuplicates();

    for (SanitizedSecret secret : secrets) {
      if (secret.name().equals(secret1.getName())) {
        assertThat(secret).isEqualToIgnoringGivenFields(SanitizedSecret.fromSecret(secret1), "id");
      } else {
        assertThat(secret).isEqualToIgnoringGivenFields(SanitizedSecret.fromSecret(secret2), "id");
      }
    }

    aclJooqDao.evictClient(client2.getId(), group2.getId());
    assertThat(aclJooqDao.getSanitizedSecretsFor(client2)).isEmpty();
  }

  @Test
  public void getClientsForSecret() {
    assertThat(aclJooqDao.getClientsFor(secret2)).isEmpty();

    aclJooqDao.allowAccess(secret2.getId(), group2.getId());
    aclJooqDao.enrollClient(client2.getId(), group2.getId());
    assertThat(aclJooqDao.getClientsFor(secret2)).containsOnly(client2);

    aclJooqDao.enrollClient(client1.getId(), group2.getId());
    assertThat(aclJooqDao.getClientsFor(secret2)).containsOnly(client1, client2);

    aclJooqDao.revokeAccess(secret2.getId(), group2.getId());
    assertThat(aclJooqDao.getClientsFor(secret2)).isEmpty();
  }

  @Test
  public void getSecretSeriesForWhenUnauthorized() throws Exception {
    assertThat(aclJooqDao.getSecretSeriesFor(client1, secret1.getName()).isPresent()).isFalse();
  }

  @Test
  public void getSecretSeriesForWhenMissing() throws Exception {
    assertThat(aclJooqDao.getSecretSeriesFor(client1, "non-existent").isPresent()).isFalse();
  }

  @Test
  public void getSecretSeriesFor() throws Exception {
    SecretSeries secretSeries1 = secretSeriesDAO.getSecretSeriesById(secret1.getId()).get();

    aclJooqDao.enrollClient(client2.getId(), group1.getId());
    aclJooqDao.enrollClient(client2.getId(), group3.getId());
    aclJooqDao.allowAccess(secret1.getId(), group1.getId());

    SecretSeries secretSeries = aclJooqDao.getSecretSeriesFor(client2, secret1.getName())
        .orElseThrow(RuntimeException::new);
    assertThat(secretSeries).isEqualToIgnoringGivenFields(secretSeries1, "id");

    aclJooqDao.evictClient(client2.getId(), group1.getId());
    assertThat(aclJooqDao.getSecretSeriesFor(client2, secret1.getName()).isPresent()).isFalse();

    aclJooqDao.allowAccess(secret1.getId(), group3.getId());

    secretSeries = aclJooqDao.getSecretSeriesFor(client2, secret1.getName())
        .orElseThrow(RuntimeException::new);
    assertThat(secretSeries).isEqualToIgnoringGivenFields(secretSeries1, "id");
  }

  @Test
  public void getSecretForWhenUnauthorized() throws Exception {
    Optional<SanitizedSecret> secret = aclJooqDao.getSanitizedSecretFor(client1, secret1.getName(), secret1.getVersion());
    assertThat(secret.isPresent()).isFalse();
  }

  @Test
  public void getSecretForWhenMissing() throws Exception {
    assertThat(aclJooqDao.getSanitizedSecretFor(client1, "non-existent", "").isPresent()).isFalse();
  }

  @Test
  public void getSecretFor() throws Exception {
    SanitizedSecret sanitizedSecret1 = SanitizedSecret.fromSecret(secret1);

    aclJooqDao.enrollClient(client2.getId(), group1.getId());
    aclJooqDao.enrollClient(client2.getId(), group3.getId());

    aclJooqDao.allowAccess(secret1.getId(), group1.getId());

    SanitizedSecret secret = aclJooqDao.getSanitizedSecretFor(client2, sanitizedSecret1.name(), sanitizedSecret1.version())
        .orElseThrow(RuntimeException::new);
    assertThat(secret).isEqualToIgnoringGivenFields(sanitizedSecret1, "id");

    aclJooqDao.evictClient(client2.getId(), group1.getId());
    Optional<SanitizedSecret> missingSecret =
        aclJooqDao.getSanitizedSecretFor(client2, sanitizedSecret1.name(), sanitizedSecret1.version());
    assertThat(missingSecret.isPresent()).isFalse();

    aclJooqDao.allowAccess(sanitizedSecret1.id(), group3.getId());

    secret = aclJooqDao.getSanitizedSecretFor(client2, sanitizedSecret1.name(), sanitizedSecret1.version())
        .orElseThrow(RuntimeException::new);
    assertThat(secret).isEqualToIgnoringGivenFields(sanitizedSecret1, "id");
  }

  @Test
  public void getSecretsReturnsDistinct() {
    // client1 has two paths to secret1
    aclJooqDao.enrollClient(client1.getId(), group1.getId());
    aclJooqDao.enrollClient(client1.getId(), group2.getId());
    aclJooqDao.allowAccess(secret1.getId(), group1.getId());
    aclJooqDao.allowAccess(secret1.getId(), group2.getId());

    Set<SanitizedSecret> secret = aclJooqDao.getSanitizedSecretsFor(client1);
    assertThat(secret).hasSize(1);
  }

  private int accessGrantsTableSize() {
    return testDBRule.jooqContext().fetchCount(ACCESSGRANTS);
  }

  private int membershipsTableSize() {
    return testDBRule.jooqContext().fetchCount(MEMBERSHIPS);
  }
}
