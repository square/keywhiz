package keywhiz.service.resources.automation.v2;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.Sets;
import io.dropwizard.auth.Auth;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import keywhiz.api.automation.v2.ClientDetailResponseV2;
import keywhiz.api.automation.v2.CreateClientRequestV2;
import keywhiz.api.automation.v2.ModifyClientRequestV2;
import keywhiz.api.automation.v2.ModifyGroupsRequestV2;
import keywhiz.api.model.AutomationClient;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.log.AuditLog;
import keywhiz.log.Event;
import keywhiz.log.EventTag;
import keywhiz.service.daos.AclDAO;
import keywhiz.service.daos.AclDAO.AclDAOFactory;
import keywhiz.service.daos.ClientDAO;
import keywhiz.service.daos.ClientDAO.ClientDAOFactory;
import keywhiz.service.daos.GroupDAO;
import keywhiz.service.daos.GroupDAO.GroupDAOFactory;
import keywhiz.service.exceptions.ConflictException;
import keywhiz.service.permissions.Action;
import keywhiz.service.permissions.PermissionCheck;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;
import static java.util.stream.Collectors.toSet;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static keywhiz.Tracing.setTag;
import static keywhiz.Tracing.tagErrors;

/**
 * parentEndpointName automation/v2-client-management
 * <p>
 * resourceDescription Automation endpoints to manage clients
 */
@Path("/automation/v2/clients")
public class ClientResource {
  private static final Logger logger = LoggerFactory.getLogger(ClientResource.class);

  private final AclDAO aclDAOReadOnly;
  private final AclDAO aclDAOReadWrite;
  private final ClientDAO clientDAOReadOnly;
  private final ClientDAO clientDAOReadWrite;
  private final GroupDAO groupDAOReadWrite;
  private final AuditLog auditLog;
  private final PermissionCheck permissionCheck;

  @Inject public ClientResource(AclDAOFactory aclDAOFactory, ClientDAOFactory clientDAOFactory,
      GroupDAOFactory groupDAOFactory, AuditLog auditLog, PermissionCheck permissionCheck) {
    this.aclDAOReadOnly = aclDAOFactory.readonly();
    this.aclDAOReadWrite = aclDAOFactory.readwrite();
    this.clientDAOReadOnly = clientDAOFactory.readonly();
    this.clientDAOReadWrite = clientDAOFactory.readwrite();
    this.groupDAOReadWrite = groupDAOFactory.readwrite();
    this.auditLog = auditLog;
    this.permissionCheck = permissionCheck;
  }

  /**
   * Creates a client and assigns to given groups
   *
   * @param request JSON request to create a client
   * @return 200 if the client is created successfully, 409 if it already exists
   * <p>
   * responseMessage 201 Created client and assigned to given groups
   * <p>
   * responseMessage 409 Client already exists
   */
  @Timed @ExceptionMetered
  @POST
  @Consumes(APPLICATION_JSON)
  public Response createClient(@Auth AutomationClient automationClient,
      @Valid CreateClientRequestV2 request) {
    permissionCheck.checkAllowedForTargetTypeOrThrow(automationClient, Action.CREATE, Client.class);

    return tagErrors(() -> doCreateClient(automationClient, request));
  }

  private Response doCreateClient(AutomationClient automationClient,
      CreateClientRequestV2 request) {
    String creator = automationClient.getName();
    String client = request.name();

    setTag("client", client);

    clientDAOReadWrite.getClientByName(client).ifPresent((c) -> {
      logger.info("Automation ({}) - Client {} already exists", creator, client);
      throw new ConflictException("Client name already exists.");
    });

    // Creates new client record
    long clientId;
    try {
      clientId = clientDAOReadWrite.createClient(client, creator, request.description(),
          new URI(request.spiffeId()));
    } catch (URISyntaxException e) {
      logger.info(format("Automation (%s) - Client %s could not be created because of invalid SPIFFE ID %s",
          creator, client, request.spiffeId()), e);
      throw new BadRequestException("Invalid SPIFFE ID provided (not a URI)");
    }
    auditLog.recordEvent(new Event(Instant.now(), EventTag.CLIENT_CREATE, creator, client));

    // Enrolls client in any requested groups
    groupsToGroupIds(request.groups())
        .forEach((maybeGroupId) -> maybeGroupId.ifPresent(
            (groupId) -> aclDAOReadWrite.findAndEnrollClient(clientId, groupId, auditLog, creator,
                new HashMap<>())));

    URI uri = UriBuilder.fromResource(ClientResource.class).path(client).build();
    return Response.created(uri).build();
  }

  /**
   * Retrieve listing of client names
   * <p>
   * responseMessage 200 List of client names
   */
  @Timed @ExceptionMetered
  @GET
  @Produces(APPLICATION_JSON)
  public Iterable<String> clientListing(@Auth AutomationClient automationClient) {
    permissionCheck.checkAllowedForTargetTypeOrThrow(automationClient, Action.READ, Client.class);

    return clientDAOReadOnly.getClients().stream()
        .map(Client::getName)
        .collect(toSet());
  }

  /**
   * Retrieve information on a client
   *
   * @param name Client name
   * @return the retrieved client
   * <p>
   * responseMessage 200 Client information retrieved
   * <p>
   * responseMessage 404 Client not   found
   */
  @Timed @ExceptionMetered
  @GET
  @Path("{name}")
  @Produces(APPLICATION_JSON)
  public ClientDetailResponseV2 clientInfo(@Auth AutomationClient automationClient,
      @PathParam("name") String name) {
    Client client = clientDAOReadOnly.getClientByName(name)
        .orElseThrow(NotFoundException::new);
    permissionCheck.checkAllowedOrThrow(automationClient, Action.READ, client);

    return ClientDetailResponseV2.fromClient(client);
  }

