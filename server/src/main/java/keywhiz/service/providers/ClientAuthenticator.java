package keywhiz.service.providers;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.NotAuthorizedException;
import keywhiz.api.model.Client;
import keywhiz.auth.mutualssl.CertificatePrincipal;
import keywhiz.service.config.ClientAuthConfig;
import keywhiz.service.daos.ClientDAO;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;

public class ClientAuthenticator {
  private static final Logger logger = LoggerFactory.getLogger(ClientAuthenticator.class);

  // The integer representation of the URIName SAN for a certificate.
  // 6 is hardcoded via RFC:
  // uniformResourceIdentifier       [6]     IA5String,
  // E.g. see BouncyCastle documentation:
  // http://people.eecs.berkeley.edu/~jonah/bc/org/bouncycastle/asn1/x509/GeneralName.html
  private static final Integer URINAME_SAN = 6;

  // The expected prefix for SPIFFE URIs
  private static final String SPIFFE_SCHEME = "spiffe://";

  private final ClientDAO clientDAOReadWrite;
  private final ClientDAO clientDAOReadOnly;
  private final ClientAuthConfig clientAuthConfig;

  public ClientAuthenticator(
      ClientDAO clientDAOReadWrite,
      ClientDAO clientDAOReadOnly,
      ClientAuthConfig clientAuthConfig) {
    this.clientDAOReadWrite = clientDAOReadWrite;
    this.clientDAOReadOnly = clientDAOReadOnly;
    this.clientAuthConfig = clientAuthConfig;
  }

  public Optional<Client> authenticate(Principal principal, boolean createMissingClient) {
    // Try to retrieve clients based on the client name and SPIFFE ID
    Optional<String> possibleClientName = getClientName(principal);
    Optional<URI> possibleClientSpiffeId = getSpiffeId(principal);

    Optional<Client> possibleClientFromName = possibleClientName.flatMap((name) -> {
      if (clientAuthConfig.typeConfig().useCommonName()) {
        return clientDAOReadOnly.getClientByName(name);
      } else {
        return Optional.empty();
      }
    });

    Optional<Client> possibleClientFromSpiffeId = possibleClientSpiffeId.flatMap((spiffeId) -> {
      if (clientAuthConfig.typeConfig().useSpiffeId()) {
        return clientDAOReadOnly.getClientBySpiffeId(spiffeId);
      } else {
        return Optional.empty();
      }
    });

    // If the name and SPIFFE id both defined a client, make sure that they defined the same
    // client! (Note that if retrieving clients by common name or SPIFFE ID is disabled, this
    // check will not reject a certificate with a mismatched CN and SPIFFE URI.)
    if (possibleClientFromName.isPresent() && possibleClientFromSpiffeId.isPresent()) {
      if (!possibleClientFromName.get().equals(possibleClientFromSpiffeId.get())) {
        throw new NotAuthorizedException(format(
            "Input principal's CN and SPIFFE ID correspond to different clients! (cn = %s, spiffe = %s)",
            possibleClientFromName.get().getName(), possibleClientFromSpiffeId.get().getName()));
      }
    } else if (possibleClientFromName.isEmpty() && possibleClientFromSpiffeId.isEmpty()) {
      // Create missing clients if configured to do so (client name must be present)
      return handleMissingClient(createMissingClient, possibleClientName.orElse(""),
          possibleClientSpiffeId);
    }

    // Retrieve whichever of the clients is set (if both are present, the earlier check guarantees
    // that they contain the same client).
    Client client = possibleClientFromName
        .or(() -> possibleClientFromSpiffeId)
        .orElseThrow(() -> new IllegalStateException(
            "Unable to identify client, and fallback code in server did not handle this case"));

    // Record that this client has been retrieved
    clientDAOReadWrite.sawClient(client, principal);
    if (client.isEnabled()) {
      return Optional.of(client);
    } else {
      logger.warn("Client {} authenticated but disabled via DB", client);
      return Optional.empty();
    }
  }

  private Optional<Client> handleMissingClient(boolean createMissingClient, String name,
      Optional<URI> spiffeId) {
    if (createMissingClient && !name.isEmpty()) {
      /*
       * If a client is seen for the first time, authenticated by certificate, and has no DB entry,
       * then a DB entry is created here. The client can be disabled in the future by flipping the
       * 'enabled' field.
       */
      long clientId = clientDAOReadWrite.createClient(name, "automatic",
          "Client created automatically from valid certificate authentication",
          spiffeId.orElse(null));
      return clientDAOReadWrite.getClientById(clientId);
    } else {
      return Optional.empty();
    }
  }

  static Optional<String> getClientName(Principal principal) {
    X500Name name = new X500Name(principal.getName());
    RDN[] rdns = name.getRDNs(BCStyle.CN);
    if (rdns.length == 0) {
      logger.warn("Certificate does not contain CN=xxx,...: {}", principal.getName());
      return Optional.empty();
    }
    return Optional.of(IETFUtils.valueToString(rdns[0].getFirst().getValue()));
  }

  static Optional<URI> getSpiffeId(Principal principal) {
    if (!(principal instanceof CertificatePrincipal)) {
      // A SPIFFE ID can only be parsed from a principal with a certificate
      return Optional.empty();
    }

    // This chain is either from the XFCC header's "Cert" field, which includes only the
    // client certificate rather than the chain, or from the CertificateSecurityContext
    // configured by Keywhiz' ClientCertificateFilter, which sets it based on
    // X509Certificate[] chain =
    //        (X509Certificate[]) context.getProperty("javax.servlet.request.X509Certificate");
    // which appears to place the leaf as the zero-index entry in the chain.
    X509Certificate cert = ((CertificatePrincipal) principal).getCertificateChain()
        .get(0);
    return getSpiffeIdFromCertificate(cert);
  }

  static Optional<URI> getSpiffeIdFromCertificate(X509Certificate cert) {
    Collection<List<?>> sans;
    try {
      sans = cert.getSubjectAlternativeNames();
    } catch (CertificateParsingException e) {
      logger.warn("Unable to parse SANs from principal", e);
      return Optional.empty();
    }

    if (sans == null || sans.isEmpty()) {
      return Optional.empty();
    }

    // The sub-lists returned by getSubjectAlternativeNames have an integer for the first
    // entry, representing a field, and the value as a string as the second entry.
    List<String> spiffeUriNames = sans.stream()
        .filter(sanPair -> sanPair.get(0).equals(URINAME_SAN))
        .map(sanPair -> (String) sanPair.get(1))
        .filter(uri -> uri.startsWith(SPIFFE_SCHEME))
        .collect(Collectors.toUnmodifiableList());

    if (spiffeUriNames.size() > 1) {
      logger.warn("Got multiple SPIFFE URIs from certificate: {}", spiffeUriNames);
      return Optional.empty();
    }

    Optional<String> possibleName = spiffeUriNames.stream().findFirst();

    if (possibleName.isPresent()) {
      try {
        return Optional.of(new URI(possibleName.get()));
      } catch (URISyntaxException e) {
        logger.warn(
            format("Unable to parse SPIFFE URI (%s) from certificate as a URI", possibleName.get()),
            e);
      }
    }
    return Optional.empty();
  }
}
