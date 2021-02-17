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
import java.net.URISyntaxException;
import java.security.Principal;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.core.SecurityContext;
import keywhiz.api.model.Client;
import keywhiz.auth.mutualssl.CertificatePrincipal;
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
import static org.mockito.Mockito.when;

public class ClientAuthFactoryTest {
  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  private static Principal clientPrincipal;
  private static final String clientName = "principal";
  private static final String clientSpiffe = "spiffe://example.org/principal";
  private static final Client client =
      new Client(0, clientName, null, clientSpiffe, null, null, null, null, null, null, true,
          false);

  private static final String newClientName = "new-principal";
  private static final String newClientSpiffe = "spiffe://example.org/new-principal";
  private static final Client newClient =
      new Client(123L, newClientName, null, newClientSpiffe, null, null, null, null, null, null,
          true,
          false);

  private static final String callerSpiffeHeaderName = "x-caller-spiffe-id";
  private static final String nonExistentClientSpiffe =
      "spiffe://example.org/non-existent-principal";

  private static Principal xfccPrincipal;
  private static final String xfccName = "principal-allowed-for-xfcc";
  private static final String xfccSpiffe = "spiffe://example.org/principal-allowed-for-xfcc";

  // certstrap init --common-name "KeywhizAuth"
  // certstrap request-cert --common-name principal-allowed-for-xfcc --ou organizational-unit
  //     --uri spiffe://example.org/principal-allowed-for-xfcc
  // certstrap sign principal-allowed-for-xfcc --CA KeywhizAuth
  private static final String xfccPem = "-----BEGIN CERTIFICATE-----\n"
      + "MIIEkzCCAnugAwIBAgIRAOQ9Lh+heQYgQENI/d6pnz4wDQYJKoZIhvcNAQELBQAw\n"
      + "FjEUMBIGA1UEAxMLS2V5d2hpekF1dGgwHhcNMjAwNjI1MjIyMTE0WhcNMjExMjE2\n"
      + "MDAzNzAxWjBDMRwwGgYDVQQLExNvcmdhbml6YXRpb25hbC11bml0MSMwIQYDVQQD\n"
      + "ExpwcmluY2lwYWwtYWxsb3dlZC1mb3IteGZjYzCCASIwDQYJKoZIhvcNAQEBBQAD\n"
      + "ggEPADCCAQoCggEBANIDghKpOJv4En7ubT8bmgab+2kTidQKK6PaFm118K3z8Qr9\n"
      + "sqENKPcjsGgJucH/CWrxN3JpybX1NyEe+XvxWqRFqFsNTfOTKN8NIdqVxW0LPGCv\n"
      + "AIH3Nrxuo26isqMRRF6sHp8g6C98H9EoDW3w0wFHr2J/M/5WVx9biBHUhTdNpLAj\n"
      + "pQ9VUBn/1mNYMaTsJMxw/YPW8pH6yzLUGw9Kq4Dm6RCUqSc3VyC9uQFIHKeIphme\n"
      + "P4EeoQcoMDVaSghHeXJ2qKpWPpokq6V1Yx56AmVHTui2qanp0InOAqKozU43C/rt\n"
      + "0EWk+jsd4IJizpSnEDOdvSqqx3vzNhzxH3mLwAUCAwEAAaOBrjCBqzAOBgNVHQ8B\n"
      + "Af8EBAMCA7gwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUFBwMCMB0GA1UdDgQW\n"
      + "BBRmicipX4nvPRhdFOAziDFQ03NVFzAfBgNVHSMEGDAWgBRS1V0zAdxtZ1Fn9XKl\n"
      + "MGWkJvGAMjA6BgNVHREEMzAxhi9zcGlmZmU6Ly9leGFtcGxlLm9yZy9wcmluY2lw\n"
      + "YWwtYWxsb3dlZC1mb3IteGZjYzANBgkqhkiG9w0BAQsFAAOCAgEAJIG7+DoDvH32\n"
      + "3kJV2uhPUg2g+omouj9ZaHCT7j4I+B/sen6xvE3qHSM2j2bzIt20/RjM3ql6Bqc8\n"
      + "OPR52NUVZUrfjCmkMc2clN9fSjIaKArx63wXpCgNJckEkhzPgoFjLlpkfIjXiSFb\n"
      + "0ZMVrQHsPd8yAOnLxTm9RxoCh029TGxs0a5a7whWZG6UwmHgt4z1dZCO519CPpER\n"
      + "EW/sRC7ceJiDgJULWDjYoJrHBS/vWYWZxTboB34bk9ZkGr8eRkswboiQGzjPV7cU\n"
      + "IlYMa4wqZTDCJrevl3WbUiIVRy5k9pM88LNAqRRsCF3tlB0neyhvUdzMTy1MWoW/\n"
      + "sJvGbBQWyRbk6mvzcvcKomsGosGvtLPfqSCKxeGpW1wu5nz0ybNpWr0h//E3HP2W\n"
      + "k/WlOVrLZkQLergW5ggzhwuJYnSi5ei1inCC9DzOfj8HRiJXJ69Ef2tQRLkSVUb+\n"
      + "2yQJn9Z62suVSTi/Pk9zTbaHlsq12ovP4Btt23hxlg0ScmJNX83a38fEFjOT/ELW\n"
      + "fUpvPDvECi/B+yGZhIhcvA2fn7+8+JUjZG1WT6RRBj1fCOOTsmcSa1bA57v6/1sM\n"
      + "K+QLScCfQ/QgWR5kMhvyGH6Yw00Af9Kjs3QRN9EzZ3CmQBKqwWAEzsfiMxBxPcW/\n"
      + "PimCeTSLAdMAH/Q+cvlp5w5GYpEwsQk=\n"
      + "-----END CERTIFICATE-----";

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

