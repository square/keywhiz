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
import java.util.List;
import java.util.Optional;
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
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import keywhiz.api.CreateGroupRequest;
import keywhiz.api.GroupDetailResponse;
import keywhiz.api.model.AutomationClient;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.service.daos.AclDAO;
import keywhiz.service.daos.GroupDAO;
import keywhiz.service.exceptions.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.toList;

/**
 * @parentEndpointName groups-automation
 *
 * @resourcePath /automation/groups
 * @resourceDescription Create and retrieve groups
 */
@Path("/automation/groups")
@Produces(MediaType.APPLICATION_JSON)
public class AutomationGroupResource {
  private static final Logger logger = LoggerFactory.getLogger(AutomationGroupResource.class);

  private final GroupDAO groupDAO;
  private final AclDAO aclDAO;

  @Inject
  public AutomationGroupResource(GroupDAO groupDAO, AclDAO aclDAO) {
    this.groupDAO = groupDAO;
    this.aclDAO = aclDAO;
  }

  /**
   * Retrieve Group by ID
   *
   * @param groupId the ID of the group to retrieve
   *
   * @description Returns a single Group if found
   * @responseMessage 200 Found and retrieved Group with given ID
   * @responseMessage 404 Group with given ID not Found
   */
  @GET
  @Path("{groupId}")
  public GroupDetailResponse getGroupById(
      @Auth AutomationClient automationClient,
      @PathParam("groupId") LongParam groupId) {
    Group group = groupDAO.getGroupById(groupId.get()).orElseThrow(NotFoundException::new);

    ImmutableList<Client> clients = ImmutableList.copyOf(aclDAO.getClientsFor(group));
    ImmutableList<SanitizedSecret> sanitizedSecrets =
        ImmutableList.copyOf(aclDAO.getSanitizedSecretsFor(group));
    return GroupDetailResponse.fromGroup(group, sanitizedSecrets, clients);
  }

  /**
   * Retrieve Group by a specified name, or all Groups if no name given
   *
   * @optionalParams name
   * @param name the name of the Group to retrieve, if provided
   *
   * @description Returns a single Group or a set of all Groups
   * @responseMessage 200 Found and retrieved Group(s)
   * @responseMessage 404 Group with given name not found (if name provided)
   */
  @GET
  public Response getGroupByName(@QueryParam("name") Optional<String> name) {
    if (name.isPresent()) {
      Group group = groupDAO.getGroup(name.get()).orElseThrow(NotFoundException::new);

      ImmutableList<Client> clients = ImmutableList.copyOf(aclDAO.getClientsFor(group));
      ImmutableList<SanitizedSecret> sanitizedSecrets = ImmutableList.copyOf(aclDAO.getSanitizedSecretsFor(group));
      return Response.ok()
          .entity(GroupDetailResponse.fromGroup(group, sanitizedSecrets, clients))
          .build();
    }

    ImmutableList<SanitizedSecret> emptySecrets = ImmutableList.of();
    ImmutableList<Client> emptyClients = ImmutableList.of();
    List<GroupDetailResponse> groups = groupDAO.getGroups().stream()
        .map((g) -> GroupDetailResponse.fromGroup(g, emptySecrets, emptyClients))
        .collect(toList());
    return Response.ok()
        .entity(groups)
        .build();
  }

  /**
   * Create Group
   *
   * @param groupRequest the JSON group request used to formulate the Group
   *
   * @description Creates a Group with the name from a valid group request
   * @responseMessage 200 Successfully created Group
   * @responseMessage 409 Group with given name already exists
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Group createGroup(
      @Auth AutomationClient automationClient,
      @Valid CreateGroupRequest groupRequest) {

    Optional<Group> group = groupDAO.getGroup(groupRequest.name);
    if (group.isPresent()) {
      logger.info("Automation ({}) - Group {} already exists", automationClient.getName(), groupRequest.name);
      throw new ConflictException("Group name already exists.");
    }

    long id = groupDAO.createGroup(groupRequest.name, automationClient.getName(),
        Optional.ofNullable(groupRequest.description));
    return groupDAO.getGroupById(id).get();
  }

  /**
   * Deletes a group
   *
   * @param groupId the ID of the group to delete
   *
   * @description Deletes a single group by id
   * @responseMessage 200 Deleted group
   * @responseMessage 404 Group not found by id
   */
  @DELETE
  @Path("{groupId}")
  public Response deleteGroup(@Auth AutomationClient automationClient,
      @PathParam("groupId") LongParam groupId) {
    Group group = groupDAO.getGroupById(groupId.get()).orElseThrow(NotFoundException::new);
    groupDAO.deleteGroup(group);
    return Response.ok().build();
  }
}
