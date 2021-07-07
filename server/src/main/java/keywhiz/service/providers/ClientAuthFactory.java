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

import com.google.common.annotations.VisibleForTesting;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.security.Principal;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.NotAuthorizedException;
import keywhiz.KeywhizConfig;
import keywhiz.api.model.Client;
import keywhiz.auth.mutualssl.CertificatePrincipal;
import keywhiz.auth.mutualssl.SpiffePrincipal;
import keywhiz.service.config.ClientAuthConfig;
import keywhiz.service.config.XfccSourceConfig;
import keywhiz.service.daos.ClientDAO;
import keywhiz.service.daos.ClientDAO.ClientDAOFactory;
import org.glassfish.jersey.server.ContainerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static keywhiz.Tracing.setTag;
import static keywhiz.Tracing.trace;

/**
 * Authenticates {@link Client}s from requests based on the principal present in a {@link
 * javax.ws.rs.core.SecurityContext} and by querying the database.
 * <p>
 * Modeled similar to io.dropwizard.auth.AuthFactory, however that is not yet usable. See
 * https://github.com/dropwizard/dropwizard/issues/864.
 */
public class ClientAuthFactory {
  private static final Logger logger = LoggerFactory.getLogger(ClientAuthFactory.class);

  @VisibleForTesting
  protected static final String XFCC_HEADER_NAME = "X-Forwarded-Client-Cert";

  // The key that will be searched for in the XFCC header. Eventually, this should be made
  // configurable, with different parsing depending on the key being searched for.
  @VisibleForTesting
  protected static final String CERT_KEY = "Cert";

  private final ClientAuthenticator authenticator;
  private final ClientAuthConfig clientAuthConfig;

  @Inject
  public ClientAuthFactory(ClientDAOFactory clientDAOFactory, KeywhizConfig keywhizConfig) {
    this.authenticator =
        new ClientAuthenticator(clientDAOFactory.readwrite(), clientDAOFactory.readonly(),
            keywhizConfig.getClientAuthConfig());
    this.clientAuthConfig = keywhizConfig.getClientAuthConfig();
  }

  @VisibleForTesting ClientAuthFactory(ClientDAO clientDAO, ClientAuthConfig clientAuthConfig) {
    this.authenticator = new ClientAuthenticator(clientDAO, clientDAO, clientAuthConfig);
    this.clientAuthConfig = clientAuthConfig;
  }

  /**
   * Extracts a client (which may be an AutomationClient--see AutomationClientAuthFactory)
   * from the input data. This client may be identified in multiple ways.
   *
   * The input request comes from a TLS connection to a port on the Keywhiz server.
   *
   * The input request data either identifies a Keywhiz client directly, or identifies
   * a trusted proxy of some sort which is forwarding information about a Keywhiz
   * client. The forwarded information may be a certificate or a client name.
   *
   * WARNING: This code assumes that a proxy will _always_ set the x-forwarded-client-cert
   * header, even if it _also_ sets other headers (such as the caller ID header exposed
   * in Keywhiz' configuration). This is fragile and should probably be removed, so that
   * the two headers (and any other headers set by a proxy) are handled independently.
   *
   * Each port that the Keywhiz server exposes must support either direct client
   * connections, or proxy connections with forwarded client data, but never both. Every
   * proxy must be added to an allowlist in Keywhiz' configuration, and only allowlisted
   * proxies can be trusted to forward client information.
   *
   * This method identifies the client or proxy from the TLS connection to Keywhiz,
   * confirms that the connection's port and proxy (if present) are allowed by Keywhiz'
   * configuration, and then extracts the real client data from the forwarded information
   * if a proxy was used. The ClientAuthenticator handles the actual lookup of client data.
   *
   * @param containerRequest information about the entity that connected to Keywhiz (a
   *                         client or a proxy forwarding client data)
   * @param httpServletRequest information about the connection to Keywhiz (specifically,
   *                           the port on which the connection was made)
   * @return the client identified by this request, if present
   */
  public Client provide(ContainerRequest containerRequest,
      HttpServletRequest httpServletRequest) {
    return trace("ClientAuthFactory.provide", () -> {
      Client client = doProvide(containerRequest, httpServletRequest);
      setTag("authenticatedClient", client.getName());
      return client;
    });
  }

