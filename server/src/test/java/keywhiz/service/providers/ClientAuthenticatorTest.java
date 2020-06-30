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

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.security.Principal;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Optional;
import javax.ws.rs.NotAuthorizedException;
import keywhiz.api.ApiDate;
import keywhiz.api.model.Client;
import keywhiz.auth.mutualssl.CertificatePrincipal;
import keywhiz.auth.mutualssl.SimplePrincipal;
import keywhiz.service.config.ClientAuthConfig;
import keywhiz.service.config.ClientAuthTypeConfig;
import keywhiz.service.daos.ClientDAO;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class ClientAuthenticatorTest {
  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  private static final String clientName = "principal";
  private static final String clientSpiffeStr = "spiffe://example.org/principal";
  private static URI clientSpiffe;

  private static final Principal simplePrincipal =
      SimplePrincipal.of(format("CN=%s,OU=organizational-unit", clientName));
  private static final Client client =
      new Client(0, clientName, null, clientSpiffeStr, null, null, null, null,
          null, null, true, false);

  private static Principal certPrincipal;

  // certstrap init --common-name "KeywhizAuth"
  // certstrap request-cert --common-name principal --ou organizational-unit
  //     --uri spiffe://example.org/principal
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

  // certstrap init --common-name "KeywhizAuth"
  // certstrap request-cert --common-name other-principal --ou organizational-unit
  //     --uri spiffe://example.org/principal,spiffe://example.org/other-principal
  // certstrap sign other-principal --CA KeywhizAuth
  private static final String multipleSpiffePem = "-----BEGIN CERTIFICATE-----\n"
      + "MIIEnTCCAoWgAwIBAgIRAJ3eemLVkvReTvZJqcBWWBswDQYJKoZIhvcNAQELBQAw\n"
      + "FjEUMBIGA1UEAxMLS2V5d2hpekF1dGgwHhcNMjAwNjI1MjIxMTQ5WhcNMjExMjE2\n"
      + "MDAzNzAwWjA4MRwwGgYDVQQLExNvcmdhbml6YXRpb25hbC11bml0MRgwFgYDVQQD\n"
      + "Ew9vdGhlci1wcmluY2lwYWwwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIB\n"
      + "AQC5ImUMp17F38Wyyph/rUIL4qJE86pZLIrv2lyZd8qzHXxw1o9TawBu3fNtFx0Y\n"
      + "+96lAb7A50uNMfuzibb2U+w2mDEikFGtchrzDHC6jNPCq7eXL06adcnIEyK2w0Mq\n"
      + "qvlzuHGJxLNSRsmiuV4SQS3K96t4vCHF30WrboQKD8xsdeqdT2cspkr3WD3nHJih\n"
      + "ZMg1YdekoV7+qogXrPawnJ7kKK37hBFAF3OnHsxJMS5UMWR6SHYpYU+V8yifp24H\n"
      + "erJpL9lwJ2BXpMKHsewCLC+VclQVxArCcbmQsXmZhzKTikff/ZngiSfnSnDm9LQL\n"
      + "vG+HCzr36xwCEmlWnusbcTozAgMBAAGjgcMwgcAwDgYDVR0PAQH/BAQDAgO4MB0G\n"
      + "A1UdJQQWMBQGCCsGAQUFBwMBBggrBgEFBQcDAjAdBgNVHQ4EFgQUpGY0z/FVU+y/\n"
      + "ZlAFIdnoHzj25JEwHwYDVR0jBBgwFoAUUtVdMwHcbWdRZ/VypTBlpCbxgDIwTwYD\n"
      + "VR0RBEgwRoYec3BpZmZlOi8vZXhhbXBsZS5vcmcvcHJpbmNpcGFshiRzcGlmZmU6\n"
      + "Ly9leGFtcGxlLm9yZy9vdGhlci1wcmluY2lwYWwwDQYJKoZIhvcNAQELBQADggIB\n"
      + "AFjQdKh/mETvX82ltdxzsCmu+2WQXkw0Q7L2toseAxrAEdH/3teHfFlm/E4ozv3d\n"
      + "D46DR6SnWY5qsPqlucMQlxGnLQd291JEFeRd2pr5avV7F4K85UhslyM27DrihTpT\n"
      + "Lex5muGECm9oaJz02QanHOiJU9I5i3ggnJQnfiu9cSlbi/puLb9WR/2Q+kldMQ5A\n"
      + "m3WaP1eKGug022n/bM+OFGgaoAJHH7l2MBfwAvIltgJZLHsRLAP16G3OotmDt2Eh\n"
      + "/A3WJt0EbD3PIPkUdt643qdfQGHgw+3ATGARVPypSYG9iOeo9oGUily4irGk5Iss\n"
      + "wgU10lViC6BHcLs3YY+D8oB7Ik0p2l9JZmJmBuArRMqztSnRiVKIteHUMN0cAfrP\n"
      + "3VrKlKjJn9amdWk0feiUwb7uWO2CunQewM26MGzM0RntdxyQZ8lsqq5e8zTsHDol\n"
      + "CrD7eyfBv+98TURnNcLDAXMqd3amAhgmedUDVTrNootKuS6fyg+AmmRjcKOZ4t4g\n"
      + "16cNALu0koCWdETGGsaeu/NX7RA/ztNme+i6ZCIFaS1Fp2ucTfWyWRMkQ9+DM3vC\n"
      + "jNPreGsiWrjoReHqRxO+dtOuQf5gSGDTKMTvlESD6xilmmE5n58MCl1bSamEY7rv\n"
      + "Lz9DeXBJLBSOaXNO+/wdJY/Ix8ADwQYc+jLhdyVOKW7k\n"
      + "-----END CERTIFICATE-----";

  @Mock ClientAuthTypeConfig clientAuthTypeConfig;
  @Mock ClientAuthConfig clientAuthConfig;

  @Mock ClientDAO clientDAO;

  ClientAuthenticator authenticator;

  @Before public void setUp() throws Exception {
    clientSpiffe = new URI(clientSpiffeStr);

    CertificateFactory cf = CertificateFactory.getInstance("X.509");
    X509Certificate clientCert = (X509Certificate) cf.generateCertificate(
        new ByteArrayInputStream(clientPem.getBytes(UTF_8)));
    certPrincipal = new CertificatePrincipal(clientCert.getSubjectDN().toString(),
        new X509Certificate[] {clientCert});

    authenticator = new ClientAuthenticator(clientDAO, clientDAO, clientAuthConfig);

    when(clientDAO.getClientByName(clientName)).thenReturn(Optional.of(client));
    when(clientDAO.getClientBySpiffeId(clientSpiffe)).thenReturn(Optional.of(client));

    when(clientAuthConfig.typeConfig()).thenReturn(clientAuthTypeConfig);

    when(clientAuthTypeConfig.useCommonName()).thenReturn(true);
    when(clientAuthTypeConfig.useSpiffeId()).thenReturn(true);
  }

  @Test public void returnsClientWhenClientPresent_simplePrincipal() {
    assertThat(authenticator.authenticate(simplePrincipal, true)).isEqualTo(Optional.of(client));
  }

  @Test public void retrievesClientIfPresent_certPrincipal() {
    assertThat(authenticator.authenticate(certPrincipal, false)).isEqualTo(Optional.of(client));
  }

  @Test public void rejectsDisabledClients() {
    Client disabledClient =
        new Client(1, "disabled", null, null, null, null, null, null, null, null,
            false, false
            /* disabled */);
    when(clientDAO.getClientByName("disabled")).thenReturn(Optional.of(disabledClient));

    assertThat(authenticator.authenticate(SimplePrincipal.of("CN=disabled"), true)).isEmpty();
  }

  @Test public void createsDbRecordForNewClient_whenConfigured() {
    ApiDate now = ApiDate.now();
    Client newClient =
        new Client(2345L, "new-client", "desc", null, now, "automatic", now, "automatic",
            null, null, true, false
        );

    // lookup doesn't find client
    when(clientDAO.getClientByName("new-client")).thenReturn(Optional.empty());

    // a new DB record is created
    when(clientDAO.createClient(eq("new-client"), eq("automatic"), any(), any())).thenReturn(2345L);
    when(clientDAO.getClientById(2345L)).thenReturn(Optional.of(newClient));

    assertThat(authenticator.authenticate(SimplePrincipal.of("CN=new-client"), true)).isEqualTo(
        Optional.of(newClient));
  }

  @Test public void doesNotCreateDbRecordForNewClient_whenNotConfigured() {
    ApiDate now = ApiDate.now();
    Client newClient =
        new Client(2345L, "new-client", "desc", null, now, "automatic", now, "automatic",
            null, null, true, false
        );

    // lookup doesn't find client
    when(clientDAO.getClientByName("new-client")).thenReturn(Optional.empty());

    // a new DB record should not be created, but mock the DAO to create a client if called
    when(clientDAO.createClient(eq("new-client"), eq("automatic"), any(), any())).thenReturn(2345L);
    when(clientDAO.getClientById(2345L)).thenReturn(Optional.of(newClient));

    assertThat(authenticator.authenticate(SimplePrincipal.of("CN=new-client"), false)).isEmpty();

    // the authenticator should not have tried to create the new client
    verify(clientDAO, never()).createClient(anyString(), anyString(), anyString(), any());
  }

  @Test public void updatesClientLastSeen() {
    assertThat(authenticator.authenticate(simplePrincipal, true)).isPresent();
    verify(clientDAO, times(1)).sawClient(any(), eq(simplePrincipal));
  }

  @Test(expected = NotAuthorizedException.class)
  public void rejectsCertMatchingMultipleClients() {
    ApiDate now = ApiDate.now();
    Client otherClient =
        new Client(2345L, "other-client", "desc", null, now, "automatic", now, "automatic",
            null, null, true, false
        );

    when(clientDAO.getClientByName(clientName)).thenReturn(Optional.of(client));
    when(clientDAO.getClientBySpiffeId(clientSpiffe)).thenReturn(Optional.of(otherClient));

    authenticator.authenticate(certPrincipal, true);
  }

  @Test
  public void respectsClientAuthConfig() {
    ApiDate now = ApiDate.now();
    Client otherClient =
        new Client(2345L, "other-client", "desc", null, now, "automatic", now, "automatic",
            null, null, true, false
        );

    when(clientDAO.getClientByName(clientName)).thenReturn(Optional.of(client));
    when(clientDAO.getClientBySpiffeId(clientSpiffe)).thenReturn(
        Optional.of(otherClient));

    // Retrieve the client using the client name only
    when(clientAuthTypeConfig.useCommonName()).thenReturn(true);
    when(clientAuthTypeConfig.useSpiffeId()).thenReturn(false);

    assertThat(authenticator.authenticate(certPrincipal, false)).isEqualTo(Optional.of(client));

    // Retrieve the client using the SPIFFE ID only
    when(clientAuthTypeConfig.useCommonName()).thenReturn(false);
    when(clientAuthTypeConfig.useSpiffeId()).thenReturn(true);

    assertThat(authenticator.authenticate(certPrincipal, false)).isEqualTo(
        Optional.of(otherClient));
  }

  @Test public void ignoresMultipleSpiffeIds() throws Exception {
    CertificateFactory cf = CertificateFactory.getInstance("X.509");
    X509Certificate multipleSpiffeClientCert = (X509Certificate) cf.generateCertificate(
        new ByteArrayInputStream(multipleSpiffePem.getBytes(UTF_8)));
    Principal multipleSpiffePrincipal =
        new CertificatePrincipal(multipleSpiffeClientCert.getSubjectDN().toString(),
            new X509Certificate[] {multipleSpiffeClientCert});

    // Use only the (malformatted) SPIFFE IDs to retrieve a client (which should fail)
    when(clientAuthTypeConfig.useCommonName()).thenReturn(false);
    when(clientAuthTypeConfig.useSpiffeId()).thenReturn(true);

    assertThat(authenticator.authenticate(multipleSpiffePrincipal, false)).isEmpty();
    verifyNoInteractions(clientDAO);
  }
}