  private static final int xfccAllowedPort = 4446;
  private static final int xfccDisallowedPort = 4445;

  @Mock XfccSourceConfig xfccSourceConfig;
  @Mock ClientAuthTypeConfig clientAuthTypeConfig;
  @Mock ClientAuthConfig clientAuthConfig;

  @Mock ContainerRequest request;
  @Mock HttpServletRequest httpServletRequest;
  @Mock SecurityContext securityContext;
  @Mock ClientDAO clientDAO;

  ClientAuthFactory factory;

  @Before public void setUp() throws Exception {
    CertificateFactory cf = CertificateFactory.getInstance("X.509");
    X509Certificate clientCert = (X509Certificate) cf.generateCertificate(
        new ByteArrayInputStream(clientPem.getBytes(UTF_8)));
    clientPrincipal = new CertificatePrincipal(clientCert.getSubjectDN().toString(),
        new X509Certificate[] { clientCert });

    X509Certificate xfccCert = (X509Certificate) cf.generateCertificate(
        new ByteArrayInputStream(xfccPem.getBytes(UTF_8)));
    xfccPrincipal = new CertificatePrincipal(xfccCert.getSubjectDN().toString(),
        new X509Certificate[] { xfccCert });

    factory = new ClientAuthFactory(clientDAO, clientAuthConfig);

    when(request.getSecurityContext()).thenReturn(securityContext);
    when(clientDAO.getClientByName(clientName)).thenReturn(Optional.of(client));
    when(clientDAO.getClientBySpiffeId(new URI(clientSpiffe))).thenReturn(Optional.of(client));

    when(clientAuthConfig.xfccConfigs()).thenReturn(List.of(xfccSourceConfig));
    when(clientAuthConfig.typeConfig()).thenReturn(clientAuthTypeConfig);
    when(clientAuthConfig.createMissingClients()).thenReturn(false);

    when(xfccSourceConfig.port()).thenReturn(xfccAllowedPort);
    when(xfccSourceConfig.allowedClientNames()).thenReturn(List.of(xfccName));
    when(xfccSourceConfig.allowedSpiffeIds()).thenReturn(List.of(xfccSpiffe));

    when(clientAuthTypeConfig.useCommonName()).thenReturn(true);
    when(clientAuthTypeConfig.useSpiffeId()).thenReturn(true);
  }

  @Test public void returnsClientWhenClientPresent() {
    when(httpServletRequest.getLocalPort()).thenReturn(xfccDisallowedPort);
    when(securityContext.getUserPrincipal()).thenReturn(clientPrincipal);

    assertThat(factory.provide(request, httpServletRequest)).isEqualTo(client);
  }

