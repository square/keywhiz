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
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DescribeActionTest {
  private static final ApiDate NOW = ApiDate.now();

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock KeywhizClient keywhizClient;
  @Mock Printing printing;

  DescribeActionConfig describeActionConfig;
  DescribeAction describeAction;
  Secret secret = new Secret(0, "secret", null, () ->  "c2VjcmV0MQ==", NOW, null, NOW, null, null, null,
      ImmutableMap.of(), 0);
  SanitizedSecret sanitizedSecret = SanitizedSecret.fromSecret(secret);

  @Before
  public void setUp() {
    describeActionConfig = new DescribeActionConfig();
    describeAction = new DescribeAction(describeActionConfig, keywhizClient, printing);
  }

  @Test
  public void describeCallsPrintForGroup() throws Exception {
    describeActionConfig.describeType = Arrays.asList("group");
    describeActionConfig.name = "Web";

    Group group = new Group(0, describeActionConfig.name, null, null, null, null, null);
    when(keywhizClient.getGroupByName(anyString())).thenReturn(group);

    describeAction.run();
    verify(printing).printGroupWithDetails(group, Arrays.asList("clients", "secrets"));
  }

  @Test
  public void describeCallsPrintForClient() throws Exception {
    describeActionConfig.describeType = Arrays.asList("client");
    describeActionConfig.name = "client-name";

    Client client = new Client(0, describeActionConfig.name, null, null, null, null, null, false, false);
    when(keywhizClient.getClientByName(describeActionConfig.name)).thenReturn(client);

    describeAction.run();
    verify(printing).printClientWithDetails(client, Arrays.asList("groups", "secrets"));
  }

  @Test
  public void describeCallsPrintForSecret() throws Exception {
    describeActionConfig.describeType = Arrays.asList("secret");
    describeActionConfig.name = "General_Password";

    when(keywhizClient.getSanitizedSecretByName(anyString())).thenReturn(sanitizedSecret);

    describeAction.run();
    verify(printing).printSanitizedSecretWithDetails(sanitizedSecret,
        Arrays.asList("groups", "clients", "metadata"));
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
