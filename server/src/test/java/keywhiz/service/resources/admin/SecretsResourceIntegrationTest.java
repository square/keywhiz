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
import java.util.Base64;
import java.util.List;
import keywhiz.IntegrationTestRule;
import keywhiz.TestClients;
import keywhiz.api.SecretDetailResponse;
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

public class SecretsResourceIntegrationTest {
  KeywhizClient keywhizClient;

  @ClassRule public static final RuleChain chain = IntegrationTestRule.rule();

  @Before public void setUp() {
    keywhizClient = TestClients.keywhizClient();
  }

  @Test public void listsSecrets() throws IOException {
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());
    assertThat(keywhizClient.allSecrets().stream().map(SanitizedSecret::name).toArray())
        .contains("Nobody_PgPass", "Hacking_Password", "General_Password", "NonexistentOwner_Pass",
            "Versioned_Password");
  }
  @Test public void listingExcludesSecretContent() throws IOException {
    // This is checking that the response body doesn't contain the secret information anywhere, not
    // just that the resulting Java objects parsed by gson don't.
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());

    List<SanitizedSecret> sanitizedSecrets = keywhizClient.allSecrets();
    assertThat(sanitizedSecrets.toString())
        .doesNotContain("MTMzNw==")
        .doesNotContain(new String(Base64.getDecoder().decode("MTMzNw=="), UTF_8));

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
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());
    SecretDetailResponse secretDetails = keywhizClient.createSecret("newSecret", "",
        "content".getBytes(UTF_8), ImmutableMap.of(), 0);
    assertThat(secretDetails.name).isEqualTo("newSecret");

    assertThat(keywhizClient.allSecrets().stream().map(SanitizedSecret::name).toArray())
        .contains("newSecret");
  }

  @Test(expected = KeywhizClient.ConflictException.class)
  public void rejectsCreatingDuplicateSecretWithoutVersion() throws IOException {
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());

    keywhizClient.createSecret("passage", "v1", "content".getBytes(UTF_8), ImmutableMap.of(), 0);
    keywhizClient.createSecret("passage", "v2", "content".getBytes(UTF_8), ImmutableMap.of(), 0);
  }

  @Test public void deletesSecret() throws IOException {
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());
    keywhizClient.deleteSecretWithId(739);

    try {
      keywhizClient.secretDetailsForId(739);
      failBecauseExceptionWasNotThrown(KeywhizClient.NotFoundException.class);
    } catch (KeywhizClient.NotFoundException e) {
      // Secret was successfully deleted
    }
  }

  @Test public void listsSpecificSecret() throws IOException {
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());

    SecretDetailResponse response = keywhizClient.secretDetailsForId(737);
    assertThat(response.name).isEqualTo("Nobody_PgPass");
  }

  @Test public void listSpecificNonVersionedSecretByName() throws IOException {
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());

    SanitizedSecret sanitizedSecret = keywhizClient.getSanitizedSecretByName("Nobody_PgPass");
    assertThat(sanitizedSecret.id()).isEqualTo(737);
  }

  @Test(expected = KeywhizClient.NotFoundException.class)
  public void notFoundOnBadSecretId() throws IOException {
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());
    keywhizClient.secretDetailsForId(283092384);
  }

  @Test(expected = KeywhizClient.NotFoundException.class)
  public void notFoundOnBadSecretName() throws IOException {
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());
    keywhizClient.getSanitizedSecretByName("non-existent-secret");
  }
}