  private Client doProvide(ContainerRequest containerRequest,
      HttpServletRequest httpServletRequest) {
    // Check whether this port is configured to be used by a proxy.
    // If this configuration is present, traffic on this port _must_
    // identify its clients using the x-forwarded-client-cert header or
    // caller ID header.
    // If the configuration is not present, traffic _must_ identify its
    // clients using the request's security context.
    int requestPort = httpServletRequest.getLocalPort();
    Optional<XfccSourceConfig> possibleXfccConfig =
        getXfccConfigForPort(requestPort);

    List<String> xfccHeaderValues =
        Optional.ofNullable(containerRequest.getRequestHeader(XFCC_HEADER_NAME)).orElse(List.of());

    // WARNING: This code assumes that the XFCC header will always be set by the proxy
    // EVEN IF the callerSpiffeId header is also set! (If both headers are set, only
    // the callerSpiffeIdHeader identifies the client.)
    if (possibleXfccConfig.isEmpty() != xfccHeaderValues.isEmpty()) {
      throw new NotAuthorizedException(format(
          "Port %d is configured to %s receive traffic with the %s header set",
          requestPort, possibleXfccConfig.isEmpty() ? "never" : "only", XFCC_HEADER_NAME));
    }

    // Extract information about the entity that connected directly to Keywhiz.
    // This must be identified from the request's security context, rather than
    // easily modified information like a header.
    //
    // This entity may be a Keywhiz client, or it may be a proxy
    // forwarding the real Keywhiz client information.
    Principal connectedPrincipal = getPrincipal(containerRequest).orElseThrow(
        () -> new NotAuthorizedException("Not authorized as Keywhiz client"));
    setTag("principal", connectedPrincipal.getName());

    // Extract client information based on the x-forwarded-client-cert header or
    // on the security context of this request
    if (possibleXfccConfig.isEmpty()) {
      // The XFCC header is not used; use the security context of this request to
      // identify the client
      return authenticateClientFromPrincipal(connectedPrincipal);
    } else {
      // Use either the XFCC header or a caller-ID header to identify the client.
      return authenticateClientFromForwardedData(possibleXfccConfig.get(), xfccHeaderValues,
          connectedPrincipal, containerRequest);
    }
  }

  static Optional<Principal> getPrincipal(ContainerRequest request) {
    return Optional.ofNullable(request.getSecurityContext().getUserPrincipal());
  }

  private Optional<XfccSourceConfig> getXfccConfigForPort(int port) {
    List<XfccSourceConfig> matchingConfigs = clientAuthConfig.xfccConfigs()
        .stream()
        .filter(xfccSourceConfig -> xfccSourceConfig.port().equals(port))
        .collect(Collectors.toUnmodifiableList());

    if (matchingConfigs.size() > 1) {
      throw new NotAuthorizedException(format(
          "Invalid 'xfcc' configuration for port %d; at most one configuration must be present per port",
          port));
    } else {
      return matchingConfigs.stream().findFirst();
    }
  }

  private Client authenticateClientFromForwardedData(XfccSourceConfig xfccConfig,
      List<String> xfccHeaderValues, Principal connectedPrincipal,
      ContainerRequest containerRequest) {
    // Do not allow client identification data (the callerSpiffeIdHeader or the
    // XFCC header) to be set by entities which are not explicitly allowlisted
    // in Keywhiz' configuration. This throws a NotAuthorizedException when the
    // traffic is not coming from a source allowed to forward client identification
    // data in these headers.
    validateForwardedClientDataAllowed(xfccConfig, connectedPrincipal);

    // Extract information from the header which, if present, will contain the client's
    // SPIFFE ID. If this header is set the XFCC header MUST NOT be used to identify
    // a client.
    Optional<String> callerSpiffeIdHeaderName =
        Optional.ofNullable(xfccConfig.callerSpiffeIdHeader());

    // If the callerSpiffeIdHeader is set, use it to authenticate the client.
    // Throw an exception if it was malformed, but do not fall back to other
    // headers since they likely do not contain the client information.
    if (callerSpiffeIdHeaderName.isPresent() &&
        containerRequest.getRequestHeader(callerSpiffeIdHeaderName.get()).size() > 0) {
      return authenticateClientFromCallerSpiffeIdHeader(containerRequest,
          callerSpiffeIdHeaderName.get());
    } else {
      // Fall back to the XFCC header only if the callerSpiffeId header was not present
      return authenticateClientFromXfccHeader(xfccHeaderValues);
    }
  }

