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
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import keywhiz.api.ApiDate;
import keywhiz.api.SecretDetailResponse;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import keywhiz.cli.configs.AddActionConfig;
import keywhiz.cli.configs.UpdateActionConfig;
import keywhiz.client.KeywhizClient;
import keywhiz.client.KeywhizClient.NotFoundException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UpdateActionTest {
  private static final ApiDate NOW = ApiDate.now();
  private static final Base64.Decoder base64Decoder = Base64.getDecoder();

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock KeywhizClient keywhizClient;

  UpdateActionConfig updateActionConfig;
  UpdateAction updateAction;

  Secret secret = new Secret(15, "newSecret", null, () -> "c2VjcmV0MQ==", "checksum", NOW, null, NOW, null, null, null,
      ImmutableMap.of(), 0, 1L);
  SecretDetailResponse secretDetailResponse = SecretDetailResponse.fromSecret(secret, null, null);

  @Before
  public void setUp() {
    updateActionConfig = new UpdateActionConfig();
    updateAction = new UpdateAction(updateActionConfig, keywhizClient, Jackson.newObjectMapper());
  }

  @Test
  public void updateCallsUpdateForSecret() throws Exception {
    updateActionConfig.name = secret.getDisplayName();
    updateActionConfig.expiry = "2006-01-02T15:04:05Z";
    updateActionConfig.contentProvided = true;

    byte[] content = base64Decoder.decode(secret.getSecret());
    updateAction.stream = new ByteArrayInputStream(content);
    when(keywhizClient.getSanitizedSecretByName(secret.getName()))
        .thenThrow(new NotFoundException()); // Call checks for existence.

    when(keywhizClient.updateSecret(secret.getName(), false, "", true, content, false,
        updateActionConfig.getMetadata(Jackson.newObjectMapper()), true, 1136214245))
        .thenReturn(secretDetailResponse);

    updateAction.run();
    verify(keywhizClient, times(1)).updateSecret(secret.getName(), false, "", true, content, false,
        updateActionConfig.getMetadata(Jackson.newObjectMapper()), true, 1136214245);
  }

  @Test
  public void updateWithMetadata() throws Exception {
    updateActionConfig.name = secret.getDisplayName();
    updateActionConfig.description = "metadata test";
    updateActionConfig.json = "{\"owner\":\"example-name\", \"group\":\"example-group\"}";
    updateActionConfig.contentProvided = true;

    byte[] content = base64Decoder.decode(secret.getSecret());
    updateAction.stream = new ByteArrayInputStream(content);
    when(keywhizClient.getSanitizedSecretByName(secret.getName()))
        .thenThrow(new NotFoundException()); // Call checks for existence.

    when(keywhizClient.updateSecret(secret.getName(), true, "metadata test", true, content, true,
        updateActionConfig.getMetadata(Jackson.newObjectMapper()), false, 0))
        .thenReturn(secretDetailResponse);

    updateAction.run();

    verify(keywhizClient, times(1)).updateSecret(secret.getName(), true, "metadata test", true, content, true,
        updateActionConfig.getMetadata(Jackson.newObjectMapper()), false, 0);
  }

  @Test
  public void updateWithContentPipedInButNoContentFlag() throws Exception {
    updateActionConfig.name = secret.getDisplayName();
    updateActionConfig.description = "content test";
    updateActionConfig.json = "{\"owner\":\"example-name\", \"group\":\"example-group\"}";
    updateActionConfig.contentProvided = false;

    byte[] content = base64Decoder.decode(secret.getSecret());
    updateAction.stream = new ByteArrayInputStream(content);
    when(keywhizClient.getSanitizedSecretByName(secret.getName()))
        .thenThrow(new NotFoundException()); // Call checks for existence.

    // If the content flag is not specified, the content should not be sent to Keywhiz
    when(keywhizClient.updateSecret(secret.getName(), true, "content test", false, new byte[]{}, true,
        updateActionConfig.getMetadata(Jackson.newObjectMapper()), false, 0))
        .thenReturn(secretDetailResponse);

    updateAction.run();

    // Content should not have been sent to Keywhiz, even though it was piped in (warning should also have been printed to stdout)
    verify(keywhizClient, times(1)).updateSecret(secret.getName(), true, "content test", false, new byte[]{}, true,
        updateActionConfig.getMetadata(Jackson.newObjectMapper()), false, 0);
  }

  @Test(expected = IllegalArgumentException.class)
  public void updateThrowsIfMetadataHasBadKeys() throws Exception {
    updateActionConfig.name = secret.getDisplayName();
    updateActionConfig.json = "{\"ThisIsABadKey\":\"doh\"}";

    updateAction.stream = new ByteArrayInputStream(base64Decoder.decode(secret.getSecret()));
    when(keywhizClient.getSanitizedSecretByName(secret.getName()))
        .thenThrow(new NotFoundException()); // Call checks for existence.

    updateAction.run();
  }


  @Test(expected = IllegalArgumentException.class)
  public void updateValidatesSecretName() throws Exception {
    updateActionConfig.name = "Invalid Name";

    updateAction.run();
  }
}