  /**
   * Listing of groups accessible to a client
   *
   * @param name Client name
   * @return Listing of groups the client has membership to
   * <p>
   * responseMessage 200 Listing succeeded
   * <p>
   * responseMessage 404 Client not found
   */
  @Timed @ExceptionMetered
  @GET
  @Path("{name}/groups")
  @Produces(APPLICATION_JSON)
  public Iterable<String> clientGroupsListing(@Auth AutomationClient automationClient,
      @PathParam("name") String name) {
    Client client = clientDAOReadOnly.getClientByName(name)
        .orElseThrow(NotFoundException::new);
    permissionCheck.checkAllowedOrThrow(automationClient, Action.READ, client);

    return aclDAOReadOnly.getGroupsFor(client).stream()
        .map(Group::getName)
        .collect(toSet());
  }

  /**
   * Modify groups a client has membership in
   *
   * @param name    Client name
   * @param request JSON request specifying which groups to add or remove
   * @return Listing of groups client has membership in
   * <p>
   * responseMessage 201 Client modified successfully
   * <p>
   * responseMessage 404 Client not found
   */
  @Timed @ExceptionMetered
  @PUT
  @Path("{name}/groups")
  @Produces(APPLICATION_JSON)
  public Iterable<String> modifyClientGroups(@Auth AutomationClient automationClient,
      @PathParam("name") String name, @Valid ModifyGroupsRequestV2 request) {
    Client client = clientDAOReadWrite.getClientByName(name)
        .orElseThrow(NotFoundException::new);
    permissionCheck.checkAllowedOrThrow(automationClient, Action.UPDATE, client);

    String user = automationClient.getName();

    long clientId = client.getId();
    Set<String> oldGroups = aclDAOReadWrite.getGroupsFor(client).stream()
        .map(Group::getName)
        .collect(toSet());

    Set<String> groupsToAdd = Sets.difference(request.addGroups(), oldGroups);
    Set<String> groupsToRemove = Sets.intersection(request.removeGroups(), oldGroups);

    // TODO: should optimize AclDAO to use names and return only name column

    groupsToGroupIds(groupsToAdd)
        .forEach((maybeGroupId) -> maybeGroupId.ifPresent(
            (groupId) -> aclDAOReadWrite.findAndEnrollClient(clientId, groupId, auditLog, user,
                new HashMap<>())));

    groupsToGroupIds(groupsToRemove)
        .forEach((maybeGroupId) -> maybeGroupId.ifPresent(
            (groupId) -> aclDAOReadWrite.findAndEvictClient(clientId, groupId, auditLog, user,
                new HashMap<>())));

    return aclDAOReadWrite.getGroupsFor(client).stream()
        .map(Group::getName)
        .collect(toSet());
  }

  /**
   * Listing of secrets accessible to a client
   *
   * @param name Client name
   * @return Listing of secrets accessible to client
   * <p>
   * responseMessage 200 Client lookup succeeded
   * <p>
   * responseMessage 404 Client not found
   */
  @Timed @ExceptionMetered
  @GET
  @Path("{name}/secrets")
  @Produces(APPLICATION_JSON)
  public Iterable<String> clientSecretsListing(@Auth AutomationClient automationClient,
      @PathParam("name") String name) {
    Client client = clientDAOReadOnly.getClientByName(name)
        .orElseThrow(NotFoundException::new);
    permissionCheck.checkAllowedOrThrow(automationClient, Action.READ, client);

    return aclDAOReadOnly.getSanitizedSecretsFor(client).stream()
        .map(SanitizedSecret::name)
        .collect(toSet());
  }

  /**
   * Delete a client
   *
   * @param name Client name
   * @return 200 if the deletion was successful, 404 if the client was not found
   * <p>
   * responseMessage 204 Client deleted
   * <p>
   * responseMessage 404 Client not found
   */
  @Timed @ExceptionMetered
  @DELETE
  @Path("{name}")
  public Response deleteClient(@Auth AutomationClient automationClient,
      @PathParam("name") String name) {
    Client client = clientDAOReadWrite.getClientByName(name)
        .orElseThrow(NotFoundException::new);
    permissionCheck.checkAllowedOrThrow(automationClient, Action.DELETE, client);

    // Group memberships are deleted automatically by DB cascading.
    clientDAOReadWrite.deleteClient(client);
    auditLog.recordEvent(
        new Event(Instant.now(), EventTag.CLIENT_DELETE, automationClient.getName(),
            client.getName()));
    return Response.noContent().build();
  }

  /**
   * Modify a client
   *
   * @param currentName Client name
   * @param request     JSON request to modify the client
   * @return the updated client
   * <p>
   * responseMessage 201 Client updated
   * <p>
   * responseMessage 404 Client not found
   */
  @Timed @ExceptionMetered
  @POST
  @Path("{name}")
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  public ClientDetailResponseV2 modifyClient(@Auth AutomationClient automationClient,
      @PathParam("name") String currentName, @Valid ModifyClientRequestV2 request) {
    Client client = clientDAOReadWrite.getClientByName(currentName)
        .orElseThrow(NotFoundException::new);
    permissionCheck.checkAllowedOrThrow(automationClient, Action.UPDATE, client);

    String newName = request.name();

    // TODO: implement change client (name, updatedAt, updatedBy)
    throw new NotImplementedException(format(
        "Need to implement mutation methods in DAO to rename %s to %s", client.getName(), newName));
  }

  private Stream<Optional<Long>> groupsToGroupIds(Set<String> groupNames) {
    return groupNames.stream()
        .map(groupDAOReadWrite::getGroup)
        .map((group) -> group.map(Group::getId));
  }
}
