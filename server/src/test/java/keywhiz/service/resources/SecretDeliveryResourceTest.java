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

import java.time.OffsetDateTime;
import java.util.Optional;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotFoundException;
import keywhiz.api.SecretDeliveryResponse;
import keywhiz.api.model.Client;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import keywhiz.api.model.VersionGenerator;
import keywhiz.service.daos.AclJooqDao;
import keywhiz.service.daos.ClientDAO;
import keywhiz.service.daos.SecretController;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SecretDeliveryResourceTest {
  private static final OffsetDateTime NOW = OffsetDateTime.now();

  @Mock SecretController secretController;
  @Mock AclJooqDao aclJooqDao;
  @Mock ClientDAO clientDAO;
  SecretDeliveryResource secretDeliveryResource;

  final Client client = new Client(0, "principal", null, null, null, null, null, false, false);
  final Secret secret = new Secret(0, "secret_name", null, null, "secret_value", NOW, null, NOW, null,
      null, null, null);
  final Secret secretBase64 = new Secret(1, "Base64With=", null, null, "SGVsbG8=", NOW, null, NOW,
      null, null, null, null);

  @Before public void setUp() {
    secretDeliveryResource = new SecretDeliveryResource(secretController, aclJooqDao, clientDAO);
  }

  @Test public void returnsSecretWhenAllowed() throws Exception {
    Secret secret = new Secret(0, "secret_name", null, null, "unused_secret", NOW, null, NOW, null, null, null, null);
    SanitizedSecret sanitizedSecret = SanitizedSecret.fromSecret(secret);
    String name = sanitizedSecret.name();
    String version = sanitizedSecret.version();

    when(aclJooqDao.getSanitizedSecretFor(client, name, version))
        .thenReturn(Optional.of(sanitizedSecret));
    when(secretController.getSecretByNameAndVersion(name, version))
        .thenReturn(Optional.of(secret));

    SecretDeliveryResponse response = secretDeliveryResource.getSecret(sanitizedSecret.name(), client);
    assertThat(response).isEqualTo(SecretDeliveryResponse.fromSecret(secret));
  }

  @Test public void returnsVersionedSecretWhenAllowed() throws Exception {
    String name = "secret_name";
    String version = VersionGenerator.now().toHex();
    Secret versionedSecret = new Secret(2, name, version, null, "U3BpZGVybWFu", NOW, null, NOW,
        null, null, null, null);

    when(aclJooqDao.getSanitizedSecretFor(client, name, version))
        .thenReturn(Optional.of(SanitizedSecret.fromSecret(versionedSecret)));
    when(secretController.getSecretByNameAndVersion(name, version))
        .thenReturn(Optional.of(versionedSecret));

    String displayName = versionedSecret.getDisplayName();
    SecretDeliveryResponse response = secretDeliveryResource.getSecret(displayName, client);
    assertThat(response).isEqualTo(SecretDeliveryResponse.fromSecret(versionedSecret));
  }

  @Test(expected = NotFoundException.class)
  public void returnsNotFoundWhenClientDoesNotExist() throws Exception {
    when(aclJooqDao.getSanitizedSecretFor(client, secret.getName(), "")).thenReturn(Optional.empty());
    when(clientDAO.getClient(client.getName())).thenReturn(Optional.empty());
    when(secretController.getSecretByNameAndVersion(secret.getName(), ""))
        .thenReturn(Optional.of(secret));

    secretDeliveryResource.getSecret(secret.getName(), client);
  }

  @Test(expected = NotFoundException.class)
  public void returnsNotFoundWhenSecretDoesNotExist() throws Exception {
    when(aclJooqDao.getSanitizedSecretFor(client, "secret_name", "")).thenReturn(Optional.empty());
    when(clientDAO.getClient(client.getName())).thenReturn(Optional.of(client));
    when(secretController.getSecretByNameAndVersion("secret_name", ""))
        .thenReturn(Optional.empty());

    secretDeliveryResource.getSecret("secret_name", client);
  }

  @Test(expected = ForbiddenException.class)
  public void returnsUnauthorizedWhenDenied() throws Exception {
    when(aclJooqDao.getSanitizedSecretFor(client, secret.getName(), "")).thenReturn(Optional.empty());
    when(clientDAO.getClient(client.getName())).thenReturn(Optional.of(client));
    when(secretController.getSecretByNameAndVersion(secret.getName(), ""))
        .thenReturn(Optional.of(secret));

    secretDeliveryResource.getSecret(secret.getName(), client);
  }

  @Test public void doesNotEscapeBase64() throws Exception {
    String name = secretBase64.getName();
    String version = secretBase64.getVersion();

    when(aclJooqDao.getSanitizedSecretFor(client, name, version))
        .thenReturn(Optional.of(SanitizedSecret.fromSecret(secretBase64)));
    when(secretController.getSecretByNameAndVersion(name, version))
        .thenReturn(Optional.of(secretBase64));

    SecretDeliveryResponse response = secretDeliveryResource.getSecret(secretBase64.getName(), client);
    assertThat(response.getSecret()).isEqualTo(secretBase64.getSecret());
  }
}
