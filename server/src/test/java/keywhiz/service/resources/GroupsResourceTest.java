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

import com.google.common.collect.ImmutableSet;
import io.dropwizard.jersey.params.LongParam;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import keywhiz.api.CreateGroupRequest;
import keywhiz.api.GroupDetailResponse;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.auth.User;
import keywhiz.service.daos.AclDAO;
import keywhiz.service.daos.GroupDAO;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class GroupsResourceTest {
  @Mock AclDAO aclDAO;
  @Mock GroupDAO groupDAO;

  User user = User.named("user");
  OffsetDateTime now = OffsetDateTime.now();
  Group group = new Group(1, "group", "desc", now, "creator", now, "creator");

  GroupsResource resource;

  @Before public void setUp() {
    resource = new GroupsResource(aclDAO, groupDAO);
  }

  @Test public void listingOfGroups() {
    Group group1 = new Group(1, "group1", "desc", now, "creator", now, "updater");
    Group group2 = new Group(2, "group2", "desc", now, "creator", now, "updater");
    when(groupDAO.getGroups()).thenReturn(ImmutableSet.of(group1, group2));

    List<Group> response = resource.listGroups(user);
    assertThat(response).containsOnly(group1, group2);
  }

  @Test public void createsGroup() {
    CreateGroupRequest request = new CreateGroupRequest("newGroup", "description");
    when(groupDAO.getGroup("newGroup")).thenReturn(Optional.empty());
    when(groupDAO.createGroup("newGroup", "user", Optional.of("description"))).thenReturn(55L);
    when(groupDAO.getGroupById(55L)).thenReturn(Optional.of(group));
    when(aclDAO.getSanitizedSecretsFor(group)).thenReturn(ImmutableSet.of());

    Response response = resource.createGroup(user, request);
    assertThat(response.getStatus()).isEqualTo(201);
  }

  @Test(expected = BadRequestException.class)
  public void rejectsWhenGroupExists() {
    CreateGroupRequest request = new CreateGroupRequest("newGroup", "description");
    Group group = new Group(3, "newGroup", "desc", now, "creator", now, "updater");
    when(groupDAO.getGroup("newGroup")).thenReturn(Optional.of(group));

    resource.createGroup(user, request);
  }

  @Test public void getSpecificIncludesAllTheThings() {
    when(groupDAO.getGroupById(4444)).thenReturn(Optional.of(group));

    SanitizedSecret secret = SanitizedSecret.of(1, "name", "", null, now, "creator", now, "creator", null, null, null);
    when(aclDAO.getSanitizedSecretsFor(group)).thenReturn(ImmutableSet.of(secret));

    Client client = new Client(1, "client", "desc", now, "creator", now, "creator", true, false);
    when(aclDAO.getClientsFor(group)).thenReturn(ImmutableSet.of(client));

    GroupDetailResponse response = resource.getGroup(user, new LongParam("4444"));

    assertThat(response.getId()).isEqualTo(group.getId());
    assertThat(response.getName()).isEqualTo(group.getName());
    assertThat(response.getDescription()).isEqualTo(group.getDescription());
    assertThat(response.getCreationDate()).isEqualTo(group.getCreatedAt());
    assertThat(response.getCreatedBy()).isEqualTo(group.getCreatedBy());
    assertThat(response.getUpdateDate()).isEqualTo(group.getUpdatedAt());
    assertThat(response.getUpdatedBy()).isEqualTo(group.getUpdatedBy());
    assertThat(response.getSecrets()).containsExactly(secret);
    assertThat(response.getClients()).containsExactly(client);
  }

  @Test(expected = NotFoundException.class)
  public void notFoundWhenRetrievingBadId() {
    when(groupDAO.getGroupById(0x1bad1dea)).thenReturn(Optional.empty());
    resource.getGroup(user, new LongParam(Long.toString(0x1bad1dea)));
  }

  @Test public void findGroupByName() {
    when(groupDAO.getGroup(group.getName())).thenReturn(Optional.of(group));
    assertThat(resource.getGroupByName(user, "group")).isEqualTo(group);
  }

  @Test(expected = NotFoundException.class)
  public void notFoundWhenRetrievingBadName() {
    when(groupDAO.getGroup("non-existent-group")).thenReturn(Optional.empty());
    resource.getGroupByName(user, "non-existent-group");
  }

  @Test public void canDelete() {
    when(groupDAO.getGroupById(0xdeadbeef)).thenReturn(Optional.of(group));

    Response response = resource.deleteGroup(user, new LongParam(Long.toString(0xdeadbeef)));
    verify(groupDAO).deleteGroup(group);
    assertThat(response.getStatus()).isEqualTo(204);
  }

  @Test(expected = NotFoundException.class)
  public void notFoundWhenDeletingBadId() {
    when(groupDAO.getGroupById(0x1bad1dea)).thenReturn(Optional.empty());
    resource.deleteGroup(user, new LongParam(Long.toString(0x1bad1dea)));
  }
}