  /**
   * Extracts client information from the callerSpiffeIdHeader and retrieves the client if present,
   * throwing exceptions if the header is malformatted or the client is absent.
   */
  private Client authenticateClientFromCallerSpiffeIdHeader(ContainerRequest containerRequest,
      String header) {
    // Retrieve the client's SPIFFE ID from the input header
    URI callerSpiffeId =
        ClientAuthenticator.getSpiffeIdFromHeader(containerRequest, header)
            .orElseThrow(() -> new NotAuthorizedException(
                format("unable to parse client SPIFFE ID from %s header", header)));

    SpiffePrincipal spiffePrincipal = new SpiffePrincipal(callerSpiffeId);
    return authenticateClientFromPrincipal(spiffePrincipal);
  }

  /**
   * Extracts client information from the XFCC header and retrieves the client if present, throwing
   * exceptions if the header is malformatted or the client is absent.
   */
  private Client authenticateClientFromXfccHeader(List<String> xfccHeaderValues) {
    X509Certificate clientCert =
        getClientCertFromXfccHeaderEnvoyFormatted(xfccHeaderValues).orElseThrow(() ->
            new NotAuthorizedException(
                format("unable to parse client certificate from %s header", XFCC_HEADER_NAME))
        );

    CertificatePrincipal certificatePrincipal =
        new CertificatePrincipal(clientCert.getSubjectDN().toString(),
            new X509Certificate[] {clientCert});

    return authenticateClientFromPrincipal(certificatePrincipal);
  }

  /**
   * Checks that the specified principal identifies a proxy which is trusted
   * to forward client information to Keywhiz, based on the input information
   * and allowlists in Keywhiz' configuration.
   *
   * @param xfccConfig Keywhiz' configuration for proxies
   * @param requestPrincipal the entity which connected to Keywhiz using a port
   *                         that only proxies are allowed to use
   */
  private void validateForwardedClientDataAllowed(XfccSourceConfig xfccConfig, Principal requestPrincipal) {
    if (clientAuthConfig == null || clientAuthConfig.xfccConfigs() == null) {
      throw new NotAuthorizedException(
          format(
              "not configured to handle requests with %s header set; set 'xfcc' field in config",
              XFCC_HEADER_NAME));
    }

    // All XFCC traffic must be checked against allowlists; all connections that set the XFCC
    // header must send identifiers for the requester, as well as the XFCC certificate
    Optional<String> requestName = ClientAuthenticator.getClientName(requestPrincipal);
    Optional<URI> requestSpiffeId = ClientAuthenticator.getSpiffeId(requestPrincipal);

    if (requestName.isEmpty() && requestSpiffeId.isEmpty()) {
      throw new NotAuthorizedException(
          format("requests with %s header set must connect over TLS", XFCC_HEADER_NAME));
    }

    // Only certain clients may set the XFCC header
    if (requestName.isPresent() && !xfccConfig.allowedClientNames().contains(requestName.get())) {
      throw new NotAuthorizedException(
          format(
              "requests with %s header set may not be sent from client with name %s; check configuration",
              XFCC_HEADER_NAME, requestName.get()));
    }

    if (requestSpiffeId.isPresent() && !containsUri(xfccConfig.allowedSpiffeIds(),
        requestSpiffeId.get())) {
      throw new NotAuthorizedException(
          format(
              "requests with %s header set may not be sent from client with spiffe ID %s; check configuration",
              XFCC_HEADER_NAME, requestSpiffeId.get()));
    }
  }

  private boolean containsUri(List<String> uriList, URI targetUri) {
    for (String uri : uriList) {
      try {
        if (new URI(uri).equals(targetUri)) {
          return true;
        }
      } catch (URISyntaxException e) {
        logger.warn(format("Unable to parse URI from %s", uri), e);
      }
    }
    return false;
  }

