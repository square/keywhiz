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
import com.google.common.collect.ImmutableMap;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.BadRequestException;
import keywhiz.FakeRandom;
import keywhiz.api.TemplatedSecretsGeneratorRequest;
import keywhiz.api.model.Secret;
import keywhiz.auth.User;
import keywhiz.generators.TemplatedSecretGenerator;
import keywhiz.service.daos.AclJooqDao;
import keywhiz.service.daos.SecretController;
import keywhiz.service.daos.SecretDAO;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.exceptions.StatementException;
import org.skife.jdbi.v2.exceptions.UnableToExecuteStatementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TemplatedSecretGeneratorTest {
  @Mock AclJooqDao aclJooqDao;
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
    when(secretController.builder(anyString(), anyString(), anyString())).thenReturn(secretBuilder);
  }

  @Test
  public void createsSecret() throws Exception {
    OffsetDateTime now = OffsetDateTime.now();
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
    StatementException exception = new UnableToExecuteStatementException("", (StatementContext) null);
    TemplatedSecretsGeneratorRequest req = new TemplatedSecretsGeneratorRequest(
        "{{#numeric}}10{{/numeric}}", "test-database.yaml", "desc", true, emptyMap);

    doThrow(exception).when(secretBuilder).build();

    generator.generate(user.getName(), req);
  }
}
