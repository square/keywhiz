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
  DSLContext jooqContext;

  @Before
  public void setUp() {
    ObjectMapper objectMapper = new ObjectMapper();
    jooqContext = testDBRule.jooqContext();
    jooqContext.delete(CLIENTS).execute();
    jooqContext.delete(GROUPS).execute();
    jooqContext.delete(SECRETS).execute();
    jooqContext.delete(SECRETS_CONTENT).execute();

    clientDAO = new ClientDAO();
    long id = clientDAO.createClient(jooqContext, "client1", "creator", Optional.empty());
    client1 = clientDAO.getClientById(jooqContext, id).get();

    id = clientDAO.createClient(jooqContext, "client2", "creator", Optional.empty());
    client2 = clientDAO.getClientById(jooqContext, id).get();

    groupDAO = new GroupDAO();
    id = groupDAO.createGroup(jooqContext, "group1", "creator", Optional.empty());
    group1 = groupDAO.getGroupById(jooqContext, id).get();

    id = groupDAO.createGroup(jooqContext, "group2", "creator", Optional.empty());
    group2 = groupDAO.getGroupById(jooqContext, id).get();

    id = groupDAO.createGroup(jooqContext, "group3", "creator", Optional.empty());
    group3 = groupDAO.getGroupById(jooqContext, id).get();

    secretSeriesDAO = new SecretSeriesDAO(jooqContext, objectMapper);

    SecretContentDAO secretContentDAO = new SecretContentDAO(objectMapper);

    secretDAO = new SecretDAO(objectMapper, secretContentDAO);
    SecretFixtures secretFixtures = SecretFixtures.using(jooqContext, secretDAO);
    secret1 = secretFixtures.createSecret("secret1", "c2VjcmV0MQ==", VersionGenerator.now().toHex());
    secret2 = secretFixtures.createSecret("secret2", "c2VjcmV0Mg==");

    aclDAO = new AclDAO(objectMapper, clientDAO, groupDAO, secretContentDAO);
  }

  @Test
  public void allowsAccess() {
    int before = accessGrantsTableSize();
    aclDAO.allowAccess(jooqContext, secret2.getId(), group1.getId());
    assertThat(accessGrantsTableSize()).isEqualTo(before + 1);
  }

  @Test
  public void revokesAccess() {
    aclDAO.allowAccess(jooqContext, secret2.getId(), group1.getId());
    int before = accessGrantsTableSize();

    aclDAO.revokeAccess(jooqContext, secret2.getId(), group2.getId());
    assertThat(accessGrantsTableSize()).isEqualTo(before);

    aclDAO.revokeAccess(jooqContext, secret2.getId(), group1.getId());
    assertThat(accessGrantsTableSize()).isEqualTo(before - 1);
  }

  @Test
  public void accessGrantsHasReferentialIntegrity() {
    aclDAO.allowAccess(jooqContext, secret1.getId(), group1.getId());
    aclDAO.allowAccess(jooqContext, secret2.getId(), group2.getId());
    int before = accessGrantsTableSize();

    groupDAO.deleteGroup(jooqContext, group1);
    assertThat(accessGrantsTableSize()).isEqualTo(before - 1);

    secretSeriesDAO.deleteSecretSeriesById(secret2.getId());
    assertThat(accessGrantsTableSize()).isEqualTo(before - 2);
  }

  @Test
  public void enrollsClients() {
    int before = membershipsTableSize();
    aclDAO.enrollClient(jooqContext, client1.getId(), group2.getId());
    assertThat(membershipsTableSize()).isEqualTo(before + 1);
  }

  @Test
  public void evictsClient() {
    aclDAO.enrollClient(jooqContext, client1.getId(), group2.getId());
    int before = membershipsTableSize();

    aclDAO.evictClient(jooqContext, client2.getId(), group2.getId());
    assertThat(membershipsTableSize()).isEqualTo(before);

    aclDAO.evictClient(jooqContext, client1.getId(), group2.getId());
    assertThat(membershipsTableSize()).isEqualTo(before - 1);
  }

  @Test
  public void membershipsHasReferentialIntegrity() {
    aclDAO.enrollClient(jooqContext, client1.getId(), group1.getId());
    aclDAO.enrollClient(jooqContext, client2.getId(), group2.getId());
    int before = membershipsTableSize();

    groupDAO.deleteGroup(jooqContext, group1);
    assertThat(membershipsTableSize()).isEqualTo(before - 1);

    clientDAO.deleteClient(jooqContext, client2);
    assertThat(membershipsTableSize()).isEqualTo(before - 2);
  }

  @Test
  public void getsSanitizedSecretsForGroup() {
    SanitizedSecret sanitizedSecret1 = SanitizedSecret.fromSecret(secret1);
    SanitizedSecret sanitizedSecret2 = SanitizedSecret.fromSecret(secret2);

    aclDAO.allowAccess(jooqContext, secret2.getId(), group1.getId());
    Set<SanitizedSecret> secrets = aclDAO.getSanitizedSecretsFor(jooqContext, group1);
    assertThat(Iterables.getOnlyElement(secrets)).isEqualToIgnoringGivenFields(sanitizedSecret2, "id");

    aclDAO.allowAccess(jooqContext, secret1.getId(), group1.getId());
    secrets = aclDAO.getSanitizedSecretsFor(jooqContext, group1);
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
    aclDAO.allowAccess(jooqContext, secret1.getId(), group2.getId());
    assertThat(aclDAO.getGroupsFor(jooqContext, secret1)).containsOnly(group2);

    aclDAO.allowAccess(jooqContext, secret1.getId(), group1.getId());
    assertThat(aclDAO.getGroupsFor(jooqContext, secret1)).containsOnly(group1, group2);
  }

  @Test
  public void getGroupsForClient() {
    aclDAO.enrollClient(jooqContext, client1.getId(), group2.getId());
    assertThat(aclDAO.getGroupsFor(jooqContext, client1)).containsOnly(group2);

    aclDAO.enrollClient(jooqContext, client1.getId(), group1.getId());
    assertThat(aclDAO.getGroupsFor(jooqContext, client1)).containsOnly(group1, group2);
  }

  @Test
  public void getClientsForGroup() {
    aclDAO.enrollClient(jooqContext, client2.getId(), group1.getId());
    assertThat(aclDAO.getClientsFor(jooqContext, group1)).containsOnly(client2);

    aclDAO.enrollClient(jooqContext, client1.getId(), group1.getId());
    assertThat(aclDAO.getClientsFor(jooqContext, group1)).containsOnly(client1, client2);
  }

  @Test
  public void getSanitizedSecretsForClient() {
    assertThat(aclDAO.getSanitizedSecretsFor(jooqContext, client2)).isEmpty();

    aclDAO.enrollClient(jooqContext, client2.getId(), group2.getId());
    aclDAO.allowAccess(jooqContext, secret2.getId(), group2.getId());
    Set<SanitizedSecret> secrets = aclDAO.getSanitizedSecretsFor(jooqContext, client2);
    assertThat(Iterables.getOnlyElement(secrets))
        .isEqualToIgnoringGivenFields(SanitizedSecret.fromSecret(secret2), "id");

    aclDAO.allowAccess(jooqContext, secret1.getId(), group2.getId());
    secrets = aclDAO.getSanitizedSecretsFor(jooqContext, client2);
    assertThat(secrets).hasSize(2).doesNotHaveDuplicates();

    for (SanitizedSecret secret : secrets) {
      if (secret.name().equals(secret1.getName())) {
        assertThat(secret).isEqualToIgnoringGivenFields(SanitizedSecret.fromSecret(secret1), "id");
      } else {
        assertThat(secret).isEqualToIgnoringGivenFields(SanitizedSecret.fromSecret(secret2), "id");
      }
    }

    aclDAO.evictClient(jooqContext, client2.getId(), group2.getId());
    assertThat(aclDAO.getSanitizedSecretsFor(jooqContext, client2)).isEmpty();
  }

  @Test
  public void getClientsForSecret() {
    assertThat(aclDAO.getClientsFor(jooqContext, secret2)).isEmpty();

    aclDAO.allowAccess(jooqContext, secret2.getId(), group2.getId());
    aclDAO.enrollClient(jooqContext, client2.getId(), group2.getId());
    assertThat(aclDAO.getClientsFor(jooqContext, secret2)).containsOnly(client2);

    aclDAO.enrollClient(jooqContext, client1.getId(), group2.getId());
    assertThat(aclDAO.getClientsFor(jooqContext, secret2)).containsOnly(client1, client2);

    aclDAO.revokeAccess(jooqContext, secret2.getId(), group2.getId());
    assertThat(aclDAO.getClientsFor(jooqContext, secret2)).isEmpty();
  }

  @Test
  public void getSecretSeriesForWhenUnauthorized() throws Exception {
    assertThat(aclDAO.getSecretSeriesFor(jooqContext, client1, secret1.getName()).isPresent()).isFalse();
  }

  @Test
  public void getSecretSeriesForWhenMissing() throws Exception {
    assertThat(aclDAO.getSecretSeriesFor(jooqContext, client1, "non-existent").isPresent()).isFalse();
  }

  @Test
  public void getSecretSeriesFor() throws Exception {
    SecretSeries secretSeries1 = secretSeriesDAO.getSecretSeriesById(secret1.getId()).get();

    aclDAO.enrollClient(jooqContext, client2.getId(), group1.getId());
    aclDAO.enrollClient(jooqContext, client2.getId(), group3.getId());
    aclDAO.allowAccess(jooqContext, secret1.getId(), group1.getId());

    SecretSeries secretSeries = aclDAO.getSecretSeriesFor(jooqContext, client2, secret1.getName())
        .orElseThrow(RuntimeException::new);
    assertThat(secretSeries).isEqualToIgnoringGivenFields(secretSeries1, "id");

    aclDAO.evictClient(jooqContext, client2.getId(), group1.getId());
    assertThat(aclDAO.getSecretSeriesFor(jooqContext, client2, secret1.getName()).isPresent()).isFalse();

    aclDAO.allowAccess(jooqContext, secret1.getId(), group3.getId());

    secretSeries = aclDAO.getSecretSeriesFor(jooqContext, client2, secret1.getName())
        .orElseThrow(RuntimeException::new);
    assertThat(secretSeries).isEqualToIgnoringGivenFields(secretSeries1, "id");
  }

  @Test
  public void getSecretForWhenUnauthorized() throws Exception {
    Optional<SanitizedSecret> secret = aclDAO.getSanitizedSecretFor(jooqContext, client1, secret1.getName(), secret1.getVersion());
    assertThat(secret.isPresent()).isFalse();
  }

  @Test
  public void getSecretForWhenMissing() throws Exception {
    assertThat(aclDAO.getSanitizedSecretFor(jooqContext, client1, "non-existent", "").isPresent()).isFalse();
  }

  @Test
  public void getSecretFor() throws Exception {
    SanitizedSecret sanitizedSecret1 = SanitizedSecret.fromSecret(secret1);

    aclDAO.enrollClient(jooqContext, client2.getId(), group1.getId());
    aclDAO.enrollClient(jooqContext, client2.getId(), group3.getId());

    aclDAO.allowAccess(jooqContext, secret1.getId(), group1.getId());

    SanitizedSecret secret = aclDAO.getSanitizedSecretFor(jooqContext, client2, sanitizedSecret1.name(), sanitizedSecret1.version())
        .orElseThrow(RuntimeException::new);
    assertThat(secret).isEqualToIgnoringGivenFields(sanitizedSecret1, "id");

    aclDAO.evictClient(jooqContext, client2.getId(), group1.getId());
    Optional<SanitizedSecret> missingSecret =
        aclDAO.getSanitizedSecretFor(jooqContext, client2, sanitizedSecret1.name(), sanitizedSecret1.version());
    assertThat(missingSecret.isPresent()).isFalse();

    aclDAO.allowAccess(jooqContext, sanitizedSecret1.id(), group3.getId());

    secret = aclDAO.getSanitizedSecretFor(jooqContext, client2, sanitizedSecret1.name(), sanitizedSecret1.version())
        .orElseThrow(RuntimeException::new);
    assertThat(secret).isEqualToIgnoringGivenFields(sanitizedSecret1, "id");
  }

  @Test
  public void getSecretsReturnsDistinct() {
    // client1 has two paths to secret1
    aclDAO.enrollClient(jooqContext, client1.getId(), group1.getId());
    aclDAO.enrollClient(jooqContext, client1.getId(), group2.getId());
    aclDAO.allowAccess(jooqContext, secret1.getId(), group1.getId());
    aclDAO.allowAccess(jooqContext, secret1.getId(), group2.getId());

    Set<SanitizedSecret> secret = aclDAO.getSanitizedSecretsFor(jooqContext, client1);
    assertThat(secret).hasSize(1);
  }

  private int accessGrantsTableSize() {
    return testDBRule.jooqContext().fetchCount(ACCESSGRANTS);
  }

  private int membershipsTableSize() {
    return testDBRule.jooqContext().fetchCount(MEMBERSHIPS);
  }
}
