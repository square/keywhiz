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
import java.util.Base64;
import java.util.List;
import keywhiz.api.ApiDate;
import keywhiz.api.SecretDeliveryResponse;
import keywhiz.api.model.Client;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import keywhiz.service.daos.AclDAO;
import keywhiz.service.daos.ClientDAO;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class SecretsDeliveryResourceTest {
  private static final ApiDate NOW = ApiDate.now();

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock AclDAO aclDAO;
  @Mock ClientDAO clientDAO;
  SecretsDeliveryResource secretsDeliveryResource;

  Secret firstSecret = new Secret(0, "first_secret_name", null,
      () -> Base64.getEncoder().encodeToString("first_secret_contents".getBytes(UTF_8)), "checksum", NOW, null, NOW, null, null,
      null, null, 0);
  SanitizedSecret sanitizedFirstSecret = SanitizedSecret.fromSecret(firstSecret);
  Secret secondSecret = new Secret(1, "second_secret_name", null,
      () -> Base64.getEncoder().encodeToString("second_secret_contents".getBytes(UTF_8)), "checksum", NOW, null, NOW, null, null,
      null, null, 0);
  SanitizedSecret sanitizedSecondSecret = SanitizedSecret.fromSecret(secondSecret);
  Client client;

  @Before public void setUp() {
    secretsDeliveryResource = new SecretsDeliveryResource(aclDAO);
    client = new Client(0, "client_name", null, null, null, null, null, null, false, false);
  }

  @Test public void returnsEmptyJsonArrayWhenUserHasNoSecrets() throws Exception {
    when(aclDAO.getSanitizedSecretsFor(client)).thenReturn(ImmutableSet.of());
    List<SecretDeliveryResponse> secrets = secretsDeliveryResource.getSecrets(client);
    assertThat(secrets).isEmpty();
  }

  @Test public void returnsJsonArrayWhenUserHasOneSecret() throws Exception {
    when(aclDAO.getSanitizedSecretsFor(client)).thenReturn(ImmutableSet.of(sanitizedFirstSecret));

    List<SecretDeliveryResponse> secrets = secretsDeliveryResource.getSecrets(client);
    assertThat(secrets).containsOnly(SecretDeliveryResponse.fromSanitizedSecret(
        SanitizedSecret.fromSecret(firstSecret)));
  }

  @Test public void returnsJsonArrayWhenUserHasMultipleSecrets() throws Exception {
    when(aclDAO.getSanitizedSecretsFor(client))
        .thenReturn(ImmutableSet.of(sanitizedFirstSecret, sanitizedSecondSecret));

    List<SecretDeliveryResponse> secrets = secretsDeliveryResource.getSecrets(client);
    assertThat(secrets).containsOnly(
        SecretDeliveryResponse.fromSanitizedSecret(SanitizedSecret.fromSecret(firstSecret)),
        SecretDeliveryResponse.fromSanitizedSecret(SanitizedSecret.fromSecret(secondSecret)));
  }
}
