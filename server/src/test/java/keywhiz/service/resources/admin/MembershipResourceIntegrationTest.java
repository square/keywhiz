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
package keywhiz.service.resources.admin;

import java.io.IOException;
import keywhiz.IntegrationTestRule;
import keywhiz.TestClients;
import keywhiz.api.model.Group;
import keywhiz.client.KeywhizClient;
import keywhiz.commands.DbSeedCommand;
import org.assertj.core.api.Condition;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static org.assertj.core.api.Assertions.assertThat;

public class MembershipResourceIntegrationTest {
  KeywhizClient keywhizClient;

  @ClassRule public static final RuleChain chain = IntegrationTestRule.rule();

  @Before public void setUp() {
    keywhizClient = TestClients.keywhizClient();
  }

  /** @return condition where group has given id. */
  private static Condition<Group> groupId(final long id) {
    return new Condition<Group>() {
      @Override public boolean matches(Group group) {
        return group.getId() == id;
      }
    };
  }

  @Test public void allowingSecretInGroup() throws IOException {
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());

    assertThat(keywhizClient.secretDetailsForId(741).groups).doNotHave(groupId(919));
    keywhizClient.grantSecretToGroupByIds(741, 919);
    assertThat(keywhizClient.secretDetailsForId(741).groups).haveExactly(1, groupId(919));
  }

  @Test(expected = KeywhizClient.NotFoundException.class)
  public void allowingMissingSecretInGroup() throws IOException {
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());
    keywhizClient.grantSecretToGroupByIds(4539475, 237694);
  }

  @Test(expected = KeywhizClient.UnauthorizedException.class)
  public void adminRejectsNonKeywhizUsers() throws IOException {
    keywhizClient.login("username", "password".toCharArray());
    keywhizClient.grantSecretToGroupByIds(741, 916);
  }

  @Test(expected = KeywhizClient.UnauthorizedException.class)
  public void adminRejectsWithoutCookie() throws IOException {
    keywhizClient.grantSecretToGroupByIds(741, 916);
  }

  @Test public void revokesSecretFromGroup() throws IOException {
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());

    assertThat(keywhizClient.secretDetailsForId(737).groups).haveExactly(1, groupId(918));
    keywhizClient.revokeSecretFromGroupByIds(737, 918);
    assertThat(keywhizClient.secretDetailsForId(737).groups).doNotHave(groupId(918));
  }

  @Test(expected = KeywhizClient.NotFoundException.class)
  public void revokingMissingSecretFromGroup() throws IOException {
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());
    keywhizClient.revokeSecretFromGroupByIds(4539475, 237694);
  }

  @Test public void enrollsClientInGroup() throws IOException {
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());

    assertThat(keywhizClient.clientDetailsForId(770).groups).doNotHave(groupId(918));
    keywhizClient.enrollClientInGroupByIds(770, 918);
    assertThat(keywhizClient.clientDetailsForId(770).groups).haveExactly(1, groupId(918));
  }

  @Test(expected = KeywhizClient.NotFoundException.class)
  public void enrollingMissingClientInGroup() throws IOException {
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());
    keywhizClient.enrollClientInGroupByIds(4539575, 237694);
  }

  @Test public void evictsClientFromGroup() throws IOException {
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());

    assertThat(keywhizClient.clientDetailsForId(770).groups).haveExactly(1, groupId(917));
    keywhizClient.evictClientFromGroupByIds(770, 917);
    assertThat(keywhizClient.clientDetailsForId(770).groups).doNotHave(groupId(917));
  }

  @Test(expected = KeywhizClient.NotFoundException.class)
  public void evictingMissingClientFromGroup() throws IOException {
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());

    keywhizClient.evictClientFromGroupByIds(4539475, 237694);
  }
}