  @Test(expected = NotAuthorizedException.class)
  public void clientWhenPrincipalAbsentThrows() {
    when(httpServletRequest.getLocalPort()).thenReturn(xfccDisallowedPort);
    when(securityContext.getUserPrincipal()).thenReturn(null);

    factory.provide(request, httpServletRequest);
  }

  @Test public void returnsClientWhenClientPresent_fromXfccHeader() {
    when(httpServletRequest.getLocalPort()).thenReturn(xfccAllowedPort);
    when(request.getRequestHeader(ClientAuthFactory.XFCC_HEADER_NAME)).thenReturn(
        List.of(xfccHeader));
    when(securityContext.getUserPrincipal()).thenReturn(xfccPrincipal);

    assertThat(factory.provide(request, httpServletRequest)).isEqualTo(client);
  }

  @Test public void returnsClientWhenClientPresent_fromXfccHeader_customSpiffeIdHeader() {
    when(httpServletRequest.getLocalPort()).thenReturn(xfccAllowedPort);
    when(request.getRequestHeader(ClientAuthFactory.XFCC_HEADER_NAME)).thenReturn(
        List.of(xfccHeader));
    when(securityContext.getUserPrincipal()).thenReturn(xfccPrincipal);

    when(xfccSourceConfig.callerSpiffeIdHeader()).thenReturn(callerSpiffeHeaderName);
    when(request.getRequestHeader(callerSpiffeHeaderName)).thenReturn(List.of(clientSpiffe));

    assertThat(factory.provide(request, httpServletRequest)).isEqualTo(client);
  }

  @Test
  public void returnsClientWhenClientPresent_fromXfccHeader_customSpiffeIdHeader_fallbackToXfccWhenNoValue() {
    when(httpServletRequest.getLocalPort()).thenReturn(xfccAllowedPort);
    when(request.getRequestHeader(ClientAuthFactory.XFCC_HEADER_NAME)).thenReturn(
        List.of(xfccHeader));
    when(securityContext.getUserPrincipal()).thenReturn(xfccPrincipal);

    when(xfccSourceConfig.callerSpiffeIdHeader()).thenReturn(callerSpiffeHeaderName);

    assertThat(factory.provide(request, httpServletRequest)).isEqualTo(client);
  }

  @Test public void returnsNewClientWhenClientNotPresent_fromXfccHeader_customSpiffeIdHeader()
      throws URISyntaxException {
    when(httpServletRequest.getLocalPort()).thenReturn(xfccAllowedPort);
    when(request.getRequestHeader(ClientAuthFactory.XFCC_HEADER_NAME)).thenReturn(
        List.of(xfccHeader));
    when(securityContext.getUserPrincipal()).thenReturn(xfccPrincipal);

    when(xfccSourceConfig.callerSpiffeIdHeader()).thenReturn(callerSpiffeHeaderName);
    when(request.getRequestHeader(callerSpiffeHeaderName)).thenReturn(List.of(newClientSpiffe));

    // Create missing clients
    when(clientAuthConfig.createMissingClients()).thenReturn(true);
    // lookup doesn't find client
    when(clientDAO.getClientByName(newClientName)).thenReturn(Optional.empty());
    // a new DB record is created
    when(clientDAO.createClient(eq(newClientName), eq("automatic"), any(),
        eq(new URI(newClientSpiffe)))).thenReturn(123L);
    when(clientDAO.getClientById(123L)).thenReturn(Optional.of(newClient));

    assertThat(factory.provide(request, httpServletRequest)).isEqualTo(newClient);
  }

  @Test(expected = NotAuthorizedException.class)
  public void rejectsXfcc_requesterAuthMissing() {
    when(httpServletRequest.getLocalPort()).thenReturn(xfccAllowedPort);
    when(request.getRequestHeader(ClientAuthFactory.XFCC_HEADER_NAME)).thenReturn(
        List.of(xfccHeader));
    when(securityContext.getUserPrincipal()).thenReturn(null);

    factory.provide(request, httpServletRequest);
  }

