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
package keywhiz.generators;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import keywhiz.IntegrationTestRule;
import keywhiz.TestClients;
import keywhiz.api.TemplatedSecretsGeneratorRequest;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.client.KeywhizClient;
import keywhiz.commands.DbSeedCommand;
import org.assertj.core.api.Condition;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.failBecauseExceptionWasNotThrown;

// TODO(jlfwong): Change this to SecretGeneratorsResourceIntegrationTest and use the nesting syntax
// to have tests per different generator...?
public class TemplatedSecretGeneratorIntegrationTest {
  KeywhizClient keywhizClient;

  @ClassRule public static final RuleChain chain = IntegrationTestRule.rule();

  @Before public void setUp() {
    keywhizClient = TestClients.keywhizClient();
  }

  @Test public void createsSecret() throws IOException {
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());
    keywhizClient.generateSecrets("templated",
        new TemplatedSecretsGeneratorRequest(
            "{{#alphanumeric}}20{{/alphanumeric}}",
            "ou-database.yaml",
            "description",
            false,
            ImmutableMap.of()));

    assertThat(keywhizClient.allSecrets().stream().map(SanitizedSecret::name).toArray())
        .contains("ou-database.yaml");
  }

  @Test public void rollsBackOnBatchTemplates() throws IOException {
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());

    List<TemplatedSecretsGeneratorRequest> templateBatch = ImmutableList.of(
        new TemplatedSecretsGeneratorRequest("{{#numeric}}20{{/numeric}}",
            "batchName", "desc", false, ImmutableMap.of()),
        new TemplatedSecretsGeneratorRequest("{{#numeric}}20{{/numeric}}",
            "batchName", "desc", false, ImmutableMap.of())
    );

    try {
      keywhizClient.batchGenerateSecrets("templated", templateBatch);
      failBecauseExceptionWasNotThrown(KeywhizClient.MalformedRequestException.class);
    } catch (KeywhizClient.MalformedRequestException e) {
    }

    assertThat(keywhizClient.allSecrets()).haveExactly(1, secretWithName("batchName"));
  }

  @Test public void createsTemplatesInBatch() throws IOException {
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());

    List<TemplatedSecretsGeneratorRequest> templateBatch = ImmutableList.of(
      new TemplatedSecretsGeneratorRequest("{{#numeric}}20{{/numeric}}",
          "batchName1", "desc", false, ImmutableMap.of()),
      new TemplatedSecretsGeneratorRequest("{{#numeric}}20{{/numeric}}",
          "batchName2", "desc", false, ImmutableMap.of())
    );

    keywhizClient.batchGenerateSecrets("templated", templateBatch);

    assertThat(keywhizClient.allSecrets().stream().map(SanitizedSecret::name).toArray())
        .contains("batchName1", "batchName2");
  }

  @Test(expected = KeywhizClient.MalformedRequestException.class)
  public void rejectsBatchesThatAreTooBig() throws IOException {
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());

    List<TemplatedSecretsGeneratorRequest> templateBatch = Lists.newArrayList();

    for (int i = 0; i < SecretGenerator.MAX_TEMPLATE_BATCH_SIZE + 1; i++) {
      templateBatch.add(new TemplatedSecretsGeneratorRequest("{{#numeric}}20{{/numeric}}",
          "batchName" + i, "desc", false, ImmutableMap.of()));
    }

    keywhizClient.batchGenerateSecrets("templated", templateBatch);
  }

  @Test(expected = KeywhizClient.MalformedRequestException.class)
  public void rejectsEmptyBatches() throws IOException {
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());

    List<TemplatedSecretsGeneratorRequest> templateBatch = new ArrayList<>();

    keywhizClient.batchGenerateSecrets("templated", templateBatch);
  }

  @Test public void createsDuplicateSecretWithVersion() throws IOException {
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());

    keywhizClient.generateSecrets("templated",
        new TemplatedSecretsGeneratorRequest(
            "{{#alphanumeric}}20{{/alphanumeric}}",
            "newTemplatedSecret",
            "desc1",
            true,
            ImmutableMap.of()));

    List<SanitizedSecret> sanitizedSecrets1 = keywhizClient.allSecrets();

    boolean found1 = false;
    for (SanitizedSecret secret : sanitizedSecrets1) {
      if (secret.name().equals("newTemplatedSecret")) {
        assertThat(secret.description().equals("desc1"));
        found1 = true;
        break;
      }
    }
    assertThat(found1).isTrue();

    keywhizClient.generateSecrets("templated",
        new TemplatedSecretsGeneratorRequest(
            "{{#alphanumeric}}20{{/alphanumeric}}",
            "newTemplatedSecret",
            "desc2",
            true,
            ImmutableMap.of()));

    List<SanitizedSecret> sanitizedSecrets2 = keywhizClient.allSecrets();

    boolean found2 = false;
    for (SanitizedSecret secret : sanitizedSecrets2) {
      if (secret.name().equals("newTemplatedSecret")) {
        assertThat(secret.description().equals("desc2"));
        found2 = true;
        break;
      }
    }
    assertThat(found2).isTrue();
  }

  @Test(expected = KeywhizClient.MalformedRequestException.class)
  public void rejectsCreatingDuplicateSecretWithoutVersion() throws IOException {
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());

    keywhizClient.generateSecrets("templated",
        new TemplatedSecretsGeneratorRequest(
            "{{#alphanumeric}}20{{/alphanumeric}}",
            "unversionedTemplated",
            "desc1",
            false,
            ImmutableMap.of()));

    keywhizClient.generateSecrets("templated",
        new TemplatedSecretsGeneratorRequest(
            "{{#alphanumeric}}20{{/alphanumeric}}",
            "unversionedTemplated",
            "desc2",
            false,
            ImmutableMap.of()));
  }

  private static Condition<SanitizedSecret> secretWithName(final String name) {
    return new Condition<SanitizedSecret>() {
      @Override public boolean matches(SanitizedSecret value) {
        return value.name().equals(name);
      }
    };
  }
}
