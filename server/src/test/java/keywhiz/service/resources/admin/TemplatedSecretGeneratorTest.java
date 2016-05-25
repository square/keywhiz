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
import java.util.List;
import java.util.Optional;
import javax.ws.rs.BadRequestException;
import keywhiz.FakeRandom;
import keywhiz.api.ApiDate;
import keywhiz.api.TemplatedSecretsGeneratorRequest;
import keywhiz.api.model.Secret;
import keywhiz.auth.User;
import keywhiz.generators.TemplatedSecretGenerator;
import keywhiz.service.daos.AclDAO;
import keywhiz.service.daos.SecretController;
import keywhiz.service.daos.SecretDAO;
import org.jooq.exception.DataAccessException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

public class TemplatedSecretGeneratorTest {
  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock AclDAO aclDAO;
  @Mock SecretDAO secretDAO;
  @Mock SecretController secretController;
  @Mock SecretController.SecretBuilder secretBuilder;

  User user = User.named("user");
  ImmutableMap<String, String> emptyMap = ImmutableMap.of();

  TemplatedSecretGenerator generator;

  @Before
  public void setUp() {
    generator = new TemplatedSecretGenerator(secretController, FakeRandom.create());

    when(secretBuilder.withDescription(anyString())).thenReturn(secretBuilder);
    when(secretBuilder.withType(anyString())).thenReturn(secretBuilder);
    when(secretBuilder.withMetadata(anyMapOf(String.class, String.class))).thenReturn(secretBuilder);
    when(secretBuilder.withGenerationOptions(anyMapOf(String.class, String.class))).thenReturn(secretBuilder);
    when(secretController.builder(anyString(), anyString(), anyString(), anyLong())).thenReturn(secretBuilder);
  }

  @Test
  public void createsSecret() throws Exception {
    ApiDate now = ApiDate.now();
    Secret secret = new Secret(5, "test-database.yaml", "versionStamp", "desc", "content", now, "creator", now, "creator", null, null, null);
    when(secretBuilder.build()).thenReturn(secret);

    when(secretController.getSecretsById(5L)).thenReturn(ImmutableList.of(secret));
    when(secretController.getSecretByNameAndVersion(eq("test-database.yaml"), anyString()))
        .thenReturn(Optional.of(secret));

    TemplatedSecretsGeneratorRequest req = new TemplatedSecretsGeneratorRequest(
        "{{#numeric}}10{{/numeric}}", "test-database.yaml", "desc", true, emptyMap);

    List<Secret> secrets = generator.generate(user.getName(), req);
    assertThat(secrets).hasSize(1);
    assertThat(secrets.get(0).getId()).isEqualTo(5);
  }

  @Test(expected = BadRequestException.class)
  public void triesToCreateDuplicateSecret() throws Exception {
    DataAccessException exception = new DataAccessException("");
    TemplatedSecretsGeneratorRequest req = new TemplatedSecretsGeneratorRequest(
        "{{#numeric}}10{{/numeric}}", "test-database.yaml", "desc", true, emptyMap);

    doThrow(exception).when(secretBuilder).build();

    generator.generate(user.getName(), req);
  }
}
