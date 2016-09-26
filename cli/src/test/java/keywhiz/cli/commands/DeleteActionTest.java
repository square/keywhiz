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
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import keywhiz.api.ApiDate;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import keywhiz.cli.configs.DeleteActionConfig;
import keywhiz.client.KeywhizClient;
import keywhiz.client.KeywhizClient.NotFoundException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DeleteActionTest {
  private static final ApiDate NOW = ApiDate.now();

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock KeywhizClient keywhizClient;

  DeleteActionConfig deleteActionConfig;
  DeleteAction deleteAction;

  Secret secret = new Secret(0, "secret", null, () -> "c2VjcmV0MQ==", NOW, null, NOW, null, null, null,
      ImmutableMap.of(), 0);
  SanitizedSecret sanitizedSecret = SanitizedSecret.fromSecret(secret);

  ByteArrayInputStream yes;
  ByteArrayInputStream no;

  @Before
  public void setUp() {
    deleteActionConfig = new DeleteActionConfig();
    deleteAction = new DeleteAction(deleteActionConfig, keywhizClient);

    yes = new ByteArrayInputStream("Y".getBytes(UTF_8));
    no = new ByteArrayInputStream("\nOther\nN".getBytes(UTF_8)); // empty line, not yes or no, then no
  }

  @Test
  public void deleteCallsDeleteForGroup() throws Exception {
    deleteAction.inputStream = yes;
    deleteActionConfig.deleteType = Arrays.asList("group");
    deleteActionConfig.name = "Web";

    Group group = new Group(0, deleteActionConfig.name, null, null, null, null, null, null);
    when(keywhizClient.getGroupByName(deleteActionConfig.name)).thenReturn(group);

    deleteAction.run();
    verify(keywhizClient).deleteGroupWithId(group.getId());
  }

  @Test
  public void deleteCallsDeleteForClient() throws Exception {
    deleteAction.inputStream = yes;
    deleteActionConfig.deleteType = Arrays.asList("client");
    deleteActionConfig.name = "newClient";

    Client client = new Client(657, "newClient", null, NOW, null, NOW, null, null, true, false);
    when(keywhizClient.getClientByName(deleteActionConfig.name)).thenReturn(client);

    deleteAction.run();
    verify(keywhizClient).deleteClientWithId(client.getId());
  }

  @Test
  public void deleteCallsDeleteForSecret() throws Exception {
    deleteAction.inputStream = yes;
    deleteActionConfig.deleteType = Arrays.asList("secret");
    deleteActionConfig.name = secret.getDisplayName();
    when(keywhizClient.getSanitizedSecretByName(secret.getName())).thenReturn(sanitizedSecret);

    deleteAction.run();
    verify(keywhizClient).deleteSecretWithId(sanitizedSecret.id());
  }

  @Test
  public void deleteSkipsWithoutConfirmation() throws Exception {
    deleteAction.inputStream = no;
    deleteActionConfig.deleteType = Arrays.asList("secret");
    deleteActionConfig.name = secret.getDisplayName();
    when(keywhizClient.getSanitizedSecretByName(secret.getName())).thenReturn(sanitizedSecret);

    deleteAction.run();
    verify(keywhizClient, never()).deleteSecretWithId(anyInt());
  }

  @Test(expected = AssertionError.class)
  public void deleteThrowsIfDeleteGroupFails() throws Exception {
    deleteActionConfig.deleteType = Arrays.asList("group");
    deleteActionConfig.name = "Web";
    when(keywhizClient.getGroupByName(deleteActionConfig.name)).thenThrow(new NotFoundException());

    deleteAction.run();
  }

  @Test(expected = AssertionError.class)
  public void deleteThrowsIfDeleteClientFails() throws Exception {
    deleteActionConfig.deleteType = Arrays.asList("client");
    deleteActionConfig.name = "nonexistent-client-name";
    when(keywhizClient.getClientByName(deleteActionConfig.name)).thenThrow(new NotFoundException());

    deleteAction.run();
  }

  @Test(expected = AssertionError.class)
  public void deleteThrowsIfDeleteSecretFails() throws Exception {
    deleteActionConfig.deleteType = Arrays.asList("secret");
    deleteActionConfig.name = secret.getDisplayName();
    when(keywhizClient.getSanitizedSecretByName(secret.getName()))
        .thenThrow(new NotFoundException());

    deleteAction.run();
  }

  @Test(expected = IllegalArgumentException.class)
  public void deleteThrowsIfNoTypeSpecified() throws Exception {
    deleteActionConfig.deleteType = null;

    deleteAction.run();
  }

  @Test(expected = IllegalArgumentException.class)
  public void deleteThrowsIfTooManyArguments() throws Exception {
    deleteActionConfig.deleteType = Arrays.asList("group", "secret");

    deleteAction.run();
  }

  @Test(expected = IllegalArgumentException.class)
  public void deleteThrowsIfInvalidType() throws Exception {
    deleteActionConfig.deleteType = Arrays.asList("invalid_type");
    deleteActionConfig.name = "any-name";

    deleteAction.run();
  }

  @Test(expected = IllegalArgumentException.class)
  public void deleteValidatesGroupName() throws Exception {
    deleteActionConfig.deleteType = Arrays.asList("group");
    deleteActionConfig.name = "Invalid Name";

    deleteAction.run();
  }

  @Test(expected = IllegalArgumentException.class)
  public void deleteValidatesClientName() throws Exception {
    deleteActionConfig.deleteType = Arrays.asList("client");
    deleteActionConfig.name = "Invalid Name";

    deleteAction.run();
  }

  @Test(expected = IllegalArgumentException.class)
  public void deleteValidatesSecretName() throws Exception {
    deleteActionConfig.deleteType = Arrays.asList("secret");
    deleteActionConfig.name = "Invalid Name";

    deleteAction.run();
  }
}
