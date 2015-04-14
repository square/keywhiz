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

import io.dropwizard.auth.Auth;
import io.dropwizard.jersey.params.LongParam;
import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import keywhiz.api.model.AutomationClient;
import keywhiz.service.daos.AclDAO;

/**
 * @parentEndpointName enroll-clients-automation
 *
 * @resourceDescription Assign or unassign clients to groups
 */
@Path("/automation/clients/{clientId}/groups/{groupId}")
@Produces(MediaType.APPLICATION_JSON)
public class AutomationEnrollClientGroupResource {
  private final AclDAO aclDAO;

  @Inject
  public AutomationEnrollClientGroupResource(AclDAO aclDAO) {
    this.aclDAO = aclDAO;
  }

  /**
   * Enroll Client in Group
   *
   * @param clientId the ID of the Client to assign
   * @param groupId the ID of the Group to be assigned to
   *
   * @description Assigns the Client specified by the clientID to the Group specified by the groupID
   * @responseMessage 200 Successfully enrolled Client in Group
   * @responseMessage 404 Could not find Client or Group
   */
  @PUT
  public Response enrollClientInGroup(
      @Auth AutomationClient automationClient,
      @PathParam("clientId") LongParam clientId,
      @PathParam("groupId") LongParam groupId) {

    try {
      aclDAO.findAndEnrollClient(clientId.get(), groupId.get());
    } catch (IllegalStateException e) {
      throw new NotFoundException();
    }

    return Response.ok().build();
  }

  /**
   * Remove Client from Group
   *
   * @param clientId the ID of the Client to unassign
   * @param groupId the ID of the Group to be removed from
   *
   * @description Unassigns the Client specified by the clientID from the Group specified by the groupID
   * @responseMessage 200 Successfully removed Client from Group
   * @responseMessage 404 Could not find Client or Group
   */
  @DELETE
  public Response evictClientFromGroup(
      @Auth AutomationClient automationClient,
      @PathParam("clientId") long clientId,
      @PathParam("groupId") long groupId) {

    try {
      aclDAO.findAndEvictClient(clientId, groupId);
    } catch (IllegalStateException e) {
      throw new NotFoundException();
    }

    return Response.ok().build();
  }
}
