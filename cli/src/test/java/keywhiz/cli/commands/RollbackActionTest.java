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
import javax.ws.rs.BadRequestException;
import keywhiz.api.ApiDate;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import keywhiz.cli.configs.RollbackActionConfig;
import keywhiz.client.KeywhizClient;
import keywhiz.client.KeywhizClient.NotFoundException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RollbackActionTest {
  private static final ApiDate NOW = ApiDate.now();

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock KeywhizClient keywhizClient;

  RollbackActionConfig rollbackActionConfig;
  RollbackAction rollbackAction;

  Secret secret = new Secret(0, "secret", null, () -> "c2VjcmV0MQ==", "checksum", NOW, null, NOW, null, null, null,
      ImmutableMap.of(), 0, 1L, NOW, null);
  SanitizedSecret sanitizedSecret = SanitizedSecret.fromSecret(secret);

  ByteArrayInputStream yes;
  ByteArrayInputStream no;

  @Before
  public void setUp() {
    rollbackActionConfig = new RollbackActionConfig();
    rollbackAction = new RollbackAction(rollbackActionConfig, keywhizClient);

    yes = new ByteArrayInputStream("Y".getBytes(UTF_8));
    no = new ByteArrayInputStream("\nOther\nN".getBytes(UTF_8)); // empty line, not yes or no, then no
  }

  @Test
  public void rollbackCallsRollback() throws Exception {
    rollbackAction.inputStream = yes;
    rollbackActionConfig.name = secret.getDisplayName();
    rollbackActionConfig.id = 1L;

    when(keywhizClient.getSanitizedSecretByName(secret.getName())).thenReturn(sanitizedSecret);

    rollbackAction.run();
    verify(keywhizClient).rollbackSecret(sanitizedSecret.name(), rollbackActionConfig.id);
  }

  @Test
  public void rollbackCallsRollbackWithNegativeId() throws Exception {
    rollbackAction.inputStream = yes;
    rollbackActionConfig.name = secret.getDisplayName();
    rollbackActionConfig.id = -1L;

    when(keywhizClient.getSanitizedSecretByName(secret.getName())).thenReturn(sanitizedSecret);

    rollbackAction.run();
    verify(keywhizClient).rollbackSecret(sanitizedSecret.name(), rollbackActionConfig.id);
  }

  @Test
  public void rollbackSkipsWithoutConfirmation() throws Exception {
    rollbackAction.inputStream = no;
    rollbackActionConfig.name = secret.getDisplayName();
    rollbackActionConfig.id = 1L;

    when(keywhizClient.getSanitizedSecretByName(secret.getName())).thenReturn(sanitizedSecret);

    rollbackAction.run();
    verify(keywhizClient, never()).rollbackSecret(anyString(), anyLong());
  }

  @Test(expected = AssertionError.class)
  public void rollbackThrowsIfFindSecretFails() throws Exception {
    rollbackAction.inputStream = yes;
    rollbackActionConfig.name = secret.getDisplayName();
    rollbackActionConfig.id = 1L;

    when(keywhizClient.getSanitizedSecretByName(secret.getName())).thenThrow(new NotFoundException());

    rollbackAction.run();
  }

  @Test(expected = IllegalStateException.class)
  public void rollbackThrowsIfIllegalIdInput() throws Exception {
    rollbackAction.inputStream = yes;
    rollbackActionConfig.name = secret.getDisplayName();
    rollbackActionConfig.id = 1L;

    when(keywhizClient.getSanitizedSecretByName(secret.getName())).thenReturn(sanitizedSecret);
    when(keywhizClient.rollbackSecret(secret.getDisplayName(), 1L)).thenThrow(new IllegalStateException());

    rollbackAction.run();
  }

  @Test(expected = BadRequestException.class)
  public void rollbackThrowsIfInvalidIdInput() throws Exception {
    rollbackAction.inputStream = yes;
    rollbackActionConfig.name = secret.getDisplayName();
    rollbackActionConfig.id = 1L;

    when(keywhizClient.getSanitizedSecretByName(secret.getName())).thenReturn(sanitizedSecret);
    when(keywhizClient.rollbackSecret(secret.getDisplayName(), 1L)).thenThrow(new BadRequestException());

    rollbackAction.run();
  }

  @Test(expected = IllegalArgumentException.class)
  public void rollbackThrowsIfNoSecretSpecified() throws Exception {
    rollbackActionConfig.name = null;
    rollbackActionConfig.id = 1L;

    rollbackAction.run();
  }

  @Test(expected = IllegalArgumentException.class)
  public void rollbackThrowsIfNoIdSpecified() throws Exception {
    rollbackActionConfig.name = "test-name";
    rollbackActionConfig.id = null;

    rollbackAction.run();
  }

  @Test(expected = IllegalArgumentException.class)
  public void rollbackValidatesSecretName() throws Exception {
    rollbackActionConfig.name = "Invalid Name";
    rollbackAction.run();
  }
}
