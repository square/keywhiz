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

public class AclDAOTest {
  @Rule public final TestDBRule testDBRule = new TestDBRule();

  Client client1, client2;
  Group group1, group2, group3;
  Secret secret1, secret2;
  ClientDAO clientDAO;
  GroupDAO groupDAO;
  SecretDAO secretDAO;
  SecretSeriesDAO secretSeriesDAO;
  AclDAO aclDAO;

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
    aclDAO = new AclDAO(jooqContext, aclDeps);
  }

  @Test
  public void allowsAccess() {
    int before = accessGrantsTableSize();
    aclDAO.allowAccess(secret2.getId(), group1.getId());
    assertThat(accessGrantsTableSize()).isEqualTo(before + 1);
  }

  @Test
  public void revokesAccess() {
    aclDAO.allowAccess(secret2.getId(), group1.getId());
    int before = accessGrantsTableSize();

    aclDAO.revokeAccess(secret2.getId(), group2.getId());
    assertThat(accessGrantsTableSize()).isEqualTo(before);

    aclDAO.revokeAccess(secret2.getId(), group1.getId());
    assertThat(accessGrantsTableSize()).isEqualTo(before - 1);
  }

  @Test
  public void accessGrantsHasReferentialIntegrity() {
    aclDAO.allowAccess(secret1.getId(), group1.getId());
    aclDAO.allowAccess(secret2.getId(), group2.getId());
    int before = accessGrantsTableSize();

    groupDAO.deleteGroup(group1);
    assertThat(accessGrantsTableSize()).isEqualTo(before - 1);

    secretSeriesDAO.deleteSecretSeriesById(secret2.getId());
    assertThat(accessGrantsTableSize()).isEqualTo(before - 2);
  }

  @Test
  public void enrollsClients() {
    int before = membershipsTableSize();
    aclDAO.enrollClient(client1.getId(), group2.getId());
    assertThat(membershipsTableSize()).isEqualTo(before + 1);
  }

  @Test
  public void evictsClient() {
    aclDAO.enrollClient(client1.getId(), group2.getId());
    int before = membershipsTableSize();

    aclDAO.evictClient(client2.getId(), group2.getId());
    assertThat(membershipsTableSize()).isEqualTo(before);

    aclDAO.evictClient(client1.getId(), group2.getId());
    assertThat(membershipsTableSize()).isEqualTo(before - 1);
  }

  @Test
  public void membershipsHasReferentialIntegrity() {
    aclDAO.enrollClient(client1.getId(), group1.getId());
    aclDAO.enrollClient(client2.getId(), group2.getId());
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

    aclDAO.allowAccess(secret2.getId(), group1.getId());
    Set<SanitizedSecret> secrets = aclDAO.getSanitizedSecretsFor(group1);
    assertThat(Iterables.getOnlyElement(secrets)).isEqualToIgnoringGivenFields(sanitizedSecret2, "id");

    aclDAO.allowAccess(secret1.getId(), group1.getId());
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

  @Test
  public void getGroupsForSecret() {
    aclDAO.allowAccess(secret1.getId(), group2.getId());
    assertThat(aclDAO.getGroupsFor(secret1)).containsOnly(group2);

    aclDAO.allowAccess(secret1.getId(), group1.getId());
    assertThat(aclDAO.getGroupsFor(secret1)).containsOnly(group1, group2);
  }

  @Test
  public void getGroupsForClient() {
    aclDAO.enrollClient(client1.getId(), group2.getId());
    assertThat(aclDAO.getGroupsFor(client1)).containsOnly(group2);

    aclDAO.enrollClient(client1.getId(), group1.getId());
    assertThat(aclDAO.getGroupsFor(client1)).containsOnly(group1, group2);
  }

  @Test
  public void getClientsForGroup() {
    aclDAO.enrollClient(client2.getId(), group1.getId());
    assertThat(aclDAO.getClientsFor(group1)).containsOnly(client2);

    aclDAO.enrollClient(client1.getId(), group1.getId());
    assertThat(aclDAO.getClientsFor(group1)).containsOnly(client1, client2);
  }

  @Test
  public void getSanitizedSecretsForClient() {
    assertThat(aclDAO.getSanitizedSecretsFor(client2)).isEmpty();

    aclDAO.enrollClient(client2.getId(), group2.getId());
    aclDAO.allowAccess(secret2.getId(), group2.getId());
    Set<SanitizedSecret> secrets = aclDAO.getSanitizedSecretsFor(client2);
    assertThat(Iterables.getOnlyElement(secrets))
        .isEqualToIgnoringGivenFields(SanitizedSecret.fromSecret(secret2), "id");

    aclDAO.allowAccess(secret1.getId(), group2.getId());
    secrets = aclDAO.getSanitizedSecretsFor(client2);
    assertThat(secrets).hasSize(2).doesNotHaveDuplicates();

    for (SanitizedSecret secret : secrets) {
      if (secret.name().equals(secret1.getName())) {
        assertThat(secret).isEqualToIgnoringGivenFields(SanitizedSecret.fromSecret(secret1), "id");
      } else {
        assertThat(secret).isEqualToIgnoringGivenFields(SanitizedSecret.fromSecret(secret2), "id");
      }
    }

    aclDAO.evictClient(client2.getId(), group2.getId());
    assertThat(aclDAO.getSanitizedSecretsFor(client2)).isEmpty();
  }

  @Test
  public void getClientsForSecret() {
    assertThat(aclDAO.getClientsFor(secret2)).isEmpty();

    aclDAO.allowAccess(secret2.getId(), group2.getId());
    aclDAO.enrollClient(client2.getId(), group2.getId());
    assertThat(aclDAO.getClientsFor(secret2)).containsOnly(client2);

    aclDAO.enrollClient(client1.getId(), group2.getId());
    assertThat(aclDAO.getClientsFor(secret2)).containsOnly(client1, client2);

    aclDAO.revokeAccess(secret2.getId(), group2.getId());
    assertThat(aclDAO.getClientsFor(secret2)).isEmpty();
  }

  @Test
  public void getSecretSeriesForWhenUnauthorized() throws Exception {
    assertThat(aclDAO.getSecretSeriesFor(client1, secret1.getName()).isPresent()).isFalse();
  }

  @Test
  public void getSecretSeriesForWhenMissing() throws Exception {
    assertThat(aclDAO.getSecretSeriesFor(client1, "non-existent").isPresent()).isFalse();
  }

  @Test
  public void getSecretSeriesFor() throws Exception {
    SecretSeries secretSeries1 = secretSeriesDAO.getSecretSeriesById(secret1.getId()).get();

    aclDAO.enrollClient(client2.getId(), group1.getId());
    aclDAO.enrollClient(client2.getId(), group3.getId());
    aclDAO.allowAccess(secret1.getId(), group1.getId());

    SecretSeries secretSeries = aclDAO.getSecretSeriesFor(client2, secret1.getName())
        .orElseThrow(RuntimeException::new);
    assertThat(secretSeries).isEqualToIgnoringGivenFields(secretSeries1, "id");

    aclDAO.evictClient(client2.getId(), group1.getId());
    assertThat(aclDAO.getSecretSeriesFor(client2, secret1.getName()).isPresent()).isFalse();

    aclDAO.allowAccess(secret1.getId(), group3.getId());

    secretSeries = aclDAO.getSecretSeriesFor(client2, secret1.getName())
        .orElseThrow(RuntimeException::new);
    assertThat(secretSeries).isEqualToIgnoringGivenFields(secretSeries1, "id");
  }

  @Test
  public void getSecretForWhenUnauthorized() throws Exception {
    Optional<SanitizedSecret> secret = aclDAO.getSanitizedSecretFor(client1, secret1.getName(), secret1.getVersion());
    assertThat(secret.isPresent()).isFalse();
  }

  @Test
  public void getSecretForWhenMissing() throws Exception {
    assertThat(aclDAO.getSanitizedSecretFor(client1, "non-existent", "").isPresent()).isFalse();
  }

  @Test
  public void getSecretFor() throws Exception {
    SanitizedSecret sanitizedSecret1 = SanitizedSecret.fromSecret(secret1);

    aclDAO.enrollClient(client2.getId(), group1.getId());
    aclDAO.enrollClient(client2.getId(), group3.getId());

    aclDAO.allowAccess(secret1.getId(), group1.getId());

    SanitizedSecret secret = aclDAO.getSanitizedSecretFor(client2, sanitizedSecret1.name(), sanitizedSecret1.version())
        .orElseThrow(RuntimeException::new);
    assertThat(secret).isEqualToIgnoringGivenFields(sanitizedSecret1, "id");

    aclDAO.evictClient(client2.getId(), group1.getId());
    Optional<SanitizedSecret> missingSecret =
        aclDAO.getSanitizedSecretFor(client2, sanitizedSecret1.name(), sanitizedSecret1.version());
    assertThat(missingSecret.isPresent()).isFalse();

    aclDAO.allowAccess(sanitizedSecret1.id(), group3.getId());

    secret = aclDAO.getSanitizedSecretFor(client2, sanitizedSecret1.name(), sanitizedSecret1.version())
        .orElseThrow(RuntimeException::new);
    assertThat(secret).isEqualToIgnoringGivenFields(sanitizedSecret1, "id");
  }

  @Test
  public void getSecretsReturnsDistinct() {
    // client1 has two paths to secret1
    aclDAO.enrollClient(client1.getId(), group1.getId());
    aclDAO.enrollClient(client1.getId(), group2.getId());
    aclDAO.allowAccess(secret1.getId(), group1.getId());
    aclDAO.allowAccess(secret1.getId(), group2.getId());

    Set<SanitizedSecret> secret = aclDAO.getSanitizedSecretsFor(client1);
    assertThat(secret).hasSize(1);
  }

  private int accessGrantsTableSize() {
    return testDBRule.jooqContext().fetchCount(ACCESSGRANTS);
  }

  private int membershipsTableSize() {
    return testDBRule.jooqContext().fetchCount(MEMBERSHIPS);
  }
}
