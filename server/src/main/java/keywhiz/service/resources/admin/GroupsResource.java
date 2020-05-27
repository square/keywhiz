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

package keywhiz.service.resources.admin;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import io.dropwizard.auth.Auth;
import io.dropwizard.jersey.params.LongParam;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import keywhiz.api.GroupDetailResponse;
import keywhiz.api.automation.v2.CreateGroupRequestV2;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.auth.User;
import keywhiz.log.AuditLog;
import keywhiz.log.Event;
import keywhiz.log.EventTag;
import keywhiz.service.daos.AclDAO;
import keywhiz.service.daos.AclDAO.AclDAOFactory;
import keywhiz.service.daos.GroupDAO;
import keywhiz.service.daos.GroupDAO.GroupDAOFactory;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Strings.nullToEmpty;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * parentEndpointName groups-admin
 *
 * resourcePath /admin/groups
 * resourceDescription Create, retrieve, and delete groups
 */
@Path("/admin/groups")
@Produces(APPLICATION_JSON)
public class GroupsResource {
  private static final Logger logger = LoggerFactory.getLogger(GroupsResource.class);
  private final AclDAO aclDAO;
  private final GroupDAO groupDAO;
  private final AuditLog auditLog;

  @Inject public GroupsResource(AclDAOFactory aclDAOFactory, GroupDAOFactory groupDAOFactory, AuditLog auditLog) {
    this.aclDAO = aclDAOFactory.readwrite();
    this.groupDAO = groupDAOFactory.readwrite();
    this.auditLog = auditLog;
  }

  @VisibleForTesting GroupsResource(AclDAO aclDAO, GroupDAO groupDAO, AuditLog auditLog) {
    this.aclDAO = aclDAO;
    this.groupDAO = groupDAO;
    this.auditLog = auditLog;
  }

  /**
   * Retrieve Group by a specified name, or all Groups if no name given
   *
   * @param user the admin user performing this operation
   * @param name the name of the Group to retrieve, if provided
   * @return the named group, or all groups if no name was given
   *
   * description Returns a single Group or a set of all Groups for this user.
   * Used by Keywhiz CLI and the web ui.
   * responseMessage 200 Found and retrieved Group(s)
   * responseMessage 404 Group with given name not found (if name provided)
   */
  @Timed @ExceptionMetered
  @GET
  public Response findGroups(@Auth User user, @DefaultValue("") @QueryParam("name") String name) {
    if (name.isEmpty()) {
      return Response.ok().entity(listGroups(user)).build();
    }
    return Response.ok().entity(getGroupByName(user, name)).build();
  }

  protected List<Group> listGroups(@Auth User user) {
    logger.info("User '{}' listing groups.", user);
    Set<Group> groups = groupDAO.getGroups();
    return ImmutableList.copyOf(groups);
  }

  protected Group getGroupByName(@Auth User user, String name) {
    logger.info("User '{}' retrieving group name={}.", user, name);
    return groupFromName(name);
  }

  /**
   * Create Group
   *
   * @param user the admin user performing this operation
   * @param request the JSON client request used to formulate the Group
   * @return 200 if the group was created, 409 if the name already existed
   *
   * description Creates a Group with the name from a valid group request.
   * Used by Keywhiz CLI and the web ui.
   * responseMessage 200 Successfully created Group
   * responseMessage 400 Group with given name already exists
   */
  @Timed @ExceptionMetered
  @POST
  @Consumes(APPLICATION_JSON)
  public Response createGroup(@Auth User user, @Valid CreateGroupRequestV2 request) {

    logger.info("User '{}' creating group.", user);
    if (groupDAO.getGroup(request.name()).isPresent()) {
      throw new BadRequestException("Group already exists.");
    }

    long groupId = groupDAO.createGroup(request.name(), user.getName(),
        nullToEmpty(request.description()), request.metadata());
    URI uri = UriBuilder.fromResource(GroupsResource.class).build(groupId);
    Response response = Response
        .created(uri)
        .entity(groupDetailResponseFromId(groupId))
        .build();

    if (response.getStatus() == HttpStatus.SC_CREATED) {
      Map<String, String> extraInfo = new HashMap<>();
      if (request.description() != null) {
        extraInfo.put("description", request.description());
      }
      if (request.metadata() != null) {
        extraInfo.put("metadata", request.metadata().toString());
      }
      auditLog.recordEvent(new Event(Instant.now(), EventTag.GROUP_CREATE, user.getName(), request.name(), extraInfo));
    }
    return response;
  }

  /**
   * Retrieve Group by ID
   *
   * @param user the admin user performing this operation
   * @param groupId the ID of the Group to retrieve
   * @return the specified group, if found
   *
   * description Returns a single Group if found.
   * Used by Keywhiz CLI and the web ui.
   * responseMessage 200 Found and retrieved Group with given ID
   * responseMessage 404 Group with given ID not Found
   */
  @Path("{groupId}")
  @Timed @ExceptionMetered
  @GET
  public GroupDetailResponse getGroup(@Auth User user, @PathParam("groupId") LongParam groupId) {
    logger.info("User '{}' retrieving group id={}.", user, groupId);
    return groupDetailResponseFromId(groupId.get());
  }

  /**
   * Delete Group by ID
   *
   * @param user the admin user performing this operation
   * @param groupId the ID of the Group to be deleted
   * @return 200 if the deletion succeeded, 404 if the group was not found
   *
   * description Deletes a single Group if found.
   * Used by Keywhiz CLI and the web ui.
   * responseMessage 200 Found and deleted Group with given ID
   * responseMessage 404 Group with given ID not Found
   */
  @Path("{groupId}")
  @Timed @ExceptionMetered
  @DELETE
  public Response deleteGroup(@Auth User user, @PathParam("groupId") LongParam groupId) {
    logger.info("User '{}' deleting group id={}.", user, groupId);

    Optional<Group> group = groupDAO.getGroupById(groupId.get());
    if (!group.isPresent()) {
      throw new NotFoundException("Group not found.");
    }

    groupDAO.deleteGroup(group.get());
    auditLog.recordEvent(new Event(Instant.now(), EventTag.GROUP_DELETE, user.getName(), group.get().getName()));
    return Response.noContent().build();
  }

  private GroupDetailResponse groupDetailResponseFromId(long groupId) {
    Optional<Group> optionalGroup = groupDAO.getGroupById(groupId);
    if (!optionalGroup.isPresent()) {
      throw new NotFoundException("Group not found.");
    }

    Group group = optionalGroup.get();
    ImmutableList<SanitizedSecret> secrets =
        ImmutableList.copyOf(aclDAO.getSanitizedSecretsFor(group));
    ImmutableList<Client> clients = ImmutableList.copyOf(aclDAO.getClientsFor(group));

    return GroupDetailResponse.fromGroup(group, secrets, clients);
  }

  private Group groupFromName(String name) {
    Optional<Group> optionalGroup = groupDAO.getGroup(name);
    if (!optionalGroup.isPresent()) {
      throw new NotFoundException("Group not found.");
    }

    return optionalGroup.get();
  }
}
