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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.time.OffsetDateTime;
import java.util.Arrays;
import keywhiz.api.GroupDetailResponse;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import keywhiz.api.model.VersionGenerator;
import keywhiz.cli.configs.UnassignActionConfig;
import keywhiz.client.KeywhizClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class UnassignActionTest {
  private static final OffsetDateTime NOW = OffsetDateTime.now();

  @Mock KeywhizClient keywhizClient;

  UnassignActionConfig unassignActionConfig;
  UnassignAction unassignAction;

  Client client = new Client(11, "client-name", null, null, null, null, null, false, false);
  Group group = new Group(22, "group-name", null, null, null, null, null);
  Secret secret = new Secret(33, "secret-name", VersionGenerator.now().toHex(), null, "c2VjcmV0MQ==", NOW,
      null, NOW, null, null, null, ImmutableMap.of());
  SanitizedSecret sanitizedSecret = SanitizedSecret.fromSecret(secret);
  GroupDetailResponse groupDetailResponse = GroupDetailResponse.fromGroup(group,
      ImmutableList.of(sanitizedSecret), ImmutableList.of(client));


  @Before
  public void setUp() {
    unassignActionConfig = new UnassignActionConfig();
    unassignAction = new UnassignAction(unassignActionConfig, keywhizClient);
  }

  @Test
  public void unassignCallsUnassignForClient() throws Exception {
    unassignActionConfig.unassignType = Arrays.asList("client");
    unassignActionConfig.name = client.getName();
    unassignActionConfig.group = group.getName();

    when(keywhizClient.getGroupByName(group.getName())).thenReturn(group);
    when(keywhizClient.getClientByName(client.getName())).thenReturn(client);
    when(keywhizClient.groupDetailsForId((int) group.getId())).thenReturn(groupDetailResponse);

    unassignAction.run();
    verify(keywhizClient).evictClientFromGroupByIds((int) client.getId(), (int) group.getId());
  }

  @Test
  public void unassignCallsUnassignForSecret() throws Exception {
    unassignActionConfig.unassignType = Arrays.asList("secret");
    unassignActionConfig.name = secret.getDisplayName();
    unassignActionConfig.group = group.getName();

    when(keywhizClient.getGroupByName(group.getName())).thenReturn(group);
    when(keywhizClient.getSanitizedSecretByNameAndVersion(secret.getName(), secret.getVersion()))
        .thenReturn(sanitizedSecret);
    when(keywhizClient.groupDetailsForId((int) group.getId())).thenReturn(groupDetailResponse);

    unassignAction.run();
    verify(keywhizClient).revokeSecretFromGroupByIds((int) secret.getId(), (int) group.getId());
  }

  @Test(expected = IllegalArgumentException.class)
  public void unassignThrowsIfNoTypeSpecified() throws Exception {
    unassignActionConfig.unassignType = null;

    unassignAction.run();
  }

  @Test(expected = IllegalArgumentException.class)
  public void unassignThrowsIfInvalidType() throws Exception {
    unassignActionConfig.unassignType = Arrays.asList("invalid_type");
    unassignActionConfig.name = "General_Password";
    unassignActionConfig.group = group.getName();

    when(keywhizClient.getGroupByName(group.getName())).thenReturn(group);

    unassignAction.run();
  }

  @Test(expected = IllegalArgumentException.class)
  public void unassignValidatesGroupName() throws Exception {
    unassignActionConfig.unassignType = Arrays.asList("secret");
    unassignActionConfig.name = "General_Password";
    unassignActionConfig.group = "Invalid Name";

    unassignAction.run();
  }

  @Test(expected = IllegalArgumentException.class)
  public void unassignValidatesSecretName() throws Exception {
    unassignActionConfig.unassignType = Arrays.asList("secret");
    unassignActionConfig.name = "Invalid Name";
    unassignActionConfig.group = "Web";

    unassignAction.run();
  }
}
