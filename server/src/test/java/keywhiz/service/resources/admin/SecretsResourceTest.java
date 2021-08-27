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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import keywhiz.api.ApiDate;
import keywhiz.api.SecretDetailResponse;
import keywhiz.api.automation.v2.CreateOrUpdateSecretRequestV2;
import keywhiz.api.automation.v2.CreateSecretRequestV2;
import keywhiz.api.automation.v2.PartialUpdateSecretRequestV2;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import keywhiz.api.model.SecretContent;
import keywhiz.api.model.SecretSeries;
import keywhiz.api.model.SecretSeriesAndContent;
import keywhiz.auth.User;
import keywhiz.log.AuditLog;
import keywhiz.log.Event;
import keywhiz.log.SimpleLogger;
import keywhiz.service.daos.AclDAO;
import keywhiz.service.daos.SecretController;
import keywhiz.service.daos.SecretDAO;
import keywhiz.service.exceptions.ConflictException;
import org.apache.http.HttpStatus;
import org.jooq.exception.DataAccessException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SecretsResourceTest {
  private static final ApiDate NOW = ApiDate.now();
  private static final ApiDate NOWPLUS = new ApiDate(NOW.toEpochSecond() + 10000);

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock AclDAO aclDAO;
  @Mock SecretDAO secretDAO;
  @Mock SecretController secretController;
  @Mock SecretController.SecretBuilder secretBuilder;
  @Mock SimpleLogger auditLog;

  User user = User.named("user");
  ImmutableMap<String, String> emptyMap = ImmutableMap.of();

  Secret secret = new Secret(22, "name", null, "desc", () -> "secret", "checksum", NOW, "creator", NOW,
      "updater", emptyMap, null, null, 1136214245, 1L, NOW, "user");

  SecretSeriesAndContent secretSeriesAndContent1 =
      SecretSeriesAndContent.of(
          SecretSeries.of(1, "name1", null, "desc", NOW,
              "user", NOW, "user", null, emptyMap, null),
          SecretContent.of(101, 1, "blah", "checksum",
              NOW, "creator", NOW, "updater",
              emptyMap, 0));

  SecretSeriesAndContent secretSeriesAndContent2 =
      SecretSeriesAndContent.of(
          SecretSeries.of(1, "name2", null, "desc", NOW,
              "user", NOW, "user", null, emptyMap, null),
          SecretContent.of(110, 1, "blah", "checksum",
              NOW, "creator", NOW, "updater",
              emptyMap, 0));

  SecretsResource resource;

  @Before
  public void setUp() {
    resource = new SecretsResource(secretController, aclDAO, secretDAO, auditLog);

    when(secretController.builder(any(), any(), any(), anyLong()))
        .thenReturn(secretBuilder);
    when(secretBuilder.withDescription(any())).thenReturn(secretBuilder);
    when(secretBuilder.withMetadata(any())).thenReturn(secretBuilder);
    when(secretBuilder.withOwnerName(any())).thenReturn(secretBuilder);
    when(secretBuilder.withType(any())).thenReturn(secretBuilder);
    when(secretBuilder.create()).thenReturn(secret);
    when(secretBuilder.createOrUpdate()).thenReturn(secret);

    when(secretController.getSecretById(secret.getId())).thenReturn(Optional.of(secret));
  }

  @Test
  public void createsSecretWithOwner() {
    String owner = UUID.randomUUID().toString();

    CreateSecretRequestV2 request = CreateSecretRequestV2.builder()
        .content(secret.getSecret())
        .owner(owner)
        .name(secret.getName())
        .build();

    resource.createSecret(user, request);

    verify(secretBuilder).withOwnerName(owner);
  }

  @Test
  public void secretCreationSendsOwnerToAuditLog() {
    String owner = UUID.randomUUID().toString();

    CreateSecretRequestV2 request = CreateSecretRequestV2.builder()
        .content(secret.getSecret())
        .owner(owner)
        .name(secret.getName())
        .build();

    resource.createSecret(user, request);

    ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
    verify(auditLog).recordEvent(eventCaptor.capture());
    assertEquals(owner, eventCaptor.getValue().getExtraInfo().get("owner"));
  }

  @Test
  public void listSecrets() {
    SanitizedSecret secret1 = SanitizedSecret.of(1, "name1", null, "desc","checksum", NOW, "user", NOW, "user",
        emptyMap, null, null, 1136214245, 125L, NOW, "user");
    SanitizedSecret secret2 = SanitizedSecret.of(2, "name2", null, "desc","checksum", NOW, "user", NOW, "user",
        emptyMap, null, null, 1136214245, 250L, NOW, "user");
    when(secretController.getSanitizedSecrets(null, null)).thenReturn(ImmutableList.of(secret1, secret2));

    List<SanitizedSecret> response = resource.listSecrets(user);
    assertThat(response).containsOnly(secret1, secret2);
  }

  @Test
  public void listSecretsBatched() {
    SanitizedSecret secret1 = SanitizedSecret.of(1, "name1", null, "desc", "checksum", NOW, "user", NOW, "user",
        emptyMap, null, null, 1136214245, 125L, NOW, "user");
    SanitizedSecret secret2 = SanitizedSecret.of(2, "name2", null, "desc", "checksum", NOWPLUS, "user", NOWPLUS, "user",
        emptyMap, null, null, 1136214245, 250L, NOW, "user");
    when(secretController.getSecretsBatched(0, 1, false)).thenReturn(ImmutableList.of(secret1));
    when(secretController.getSecretsBatched(0, 1, true)).thenReturn(ImmutableList.of(secret2));
    when(secretController.getSecretsBatched(1, 1, false)).thenReturn(ImmutableList.of(secret2));

    List<SanitizedSecret> response = resource.listSecretsBatched(user, 0, 1, false);
    assertThat(response).containsOnly(secret1);

    response = resource.listSecretsBatched(user, 1, 1, false);
    assertThat(response).containsOnly(secret2);

    response = resource.listSecretsBatched(user, 0, 1, true);
    assertThat(response).containsOnly(secret2);
  }

  @Test
  public void createsSecret() throws Exception {
    CreateSecretRequestV2 req =
        CreateSecretRequestV2.builder()
            .name(secret.getName())
            .description(secret.getDescription())
            .content(secret.getSecret())
            .build();
    Response response = resource.createSecret(user, req);

    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getMetadata().get(HttpHeaders.LOCATION))
        .containsExactly(new URI("/admin/secrets/" + secret.getId()));
  }

  @Test
  public void createOrUpdateSecret() throws Exception {
    CreateOrUpdateSecretRequestV2 req = CreateOrUpdateSecretRequestV2.builder()
        .description(secret.getDescription())
        .content(secret.getSecret())
        .build();

    Response response = resource.createOrUpdateSecret(user, secret.getName(), req);

    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getMetadata().get(HttpHeaders.LOCATION))
        .containsExactly(new URI("/admin/secrets/" + secret.getName()));
  }

  @Test
  public void partialUpdateSecret() throws Exception {
    PartialUpdateSecretRequestV2 req = PartialUpdateSecretRequestV2.builder()
        .description(secret.getDescription())
        .descriptionPresent(true)
        .metadata(ImmutableMap.of("owner", "keywhizAdmin"))
        .metadataPresent(true)
        .expiry(1487268151L)
        .expiryPresent(true)
        .type("test")
        .typePresent(true)
        .content(secret.getSecret())
        .contentPresent(true)
        .build();

    when(secretDAO.partialUpdateSecret(eq(secret.getName()), any(), eq(req))).thenReturn(
        secret.getId());

    Response response = resource.partialUpdateSecret(user, secret.getName(), req);

    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getMetadata().get(HttpHeaders.LOCATION))
        .containsExactly(new URI("/admin/secrets/" + secret.getName() + "/partialupdate"));
  }

  @Test
  public void listSecretVersions() {
    SanitizedSecret secret1 = SanitizedSecret.of(1, "name1", null, "desc", "checksum", NOW, "user",
        NOW, "user", emptyMap, null, null, 1136214245, 125L, NOW, "user");
    SanitizedSecret secret2 = SanitizedSecret.of(1, "name1", null, "desc", "checksum2", NOWPLUS, "user",
        NOWPLUS, "user", emptyMap, null, null, 1136214245, 250L, NOW, "user");

    when(secretDAO.getSecretVersionsByName("name1", 0, 10)).thenReturn(
        Optional.of(ImmutableList.of(secret2, secret1)));
    when(secretDAO.getSecretVersionsByName("name1", 1, 5)).thenReturn(
        Optional.of(ImmutableList.of(secret1)));
    when(secretDAO.getSecretVersionsByName("name1", 2, 10)).thenReturn(
        Optional.of(ImmutableList.of()));

    List<SanitizedSecret> response = resource.secretVersions(user, "name1", 0, 10);
    assertThat(response).containsExactly(secret2, secret1);

    response = resource.secretVersions(user, "name1", 1, 5);
    assertThat(response).containsExactly(secret1);

    response = resource.secretVersions(user, "name1", 2, 10);
    assertThat(response).isEmpty();
  }

  @Test (expected = NotFoundException.class)
  public void listSecretVersionsThrowsException() {
    when(secretDAO.getSecretVersionsByName(eq("name2"), anyInt(), anyInt())).thenReturn(
        Optional.empty());
    resource.secretVersions(user, "name2", 0, 10);
  }

  @Test
  public void rollbackSuccess() {
    Secret secret1 = new Secret(1, "name1", null, "desc", () -> "secret",
        "checksum", NOW, "user", NOW, "user", emptyMap, null,
        null, 1136214245, 125L, NOW, "user");

    when(secretController.getSecretByName("name1")).thenReturn(Optional.of(secret1));

    Response response = resource.resetSecretVersion(user, "name1", new LongParam("125"));
    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_CREATED);
  }

  @Test (expected = NotFoundException.class)
  public void rollbackThrowsException() {
    doThrow(new NotFoundException()).when(secretDAO)
        .setCurrentSecretVersionByName(eq("name2"), anyLong(), eq("user"));
    resource.resetSecretVersion(user, "name2", new LongParam("125"));
  }

  @Test public void canDelete() {
    when(secretController.getSecretById(0xdeadbeef)).thenReturn(Optional.of(secret));
    HashSet<Group> groups = new HashSet<>();
    groups.add(new Group(0, "group1", "", NOW, null, NOW, null, null));
    groups.add(new Group(0, "group2", "", NOW, null, NOW, null, null));
    when(aclDAO.getGroupsFor(secret)).thenReturn(groups);

    Response response = resource.deleteSecret(user, new LongParam(Long.toString(0xdeadbeef)));
    verify(secretDAO).deleteSecretsByName("name");
    assertThat(response.getStatus()).isEqualTo(204);
  }

  @Test (expected = NotFoundException.class)
  public void deleteErrorsOnNotFound() {
    when(secretController.getSecretById(0xdeadbeef)).thenReturn(Optional.empty());
    resource.deleteSecret(user, new LongParam(Long.toString(0xdeadbeef)));
  }

  @Test(expected = ConflictException.class)
  public void triesToCreateDuplicateSecret() throws Exception {
    SecretController.SecretBuilder secretBuilder = mock(SecretController.SecretBuilder.class);
    when(secretController.builder("name", "content", user.getName(), 0)).thenReturn(secretBuilder);
    DataAccessException exception = new DataAccessException("");
    doThrow(exception).when(secretBuilder).create();

    CreateSecretRequestV2 req = CreateSecretRequestV2.builder()
        .name("name")
        .description("desc")
        .content("content")
        .build();
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
    Client client = new Client(0, "client", null, null, null, null, null, null, null, null, false, false
    );
    Group group1 = new Group(0, "group1", null, null, null, null, null, null);
    Group group2 = new Group(0, "group2", null, null, null, null, null, null);

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

  @Test public void getSecretContentsBySecretId() {
    when(secretDAO.getDeletedSecretVersionsBySecretId(1, 0, 10)).thenReturn(
        Optional.of(ImmutableList.of(secretSeriesAndContent1, secretSeriesAndContent2)));

    assertThat(resource.getSecretsContentsBySecretId(user, new LongParam(Long.toString(1)), 0, 10))
        .containsExactlyInAnyOrder(secretSeriesAndContent1, secretSeriesAndContent2);
  }

  @Test public void setCurrentSecretVersionBySecretId() {
    when(secretController.getSecretById(1)).thenReturn(Optional.of(secret));

    Response response = resource.updateCurrentSecretVersion(user,
        new LongParam(Long.toString(1)), new LongParam(Long.toString(10)));
    verify(secretDAO).setCurrentSecretVersionBySecretId(1, 10, "user");
    assertThat(response.getStatus()).isEqualTo(204);
  }

  @Test
  public void setCurrentSecretVersionBySecretIdSecretNotFound() {
    when(secretController.getSecretById(1)).thenReturn(Optional.empty());

    Response response = resource.updateCurrentSecretVersion(user,
        new LongParam(Long.toString(1)), new LongParam(Long.toString(10)));
    verify(secretDAO).setCurrentSecretVersionBySecretId(1, 10, "user");
    assertThat(response.getStatus()).isEqualTo(204);
  }

  @Test public void renameSecret() {
    when(secretDAO.getSecretByName("name1")).thenReturn(Optional.empty());

    Response response = resource.renameSecret(user,
        new LongParam(Long.toString(1)), "name1");
    verify(secretDAO).renameSecretById(1, "name1", "user");
    assertThat(response.getStatus()).isEqualTo(204);
  }

  @Test
  public void renameSecretNotFound() {
    when(secretController.getSecretByName("name")).thenReturn(Optional.of(secret));

    assertThatThrownBy(() -> resource.renameSecret(user, new LongParam(Long.toString(1)), "name"))
        .hasMessage("That name is already taken by another secret")
        .isInstanceOf(ConflictException.class);

  }

  @Test public void findDeletedSecretsByName() {
    SecretSeries secretSeries1 = SecretSeries.of(1, "blah.name1.blah", null, "desc", NOW,
                "user", NOW, "user", null, emptyMap, null);

    SecretSeries secretSeries2 = SecretSeries.of(2, "blahblah.name1.blahh", null, "desc", NOW,
        "user", NOW, "user", null, emptyMap, null);

    when(secretDAO.getSecretsWithDeletedName("name1"))
        .thenReturn(ImmutableList.of(secretSeries1, secretSeries2));

    assertThat(resource.findDeletedSecretsByName(user, "name1"))
        .containsExactlyInAnyOrder(secretSeries1, secretSeries2);
  }
}
