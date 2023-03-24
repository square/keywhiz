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

import com.google.common.base.Throwables;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.SecretDeletionMode;
import keywhiz.cli.configs.UndeleteActionConfig;
import keywhiz.client.KeywhizClient;
import keywhiz.client.KeywhizClient.NotFoundException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UndeleteActionTest {
  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Mock KeywhizClient keywhizClient;

  UndeleteActionConfig undeleteActionConfig;
  UndeleteAction undeleteAction;

  ByteArrayInputStream yes;
  ByteArrayInputStream no;

  @Before
  public void setUp() {
    undeleteActionConfig = new UndeleteActionConfig();
    undeleteAction = new UndeleteAction(undeleteActionConfig, keywhizClient);
  }

  @Test
  public void undeletesSecret() {
    undeleteActionConfig.objectType = "secret";
    undeleteActionConfig.id = 123L;
    undeleteAction.run();
    try {
      verify(keywhizClient).undeleteSecret(123L);
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }

  @Test
  public void rejectsUnsupportedObjectType() {
    undeleteActionConfig.objectType = "group";
    undeleteActionConfig.id = 123L;
    thrown.expect(IllegalArgumentException.class);
    undeleteAction.run();
  }
}
