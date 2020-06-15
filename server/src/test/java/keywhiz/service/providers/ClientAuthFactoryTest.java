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

package keywhiz.service.providers;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.core.SecurityContext;
import keywhiz.api.ApiDate;
import keywhiz.api.model.Client;
import keywhiz.auth.mutualssl.SimplePrincipal;
import keywhiz.service.config.ClientAuthConfig;
import keywhiz.service.config.ClientAuthTypeConfig;
import keywhiz.service.config.XfccSourceConfig;
import keywhiz.service.daos.ClientDAO;
import org.eclipse.jetty.util.UrlEncoded;
import org.glassfish.jersey.server.ContainerRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ClientAuthFactoryTest {
  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  private static final Principal principal =
      SimplePrincipal.of("CN=principal,OU=organizational-unit");
  private static final Client client =
      new Client(0, "principal", null, null, null, null, null, null, null, null, true, false);

  // certstrap init --common-name "KeywhizAuth"
  // certstrap request-cert --common-name principal --ou organizational-unit --uri spiffe://example.org/principal
  // certstrap sign principal --CA KeywhizAuth
  private static final String clientPem =
      "-----BEGIN CERTIFICATE-----\n\n"
          + "MIIEcTCCAlmgAwIBAgIRALryCWgCxplmVoNtywrAfR0wDQYJKoZIhvcNAQELBQAw\n"
          + "FjEUMBIGA1UEAxMLS2V5d2hpekF1dGgwHhcNMjAwNjE2MDAzODI0WhcNMjExMjE2\n"
          + "MDAzNzAwWjAyMRwwGgYDVQQLExNvcmdhbml6YXRpb25hbC11bml0MRIwEAYDVQQD\n"
          + "EwlwcmluY2lwYWwwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDz9ex2\n"
          + "HQ7YA9nyOigFjeOqSpkDVReSG2IWSDHnugkO3TVY7NqfgMx1I+KESAj5w/PXIv1I\n"
          + "Aa4qUnLYQ2IqgYUYvJqTt6DtlFLC6dWdgV0x/zRIbtybPR9Ww0eObShzy4od97w4\n"
          + "zMN1/xXwpIrTNhn9wwzi4l7vtOYwxtoss/B6MBKyxB8R6iEUupINcFANFzcKdniG\n"
          + "40HcEW8aUS6aRC8bCc4e6ACJp3VR5wnHpHXUlnkeOyTX5yWD8MKni9eY2t0Ra5OX\n"
          + "tV1NEwOPJz8fTp8aRnoe8+Rq8Lm7W59PO7cJ45srlQ5kKnagha6KB8TTzvNOtYqj\n"
          + "SgQNkb/OhS8R7Z/9AgMBAAGjgZ0wgZowDgYDVR0PAQH/BAQDAgO4MB0GA1UdJQQW\n"
          + "MBQGCCsGAQUFBwMBBggrBgEFBQcDAjAdBgNVHQ4EFgQUj35sbmMzi/R/rrdMJHnj\n"
          + "n1TLhMwwHwYDVR0jBBgwFoAUUtVdMwHcbWdRZ/VypTBlpCbxgDIwKQYDVR0RBCIw\n"
          + "IIYec3BpZmZlOi8vZXhhbXBsZS5vcmcvcHJpbmNpcGFsMA0GCSqGSIb3DQEBCwUA\n"
          + "A4ICAQCXPUPcv9ADJACy5D4Z8bQlGyDj131+vthj95eyO8ftPzTrJANGwpl93oO1\n"
          + "d7lNh1h2exj/e+gtxdYE/I+DYyvHb2Op+SRNN/ZeZntaoqt22p8CGYIpsPQHttLw\n"
          + "KJ91ekZhyQhphzgceMrhcnSc/RH7L373ZkFi5FC9EAixKsaDftz+NVTk7vhc+cLV\n"
          + "Mhkhc3L3dA/Ffqpq6iRVs9eefFlN5Oot3PIihvCrbtl0tur02PjLVWQr5Y/nyVG0\n"
          + "kN0LU7+w3GNddqB0gsLkwBPZ+UtmbyjHaVQN50jZxA7ysr+EjNhTyZ3lliPX4bGE\n"
          + "TS/jTexOAObS3tC+e157k2UXbFMNZrE/pQb3juOJHcBgwpZ8FnYlwqe8VIJ6513K\n"
          + "sOTS2lqAXYCaCOC0X6grRuL+s2JTzhzfgz2xuOSQVtvGYK5FijQVpGBR5BlfgpMM\n"
          + "/W45PGdkvZGI4281VZUfTSSYK/OstnBAD3BgZXhnQg28dj8BD4jNd5JP7cKHb+ID\n"
          + "33dh8mAGmSmiSPbxkVwq1AKwa5y6hbfvPIQGaUKveQe0JLTFlU4KmYIRv/nl8N83\n"
          + "st5hq3sW1qoqXZZ71A/T/BYPODcKgeEBzJ64l7jHtPN91SE8U8vhcrpEWZb/D/PI\n"
          + "vZTiHaxVIvRRokUPFie1drkj5I7Q7qXqHOCy22rgccR64wkNVg==\n"
          + "-----END CERTIFICATE-----";
  private static final String xfccHeader =
      format("Cert=\"%s\"", UrlEncoded.encodeString(clientPem, UTF_8));

  private static final int xfccAllowedPort = 4445;
  private static final int xfccDisallowedPort = 4446;
  private static final ClientAuthConfig clientAuthConfig = ClientAuthConfig.of(
      XfccSourceConfig.of(List.of(xfccAllowedPort)),
      ClientAuthTypeConfig.of(true, false)
  );

  @Mock ContainerRequest request;
  @Mock SecurityContext securityContext;
  @Mock ClientDAO clientDAO;

  ClientAuthFactory factory;

  @Before public void setUp() throws Exception {
    factory = new ClientAuthFactory(clientDAO, clientAuthConfig);

    when(request.getSecurityContext()).thenReturn(securityContext);
    when(request.getBaseUri()).thenReturn(new URI(format("https://localhost:%d", xfccAllowedPort)));
    when(clientDAO.getClient("principal")).thenReturn(Optional.of(client));
  }

  @Test public void clientWhenClientPresent() {
    when(securityContext.getUserPrincipal()).thenReturn(principal);

    assertThat(factory.provide(request)).isEqualTo(client);
  }

  @Test(expected = NotAuthorizedException.class)
  public void clientWhenPrincipalAbsentThrows() {
    when(securityContext.getUserPrincipal()).thenReturn(null);

    factory.provide(request);
  }

  @Test(expected = NotAuthorizedException.class)
  public void rejectsDisabledClients() {
    Client disabledClient =
        new Client(1, "disabled", null, null, null, null, null, null, null, null,
            false, false
            /* disabled */);

    when(securityContext.getUserPrincipal()).thenReturn(SimplePrincipal.of("CN=disabled"));
    when(clientDAO.getClient("disabled")).thenReturn(Optional.of(disabledClient));

    factory.provide(request);
  }

  @Test public void createsDbRecordForNewClient() {
    ApiDate now = ApiDate.now();
    Client newClient =
        new Client(2345L, "new-client", "desc", null, now, "automatic", now, "automatic",
            null, null, true, false
        );

    // lookup doesn't find client
    when(securityContext.getUserPrincipal()).thenReturn(SimplePrincipal.of("CN=new-client"));
    when(clientDAO.getClient("new-client")).thenReturn(Optional.empty());

    // a new DB record is created
    when(clientDAO.createClient(eq("new-client"), eq("automatic"), any(), any())).thenReturn(2345L);
    when(clientDAO.getClientById(2345L)).thenReturn(Optional.of(newClient));

    assertThat(factory.provide(request)).isEqualTo(newClient);
  }

  @Test public void updatesClientLastSeen() {
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    factory.provide(request);
    verify(clientDAO, times(1)).sawClient(any(), eq(principal));
  }

  @Test public void clientWhenClientPresent_fromXfccHeader() {
    when(request.getRequestHeader(ClientAuthFactory.XFCC_HEADER_NAME)).thenReturn(
        List.of(xfccHeader));

    assertThat(factory.provide(request)).isEqualTo(client);
  }

  @Test(expected = NotAuthorizedException.class)
  public void rejectsUnauthorizedXfccPort() throws Exception {
    when(request.getBaseUri()).thenReturn(
        new URI(format("https://localhost:%d", xfccDisallowedPort)));
    when(request.getRequestHeader(ClientAuthFactory.XFCC_HEADER_NAME)).thenReturn(
        List.of(xfccHeader));

    factory.provide(request);
  }

  @Test(expected = NotAuthorizedException.class)
  public void rejectsMultipleXfccEntries() {
    when(request.getRequestHeader(ClientAuthFactory.XFCC_HEADER_NAME)).thenReturn(
        List.of(xfccHeader, xfccHeader));

    factory.provide(request);
  }

  @Test(expected = NotAuthorizedException.class)
  public void rejectsMissingXfccClient() {
    when(request.getRequestHeader(ClientAuthFactory.XFCC_HEADER_NAME)).thenReturn(
        List.of("By=principal"));

    factory.provide(request);
  }
}
