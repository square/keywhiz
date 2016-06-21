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

import com.google.common.collect.ImmutableMap;
import java.util.Base64;
import java.util.Optional;
import keywhiz.api.ApiDate;
import keywhiz.api.AutomationSecretResponse;
import keywhiz.api.CreateSecretRequest;
import keywhiz.api.model.*;
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AutomationSecretResourceTest {
  private static final ApiDate NOW = ApiDate.now();

  AutomationSecretResource resource;

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock SecretController secretController;
  @Mock SecretController.SecretBuilder secretBuilder;
  @Mock AclDAO aclDAO;
  @Mock SecretDAO secretDAO;

  AutomationClient automation = AutomationClient.of(
      new Client(1, "automation", "Automation client", NOW, "test", NOW, "test", true, true));

  @Before
  public void setUp() {
    resource = new AutomationSecretResource(secretController, secretDAO, aclDAO);

    when(secretController.builder(anyString(), anyString(), anyString(), anyLong())).thenReturn(secretBuilder);
    when(secretBuilder.withDescription(anyString())).thenReturn(secretBuilder);
  }

  @Test
  public void addSecret() {
    CreateSecretRequest request = new CreateSecretRequest("mySecret",
        "some secret",
        "ponies",
        null,
        0);

    Secret secret = new Secret(0, /* Set by DB */
        request.name,
        request.description,
        Base64.getUrlEncoder().encodeToString(request.content.getBytes(UTF_8)),
        NOW,
        automation.getName(),
        NOW, /* updatedAt set by DB */
        automation.getName(),
        request.metadata,
        null,
        null);

    when(secretBuilder.create()).thenReturn(secret);

    when(secretController.getSecretByName(eq(request.name)))
        .thenReturn(Optional.of(secret));

    AutomationSecretResponse response = resource.createSecret(automation, request);
    assertThat(response.id()).isEqualTo(secret.getId());
    assertThat(response.secret()).isEqualTo(secret.getSecret());
    assertThat(response.name()).isEqualTo(secret.getDisplayName());
    assertThat(response.metadata()).isEqualTo(secret.getMetadata());
  }

  @Test
  public void deleteSecret() throws Exception {
    SecretSeries secretSeries = SecretSeries.of(0, /* Set by DB */
        "mySecret",
        null,
        NOW,
        automation.getName(),
        NOW,
        automation.getName(),
        null,
        null,
        null);

    when(secretDAO.getSecretByName(secretSeries.name()))
        .thenReturn(Optional.of(SecretSeriesAndContent.of(secretSeries, SecretContent.of(123, secretSeries.id(), "meh", NOW, null, NOW, null, ImmutableMap.of()))));

    resource.deleteSecretSeries(automation, "mySecret");
    verify(secretDAO).deleteSecretsByName(secretSeries.name());
  }

  @Test(expected = ConflictException.class)
  public void triesToCreateDuplicateSecret() throws Exception {
    DataAccessException exception = new DataAccessException("");
    ImmutableMap<String,String> emptyMap = ImmutableMap.of();

    doThrow(exception).when(secretBuilder).create();

    CreateSecretRequest req = new CreateSecretRequest("name", "desc", "content", emptyMap, 0);
    resource.createSecret(automation, req);
  }
}
