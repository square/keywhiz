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

import io.dropwizard.jersey.params.LongParam;
import java.util.HashMap;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import keywhiz.api.ApiDate;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.Secret;
import keywhiz.auth.User;
import keywhiz.log.AuditLog;
import keywhiz.log.SimpleLogger;
import keywhiz.service.daos.AclDAO;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

public class MembershipResourceTest {
  private static final ApiDate NOW = ApiDate.now();

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock AclDAO aclDAO;

  User user = User.named("user");
  Client client = new Client(44, "client", "desc", null, NOW, "creator", NOW, "updater", null, null,
      true, false);
  Group group = new Group(55, "group", null, null, null, null, null, null);
  Secret secret = new Secret(66, "secret", null, null, () -> "shush", "checksum", NOW, null, NOW, null, null, null, null, 0, 1L, NOW, null);
  AuditLog auditLog = new SimpleLogger();

  MembershipResource resource;

  @Before public void setUp() {
    resource = new MembershipResource(aclDAO, auditLog);
  }

  @Test public void canAllowAccess() {
    Response response = resource.allowAccess(user, new LongParam("66"), new LongParam("55"));

    assertThat(response.getStatus()).isEqualTo(200);
    verify(aclDAO).findAndAllowAccess(66, 55, auditLog, "user",new HashMap<>());
  }

  @Test(expected = NotFoundException.class)
  public void missingSecretAllow() {
    doThrow(IllegalStateException.class).when(aclDAO).findAndAllowAccess(3, group.getId(), auditLog, "user",new HashMap<>());
    resource.allowAccess(user, new LongParam("3"), new LongParam(Long.toString(group.getId())));
  }

  @Test(expected = NotFoundException.class)
  public void missingGroupAllow() {
    doThrow(IllegalStateException.class).when(aclDAO).findAndAllowAccess(secret.getId(), 98, auditLog, "user",new HashMap<>());
    resource.allowAccess(user, new LongParam(Long.toString(secret.getId())), new LongParam("98"));
  }

  @Test public void canDisallowAccess() {
    Response response = resource.disallowAccess(user, new LongParam(Long.toString(secret.getId())),
        new LongParam(Long.toString(group.getId())));

    assertThat(response.getStatus()).isEqualTo(200);
    verify(aclDAO).findAndRevokeAccess(secret.getId(), group.getId(), auditLog, "user",new HashMap<>());
  }

  @Test(expected = NotFoundException.class)
  public void missingSecretDisallow() {
    doThrow(IllegalStateException.class).when(aclDAO).findAndRevokeAccess(2, group.getId(), auditLog, "user",new HashMap<>());
    resource.disallowAccess(user, new LongParam("2"), new LongParam(Long.toString(group.getId())));
  }

  @Test(expected = NotFoundException.class)
  public void missingGroupDisallow() {
    doThrow(IllegalStateException.class).when(aclDAO).findAndRevokeAccess(secret.getId(), 3543, auditLog, "user",new HashMap<>());
    resource.disallowAccess(user, new LongParam(Long.toString(secret.getId())), new LongParam("3543"));
  }

  @Test
  public void canEnroll() {
    resource.enrollClient(user, new LongParam(Long.toString(client.getId())),
        new LongParam(Long.toString(group.getId())));
    verify(aclDAO).findAndEnrollClient(client.getId(), group.getId(), auditLog, "user",new HashMap<>());
  }

  @Test(expected = NotFoundException.class)
  public void enrollThrowsWhenClientIdNotFound() {
    doThrow(IllegalStateException.class).when(aclDAO).findAndEnrollClient(6092384, group.getId(), auditLog, "user",new HashMap<>());
    resource.enrollClient(user, new LongParam("6092384"), new LongParam(Long.toString(group.getId())));
  }

  @Test(expected = NotFoundException.class)
  public void enrollThrowsWhenGroupIdNotFound() {
    doThrow(IllegalStateException.class).when(aclDAO).findAndEnrollClient(client.getId(), 0xbad, auditLog, "user",new HashMap<>());
    resource.enrollClient(user, new LongParam("44"), new LongParam(Long.toString(0xbad)));
  }

  @Test
  public void canEvict() {
    resource.evictClient(user, new LongParam(Long.toString(client.getId())),
        new LongParam(Long.toString(group.getId())));
    verify(aclDAO).findAndEvictClient(client.getId(), group.getId(), auditLog, "user",new HashMap<>());
  }

  @Test(expected = NotFoundException.class)
  public void evictThrowsWhenClientIdNotFound() {
    doThrow(IllegalStateException.class).when(aclDAO).findAndEvictClient(60984, group.getId(), auditLog, "user",new HashMap<>());
    resource.evictClient(user, new LongParam("60984"), new LongParam(Long.toString(group.getId())));
  }

  @Test(expected = NotFoundException.class)
  public void evictThrowsWhenGroupIdNotFound() {
    doThrow(IllegalStateException.class).when(aclDAO).findAndEvictClient(client.getId(), 0xbad2, auditLog, "user",new HashMap<>());
    resource.evictClient(user, new LongParam(Long.toString(client.getId())),
        new LongParam(Long.toString(0xbad2)));
  }
}