  @Test(expected = NotAuthorizedException.class)
  public void rejectsXfcc_requesterNameNotAllowed() {
    when(httpServletRequest.getLocalPort()).thenReturn(xfccAllowedPort);
    when(request.getRequestHeader(ClientAuthFactory.XFCC_HEADER_NAME)).thenReturn(
        List.of(xfccHeader));
    when(securityContext.getUserPrincipal()).thenReturn(xfccPrincipal);

    when(xfccSourceConfig.allowedClientNames()).thenReturn(List.of());
    when(xfccSourceConfig.allowedSpiffeIds()).thenReturn(List.of(xfccSpiffe));

    factory.provide(request, httpServletRequest);
  }

  @Test(expected = NotAuthorizedException.class)
  public void rejectsXfcc_requesterSpiffeNotAllowed() {
    when(httpServletRequest.getLocalPort()).thenReturn(xfccAllowedPort);
    when(request.getRequestHeader(ClientAuthFactory.XFCC_HEADER_NAME)).thenReturn(
        List.of(xfccHeader));
    when(securityContext.getUserPrincipal()).thenReturn(xfccPrincipal);

    when(xfccSourceConfig.allowedClientNames()).thenReturn(List.of(xfccName));
    when(xfccSourceConfig.allowedSpiffeIds()).thenReturn(List.of());

    factory.provide(request, httpServletRequest);
  }

  @Test(expected = NotAuthorizedException.class)
  public void rejectsXfcc_requesterNameNotAllowed_requesterSpiffeNotAllowed() {
    when(httpServletRequest.getLocalPort()).thenReturn(xfccAllowedPort);
    when(request.getRequestHeader(ClientAuthFactory.XFCC_HEADER_NAME)).thenReturn(
        List.of(xfccHeader));
    when(securityContext.getUserPrincipal()).thenReturn(xfccPrincipal);

    when(xfccSourceConfig.allowedClientNames()).thenReturn(List.of());
    when(xfccSourceConfig.allowedSpiffeIds()).thenReturn(List.of());

    factory.provide(request, httpServletRequest);
  }

  @Test(expected = NotAuthorizedException.class)
  public void rejectsXfcc_portConfigurationInvalid() {
    when(httpServletRequest.getLocalPort()).thenReturn(xfccAllowedPort);
    when(request.getRequestHeader(ClientAuthFactory.XFCC_HEADER_NAME)).thenReturn(
        List.of(xfccHeader));
    when(securityContext.getUserPrincipal()).thenReturn(xfccPrincipal);

    when(clientAuthConfig.xfccConfigs()).thenReturn(List.of(xfccSourceConfig, xfccSourceConfig));

    factory.provide(request, httpServletRequest);
  }

  @Test(expected = NotAuthorizedException.class)
  public void rejectsXfcc_disallowedPortWithHeader() {
    when(request.getRequestHeader(ClientAuthFactory.XFCC_HEADER_NAME)).thenReturn(
        List.of(xfccHeader));
    when(securityContext.getUserPrincipal()).thenReturn(xfccPrincipal);

    when(httpServletRequest.getLocalPort()).thenReturn(xfccDisallowedPort);

    factory.provide(request, httpServletRequest);
  }

  @Test(expected = NotAuthorizedException.class)
  public void rejectsXfcc_allowedPortNoHeader() {
    when(httpServletRequest.getLocalPort()).thenReturn(xfccAllowedPort);
    when(securityContext.getUserPrincipal()).thenReturn(xfccPrincipal);

    when(request.getRequestHeader(ClientAuthFactory.XFCC_HEADER_NAME)).thenReturn(List.of());

    factory.provide(request, httpServletRequest);
  }

  @Test(expected = NotAuthorizedException.class)
  public void rejectsXfcc_emptyHeader() {
    when(httpServletRequest.getLocalPort()).thenReturn(xfccAllowedPort);
    when(securityContext.getUserPrincipal()).thenReturn(xfccPrincipal);

    when(request.getRequestHeader(ClientAuthFactory.XFCC_HEADER_NAME)).thenReturn(List.of(""));

    factory.provide(request, httpServletRequest);
  }

  @Test(expected = NotAuthorizedException.class)
  public void rejectsXfcc_multipleHeaderValues() {
    when(httpServletRequest.getLocalPort()).thenReturn(xfccAllowedPort);
    when(securityContext.getUserPrincipal()).thenReturn(xfccPrincipal);

    // multiple XFCC headers
    when(request.getRequestHeader(ClientAuthFactory.XFCC_HEADER_NAME)).thenReturn(
        List.of(xfccHeader, xfccHeader));

    factory.provide(request, httpServletRequest);
  }

