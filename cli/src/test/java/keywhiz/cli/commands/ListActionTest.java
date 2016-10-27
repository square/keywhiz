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

import java.util.Arrays;
import keywhiz.cli.Printing;
import keywhiz.cli.configs.ListActionConfig;
import keywhiz.client.KeywhizClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.mockito.Mockito.verify;

public class ListActionTest {
  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock KeywhizClient keywhizClient;
  @Mock Printing printing;

  ListActionConfig listActionConfig;
  ListAction listAction;

  @Before
  public void setUp() {
    listActionConfig = new ListActionConfig();
    listAction = new ListAction(listActionConfig, keywhizClient, printing);
  }

  @Test
  public void listCallsPrintForListAll() throws Exception {
    listActionConfig.listType = null;
    listAction.run();
    verify(printing).printAllSanitizedSecrets(keywhizClient.allSecrets());
  }

  @Test
  public void listCallsPrintForListGroups() throws Exception {
    listActionConfig.listType = Arrays.asList("groups");
    listAction.run();

    verify(printing).printAllGroups(keywhizClient.allGroups());
  }

  @Test
  public void listCallsPrintForListClients() throws Exception {
    listActionConfig.listType = Arrays.asList("clients");
    listAction.run();

    verify(printing).printAllClients(keywhizClient.allClients());
  }

  @Test
  public void listCallsPrintForListSecrets() throws Exception {
    listActionConfig.listType = Arrays.asList("secrets");
    listAction.run();

    verify(printing).printAllSanitizedSecrets(keywhizClient.allSecrets());
  }

  @Test
  public void listCallsPrintForListSecretsBatched() throws Exception {
    listActionConfig.listType = Arrays.asList("secrets");
    listActionConfig.idx = 0;
    listActionConfig.num = 10;
    listActionConfig.newestFirst = false;
    listAction.run();

    verify(printing).printAllSanitizedSecrets(keywhizClient.allSecretsBatched(0, 10, false));
  }

  @Test
  public void listCallsPrintForListSecretsBatchedWithDefault() throws Exception {
    listActionConfig.listType = Arrays.asList("secrets");
    listActionConfig.idx = 5;
    listActionConfig.num = 10;
    listAction.run();

    verify(printing).printAllSanitizedSecrets(keywhizClient.allSecretsBatched(5, 10, true));
  }

  @Test
  public void listCallsErrorsCorrectly() throws Exception {
    listActionConfig.listType = Arrays.asList("secrets");
    listActionConfig.idx = 5;
    boolean error = false;
    try {
      listAction.run();
    } catch (AssertionError e) {
      error = true;
    }
    assert(error);

    listActionConfig.listType = Arrays.asList("secrets");
    listActionConfig.idx = 5;
    listActionConfig.num = -5;
    error = false;
    try {
      listAction.run();
    } catch (IllegalArgumentException e) {
      error = true;
    }
    assert(error);
  }

  @Test(expected = AssertionError.class)
  public void listThrowsIfInvalidType() throws Exception {
    listActionConfig.listType = Arrays.asList("invalid_type");
    listAction.run();
  }
}
