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
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import keywhiz.api.SecretDeliveryResponse;
import keywhiz.api.model.Client;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import keywhiz.service.daos.AclJooqDao;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SecretsDeliveryResourceTest {
  private static final OffsetDateTime NOW = OffsetDateTime.now();

  @Mock AclJooqDao aclJooqDao;
  SecretsDeliveryResource secretsDeliveryResource;

  Secret firstSecret = new Secret(0, "first_secret_name", null, null,
      Base64.getEncoder().encodeToString("first_secret_contents".getBytes(UTF_8)), NOW, null, NOW, null, null,
      null, null);
  SanitizedSecret sanitizedFirstSecret = SanitizedSecret.fromSecret(firstSecret);
  Secret secondSecret = new Secret(1, "second_secret_name", null, null,
      Base64.getEncoder().encodeToString("second_secret_contents".getBytes(UTF_8)), NOW, null, NOW, null, null,
      null, null);
  SanitizedSecret sanitizedSecondSecret = SanitizedSecret.fromSecret(secondSecret);
  Client client;

  @Before public void setUp() {
    secretsDeliveryResource = new SecretsDeliveryResource(aclJooqDao);
    client = new Client(0, "client_name", null, null, null, null, null, false, false);
  }

  @Test public void returnsEmptyJsonArrayWhenUserHasNoSecrets() throws Exception {
    when(aclJooqDao.getSanitizedSecretsFor(client)).thenReturn(ImmutableSet.of());
    List<SecretDeliveryResponse> secrets = secretsDeliveryResource.getSecrets(client);
    assertThat(secrets).isEmpty();
  }

  @Test public void returnsJsonArrayWhenUserHasOneSecret() throws Exception {
    when(aclJooqDao.getSanitizedSecretsFor(client)).thenReturn(ImmutableSet.of(sanitizedFirstSecret));

    List<SecretDeliveryResponse> secrets = secretsDeliveryResource.getSecrets(client);
    assertThat(secrets).containsOnly(SecretDeliveryResponse.fromSanitizedSecret(
        SanitizedSecret.fromSecret(firstSecret)));
  }

  @Test public void returnsJsonArrayWhenUserHasMultipleSecrets() throws Exception {
    when(aclJooqDao.getSanitizedSecretsFor(client))
        .thenReturn(ImmutableSet.of(sanitizedFirstSecret, sanitizedSecondSecret));

    List<SecretDeliveryResponse> secrets = secretsDeliveryResource.getSecrets(client);
    assertThat(secrets).containsOnly(
        SecretDeliveryResponse.fromSanitizedSecret(SanitizedSecret.fromSecret(firstSecret)),
        SecretDeliveryResponse.fromSanitizedSecret(SanitizedSecret.fromSecret(secondSecret)));
  }
}