  @Test(expected = NotAuthorizedException.class)
  public void rejectsXfcc_multipleHeaderEntries() {
    when(httpServletRequest.getLocalPort()).thenReturn(xfccAllowedPort);
    when(securityContext.getUserPrincipal()).thenReturn(xfccPrincipal);

    // a single header with multiple client elements in to it
    when(request.getRequestHeader(ClientAuthFactory.XFCC_HEADER_NAME)).thenReturn(
        List.of(xfccHeader + "," + xfccHeader));

    factory.provide(request, httpServletRequest);
  }

  @Test(expected = NotAuthorizedException.class)
  public void rejectsXfcc_multipleCertKeysInHeader() {
    when(httpServletRequest.getLocalPort()).thenReturn(xfccAllowedPort);
    when(securityContext.getUserPrincipal()).thenReturn(xfccPrincipal);

    // a single header with multiple cert tags in it for the same client
    when(request.getRequestHeader(ClientAuthFactory.XFCC_HEADER_NAME)).thenReturn(
        List.of(format("Cert=\"%s\";cert=\"%s\"", UrlEncoded.encodeString(clientPem, UTF_8),
            UrlEncoded.encodeString(clientPem, UTF_8))));

    factory.provide(request, httpServletRequest);
  }

  @Test(expected = NotAuthorizedException.class)
  public void rejectsXfcc_missingClientCert() {
    when(httpServletRequest.getLocalPort()).thenReturn(xfccAllowedPort);
    when(securityContext.getUserPrincipal()).thenReturn(xfccPrincipal);

    when(request.getRequestHeader(ClientAuthFactory.XFCC_HEADER_NAME)).thenReturn(
        List.of(format("By=%s", xfccName)));

    factory.provide(request, httpServletRequest);
  }

  @Test(expected = NotAuthorizedException.class)
  public void rejectsXfcc_customSpiffeIdHeader_invalidSpiffe() {
    when(httpServletRequest.getLocalPort()).thenReturn(xfccAllowedPort);
    when(request.getRequestHeader(ClientAuthFactory.XFCC_HEADER_NAME)).thenReturn(
        List.of(xfccHeader));
    when(securityContext.getUserPrincipal()).thenReturn(xfccPrincipal);

    when(xfccSourceConfig.callerSpiffeIdHeader()).thenReturn(callerSpiffeHeaderName);
    when(request.getRequestHeader(callerSpiffeHeaderName)).thenReturn(List.of("not-spiffe-id"));

    factory.provide(request, httpServletRequest);
  }

  @Test(expected = NotAuthorizedException.class)
  public void rejectsXfcc_customSpiffeIdHeader_multipleSpiffe() {
    when(httpServletRequest.getLocalPort()).thenReturn(xfccAllowedPort);
    when(request.getRequestHeader(ClientAuthFactory.XFCC_HEADER_NAME)).thenReturn(
        List.of(xfccHeader));
    when(securityContext.getUserPrincipal()).thenReturn(xfccPrincipal);

    when(xfccSourceConfig.callerSpiffeIdHeader()).thenReturn(callerSpiffeHeaderName);
    when(request.getRequestHeader(callerSpiffeHeaderName)).thenReturn(
        List.of(clientSpiffe, newClientSpiffe));

    factory.provide(request, httpServletRequest);
  }

  @Test(expected = NotAuthorizedException.class)
  public void rejectsXfcc_customSpiffeIdHeader_clientNotPresent() {
    when(httpServletRequest.getLocalPort()).thenReturn(xfccAllowedPort);
    when(request.getRequestHeader(ClientAuthFactory.XFCC_HEADER_NAME)).thenReturn(
        List.of(xfccHeader));
    when(securityContext.getUserPrincipal()).thenReturn(xfccPrincipal);

    when(xfccSourceConfig.callerSpiffeIdHeader()).thenReturn(callerSpiffeHeaderName);
    when(request.getRequestHeader(callerSpiffeHeaderName)).thenReturn(
        List.of(nonExistentClientSpiffe));

    factory.provide(request, httpServletRequest);
  }
}
