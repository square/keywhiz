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
import keywhiz.api.ApiDate;
import keywhiz.api.BatchSecretRequest;
import keywhiz.api.SecretDeliveryResponse;
import keywhiz.api.model.Client;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import keywhiz.service.daos.AclDAO;
import keywhiz.service.daos.ClientDAO;
import keywhiz.service.daos.SecretController;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotFoundException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class BatchSecretDeliveryResourceTest {
    private static final ApiDate NOW = ApiDate.now();

    @Rule
    public MockitoRule mockito = MockitoJUnit.rule();

    @Mock
    SecretController secretController;
    @Mock
    AclDAO aclDAO;
    @Mock
    ClientDAO clientDAO;
    BatchSecretDeliveryResource batchSecretDeliveryResource;

    final Client client = new Client(0, "principal", null, null, null, null, null, null, null, null, false,
            false);

    final Secret secret = new Secret(0, "secret_name", null, () -> "secret_value", "checksum", NOW, null, NOW, null,
            null, null, null, 0, 1L, NOW, null);
    final Secret secretBase64 = new Secret(1, "Base64With=", null, () -> "SGVsbG8=", "checksum", NOW, null, NOW,
            null, null, null, null, 0, 1L, NOW, null);

    final Secret secret2 = new Secret(2, "secret2_name", null, () -> "secret_value2", "checksum2", NOW, null, NOW, null,
            null, null, null, 0, 1L, NOW, null);


    @Before
    public void setUp() {
        batchSecretDeliveryResource = new BatchSecretDeliveryResource(secretController, aclDAO, clientDAO);
    }

    @Test
    public void returnsSingleSecretWhenAllowed() throws Exception {
        SanitizedSecret sanitizedSecret = SanitizedSecret.fromSecret(secret);

        ImmutableList<String> secretnames = ImmutableList.of(sanitizedSecret.name());
        BatchSecretRequest req = BatchSecretRequest.create(secretnames);

        when(aclDAO.getBatchSanitizedSecretsFor(client, secretnames))
                .thenReturn(List.of(sanitizedSecret));
        when(secretController.getSecretsByName(secretnames))
                .thenReturn(List.of(secret));

        List<SecretDeliveryResponse> response = batchSecretDeliveryResource.getBatchSecret(client, req);
        assertThat(response).isEqualTo(ImmutableList.of(SecretDeliveryResponse.fromSecret(secret)));
    }

    @Test
    public void returnsMultipleSecretsWhenAllowed() throws Exception {
        SanitizedSecret sanitizedSecret = SanitizedSecret.fromSecret(secret);
        SanitizedSecret sanitizedSecret2 = SanitizedSecret.fromSecret(secret2);

        ImmutableList<String> secretnames = ImmutableList.of(sanitizedSecret.name(), sanitizedSecret2.name());
        BatchSecretRequest req = BatchSecretRequest.create(secretnames);

        when(aclDAO.getBatchSanitizedSecretsFor(client, secretnames))
                .thenReturn(List.of(sanitizedSecret, sanitizedSecret2));
        when(secretController.getSecretsByName(secretnames))
                .thenReturn(List.of(secret, secret2));

        List<SecretDeliveryResponse> response = batchSecretDeliveryResource.getBatchSecret(client, req);

        // The order doesn't matter
        boolean matches = false;

        if (response.equals(ImmutableList.of(SecretDeliveryResponse.fromSecret(secret), SecretDeliveryResponse.fromSecret(secret2)))) {
            matches = true;
        } else if (response.equals(ImmutableList.of(SecretDeliveryResponse.fromSecret(secret2), SecretDeliveryResponse.fromSecret(secret)))) {
            matches = true;
        }

        assertThat(matches).isTrue();
    }

    @Test(expected = NotFoundException.class)
    public void returnsNotFoundWhenClientDoesNotExist() throws Exception {
        when(aclDAO.getBatchSanitizedSecretsFor(client, ImmutableList.of(secret.getName()))).thenReturn(ImmutableList.<SanitizedSecret>of());
        when(clientDAO.getClientByName(client.getName())).thenReturn(Optional.empty());
        when(secretController.getSecretsByName(ImmutableList.of(secret.getName())))
                .thenReturn(ImmutableList.<Secret>of());

        batchSecretDeliveryResource.getBatchSecret(client, BatchSecretRequest.create(ImmutableList.of(secret.getName())));
    }


    @Test(expected = NotFoundException.class)
    public void returnsNotFoundWhenSecretDoesNotExist() throws Exception {
        when(aclDAO.getBatchSanitizedSecretsFor(client, ImmutableList.of("secret_name"))).thenReturn(ImmutableList.<SanitizedSecret>of());
        when(clientDAO.getClientByName(client.getName())).thenReturn(Optional.of(client));
        when(secretController.getSecretsByName(ImmutableList.of("secret_name")))
                .thenReturn(ImmutableList.<Secret>of());

        batchSecretDeliveryResource.getBatchSecret(client, BatchSecretRequest.create(ImmutableList.of("secret_name")));
    }


    @Test(expected = ForbiddenException.class)
    public void returnsUnauthorizedWhenDenied() throws Exception {
        when(aclDAO.getBatchSanitizedSecretsFor(client, ImmutableList.of(secret.getName()))).thenReturn(ImmutableList.<SanitizedSecret>of());
        when(clientDAO.getClientByName(client.getName())).thenReturn(Optional.of(client));
        when(secretController.getSecretsByName(ImmutableList.of(secret.getName())))
                .thenReturn(ImmutableList.<Secret>of(secret));

        batchSecretDeliveryResource.getBatchSecret(client, BatchSecretRequest.create(ImmutableList.of(secret.getName())));
    }

    @Test
    public void doesNotEscapeBase64() throws Exception {
        String name = secretBase64.getName();

        when(aclDAO.getBatchSanitizedSecretsFor(client, ImmutableList.of(name)))
                .thenReturn(ImmutableList.of(SanitizedSecret.fromSecret(secretBase64)));
        when(secretController.getSecretsByName(ImmutableList.of(name)))
                .thenReturn(ImmutableList.of(secretBase64));

        List<SecretDeliveryResponse> response = batchSecretDeliveryResource.getBatchSecret(client, BatchSecretRequest.create(ImmutableList.of(secretBase64.getName())));
        assertThat(response.get(0).getSecret()).isEqualTo(secretBase64.getSecret());
        assertThat(response.size()).isEqualTo(1);
    }

}
