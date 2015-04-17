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
import keywhiz.auth.User;
import keywhiz.service.daos.AclJooqDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @parentEndpointName memberships-admin
 *
 * @resourcePath /admin/memberships
 * @resourceDescription Manage group assignments
 */
@Path("/admin/memberships")
@Produces(MediaType.APPLICATION_JSON)
public class MembershipResource {
  private static final Logger logger = LoggerFactory.getLogger(MembershipResource.class);

  private final AclJooqDao aclJooqDao;

  @Inject
  public MembershipResource(AclJooqDao aclJooqDao) {
    this.aclJooqDao = aclJooqDao;
  }

  /**
   * Allow a Group to access this Secret
   *
   * @param secretId ID value of a Secret
   * @param groupId ID value of a Group
   * @return HTTP response
   *
   * @description Assigns the Secret specified by the secretID to the Group specified by the groupID
   * Used by Keywhiz CLI and the web ui.
   * @responseMessage 200 Successfully enrolled Secret in Group
   * @responseMessage 404 Could not find Secret or Group
   */
  @Path("/secrets/{secretId}/groups/{groupId}")
  @PUT
  public Response allowAccess(
      @Auth User user,
      @PathParam("secretId") LongParam secretId,
      @PathParam("groupId") LongParam groupId) {

    logger.info("User '{}' allowing groupId {} access to secretId {}", user, secretId, groupId);

    try {
      aclJooqDao.findAndAllowAccess(secretId.get(), groupId.get());
    } catch (IllegalStateException e) {
      throw new NotFoundException();
    }

    return Response.ok().build();
  }

  /**
   * Disallow a Group to access this Secret
   *
   * @param secretId ID value of a Secret
   * @param groupId ID value of a Group
   * @return HTTP response
   *
   * @description Unassigns the Secret specified by the secretID from the Group specified by the groupID
   * Used by Keywhiz CLI and the web ui.
   * @responseMessage 200 Successfully removed Secret from Group
   * @responseMessage 404 Could not find Secret or Group
   */
  @Path("/secrets/{secretId}/groups/{groupId}")
  @DELETE
  public Response disallowAccess(
      @Auth User user,
      @PathParam("secretId") LongParam secretId,
      @PathParam("groupId") LongParam groupId) {

    logger.info("User '{}' disallowing groupId {} access to secretId {}", user, secretId, groupId);

    try {
      aclJooqDao.findAndRevokeAccess(secretId.get(), groupId.get());
    } catch (IllegalStateException e) {
      throw new NotFoundException();
    }

    return Response.ok().build();
  }

  /**
   * Enroll a Client into a Group
   *
   * @param clientId ID value of a Client
   * @param groupId ID value of a Group
   * @return HTTP response
   *
   * @description Assigns the Client specified by the clientID to the Group specified by the groupID
   * @responseMessage 200 Successfully enrolled Client in Group
   * @responseMessage 404 Could not find Client or Group
   */
  @Path("/clients/{clientId}/groups/{groupId}")
  @PUT
  public Response enrollClient(
    @Auth User user,
    @PathParam("clientId") LongParam clientId,
    @PathParam("groupId") LongParam groupId) {

    logger.info("User {} enrolling clientId {} in groupId {}.", user.getName(), clientId, groupId);

    try {
      aclJooqDao.findAndEnrollClient(clientId.get(), groupId.get());
    } catch (IllegalStateException e) {
      throw new NotFoundException();
    }

    return Response.ok().build();
  }

  /**
   * Remove a Client from a Group
   *
   * @param clientId ID value of a Client
   * @param groupId ID value of a Group
   * @return HTTP response
   *
   * @description Unassigns the Client specified by the clientID from the Group specified by the groupID
   * @responseMessage 200 Successfully removed Client from Group
   * @responseMessage 404 Could not find Client or Group
   */
  @Path("/clients/{clientId}/groups/{groupId}")
  @DELETE
  public Response evictClient(
      @Auth User user,
      @PathParam("clientId") LongParam clientId,
      @PathParam("groupId") LongParam groupId) {
    logger.info("User {} evicting clientId {} from groupId {}.", user.getName(), clientId, groupId);

    try {
      aclJooqDao.findAndEvictClient(clientId.get(), groupId.get());
    } catch (IllegalStateException e) {
      throw new NotFoundException();
    }

    return Response.ok().build();
  }
}
