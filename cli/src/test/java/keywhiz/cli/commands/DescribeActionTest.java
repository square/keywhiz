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

package keywhiz.cli.commands;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import keywhiz.api.ApiDate;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import keywhiz.cli.Printing;
import keywhiz.cli.configs.DescribeActionConfig;
import keywhiz.client.KeywhizClient;
import keywhiz.client.KeywhizClient.NotFoundException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DescribeActionTest {
  private static final ApiDate NOW = ApiDate.now();

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock KeywhizClient keywhizClient;
  @Mock Printing printing;

  DescribeActionConfig describeActionConfig;
  DescribeAction describeAction;
  Secret secret = new Secret(0, "secret", null, null, () ->  "c2VjcmV0MQ==", "checksum", NOW, null, NOW, null, null, null,
      ImmutableMap.of(), 0, 1L, NOW, null);
  SanitizedSecret sanitizedSecret = SanitizedSecret.fromSecret(secret);

  Secret secretABC = new Secret(1, "ABC", null, null, () ->  "c2VjcmV0MQ==", "checksum", NOW, null, NOW, null, null, null,
      ImmutableMap.of(), 0, 1L, NOW, null);
  SanitizedSecret sanitizedSecretABC = SanitizedSecret.fromSecret(secretABC);

  Secret deletedSecretABC1 =
      new Secret(2, ".ABC.deleted.1674155945.237e9877-e79b-12d4-a765-321741963000", null, null,
          () -> "c2VjcmV0NQ==", "checksum", NOW, null, NOW, null, null, null, ImmutableMap.of(), 0,
          1L, NOW, null);
  SanitizedSecret sanitizedDeletedSecretABC1 = SanitizedSecret.fromSecret(deletedSecretABC1);

  Secret deletedSecretABC2 =
      new Secret(3, ".ABC.deleted.2674155946.348e9877-e79b-12d4-a765-432741963111", null, null,
          () -> "c2VjcmV0NQ==", "checksum", NOW, null, NOW, null, null, null, ImmutableMap.of(), 0,
          1L, NOW, null);
  SanitizedSecret sanitizedDeletedSecretABC2 = SanitizedSecret.fromSecret(deletedSecretABC2);

  List<SanitizedSecret> deletedSecrets = List.of(sanitizedDeletedSecretABC1, sanitizedDeletedSecretABC2);
  List<SanitizedSecret> emptyDeletedSecrets = List.of();

  @Before
  public void setUp() {
    describeActionConfig = new DescribeActionConfig();
    describeAction = new DescribeAction(describeActionConfig, keywhizClient, printing);
  }

  @Test
  public void describeCallsPrintForGroup() throws Exception {
    describeActionConfig.describeType = Arrays.asList("group");
    describeActionConfig.name = "Web";

    Group group = new Group(0, describeActionConfig.name, null, null, null, null, null, null);
    when(keywhizClient.getGroupByName(anyString())).thenReturn(group);

    describeAction.run();
    verify(printing).printGroupWithDetails(group);
  }

  @Test
  public void describeCallsPrintForClient() throws Exception {
    describeActionConfig.describeType = Arrays.asList("client");
    describeActionConfig.name = "client-name";

    Client client = new Client(0, describeActionConfig.name, null, null, null, null, null, null, null,
        null, false, false);
    when(keywhizClient.getClientByName(describeActionConfig.name)).thenReturn(client);

    describeAction.run();
    verify(printing).printClientWithDetails(client);
  }

  @Test
  public void describeCallsPrintForNonDeletedSecret() throws Exception {
    describeActionConfig.describeType = Arrays.asList("secret");
    describeActionConfig.name = "General_Password";

    when(keywhizClient.getSanitizedSecretByName(anyString())).thenReturn(sanitizedSecret);

    describeAction.run();

    verify(printing).printSanitizedSecretWithDetails(sanitizedSecret);
    verify(printing, never()).printDeletedSecretsWithDetails(deletedSecrets);
  }

  @Test
  public void describeCallsPrintForNonDeletedSecretAndDeletedSecrets() throws Exception {
    describeActionConfig.describeType = Arrays.asList("secret");
    describeActionConfig.name = "ABC";
    describeActionConfig.includeDeleted = true;

    when(keywhizClient.getSanitizedSecretByName("ABC")).thenReturn(sanitizedSecretABC);
    when(keywhizClient.getDeletedSecretsByName("ABC")).thenReturn(deletedSecrets);

    describeAction.run();

    verify(printing).printSanitizedSecretWithDetails(sanitizedSecretABC);
    verify(printing).printDeletedSecretsWithDetails(deletedSecrets);
  }

  @Test
  public void describeCallsPrintForNonDeletedSecretAndEmptyDeletedSecrets() throws Exception {
    describeActionConfig.describeType = Arrays.asList("secret");
    describeActionConfig.name = "ABC";
    describeActionConfig.includeDeleted = true;

    when(keywhizClient.getSanitizedSecretByName("ABC")).thenReturn(sanitizedSecretABC);
    when(keywhizClient.getDeletedSecretsByName("ABC")).thenReturn(emptyDeletedSecrets);

    describeAction.run();

    verify(printing).printSanitizedSecretWithDetails(sanitizedSecretABC);
    verify(printing).printDeletedSecretsWithDetails(emptyDeletedSecrets);
  }

  @Test
  public void describeCallsPrintForDeletedSecrets() throws Exception {
    describeActionConfig.describeType = Arrays.asList("secret");
    describeActionConfig.name = "ABC";
    describeActionConfig.includeDeleted = true;

    when(keywhizClient.getSanitizedSecretByName("ABC")).thenThrow(new NotFoundException());
    when(keywhizClient.getDeletedSecretsByName("ABC")).thenReturn(deletedSecrets);

    describeAction.run();

    verify(printing, never()).printSanitizedSecretWithDetails(sanitizedSecretABC);
    verify(printing).printDeletedSecretsWithDetails(deletedSecrets);
  }

  @Test
  public void describeCallsPrintForEmptyDeletedSecrets() throws Exception {
    describeActionConfig.describeType = Arrays.asList("secret");
    describeActionConfig.name = "ABC";
    describeActionConfig.includeDeleted = true;

    when(keywhizClient.getSanitizedSecretByName("ABC")).thenThrow(new NotFoundException());
    when(keywhizClient.getDeletedSecretsByName("ABC")).thenReturn(emptyDeletedSecrets);

    describeAction.run();

    verify(printing, never()).printSanitizedSecretWithDetails(sanitizedSecretABC);
    verify(printing).printDeletedSecretsWithDetails(emptyDeletedSecrets);
  }

  @Test(expected = AssertionError.class)
  public void describeThrowsIfGroupDoesNotExist() throws Exception {
    describeActionConfig.describeType = Arrays.asList("group");
    describeActionConfig.name = "nonexistent-group-name";

    when(keywhizClient.getGroupByName(anyString())).thenThrow(new NotFoundException());

    describeAction.run();
  }

  @Test(expected = AssertionError.class)
  public void describeThrowsIfClientDoesNotExist() throws Exception {
    describeActionConfig.describeType = Arrays.asList("client");
    describeActionConfig.name = "nonexistent-client-name";

    when(keywhizClient.getClientByName("nonexistent-client-name")).thenThrow(
        new NotFoundException());
    when(keywhizClient.allClients()).thenReturn(Arrays.asList());

    describeAction.run();
  }

  @Test(expected = AssertionError.class)
  public void describeThrowsIfSecretDoesNotExist() throws Exception {
    describeActionConfig.describeType = Arrays.asList("secret");
    describeActionConfig.name = "nonexistent-secret-name";

    when(keywhizClient.getSanitizedSecretByName(anyString())).thenThrow(new NotFoundException());

    describeAction.run();
  }

  @Test
  public void describeDoesNotThrowIfSecretDoesNotExistButIncludeDeletedIsSet() throws Exception {
    describeActionConfig.describeType = Arrays.asList("secret");
    describeActionConfig.name = "nonexistent-secret-name";
    describeActionConfig.includeDeleted = true;

    when(keywhizClient.getSanitizedSecretByName(anyString())).thenThrow(new NotFoundException());

    describeAction.run();
  }

  @Test(expected = IllegalArgumentException.class)
  public void describeThrowsIfNoTypeSpecified() throws Exception {
    describeActionConfig.describeType = null;

    describeAction.run();
  }

  @Test(expected = IllegalArgumentException.class)
  public void describeThrowsIfTooManyArguments() throws Exception {
    describeActionConfig.describeType = Arrays.asList("group", "client");

    describeAction.run();
  }

  @Test(expected = IllegalArgumentException.class)
  public void describeThrowsIfInvalidType() throws Exception {
    describeActionConfig.describeType = Arrays.asList("invalid_type");
    describeActionConfig.name = "any-name";

    describeAction.run();
  }

  @Test(expected = IllegalArgumentException.class)
  public void describeValidatesGroupName() throws Exception {
    describeActionConfig.describeType = Arrays.asList("group");
    describeActionConfig.name = "Invalid Name";

    describeAction.run();
  }

  @Test(expected = IllegalArgumentException.class)
  public void describeValidatesSecretName() throws Exception {
    describeActionConfig.describeType = Arrays.asList("secret");
    describeActionConfig.name = "Invalid Name";

    describeAction.run();
  }
}
