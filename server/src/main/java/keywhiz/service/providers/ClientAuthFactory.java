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
import java.net.URLDecoder;
import java.security.Principal;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.NotAuthorizedException;
import keywhiz.KeywhizConfig;
import keywhiz.api.model.Client;
import keywhiz.service.config.ClientAuthConfig;
import keywhiz.service.config.XfccSourceConfig;
import keywhiz.service.daos.ClientDAO;
import keywhiz.service.daos.ClientDAO.ClientDAOFactory;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.glassfish.jersey.server.ContainerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;

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

  private final MyAuthenticator authenticator;
  private final ClientAuthConfig clientAuthConfig;

  @Inject
  public ClientAuthFactory(ClientDAOFactory clientDAOFactory, KeywhizConfig keywhizConfig) {
    this.authenticator =
        new MyAuthenticator(clientDAOFactory.readwrite(), clientDAOFactory.readonly());
    this.clientAuthConfig = keywhizConfig.getClientAuthConfig();
  }

  @VisibleForTesting ClientAuthFactory(ClientDAO clientDAO, ClientAuthConfig clientAuthConfig) {
    this.authenticator = new MyAuthenticator(clientDAO, clientDAO);
    this.clientAuthConfig = clientAuthConfig;
  }

  public Client provide(ContainerRequest request) {
    // Ports must either always send an x-forwarded-client-cert header, or
    // never send this header. This also throws an error if a single port
    // has multiple configurations.
    int requestPort = request.getBaseUri().getPort();
    Optional<XfccSourceConfig> possibleXfccConfig =
        getXfccConfigForPort(requestPort);

    List<String> xfccHeaderValues =
        Optional.ofNullable(request.getRequestHeader(XFCC_HEADER_NAME)).orElse(List.of());
    if (possibleXfccConfig.isPresent() && xfccHeaderValues.isEmpty()) {
      throw new NotAuthorizedException(format(
          "Port %d is configured to only receive traffic with the %s header set",
          requestPort, XFCC_HEADER_NAME));
    } else if (possibleXfccConfig.isEmpty() && !xfccHeaderValues.isEmpty()) {
      throw new NotAuthorizedException(format(
          "Port %d is not configured to receive traffic with the %s header set",
          requestPort, XFCC_HEADER_NAME));
    }

    // Extract information about the requester. This may be a Keywhiz client, or it may be a proxy
    // forwarding the real Keywhiz client information in the x-forwarded-client-certs header
    Optional<Principal> requestPrincipal = getPrincipal(request);
    if (requestPrincipal.isEmpty()) {
      throw new NotAuthorizedException("Could not parse an identity from request");
    }

    Optional<String> requestName = getClientName(requestPrincipal);
    Optional<String> requestSpiffeId = Optional.empty();

    // Extract client information based on the x-forwarded-client-cert header or
    // on the security context of this request
    if (possibleXfccConfig.isEmpty()) {
      // The XFCC header is not used; use the security context of this request to identify the client
      return authorizeClient(requestPrincipal.get(), requestName, requestSpiffeId);
    } else {
      return authorizeClientFromXfccHeader(possibleXfccConfig.get(), xfccHeaderValues, requestName,
          requestSpiffeId);
    }
  }

  static Optional<Principal> getPrincipal(ContainerRequest request) {
    return Optional.ofNullable(request.getSecurityContext().getUserPrincipal());
  }

  static Optional<String> getClientName(Optional<Principal> principal) {
    if (principal.isEmpty()) {
      return Optional.empty();
    }

    X500Name name = new X500Name(principal.get().getName());
    RDN[] rdns = name.getRDNs(BCStyle.CN);
    if (rdns.length == 0) {
      logger.warn("Certificate does not contain CN=xxx,...: {}", principal.get().getName());
      return Optional.empty();
    }
    return Optional.of(IETFUtils.valueToString(rdns[0].getFirst().getValue()));
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

  private Client authorizeClientFromXfccHeader(XfccSourceConfig xfccConfig,
      List<String> xfccHeaderValues, Optional<String> requestName, Optional<String> requestSpiffeId) {
    // Do not allow the XFCC header to be set by all incoming traffic. This throws a
    // NotAuthorizedException when the traffic is not coming from a source allowed to set the
    // header.
    validateXfccHeaderAllowed(xfccConfig, requestName, requestSpiffeId);

    // Extract client information from the XFCC header
    Optional<X509Certificate> clientCert =
        getClientCertFromXfccHeaderEnvoyFormatted(xfccHeaderValues);
    if (clientCert.isEmpty()) {
      throw new NotAuthorizedException(
          format("unable to parse client certificate from %s header", XFCC_HEADER_NAME));
    }

    Principal clientPrincipal = clientCert.get().getSubjectX500Principal();
    Optional<String> clientName = getClientName(Optional.of(clientPrincipal));
    Optional<String> clientSpiffeId = Optional.empty();

    return authorizeClient(clientPrincipal, clientName, clientSpiffeId);
  }

  private void validateXfccHeaderAllowed(XfccSourceConfig xfccConfig, Optional<String> requestName,
      Optional<String> requestSpiffeId) {
    if (clientAuthConfig == null || clientAuthConfig.xfccConfigs() == null) {
      throw new NotAuthorizedException(
          format(
              "not configured to handle requests with %s header set; set 'xfcc' field in config",
              XFCC_HEADER_NAME));
    }

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

    if (requestSpiffeId.isPresent() && !xfccConfig.allowedSpiffeIds()
        .contains(requestSpiffeId.get())) {
      throw new NotAuthorizedException(
          format(
              "requests with %s header set may not be sent from client with spiffe ID %s; check configuration",
              XFCC_HEADER_NAME, requestSpiffeId.get()));
    }
  }

  private Optional<X509Certificate> getClientCertFromXfccHeaderEnvoyFormatted(
      List<String> xfccHeaderValues) {
    // Keywhiz currently supports only one certificate set in the XFCC header,
    // since otherwise it is more difficult to distinguish which certificate should have
    // access to secrets
    if (xfccHeaderValues.size() == 0) {
      return Optional.empty();
    } else if (xfccHeaderValues.size() > 1) {
      logger.warn("Keywhiz only supports one certificate set in the {} header", XFCC_HEADER_NAME);
      return Optional.empty();
    }

    // From https://www.envoyproxy.io/docs/envoy/latest/configuration/http/http_conn_man/headers#x-forwarded-client-cert:
    // The XFCC header value is a comma (“,”) separated string. Each substring is an XFCC element,
    // which holds information added by a single proxy. A proxy can append the current client
    // certificate information as an XFCC element, to the end of the request’s XFCC header
    // after a comma.
    //
    // Each XFCC element is a semicolon “;” separated string. Each substring is a key-value pair,
    // grouped together by an equals (“=”) sign. The keys are case-insensitive, the values are
    // case-sensitive. If “,”, “;” or “=” appear in a value, the value should be double-quoted.
    // Double-quotes in the value should be replaced by backslash-double-quote (“).
    String[] keyValuePairs = xfccHeaderValues.get(0).split(";");

    for (String pair : keyValuePairs) {
      String[] keyValue = pair.split("=");
      if (keyValue.length != 2) {
        logger.warn("Got entry in {} header that wasn't a key/value pair: {}", XFCC_HEADER_NAME,
            pair);
        continue;
      }

      if (!CERT_KEY.equals(keyValue[0])) {
        continue;
      }

      return parseCertFromCertField(keyValue[1]);
    }

    logger.warn("Unable to find {} in {} header; no client ID parsed from header", CERT_KEY,
        XFCC_HEADER_NAME);
    return Optional.empty();
  }

  private Optional<X509Certificate> parseCertFromCertField(String xfccEncodedCert) {
    // remove outer quotes
    String encodedPem = xfccEncodedCert.replace("\"", "");

    // decode and parse the certificate
    String certPem = URLDecoder.decode(encodedPem, UTF_8);

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
      logger.warn("Failed to parse client certificate", e);
      return Optional.empty();
    }
    return Optional.of(cert);
  }

  private Client authorizeClient(Principal clientPrincipal, Optional<String> possibleClientName,
      Optional<String> possibleClientSpiffeId) {
    if (clientAuthConfig.typeConfig().useClientName() && possibleClientName.isPresent()) {
      String clientName = possibleClientName.get();

      Optional<Client> possibleClient =
          authenticator.authenticateByName(clientName, clientPrincipal);
      if (possibleClient.isPresent()) {
        return possibleClient.get();
      }
    }

    if (clientAuthConfig.typeConfig().useSpiffeId() && possibleClientSpiffeId.isPresent()) {
      throw new NotAuthorizedException("Identifying Clients by SPIFFE ID not yet supported");
    }

    throw new NotAuthorizedException(
        format("No authorized Client for connection using principal %s",
            clientPrincipal.getName()));
  }

  private static class MyAuthenticator {
    private final ClientDAO clientDAOReadWrite;
    private final ClientDAO clientDAOReadOnly;

    private MyAuthenticator(
        ClientDAO clientDAOReadWrite,
        ClientDAO clientDAOReadOnly) {
      this.clientDAOReadWrite = clientDAOReadWrite;
      this.clientDAOReadOnly = clientDAOReadOnly;
    }

    public Optional<Client> authenticateByName(String name, @Nullable Principal principal) {
      Optional<Client> optionalClient = clientDAOReadOnly.getClient(name);
      if (optionalClient.isPresent()) {
        Client client = optionalClient.get();
        clientDAOReadWrite.sawClient(client, principal);
        if (client.isEnabled()) {
          return optionalClient;
        } else {
          logger.warn("Client {} authenticated but disabled via DB", client);
          return Optional.empty();
        }
      }

      /*
       * If a client is seen for the first time, authenticated by certificate, and has no DB entry,
       * then a DB entry is created here. The client can be disabled in the future by flipping the
       * 'enabled' field.
       */
      // TODO(justin): Consider making this behavior configurable.
      long clientId = clientDAOReadWrite.createClient(name, "automatic",
          "Client created automatically from valid certificate authentication", "");
      return Optional.of(clientDAOReadWrite.getClientById(clientId).get());
    }
  }
}
