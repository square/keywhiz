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

package keywhiz.service.resources;

import com.google.common.collect.ImmutableList;
import io.dropwizard.auth.Auth;
import io.dropwizard.jersey.params.LongParam;
import java.net.URI;
import java.util.List;
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
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import keywhiz.api.CreateGroupRequest;
import keywhiz.api.GroupDetailResponse;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.auth.User;
import keywhiz.service.daos.AclJooqDao;
import keywhiz.service.daos.GroupDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @parentEndpointName groups-admin
 *
 * @resourcePath /admin/groups
 * @resourceDescription Create, retrieve, and delete groups
 */
@Path("/admin/groups")
@Produces(MediaType.APPLICATION_JSON)
public class GroupsResource {
  private static final Logger logger = LoggerFactory.getLogger(GroupsResource.class);
  private final AclJooqDao aclJooqDao;
  private final GroupDAO groupDAO;

  @Inject
  public GroupsResource(AclJooqDao aclJooqDao, GroupDAO groupDAO) {
    this.aclJooqDao = aclJooqDao;
    this.groupDAO = groupDAO;
  }

  /**
   * Retrieve Group by a specified name, or all Groups if no name given
   *
   * @optionalParams name
   * @param name the name of the Group to retrieve, if provided
   *
   * @description Returns a single Group or a set of all Groups for this user.
   * Used by Keywhiz CLI and the web ui.
   * @responseMessage 200 Found and retrieved Group(s)
   * @responseMessage 404 Group with given name not found (if name provided)
   */
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
   * @param request the JSON client request used to formulate the Group
   *
   * @description Creates a Group with the name from a valid group request.
   * Used by Keywhiz CLI and the web ui.
   * @responseMessage 200 Successfully created Group
   * @responseMessage 400 Group with given name already exists
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Response createGroup(@Auth User user, @Valid CreateGroupRequest request) {

    logger.info("User '{}' creating group.", user);
    if (groupDAO.getGroup(request.name).isPresent()) {
      throw new BadRequestException("Group already exists.");
    }

    long groupId = groupDAO.createGroup(request.name, user.getName(),
        Optional.ofNullable(request.description));
    URI uri = UriBuilder.fromResource(GroupsResource.class).build(groupId);
    return Response
        .created(uri)
        .entity(groupDetailResponseFromId(groupId))
        .build();
  }

  /**
   * Retrieve Group by ID
   *
   * @param groupId the ID of the Group to retrieve
   *
   * @description Returns a single Group if found.
   * Used by Keywhiz CLI and the web ui.
   * @responseMessage 200 Found and retrieved Group with given ID
   * @responseMessage 404 Group with given ID not Found
   */
  @Path("{groupId}")
  @GET
  public GroupDetailResponse getGroup(@Auth User user, @PathParam("groupId") LongParam groupId) {
    logger.info("User '{}' retrieving group id={}.", user, groupId);
    return groupDetailResponseFromId(groupId.get());
  }

  /**
   * Delete Group by ID
   *
   * @param groupId the ID of the Group to be deleted
   *
   * @description Deletes a single Group if found.
   * Used by Keywhiz CLI and the web ui.
   * @responseMessage 200 Found and deleted Group with given ID
   * @responseMessage 404 Group with given ID not Found
   */
  @Path("{groupId}")
  @DELETE
  public Response deleteGroup(@Auth User user, @PathParam("groupId") LongParam groupId) {
    logger.info("User '{}' deleting group id={}.", user, groupId);

    Optional<Group> group = groupDAO.getGroupById(groupId.get());
    if (!group.isPresent()) {
      throw new NotFoundException("Group not found.");
    }

    groupDAO.deleteGroup(group.get());
    return Response.noContent().build();
  }

  private GroupDetailResponse groupDetailResponseFromId(long groupId) {
    Optional<Group> optionalGroup = groupDAO.getGroupById(groupId);
    if (!optionalGroup.isPresent()) {
      throw new NotFoundException("Group not found.");
    }

    Group group = optionalGroup.get();
    ImmutableList<SanitizedSecret> secrets =
        ImmutableList.copyOf(aclJooqDao.getSanitizedSecretsFor(group));
    ImmutableList<Client> clients = ImmutableList.copyOf(aclJooqDao.getClientsFor(group));

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
