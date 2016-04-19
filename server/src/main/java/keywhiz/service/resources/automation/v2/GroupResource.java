package keywhiz.service.resources.automation.v2;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Metered;
import com.codahale.metrics.annotation.Timed;
import io.dropwizard.auth.Auth;
import java.net.URI;
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

  private final AclDAO aclDAO;
  private final GroupDAO groupDAO;

  @Inject public GroupResource(AclDAOFactory aclDAOFactory, GroupDAOFactory groupDAOFactory) {
    this.aclDAO = aclDAOFactory.readwrite();
    this.groupDAO = groupDAOFactory.readwrite();
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
  @POST @Timed @Metered(name="QPS") @ExceptionMetered(name="ExceptionQPS")
  @Consumes(APPLICATION_JSON)
  public Response createGroup(@Auth AutomationClient automationClient,
      @Valid CreateGroupRequestV2 request) {
    String creator = automationClient.getName();
    String group = request.name();

    groupDAO.getGroup(group).ifPresent((g) -> {
      logger.info("Automation ({}) - Group {} already exists", creator, group);
      throw new ConflictException(format("Group %s already exists", group));
    });

    groupDAO.createGroup(group, creator, request.description());
    URI uri = UriBuilder.fromResource(GroupResource.class).path(group).build();
    return Response.created(uri).build();
  }

  /**
   * Retrieve listing of group names
   *
   * @excludeParams automationClient
   * @responseMessage 200 List of group names
   */
  @GET @Timed @Metered(name="QPS") @ExceptionMetered(name="ExceptionQPS")
  @Produces(APPLICATION_JSON)
  public Iterable<String> groupListing(@Auth AutomationClient automationClient) {
    return groupDAO.getGroups().stream()
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
  @GET @Timed @Metered(name="QPS") @ExceptionMetered(name="ExceptionQPS")
  @Path("{name}")
  @Produces(APPLICATION_JSON)
  public GroupDetailResponseV2 groupInfo(@Auth AutomationClient automationClient,
      @PathParam("name") String name) {
    Group group = groupDAO.getGroup(name)
        .orElseThrow(NotFoundException::new);

    Set<String> secrets = aclDAO.getSanitizedSecretsFor(group).stream()
        .map(SanitizedSecret::name)
        .collect(toSet());

    Set<String> clients = aclDAO.getClientsFor(group).stream()
        .map(Client::getName)
        .collect(toSet());

    return GroupDetailResponseV2.builder()
        .group(group)
        .secrets(secrets)
        .clients(clients)
        .build();
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
  @DELETE @Timed @Metered(name="QPS") @ExceptionMetered(name="ExceptionQPS")
  @Path("{name}")
  public Response deleteGroup(@Auth AutomationClient automationClient,
      @PathParam("name") String name) {
    Group group = groupDAO.getGroup(name)
        .orElseThrow(NotFoundException::new);

    // Group memberships are deleted automatically by DB cascading.
    groupDAO.deleteGroup(group);
    return Response.noContent().build();
  }
}
