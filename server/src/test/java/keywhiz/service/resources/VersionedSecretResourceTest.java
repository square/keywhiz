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

import com.google.common.collect.ImmutableMap;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import keywhiz.api.CreateSecretRequest;
import keywhiz.api.model.AutomationClient;
import keywhiz.api.model.Client;
import keywhiz.api.model.Secret;
import keywhiz.api.model.SecretContent;
import keywhiz.api.model.SecretSeries;
import keywhiz.api.model.VersionGenerator;
import keywhiz.service.daos.AclDAO;
import keywhiz.service.daos.SecretController;
import keywhiz.service.daos.SecretDAO;
import keywhiz.service.daos.SecretSeriesDAO;
import keywhiz.service.exceptions.ConflictException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRule;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.exceptions.StatementException;
import org.skife.jdbi.v2.exceptions.UnableToExecuteStatementException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class VersionedSecretResourceTest {
  @Rule public TestRule mockito = new MockitoJUnitRule(this);

  private static final OffsetDateTime NOW = OffsetDateTime.now();

  VersionedSecretResource resource;

  AutomationClient automation = AutomationClient.of(
      new Client(1, "automation", "Automation client", NOW, "test", NOW, "test", true, true));

  @Mock SecretController secretController;
  @Mock SecretController.SecretBuilder secretBuilder;
  @Mock AclDAO aclDAO;
  @Mock SecretSeriesDAO secretSeriesDAO;
  @Mock SecretDAO secretDAO;

  @Before
  public void setUp() {
    resource = new VersionedSecretResource(secretController, aclDAO, secretSeriesDAO, secretDAO);

    when(secretController.builder(anyString(), anyString(), anyString())).thenReturn(secretBuilder);
  }

  @Test
  public void addSecret() throws Exception {
    CreateSecretRequest request = new CreateSecretRequest("mySecret",
        "some secret",
        "ponies",
        true,
        null);

    Secret secret = new Secret(0, /* Set by DB */
        request.name,
        VersionGenerator.now().toHex(),
        request.description,
        Base64.getUrlEncoder().encodeToString(request.content.getBytes(UTF_8)),
        NOW,
        automation.getName(),
        NOW, /* updatedAt set by DB */
        automation.getName(),
        request.metadata,
        null,
        null);

    when(secretBuilder.build()).thenReturn(secret);

    when(secretController.getSecretByNameAndVersion(eq(request.name), anyString()))
        .thenReturn(Optional.of(secret));

    Response response = resource.createSecret(automation, request);
    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getMetadata().get(HttpHeaders.LOCATION))
        .containsExactly(new URI("/v2/secrets/" + secret.getName() + "/" + secret.getVersion()));
  }

  @Test
  public void deleteSecretSeries() throws Exception {
    SecretSeries secretSeries = new SecretSeries(0, /* Set by DB */
        "mySecret",
        null,
        NOW,
        automation.getName(),
        NOW,
        automation.getName(),
        null,
        null);

    when(secretSeriesDAO.getSecretSeriesByName(secretSeries.getName()))
        .thenReturn(Optional.of(secretSeries));

    resource.deleteSecretSeries(automation, "mySecret");
    verify(secretSeriesDAO).deleteSecretSeriesByName(secretSeries.getName());
  }

  @Test
  public void deleteSecretVersion() throws Exception {
    SecretSeries secretSeries = new SecretSeries(0, /* Set by DB */
        "mySecret",
        null,
        NOW,
        automation.getName(),
        NOW,
        automation.getName(),
        null,
        null);

    SecretContent secretContent = SecretContent.of(0, 0, "[crypted]", "A", NOW,
        automation.getName(), NOW, automation.getName(), ImmutableMap.of());

    when(secretSeriesDAO.getSecretSeriesByName(secretSeries.getName()))
        .thenReturn(Optional.of(secretSeries));

    resource.deleteSecretVersion(automation, "mySecret", "A");

    verify(secretDAO).deleteSecretByNameAndVersion(secretSeries.getName(),
        secretContent.version().get());
  }

  @Test(expected = ConflictException.class)
  public void triesToCreateDuplicateSecret() throws Exception {
    StatementException exception = new UnableToExecuteStatementException("", (StatementContext) null);
    ImmutableMap<String,String> emptyMap = ImmutableMap.of();

    doThrow(exception).when(secretBuilder).build();

    CreateSecretRequest req = new CreateSecretRequest("name", "desc", "content", false, emptyMap);
    resource.createSecret(automation, req);
  }
}