  private Optional<X509Certificate> getClientCertFromXfccHeaderEnvoyFormatted(
      List<String> xfccHeaderValues) {
    // Keywhiz currently supports only one configured XFCC header,,since otherwise it is difficult
    // to distinguish which certificate should have access to secrets
    if (xfccHeaderValues.size() == 0) {
      return Optional.empty();
    } else if (xfccHeaderValues.size() > 1) {
      logger.warn(
          "Keywhiz only supports one {} header, but {} were provided",
          XFCC_HEADER_NAME, xfccHeaderValues.size());
      return Optional.empty();
    }

    // Parse the XFCC header as formatted by Envoy
    XfccHeader xfccHeader;
    try {
      xfccHeader = XfccHeader.parse(xfccHeaderValues.get(0));
    } catch (XfccHeader.ParseException e) {
      logger.warn(format("Unable to parse input %s header", XFCC_HEADER_NAME), e);
      return Optional.empty();
    }

    // Keywhiz currently supports only one certificate set in the XFCC header,
    // since otherwise it is more difficult to distinguish which certificate should have
    // access to secrets
    if (xfccHeader.elements.length == 0) {
      logger.warn("No data provided in {} header", XFCC_HEADER_NAME);
      return Optional.empty();
    } else if (xfccHeader.elements.length > 1) {
      logger.warn(
          "Keywhiz only supports one certificate set in the {} header, but {} were provided",
          XFCC_HEADER_NAME, xfccHeader.elements.length);
      return Optional.empty();
    }

    List<String> certValues = Arrays.stream(xfccHeader.elements[0].pairs)
        .filter((pair) -> CERT_KEY.equalsIgnoreCase(pair.key))
        .map((pair) -> pair.value)
        .collect(Collectors.toUnmodifiableList());

    if (certValues.size() == 0) {
      logger.warn("Unable to find {} in {} header; no client ID parsed from header", CERT_KEY,
          XFCC_HEADER_NAME);
      return Optional.empty();
    } else if (certValues.size() > 1) {
      logger.warn(
          "Keywhiz only supports one {} key in the {} header, but {} were provided",
          CERT_KEY, XFCC_HEADER_NAME, certValues.size());
      return Optional.empty();
    }

    return parseUrlEncodedPem(certValues.get(0));
  }

  private Optional<X509Certificate> parseUrlEncodedPem(String urlEncodedPem) {
    String certPem;
    try {
      certPem = URLDecoder.decode(urlEncodedPem, UTF_8);
    } catch (NullPointerException | IllegalArgumentException e) {
      logger.warn(
          format("Unable to decode url-encoded certificate from %s header", XFCC_HEADER_NAME), e);
      return Optional.empty();
    }

    CertificateFactory cf;
    try {
      cf = CertificateFactory.getInstance("X.509");
    } catch (CertificateException e) {
      // Should never happen (X.509 supported by default)
      logger.error("Unexpected error: Unable to get X.509 certificate factory");
      return Optional.empty();
    }

    X509Certificate cert;
    try {
      cert = (X509Certificate) cf.generateCertificate(
          new ByteArrayInputStream(certPem.getBytes(UTF_8)));
    } catch (CertificateException e) {
      // Certificate must have been invalid
      logger.warn(format("Failed to parse client certificate from %s header", XFCC_HEADER_NAME), e);
      return Optional.empty();
    }
    return Optional.of(cert);
  }

  /**
   * Defined as a function so that subclasses can override this behavior
   *
   * @return whether to create a client that can't be found, if the client doesn't exist
   */
  protected boolean createMissingClient() {
    return clientAuthConfig.createMissingClients();
  }

  private Client authenticateClientFromPrincipal(Principal clientPrincipal) {
    Optional<Client> possibleClient =
        authenticator.authenticate(clientPrincipal, createMissingClient());
    return possibleClient.orElseThrow(() -> new NotAuthorizedException(
        format("No authorized Client for connection using principal %s",
            clientPrincipal.getName())));
  }
}
