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
import io.dropwizard.jackson.Jackson;
import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Base64;
import keywhiz.api.SecretDetailResponse;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import keywhiz.api.model.VersionGenerator;
import keywhiz.cli.configs.AddActionConfig;
import keywhiz.client.KeywhizClient;
import keywhiz.client.KeywhizClient.NotFoundException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRule;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AddActionTest {
  private static final OffsetDateTime NOW = OffsetDateTime.now();
  private static final Base64.Decoder base64Decoder = Base64.getDecoder();

  @Rule public TestRule mockito = new MockitoJUnitRule(this);

  @Mock KeywhizClient keywhizClient;

  AddActionConfig addActionConfig;
  AddAction addAction;

  Client client = new Client(4, "newClient", null, null, null, null, null, true, false);
  Group group = new Group(4, "newGroup", null, null, null, null, null);
  Secret secret = new Secret(15, "newSecret", VersionGenerator.now().toHex(), null, "c2VjcmV0MQ==",
      NOW, null, NOW, null, null, null, ImmutableMap.of());
  SanitizedSecret sanitizedSecret = SanitizedSecret.fromSecret(secret);
  SecretDetailResponse secretDetailResponse = SecretDetailResponse.fromSecret(secret, null, null);

  @Before
  public void setUp() {
    addActionConfig = new AddActionConfig();
    addAction = new AddAction(addActionConfig, keywhizClient, Jackson.newObjectMapper());
  }

  @Test
  public void addCallsAddForGroup() throws Exception {
    addActionConfig.addType = Arrays.asList("group");
    addActionConfig.name = group.getName();

    when(keywhizClient.getGroupByName(group.getName())).thenThrow(
        new NotFoundException());

    addAction.run();
    verify(keywhizClient)
        .createGroup(addActionConfig.name, null);
  }

  @Test
  public void addCallsAddForSecret() throws Exception {
    addActionConfig.addType = Arrays.asList("secret");
    addActionConfig.name = secret.getDisplayName();

    addAction.stream = new ByteArrayInputStream(base64Decoder.decode(secret.getSecret()));
    when(keywhizClient.getSanitizedSecretByNameAndVersion(secret.getName(), secret.getVersion()))
        .thenThrow(new NotFoundException()); // Call checks for existence.

    when(keywhizClient.createSecret(secret.getName(), "", secret.getSecret(),
        true, secret.getMetadata()))
        .thenReturn(secretDetailResponse);

    addAction.run();
    verify(keywhizClient, times(1)).createSecret(secret.getName(), "", secret.getSecret(),
        true, secret.getMetadata());
  }

  @Test
  public void addCallsAddForClient() throws Exception {
    addActionConfig.addType = Arrays.asList("client");
    addActionConfig.name = client.getName();

    when(keywhizClient.getClientByName(client.getName())).thenThrow(
        new NotFoundException());

    addAction.run();
    verify(keywhizClient)
        .createClient(addActionConfig.name);
  }

  @Test
  public void addSecretCanAssignGroup() throws Exception {
    addActionConfig.addType = Arrays.asList("secret");
    addActionConfig.name = secret.getDisplayName();
    addActionConfig.group = group.getName();

    addAction.stream = new ByteArrayInputStream(base64Decoder.decode(secret.getSecret()));
    when(keywhizClient.getGroupByName(group.getName())).thenReturn(group);

    when(keywhizClient.getSanitizedSecretByNameAndVersion(secret.getName(), secret.getVersion()))
        .thenThrow(new NotFoundException()); // Call checks for existence.

    when(keywhizClient.createSecret(secret.getName(), "", secret.getSecret(),
        true, secret.getMetadata())).thenReturn(secretDetailResponse);

    addAction.run();
    verify(keywhizClient).grantSecretToGroupByIds((int) secret.getId(), (int) group.getId());
  }

  @Test
  public void addCreatesWithoutVersionByDefault() throws Exception {
    addActionConfig.addType = Arrays.asList("secret");
    addActionConfig.name = secret.getName(); // Name without version

    addAction.stream = new ByteArrayInputStream(base64Decoder.decode(secret.getSecret()));

    when(keywhizClient.getSanitizedSecretByNameAndVersion(secret.getName(), ""))
        .thenThrow(new NotFoundException()); // Call checks for existence.

    when(keywhizClient.createSecret(secret.getName(), "", secret.getSecret(),
        false, secret.getMetadata()))
        .thenReturn(secretDetailResponse);

    addAction.run();

    verify(keywhizClient, never()).createSecret(secret.getName(), "", secret.getSecret(),
          true, secret.getMetadata());
  }

  @Test
  public void addCreatesVersionedSecretWhenVersionInName() throws Exception {
    addActionConfig.addType = Arrays.asList("secret");
    addActionConfig.name = secret.getDisplayName(); // Name includes version, e.g. newSecret..df97a

    addAction.stream = new ByteArrayInputStream(base64Decoder.decode(secret.getSecret()));
    when(keywhizClient.getSanitizedSecretByNameAndVersion(secret.getName(), secret.getVersion()))
        .thenThrow(new NotFoundException()); // Call checks for existence.

    when(keywhizClient.createSecret(secret.getName(), "", secret.getSecret(),
        true, secret.getMetadata()))
        .thenReturn(secretDetailResponse);

    addAction.run();

    verify(keywhizClient, times(1)).createSecret(secret.getName(), "", secret.getSecret(),
        true, secret.getMetadata());
  }

  @Test
  public void addCanCreateWithVersion() throws Exception {
    addActionConfig.addType = Arrays.asList("secret");
    addActionConfig.name = secret.getDisplayName();
    addActionConfig.withVersion = true;

    addAction.stream = new ByteArrayInputStream(base64Decoder.decode(secret.getSecret()));
    when(keywhizClient.getSanitizedSecretByNameAndVersion(secret.getName(), secret.getVersion()))
        .thenThrow(new NotFoundException()); // Call checks for existence.

    when(keywhizClient.createSecret(secret.getName(), "", secret.getSecret(),
        addActionConfig.withVersion, secret.getMetadata()))
        .thenReturn(secretDetailResponse);

    addAction.run();

    verify(keywhizClient, times(1)).createSecret(secret.getName(), "", secret.getSecret(),
        true, secret.getMetadata());
  }

  @Test
  public void addWithMetadata() throws Exception {
    addActionConfig.addType = Arrays.asList("secret");
    addActionConfig.name = secret.getDisplayName();
    addActionConfig.json = "{\"owner\":\"example-name\", \"group\":\"example-group\"}";

    addAction.stream = new ByteArrayInputStream(base64Decoder.decode(secret.getSecret()));
    when(keywhizClient.getSanitizedSecretByNameAndVersion(secret.getName(), secret.getVersion()))
        .thenThrow(new NotFoundException()); // Call checks for existence.

    ImmutableMap<String,String> expected = ImmutableMap.of("owner", "example-name", "group", "example-group");

    when(keywhizClient.createSecret(secret.getName(), "", secret.getSecret(),
        true, expected))
        .thenReturn(secretDetailResponse);

    addAction.run();

    verify(keywhizClient, times(1)).createSecret(secret.getName(), "", secret.getSecret(),
        true, expected);
  }

  @Test(expected = IllegalArgumentException.class)
  public void addThrowsIfMetadataHasBadKeys() throws Exception {
    addActionConfig.addType = Arrays.asList("secret");
    addActionConfig.name = secret.getDisplayName();
    addActionConfig.json = "{\"ThisIsABadKey\":\"doh\"}";

    addAction.stream = new ByteArrayInputStream(base64Decoder.decode(secret.getSecret()));
    when(keywhizClient.getSanitizedSecretByNameAndVersion(secret.getName(), secret.getVersion()))
        .thenThrow(new NotFoundException()); // Call checks for existence.

    addAction.run();
  }

  @Test(expected = AssertionError.class)
  public void addThrowsIfAddGroupFails() throws Exception {
    addActionConfig.addType = Arrays.asList("group");
    addActionConfig.name = group.getName();

    when(keywhizClient.getGroupByName(addActionConfig.name)).thenReturn(group);

    addAction.run();
  }

  @Test(expected = AssertionError.class)
  public void addThrowsIfAddSecretFails() throws Exception {
    addActionConfig.addType = Arrays.asList("secret");
    addActionConfig.name = secret.getDisplayName();

    when(keywhizClient.getSanitizedSecretByNameAndVersion(secret.getName(), secret.getVersion()))
        .thenReturn(sanitizedSecret);

    addAction.run();
  }

  @Test(expected = AssertionError.class)
  public void addThrowsIfAddClientFails() throws Exception {
    addActionConfig.addType = Arrays.asList("client");
    addActionConfig.name = client.getName();

    when(keywhizClient.getClientByName(addActionConfig.name)).thenReturn(client);

    addAction.run();
  }

  @Test(expected = IllegalArgumentException.class)
  public void addThrowsIfNoTypeSpecified() throws Exception {
    addActionConfig.addType = null;

    addAction.run();
  }

  @Test(expected = AssertionError.class)
  public void addThrowsIfInvalidType() throws Exception {
    addActionConfig.addType = Arrays.asList("invalid_type");
    addActionConfig.name = "any-name";

    addAction.run();
  }

  @Test(expected = IllegalArgumentException.class)
  public void addValidatesGroupName() throws Exception {
    addActionConfig.addType = Arrays.asList("group");
    addActionConfig.name = "Invalid Name";

    addAction.run();
  }

  @Test(expected = IllegalArgumentException.class)
  public void addValidatesClientName() throws Exception {
    addActionConfig.addType = Arrays.asList("client");
    addActionConfig.name = "Invalid Name";

    addAction.run();
  }

  @Test(expected = IllegalArgumentException.class)
  public void addValidatesSecretName() throws Exception {
    addActionConfig.addType = Arrays.asList("secret");
    addActionConfig.name = "Invalid Name";

    addAction.run();
  }
}
