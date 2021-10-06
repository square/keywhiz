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
package keywhiz.service.resources.admin;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import keywhiz.IntegrationTestRule;
import keywhiz.TestClients;
import keywhiz.api.SecretDetailResponse;
import keywhiz.api.automation.v2.PartialUpdateSecretRequestV2;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.client.KeywhizClient;
import keywhiz.commands.DbSeedCommand;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class SecretsResourceClientIntegrationTest {
  KeywhizClient keywhizClient;

  @ClassRule public static final RuleChain chain = IntegrationTestRule.rule();

  @Before public void setUp() {
    keywhizClient = TestClients.keywhizClient();
  }

  @Test
  public void partialUpdateSecretOverwritesNullOwnerWithNonNullValue() throws Exception {
    login();

    SecretDetailResponse originalSecret = createSecretWithOwner(null);
    assertNull(originalSecret.owner);

    String owner = createGroup();
    PartialUpdateSecretRequestV2 updateRequest = PartialUpdateSecretRequestV2.builder()
        .ownerPresent(true)
        .owner(owner)
        .build();
    SecretDetailResponse updatedSecret = keywhizClient.partialUpdateSecret(originalSecret.name, updateRequest);
    assertEquals(owner, updatedSecret.owner);
  }

  @Test
  public void partialUpdateSecretOverwritesNonNullOwnerWithNonNullValue() throws Exception {
    login();

    String group1 = createGroup();
    String group2 = createGroup();

    SecretDetailResponse originalSecret = createSecretWithOwner(group1);
    assertEquals(group1, originalSecret.owner);

    PartialUpdateSecretRequestV2 updateRequest = PartialUpdateSecretRequestV2.builder()
        .ownerPresent(true)
        .owner(group2)
        .build();
    SecretDetailResponse updatedSecret = keywhizClient.partialUpdateSecret(originalSecret.name, updateRequest);
    assertEquals(group2, updatedSecret.owner);
  }

  @Test
  public void createsSecretWithOwner() throws Exception {
    login();

    String owner = createGroup();
    SecretDetailResponse response = createSecretWithOwner(owner);
    assertEquals(owner, response.owner);
  }

  @Test
  public void createSecretWithUnknownOwnerFails() throws Exception {
    login();

    String owner = UUID.randomUUID().toString();

    assertThrows(IOException.class, () -> createSecretWithOwner(owner));
  }

  @Test public void listsSecrets() throws IOException {
    login();
    assertThat(keywhizClient.allSecrets().stream().map(SanitizedSecret::name).toArray())
        .contains("Nobody_PgPass", "Hacking_Password", "General_Password", "NonexistentOwner_Pass",
            "Versioned_Password");
  }

  @Test public void listingExcludesSecretContent() throws IOException {
    // This is checking that the response body doesn't contain the secret information anywhere, not
    // just that the resulting Java objects parsed by gson don't.
    login();

    String base64 = "c29tZWhvc3Quc29tZXBsYWNlLmNvbTo1NDMyOnNvbWVkYXRhYmFzZTptaXN0ZXJhd2Vzb21lOmhlbGwwTWNGbHkK";

    List<SanitizedSecret> sanitizedSecrets = keywhizClient.allSecrets();
    assertThat(sanitizedSecrets.toString())
        .doesNotContain(base64)
        .doesNotContain(new String(Base64.getDecoder().decode(base64), UTF_8));

  }

  @Test(expected = KeywhizClient.UnauthorizedException.class)
  public void adminRejectsNonKeywhizUsers() throws IOException {
    keywhizClient.login("username", "password".toCharArray());
    keywhizClient.allSecrets();
  }

  @Test(expected = KeywhizClient.UnauthorizedException.class)
  public void adminRejectsWithoutCookie() throws IOException {
    keywhizClient.allSecrets();
  }

  @Test public void createsSecret() throws IOException {
    login();
    SecretDetailResponse secretDetails = keywhizClient.createSecret("newSecret", "",
        "content".getBytes(UTF_8), ImmutableMap.of(), 0);
    assertThat(secretDetails.name).isEqualTo("newSecret");

    assertThat(keywhizClient.allSecrets().stream().map(SanitizedSecret::name).toArray())
        .contains("newSecret");
  }

  @Test(expected = KeywhizClient.ConflictException.class)
  public void rejectsCreatingDuplicateSecretWithoutVersion() throws IOException {
    login();

    keywhizClient.createSecret("passage", "v1", "content".getBytes(UTF_8), ImmutableMap.of(), 0);
    keywhizClient.createSecret("passage", "v2", "content".getBytes(UTF_8), ImmutableMap.of(), 0);
  }

  @Test public void deletesSecret() throws IOException {
    login();
    keywhizClient.deleteSecretWithId(739);

    try {
      keywhizClient.secretDetailsForId(739);
      failBecauseExceptionWasNotThrown(KeywhizClient.NotFoundException.class);
    } catch (KeywhizClient.NotFoundException e) {
      // Secret was successfully deleted
    }
  }

  @Test public void listsSpecificSecret() throws IOException {
    login();

    SecretDetailResponse response = keywhizClient.secretDetailsForId(737);
    assertThat(response.name).isEqualTo("Nobody_PgPass");
  }

  @Test public void listSpecificNonVersionedSecretByName() throws IOException {
    login();

    SanitizedSecret sanitizedSecret = keywhizClient.getSanitizedSecretByName("Nobody_PgPass");
    assertThat(sanitizedSecret.id()).isEqualTo(737);
  }

  @Test(expected = KeywhizClient.NotFoundException.class)
  public void notFoundOnBadSecretId() throws IOException {
    login();
    keywhizClient.secretDetailsForId(283092384);
  }

  @Test(expected = KeywhizClient.NotFoundException.class)
  public void notFoundOnBadSecretName() throws IOException {
    login();
    keywhizClient.getSanitizedSecretByName("non-existent-secret");
  }

  @Test public void doesNotRetrieveDeletedSecretVersions() throws IOException {
    login();
    String name = "versionSecret";

    // Create a secret
    SecretDetailResponse secretDetails = keywhizClient.createSecret(name, "first secret",
        "content".getBytes(UTF_8), ImmutableMap.of(), 0);
    assertThat(secretDetails.name).isEqualTo(name);

    assertThat(keywhizClient.allSecrets().stream().map(SanitizedSecret::name).toArray())
        .contains(name);

    // Retrieve versions for the first secret
    List<SanitizedSecret> versions = keywhizClient.listSecretVersions(name, 0, 10);
    assertThat(versions.size()).isEqualTo(1);
    assertThat(versions.get(0).description()).isEqualTo("first secret");

    // Delete this first secret
    keywhizClient.deleteSecretWithId(secretDetails.id);
    assertThat(keywhizClient.allSecrets().stream().map(SanitizedSecret::name).toArray())
        .doesNotContain(name);

    // Create a second secret with the same name
    secretDetails = keywhizClient.createSecret(name, "second secret",
        "content".getBytes(UTF_8), ImmutableMap.of(), 0);
    assertThat(secretDetails.name).isEqualTo(name);

    // Retrieve versions for the second secret and check that the first secret's version is not included
    versions = keywhizClient.listSecretVersions(name, 0, 10);
    assertThat(versions.size()).isEqualTo(1);
    assertThat(versions.get(0).description()).isEqualTo("second secret");
  }

  private void login() throws IOException {
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());
  }

  private SecretDetailResponse createSecretWithOwner(String owner) throws IOException {
    String secretName = UUID.randomUUID().toString();
    return keywhizClient.createSecret(
        secretName,
        owner,
        "description",
        "content".getBytes(StandardCharsets.UTF_8),
        ImmutableMap.of(),
        0);
  }

  private String createGroup() {
    String name = UUID.randomUUID().toString();
    try {
      keywhizClient.createGroup(name, "description", ImmutableMap.of());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return name;
  }
}
