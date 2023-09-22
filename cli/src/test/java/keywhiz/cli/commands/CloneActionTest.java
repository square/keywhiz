/*
 * Copyright (C) 2023 Square, Inc.
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
import keywhiz.api.ApiDate;
import keywhiz.api.SecretDetailResponse;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import keywhiz.cli.Printing;
import keywhiz.cli.configs.CloneActionConfig;
import keywhiz.client.KeywhizClient;
import keywhiz.client.KeywhizClient.NotFoundException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.IOException;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CloneActionTest {
  private static final ApiDate NOW = ApiDate.now();

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock KeywhizClient keywhizClient;
  @Mock Printing printing;

  CloneActionConfig cloneActionConfig;
  CloneAction cloneAction;

  Secret oldSecret = new Secret(0, "oldSecret", null, null, () ->  "c2VjcmV0MQ==", "checksum", NOW, null, NOW, null, null, null,
      ImmutableMap.of(), 0, 1L, NOW, null);
  Secret newSecret = new Secret(1, "newSecret", null, null, () ->  "c2VjcmV0MQ==", "checksum", NOW, null, NOW, null, null, null,
      ImmutableMap.of(), 0, 1L, NOW, null);
  Group group = new Group(5, "group", null, null, null, null, null, null);

  SecretDetailResponse detailResponse = SecretDetailResponse.fromSecret(
          newSecret, ImmutableList.of(group), ImmutableList.of());

  @Before
  public void setUp() throws IOException {
    cloneActionConfig = new CloneActionConfig();
    cloneAction = new CloneAction(cloneActionConfig, keywhizClient, printing);

    when(keywhizClient.getSanitizedSecretByName("oldSecret")).thenReturn(SanitizedSecret.fromSecret(oldSecret));
    when(keywhizClient.secretDetailsForId(0)).thenReturn(detailResponse);
  }

  @Test
  public void cloneCopiesSecret() throws Exception {
    cloneActionConfig.name = "oldSecret";
    cloneActionConfig.newName = "newSecret";

    when(keywhizClient.cloneSecret("oldSecret", "newSecret")).thenReturn(detailResponse);

    cloneAction.run();
    verify(keywhizClient).cloneSecret("oldSecret", "newSecret");
  }

  @Test
  public void cloneCopiesGroupAssignments() throws Exception {
    cloneActionConfig.name = "oldSecret";
    cloneActionConfig.newName = "newSecret";

    when(keywhizClient.cloneSecret("oldSecret", "newSecret")).thenReturn(detailResponse);

    cloneAction.run();
    verify(keywhizClient).grantSecretToGroupByIds(1, 5);
  }

  @Test
  public void cloneCallsPrint() throws Exception {
    cloneActionConfig.name = "oldSecret";
    cloneActionConfig.newName = "newSecret";

    when(keywhizClient.cloneSecret("oldSecret", "newSecret")).thenReturn(detailResponse);

    cloneAction.run();
    verify(printing).printSecretWithDetails(1);
  }

  @Test
  public void cloneThrowsIfOldSecretDoesNotExist() throws Exception {
    cloneActionConfig.name = "oldSecret";
    cloneActionConfig.newName = "newSecret";

    when(keywhizClient.cloneSecret("oldSecret", "newSecret")).thenThrow(NotFoundException.class);
    AssertionError ex = assertThrows(
            AssertionError.class,
            () -> cloneAction.run()
    );
    assertTrue(ex.getMessage().contains("Source secret doesn't exist"));
  }

  @Test
  public void cloneThrowsIfNewNameIsInvalid() throws Exception {
    cloneActionConfig.name = "oldSecret";
    cloneActionConfig.newName = "totally invalid name!!!😱";

    when(keywhizClient.cloneSecret("oldSecret", "newSecret")).thenReturn(detailResponse);

    IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> cloneAction.run()
    );
    assertTrue(ex.getMessage().contains("Invalid name"));
  }

  @Test
  public void cloneThrowsIfNewNameConflicts() throws Exception {
    cloneActionConfig.name = "oldSecret";
    cloneActionConfig.newName = "newSecret";

    when(keywhizClient.cloneSecret("oldSecret", "newSecret")).thenThrow(KeywhizClient.ConflictException.class);
    AssertionError ex = assertThrows(
            AssertionError.class,
            () -> cloneAction.run()
    );
    assertTrue(ex.getMessage().contains("New secret name is already in use"));
  }

  @Test
  public void cloneWrapsIOException() throws Exception {
    cloneActionConfig.name = "oldSecret";
    cloneActionConfig.newName = "newSecret";

    when(keywhizClient.cloneSecret("oldSecret", "newSecret")).thenThrow(new IOException("uh oh spaghettios!"));
    RuntimeException ex = assertThrows(
            RuntimeException.class,
            () -> cloneAction.run()
    );
    assertTrue(ex.getMessage().contains("uh oh spaghettios!"));
  }
}
