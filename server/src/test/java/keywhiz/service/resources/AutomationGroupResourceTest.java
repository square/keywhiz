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

package keywhiz.service.resources;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.dropwizard.jersey.params.LongParam;
import java.time.OffsetDateTime;
import java.util.Optional;
import javax.ws.rs.core.Response;
import keywhiz.api.CreateGroupRequest;
import keywhiz.api.GroupDetailResponse;
import keywhiz.api.model.AutomationClient;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.auth.User;
import keywhiz.service.daos.AclDAO;
import keywhiz.service.daos.GroupDAO;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class AutomationGroupResourceTest {
  @Rule public TestRule mockito = new MockitoJUnitRule(this);

  @Mock GroupDAO groupDAO;
  @Mock AclDAO aclDAO;
  User user = User.named("user");
  OffsetDateTime now = OffsetDateTime.now();
  AutomationClient automation = AutomationClient.of(
      new Client(1, "automation", "Automation client", now, "test", now, "test", true, true));

  AutomationGroupResource resource;

  @Before public void setUp() {
    resource = new AutomationGroupResource(groupDAO, aclDAO);
  }

  @Test public void findGroupById() {
    Group group = new Group(50, "testGroup", "testing group", now, "automation client", now, "automation client");
    when(groupDAO.getGroupById(50)).thenReturn(Optional.of(group));
    when(aclDAO.getClientsFor(group)).thenReturn(ImmutableSet.of());
    when(aclDAO.getSanitizedSecretsFor(group)).thenReturn(ImmutableSet.of());

    GroupDetailResponse expectedResponse = GroupDetailResponse.fromGroup(group,
        ImmutableList.of(), ImmutableList.of());
    GroupDetailResponse response = resource.getGroupById(automation, new LongParam("50"));
    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test public void findGroupByName() {
    Group group = new Group(50, "testGroup", "testing group", now, "automation client", now, "automation client");
    when(groupDAO.getGroup("testGroup")).thenReturn(Optional.of(group));
    when(aclDAO.getClientsFor(group)).thenReturn(ImmutableSet.of());
    when(aclDAO.getSanitizedSecretsFor(group)).thenReturn(ImmutableSet.of());

    GroupDetailResponse expectedResponse = GroupDetailResponse.fromGroup(group,
        ImmutableList.of(), ImmutableList.of());
    Response response = resource.getGroupByName(Optional.of("testGroup"));
    assertThat(response.getEntity()).isEqualTo(expectedResponse);
  }

  @Test public void groupIncludesClientsAndSecrets() {
    Group group = new Group(50, "testGroup", "testing group", now, "automation client", now, "automation client");
    Client groupClient =
        new Client(1, "firstClient", "Group client", now, "test", now, "test", true, true);
    SanitizedSecret firstGroupSecret =
        SanitizedSecret.of(1, "name1", "v1", "desc", now, "test", now, "test", null, "", null);
    SanitizedSecret secondGroupSecret =
        SanitizedSecret.of(2, "name2", "v1", "desc", now, "test", now, "test", null, "", null);

    when(groupDAO.getGroup("testGroup")).thenReturn(Optional.of(group));
    when(aclDAO.getClientsFor(group)).thenReturn(ImmutableSet.of(groupClient));
    when(aclDAO.getSanitizedSecretsFor(group))
        .thenReturn(ImmutableSet.of(firstGroupSecret, secondGroupSecret));

    GroupDetailResponse expectedResponse = GroupDetailResponse.fromGroup(group,
        ImmutableList.of(firstGroupSecret, secondGroupSecret), ImmutableList.of(groupClient));
    Response response = resource.getGroupByName(Optional.of("testGroup"));
    assertThat(response.getEntity()).isEqualTo(expectedResponse);
  }

  @Test public void createNewGroup() {
    Group group = new Group(50, "testGroup", "testing group", now, "automation client", now, "automation client");

    CreateGroupRequest request = new CreateGroupRequest("testGroup", null);

    when(groupDAO.getGroup("testGroup")).thenReturn(Optional.empty());
    when(groupDAO.createGroup(group.getName(), automation.getName(), Optional.empty())).thenReturn(500L);
    when(groupDAO.getGroupById(500L)).thenReturn(Optional.of(group));
    Group responseGroup = resource.createGroup(automation, request);

    assertThat(responseGroup).isEqualTo(group);
  }
}
