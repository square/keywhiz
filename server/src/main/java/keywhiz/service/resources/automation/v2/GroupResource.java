package keywhiz.service.resources.automation.v2;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import io.dropwizard.auth.Auth;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import keywhiz.api.automation.v2.CreateGroupRequestV2;
import keywhiz.api.automation.v2.GroupDetailResponseV2;
import keywhiz.api.model.AutomationClient;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.log.AuditLog;
import keywhiz.log.Event;
import keywhiz.log.EventTag;
import keywhiz.service.daos.AclDAO;
import keywhiz.service.daos.AclDAO.AclDAOFactory;
import keywhiz.service.daos.GroupDAO;
import keywhiz.service.daos.GroupDAO.GroupDAOFactory;
import keywhiz.service.exceptions.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;
import static java.util.stream.Collectors.toSet;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * @parentEndpointName automation/v2-group-management
 * @resourceDescription Automation endpoints to manage groups
 */
@Path("/automation/v2/groups")
public class GroupResource {
  private static final Logger logger = LoggerFactory.getLogger(GroupResource.class);

  private final AclDAO aclDAOReadOnly;
  private final GroupDAO groupDAOReadOnly;
  private final GroupDAO groupDAOReadWrite;
  private final AuditLog auditLog;

  @Inject public GroupResource(AclDAOFactory aclDAOFactory, GroupDAOFactory groupDAOFactory, AuditLog auditLog) {
    this.aclDAOReadOnly = aclDAOFactory.readonly();
    this.groupDAOReadOnly = groupDAOFactory.readonly();
    this.groupDAOReadWrite = groupDAOFactory.readwrite();
    this.auditLog = auditLog;
  }

  /**
   * Creates a group
   *
   * @excludeParams automationClient
   * @param request JSON request to create a group
   *
   * @responseMessage 201 Created group
   * @responseMessage 409 Group already exists
   */
  @Timed @ExceptionMetered
  @POST
  @Consumes(APPLICATION_JSON)
  public Response createGroup(@Auth AutomationClient automationClient,
      @Valid CreateGroupRequestV2 request) {
    String creator = automationClient.getName();
    String group = request.name();

    groupDAOReadWrite.getGroup(group).ifPresent((g) -> {
      logger.info("Automation ({}) - Group {} already exists", creator, group);
      throw new ConflictException(format("Group %s already exists", group));
    });

    groupDAOReadWrite.createGroup(group, creator, request.description(), request.metadata());
    Map<String, String> extraInfo = new HashMap<>();
    if (request.description() != null) {
      extraInfo.put("description", request.description());
    }
    if (request.metadata() != null) {
      extraInfo.put("metadata", request.metadata().toString());
    }
    auditLog.recordEvent(new Event(Instant.now(), EventTag.GROUP_CREATE, creator, group, extraInfo));
    URI uri = UriBuilder.fromResource(GroupResource.class).path(group).build();
    return Response.created(uri).build();
  }

  /**
   * Retrieve listing of group names
   *
   * @excludeParams automationClient
   * @responseMessage 200 List of group names
   */
  @Timed @ExceptionMetered
  @GET
  @Produces(APPLICATION_JSON)
  public Iterable<String> groupListing(@Auth AutomationClient automationClient) {
    return groupDAOReadOnly.getGroups().stream()
        .map(Group::getName)
        .collect(toSet());
  }

  /**
   * Retrieve information on a group
   *
   * @excludeParams automationClient
   * @param name Group name
   *
   * @responseMessage 200 Group information retrieved
   * @responseMessage 404 Group not found
   */
  @Timed @ExceptionMetered
  @GET
  @Path("{name}")
  @Produces(APPLICATION_JSON)
  public GroupDetailResponseV2 groupInfo(@Auth AutomationClient automationClient,
      @PathParam("name") String name) {
    Group group = groupDAOReadOnly.getGroup(name)
        .orElseThrow(NotFoundException::new);

    Set<String> secrets = aclDAOReadOnly.getSanitizedSecretsFor(group).stream()
        .map(SanitizedSecret::name)
        .collect(toSet());

    Set<String> clients = aclDAOReadOnly.getClientsFor(group).stream()
        .map(Client::getName)
        .collect(toSet());

    return GroupDetailResponseV2.builder()
        .group(group)
        .secrets(secrets)
        .clients(clients)
        .build();
  }

  /**
   * Retrieve metadata for secrets in a particular group.
   *
   * @excludeParams automationClient
   * @param name Group name
   *
   * @responseMessage 200 Group information retrieved
   * @responseMessage 404 Group not found
   */
  @Timed @ExceptionMetered
  @GET
  @Path("{name}/secrets")
  @Produces(APPLICATION_JSON)
  public Set<SanitizedSecret> secretDetailForGroup(@Auth AutomationClient automationClient,
      @PathParam("name") String name) {
    Group group = groupDAOReadOnly.getGroup(name)
        .orElseThrow(NotFoundException::new);

    return aclDAOReadOnly.getSanitizedSecretsFor(group);
  }

  /**
   * Retrieve metadata for clients in a particular group.
   *
   * @excludeParams automationClient
   * @param name Group name
   *
   * @responseMessage 200 Group information retrieved
   * @responseMessage 404 Group not found
   */
  @Timed @ExceptionMetered
  @GET
  @Path("{name}/clients")
  @Produces(APPLICATION_JSON)
  public Set<Client> clientDetailForGroup(@Auth AutomationClient automationClient,
      @PathParam("name") String name) {
    Group group = groupDAOReadOnly.getGroup(name)
        .orElseThrow(NotFoundException::new);

    return aclDAOReadOnly.getClientsFor(group);
  }

  /**
   * Delete a group
   *
   * @excludeParams automationClient
   * @param name Group name to delete
   *
   * @responseMessage 204 Group deleted
   * @responseMessage 404 Group not found
   */
  @Timed @ExceptionMetered
  @DELETE
  @Path("{name}")
  public Response deleteGroup(@Auth AutomationClient automationClient,
      @PathParam("name") String name) {
    Group group = groupDAOReadWrite.getGroup(name)
        .orElseThrow(NotFoundException::new);

    // Group memberships are deleted automatically by DB cascading.
    groupDAOReadWrite.deleteGroup(group);
    auditLog.recordEvent(new Event(Instant.now(), EventTag.GROUP_DELETE, automationClient.getName(), group.getName()));
    return Response.noContent().build();
  }
}
