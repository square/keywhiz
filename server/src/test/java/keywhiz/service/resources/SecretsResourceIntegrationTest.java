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
package keywhiz.service.resources;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import keywhiz.IntegrationTestRule;
import keywhiz.TestClients;
import keywhiz.api.SecretDetailResponse;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.client.KeywhizClient;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

public class SecretsResourceIntegrationTest {
  KeywhizClient keywhizClient;

  @ClassRule public static final RuleChain chain = IntegrationTestRule.rule();

  @Before public void setUp() {
    keywhizClient = TestClients.keywhizClient();
  }

  @Test public void listsSecrets() throws IOException {
    keywhizClient.login("keywhizAdmin", "adminPass");
    assertThat(keywhizClient.allSecrets().stream().map(SanitizedSecret::name).toArray())
        .contains("Nobody_PgPass", "Hacking_Password", "General_Password",
            "NonexistentOwner_Pass", "Versioned_Password");
  }
  @Test public void listingExcludesSecretContent() throws IOException {
    // This is checking that the response body doesn't contain the secret information anywhere, not
    // just that the resulting Java objects parsed by gson don't.
    keywhizClient.login("keywhizAdmin", "adminPass");

    List<SanitizedSecret> sanitizedSecrets = keywhizClient.allSecrets();
    assertThat(sanitizedSecrets.toString())
        .doesNotContain("MTMzNw==")
        .doesNotContain(new String(Base64.getDecoder().decode("MTMzNw==")));

  }

  @Test(expected = KeywhizClient.UnauthorizedException.class)
  public void adminRejectsNonKeywhizUsers() throws IOException {
    keywhizClient.login("username", "password");
    keywhizClient.allSecrets();
  }

  @Test(expected = KeywhizClient.UnauthorizedException.class)
  public void adminRejectsWithoutCookie() throws IOException {
    keywhizClient.allSecrets();
  }

  @Test public void createsSecret() throws IOException {
    keywhizClient.login("keywhizAdmin", "adminPass");
    SecretDetailResponse secretDetails = keywhizClient.createSecret("newSecret", "", "content",
        false, ImmutableMap.of());
    assertThat(secretDetails.name).isEqualTo("newSecret");

    assertThat(keywhizClient.allSecrets().stream().map(SanitizedSecret::name).toArray())
        .contains("newSecret");
  }

  @Test public void createsDuplicateSecretWithVersion() throws IOException {
    keywhizClient.login("keywhizAdmin", "adminPass");

    keywhizClient.createSecret("trapdoor", "v1", "content", true, ImmutableMap.of());

    List<SanitizedSecret> sanitizedSecrets1 = keywhizClient.allSecrets();

    boolean found1 = false;
    for (SanitizedSecret secret : sanitizedSecrets1) {
      if (secret.name().equals("trapdoor")) {
        assertThat(secret.description().equals(Optional.of("v1")));
        found1 = true;
        break;
      }
    }
    assertThat(found1).isTrue();

    keywhizClient.createSecret("trapdoor", "v2", "content", true, ImmutableMap.of());

    List<SanitizedSecret> sanitizedSecrets2 = keywhizClient.allSecrets();

    boolean found2 = false;
    for (SanitizedSecret secret : sanitizedSecrets2) {
      if (secret.name().equals("trapdoor")) {
        assertThat(secret.description().equals(Optional.of("v2")));
        found2 = true;
        break;
      }
    }
    assertThat(found2).isTrue();
  }

  @Test(expected = KeywhizClient.ConflictException.class)
  public void rejectsCreatingDuplicateSecretWithoutVersion() throws IOException {
    keywhizClient.login("keywhizAdmin", "adminPass");

    keywhizClient.createSecret("passage", "v1", "content", false, ImmutableMap.of());
    keywhizClient.createSecret("passage", "v2", "content", false, ImmutableMap.of());
  }

  @Test public void deletesSecret() throws IOException {
    keywhizClient.login("keywhizAdmin", "adminPass");
    keywhizClient.deleteSecretWithId(739);

    try {
      keywhizClient.secretDetailsForId(739);
      failBecauseExceptionWasNotThrown(KeywhizClient.NotFoundException.class);
    } catch (KeywhizClient.NotFoundException e) {
      // Secret was successfully deleted
    }
  }

  @Test public void listsSpecificSecret() throws IOException {
    keywhizClient.login("keywhizAdmin", "adminPass");

    SecretDetailResponse response = keywhizClient.secretDetailsForId(737);
    assertThat(response.name).isEqualTo("Nobody_PgPass");
  }

  @Test public void listSpecificSecretByNameWithVersion() throws IOException {
    keywhizClient.login("keywhizAdmin", "adminPass");

    SanitizedSecret sanitizedSecret = keywhizClient
        .getSanitizedSecretByNameAndVersion("Versioned_Password", "0aae825a73e161d8");
    assertThat(sanitizedSecret.id()).isEqualTo(742);
  }

  @Test public void listSpecificNonVersionedSecretByName() throws IOException {
    keywhizClient.login("keywhizAdmin", "adminPass");

    SanitizedSecret sanitizedSecret = keywhizClient
        .getSanitizedSecretByNameAndVersion("Nobody_PgPass", "");
    assertThat(sanitizedSecret.id()).isEqualTo(737);
  }

  @Test(expected = KeywhizClient.NotFoundException.class)
  public void notFoundOnBadSecretId() throws IOException {
    keywhizClient.login("keywhizAdmin", "adminPass");
    keywhizClient.secretDetailsForId(283092384);
  }

  @Test(expected = KeywhizClient.NotFoundException.class)
  public void notFoundOnBadSecretName() throws IOException {
    keywhizClient.login("keywhizAdmin", "adminPass");
    keywhizClient.getSanitizedSecretByNameAndVersion("non-existent-secret", "");
  }

  @Test public void listSecretVersions() throws IOException {
    keywhizClient.login("keywhizAdmin", "adminPass");

    List<String> versions = keywhizClient.getVersionsForSecretName("Versioned_Password");
    assertThat(versions).containsOnlyElementsOf(
        ImmutableList.of("0aae825a73e161d8", "0aae825a73e161e8", "0aae825a73e161f8", "0aae825a73e161g8"));
  }

  @Test(expected = KeywhizClient.MalformedRequestException.class)
  public void noVersionsWhenNoNameGiven() throws IOException {
    keywhizClient.login("keywhizAdmin", "adminPass");

    keywhizClient.getVersionsForSecretName("");
  }
}
