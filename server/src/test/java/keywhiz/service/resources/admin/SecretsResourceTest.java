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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import io.dropwizard.jersey.params.LongParam;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import keywhiz.api.ApiDate;
import keywhiz.api.CreateSecretRequest;
import keywhiz.api.SecretDetailResponse;
import keywhiz.api.automation.v2.CreateOrUpdateSecretRequestV2;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import keywhiz.auth.User;
import keywhiz.service.daos.AclDAO;
import keywhiz.service.daos.SecretController;
import keywhiz.service.daos.SecretDAO;
import keywhiz.service.exceptions.ConflictException;
import org.jooq.exception.DataAccessException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SecretsResourceTest {
  private static final ApiDate NOW = ApiDate.now();

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock AclDAO aclDAO;
  @Mock SecretDAO secretDAO;
  @Mock SecretController secretController;

  User user = User.named("user");
  ImmutableMap<String, String> emptyMap = ImmutableMap.of();

  Secret secret = new Secret(22, "name", "desc", () -> "secret", NOW, "creator", NOW,
      "updater", emptyMap, null, null, 1136214245);

  SecretsResource resource;

  @Before
  public void setUp() {
    resource = new SecretsResource(secretController, aclDAO, secretDAO);
  }

  @Test
  public void listSecrets() {
    SanitizedSecret secret1 = SanitizedSecret.of(1, "name1", "desc", NOW, "user", NOW, "user",
        emptyMap, null, null, 1136214245);
    SanitizedSecret secret2 = SanitizedSecret.of(2, "name2", "desc", NOW, "user", NOW, "user",
        emptyMap, null, null, 1136214245);
    when(secretController.getSanitizedSecrets(null, null)).thenReturn(ImmutableList.of(secret1, secret2));

    List<SanitizedSecret> response = resource.listSecrets(user);
    assertThat(response).containsOnly(secret1, secret2);
  }

  @Test
  public void createsSecret() throws Exception {
    when(secretController.getSecretById(secret.getId())).thenReturn(Optional.of(secret));

    SecretController.SecretBuilder secretBuilder = mock(SecretController.SecretBuilder.class);
    when(secretController.builder(secret.getName(), secret.getSecret(), user.getName(), 0))
        .thenReturn(secretBuilder);
    when(secretBuilder.create()).thenReturn(secret);

    CreateSecretRequest req = new CreateSecretRequest(secret.getName(),
        secret.getDescription(), secret.getSecret(), emptyMap, 0);
    Response response = resource.createSecret(user, req);

    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getMetadata().get(HttpHeaders.LOCATION))
        .containsExactly(new URI("/admin/secrets/" + secret.getId()));
  }

  @Test
  public void createOrUpdateSecret() throws Exception {
    when(secretController.getSecretById(secret.getId())).thenReturn(Optional.of(secret));

    SecretController.SecretBuilder secretBuilder = mock(SecretController.SecretBuilder.class);
    when(secretController.builder(secret.getName(), secret.getSecret(), user.getName(), 0))
        .thenReturn(secretBuilder);
    when(secretBuilder.withDescription(any())).thenReturn(secretBuilder);
    when(secretBuilder.withMetadata(any())).thenReturn(secretBuilder);
    when(secretBuilder.withType(any())).thenReturn(secretBuilder);
    when(secretBuilder.createOrUpdate()).thenReturn(secret);

    CreateOrUpdateSecretRequestV2 req = CreateOrUpdateSecretRequestV2.builder()
        .description(secret.getDescription())
        .content(secret.getSecret())
        .build();

    Response response = resource.createOrUpdateSecret(user, secret.getName(), req);

    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getMetadata().get(HttpHeaders.LOCATION))
        .containsExactly(new URI("/admin/secrets/" + secret.getName()));
  }


  @Test public void canDelete() {
    when(secretController.getSecretById(0xdeadbeef)).thenReturn(Optional.of(secret));

    Response response = resource.deleteSecret(user, new LongParam(Long.toString(0xdeadbeef)));
    verify(secretDAO).deleteSecretsByName("name");
    assertThat(response.getStatus()).isEqualTo(204);
  }

  @Test(expected = ConflictException.class)
  public void triesToCreateDuplicateSecret() throws Exception {
    SecretController.SecretBuilder secretBuilder = mock(SecretController.SecretBuilder.class);
    when(secretController.builder("name", "content", user.getName(), 0)).thenReturn(secretBuilder);
    DataAccessException exception = new DataAccessException("");
    doThrow(exception).when(secretBuilder).create();

    CreateSecretRequest req = new CreateSecretRequest("name", "desc", "content", emptyMap, 0);
    resource.createSecret(user, req);
  }

  @Test
  public void includesTheSecret() {
    when(secretController.getSecretById(22)).thenReturn(Optional.of(secret));
    when(aclDAO.getGroupsFor(secret)).thenReturn(Collections.emptySet());
    when(aclDAO.getClientsFor(secret)).thenReturn(Collections.emptySet());

    SecretDetailResponse response = resource.retrieveSecret(user, new LongParam("22"));

    assertThat(response.id).isEqualTo(secret.getId());
    assertThat(response.name).isEqualTo(secret.getName());
    assertThat(response.description).isEqualTo(secret.getDescription());
    assertThat(response.createdAt).isEqualTo(secret.getCreatedAt());
    assertThat(response.createdBy).isEqualTo(secret.getCreatedBy());
    assertThat(response.updatedAt).isEqualTo(secret.getUpdatedAt());
    assertThat(response.updatedBy).isEqualTo(secret.getUpdatedBy());
    assertThat(response.metadata).isEqualTo(secret.getMetadata());
  }

  @Test
  public void handlesNoAssociations() {
    when(secretController.getSecretById(22)).thenReturn(Optional.of(secret));
    when(aclDAO.getGroupsFor(secret)).thenReturn(Collections.emptySet());
    when(aclDAO.getClientsFor(secret)).thenReturn(Collections.emptySet());

    SecretDetailResponse response = resource.retrieveSecret(user, new LongParam("22"));
    assertThat(response.groups).isEmpty();
    assertThat(response.clients).isEmpty();
  }

  @Test
  public void includesAssociations() {
    Client client = new Client(0, "client", null, null, null, null, null, false, false);
    Group group1 = new Group(0, "group1", null, null, null, null, null);
    Group group2 = new Group(0, "group2", null, null, null, null, null);

    when(secretController.getSecretById(22)).thenReturn(Optional.of(secret));
    when(aclDAO.getGroupsFor(secret)).thenReturn(Sets.newHashSet(group1, group2));
    when(aclDAO.getClientsFor(secret)).thenReturn(Sets.newHashSet(client));

    SecretDetailResponse response = resource.retrieveSecret(user, new LongParam("22"));
    assertThat(response.groups).containsOnly(group1, group2);
    assertThat(response.clients).containsOnly(client);
  }

  @Test(expected = NotFoundException.class)
  public void badIdNotFound() {
    when(secretController.getSecretById(0xbad1d)).thenReturn(Optional.empty());
    resource.retrieveSecret(user, new LongParam(Long.toString(0xbad1d)));
  }

  @Test public void findSecretByNameAndVersion() {
    when(secretController.getSecretByName(secret.getName()))
        .thenReturn(Optional.of(secret));
    assertThat(resource.retrieveSecret(user, "name"))
        .isEqualTo(SanitizedSecret.fromSecret(secret));
  }

  @Test(expected = NotFoundException.class)
  public void badNameNotFound() {
    when(secretController.getSecretByName("non-existent")).thenReturn(Optional.empty());
    resource.retrieveSecret(user, "non-existent");
  }
}
