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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @parentEndpointName enroll-secrets-automation
 *
 * @resourceDescription Assign or unassign secrets to groups
 */
@Path("/automation/secrets/{secretId}/groups/{groupId}")
@Produces(MediaType.APPLICATION_JSON)
public class AutomationSecretAccessResource {
  private static final Logger logger = LoggerFactory.getLogger(AutomationSecretAccessResource.class);
  private final AclDAO aclDAO;

  @Inject
  public AutomationSecretAccessResource(AclDAO aclDAO) {
    this.aclDAO = aclDAO;
  }

  /**
   * Assign Secret to Group
   *
   * @param secretId the ID of the Secret to assign
   * @param groupId the ID of the Group to be assigned to
   *
   * @description Assigns the Secret specified by the secretID to the Group specified by the groupID
   * @responseMessage 200 Successfully enrolled Secret in Group
   * @responseMessage 404 Could not find Secret or Group
   */
  @PUT
  public Response allowAccess(
      @Auth AutomationClient automationClient,
      @PathParam("secretId") LongParam secretId,
      @PathParam("groupId") LongParam groupId) {
    logger.info("Client '{}' allowing groupId={} access to secretId={}",
        automationClient, secretId, groupId);

    try {
      aclDAO.findAndAllowAccess(secretId.get(), groupId.get());
    } catch (IllegalStateException e) {
      throw new NotFoundException();
    }

    return Response.ok().build();
  }

  /**
   * Remove Secret from Group
   *
   * @param secretId the ID of the Secret to unassign
   * @param groupId the ID of the Group to be removed from
   *
   * @description Unassigns the Secret specified by the secretID from the Group specified by the groupID
   * @responseMessage 200 Successfully removed Secret from Group
   * @responseMessage 404 Could not find Secret or Group
   */
  @DELETE
  public Response disallowAccess(
      @Auth AutomationClient automationClient,
      @PathParam("secretId") LongParam secretId,
      @PathParam("groupId") LongParam groupId) {
    logger.info("Client '{}' disallowing groupId={} access to secretId={}",
        automationClient, secretId, groupId);

    try {
      aclDAO.findAndRevokeAccess(secretId.get(), groupId.get());
    } catch (IllegalStateException e) {
      throw new NotFoundException();
    }

    return Response.ok().build();
  }
}
