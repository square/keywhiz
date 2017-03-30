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

package keywhiz.service.resources.automation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.dropwizard.jersey.params.LongParam;
import java.util.Optional;
import javax.ws.rs.core.Response;
import keywhiz.api.ApiDate;
import keywhiz.api.CreateGroupRequest;
import keywhiz.api.GroupDetailResponse;
import keywhiz.api.model.AutomationClient;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.log.AuditLog;
import keywhiz.log.SimpleLogger;
import keywhiz.service.daos.AclDAO;
import keywhiz.service.daos.GroupDAO;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class AutomationGroupResourceTest {
  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock GroupDAO groupDAO;
  @Mock AclDAO aclDAO;
  ApiDate now = ApiDate.now();
  AutomationClient automation = AutomationClient.of(
      new Client(1, "automation", "Automation client", now, "test", now, "test", null, true, true));
  AuditLog auditLog = new SimpleLogger();

  AutomationGroupResource resource;

  @Before public void setUp() {
    resource = new AutomationGroupResource(groupDAO, aclDAO, auditLog);
  }

  @Test public void findGroupById() {
    Group group = new Group(50, "testGroup", "testing group", now, "automation client", now,
        "automation client", ImmutableMap.of("app", "keywhiz"));
    when(groupDAO.getGroupById(50)).thenReturn(Optional.of(group));
    when(aclDAO.getClientsFor(group)).thenReturn(ImmutableSet.of());
    when(aclDAO.getSanitizedSecretsFor(group)).thenReturn(ImmutableSet.of());

    GroupDetailResponse expectedResponse = GroupDetailResponse.fromGroup(group,
        ImmutableList.of(), ImmutableList.of());
    GroupDetailResponse response = resource.getGroupById(automation, new LongParam("50"));
    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test public void findGroupByName() {
    Group group = new Group(50, "testGroup", "testing group", now, "automation client", now,
        "automation client", ImmutableMap.of("app", "keywhiz"));
    when(groupDAO.getGroup("testGroup")).thenReturn(Optional.of(group));
    when(aclDAO.getClientsFor(group)).thenReturn(ImmutableSet.of());
    when(aclDAO.getSanitizedSecretsFor(group)).thenReturn(ImmutableSet.of());

    GroupDetailResponse expectedResponse = GroupDetailResponse.fromGroup(group,
        ImmutableList.of(), ImmutableList.of());
    Response response = resource.getGroupByName(automation, Optional.of("testGroup"));
    assertThat(response.getEntity()).isEqualTo(expectedResponse);
  }

  @Test public void groupIncludesClientsAndSecrets() {
    Group group = new Group(50, "testGroup", "testing group", now, "automation client", now,
        "automation client", ImmutableMap.of("app", "keywhiz"));
    Client groupClient =
        new Client(1, "firstClient", "Group client", now, "test", now, "test", null, true, true);
    SanitizedSecret firstGroupSecret =
        SanitizedSecret.of(1, "name1", "desc", "checksum", now, "test", now, "test", null, "", null, 1136214245, 125L);
    SanitizedSecret secondGroupSecret =
        SanitizedSecret.of(2, "name2", "desc", "checksum", now, "test", now, "test", null, "", null, 1136214245, 250L);

    when(groupDAO.getGroup("testGroup")).thenReturn(Optional.of(group));
    when(aclDAO.getClientsFor(group)).thenReturn(ImmutableSet.of(groupClient));
    when(aclDAO.getSanitizedSecretsFor(group))
        .thenReturn(ImmutableSet.of(firstGroupSecret, secondGroupSecret));

    GroupDetailResponse expectedResponse = GroupDetailResponse.fromGroup(group,
        ImmutableList.of(firstGroupSecret, secondGroupSecret), ImmutableList.of(groupClient));
    Response response = resource.getGroupByName(automation, Optional.of("testGroup"));
    assertThat(response.getEntity()).isEqualTo(expectedResponse);
  }

  @Test public void createNewGroup() {
    Group group = new Group(50, "testGroup", "testing group", now, "automation client", now,
        "automation client", ImmutableMap.of("app", "keywhiz"));

    CreateGroupRequest request = new CreateGroupRequest("testGroup", null, null);

    when(groupDAO.getGroup("testGroup")).thenReturn(Optional.empty());
    when(groupDAO.createGroup(group.getName(), automation.getName(), "", ImmutableMap.of())).thenReturn(500L);
    when(groupDAO.getGroupById(500L)).thenReturn(Optional.of(group));
    Group responseGroup = resource.createGroup(automation, request);

    assertThat(responseGroup).isEqualTo(group);
  }
}
