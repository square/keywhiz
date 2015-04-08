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
import com.google.inject.Inject;
import io.dropwizard.auth.Auth;
import io.dropwizard.jersey.params.LongParam;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
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
import keywhiz.api.ClientDetailResponse;
import keywhiz.api.CreateClientRequest;
import keywhiz.api.model.AutomationClient;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.service.daos.AclDAO;
import keywhiz.service.daos.ClientDAO;
import keywhiz.service.exceptions.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @parentEndpointName clients-automation
 * @resourcePath /automation/clients
 * @resourceDescription Create and retrieve clients
 */
@Path("/automation/clients")
@Produces(MediaType.APPLICATION_JSON)
public class AutomationClientResource {
  private static final Logger logger = LoggerFactory.getLogger(ClientsResource.class);
  private final ClientDAO clientDAO;
  private final AclDAO aclDAO;

  @Inject
  public AutomationClientResource(ClientDAO clientDAO, AclDAO aclDAO) {
    this.clientDAO = clientDAO;
    this.aclDAO = aclDAO;
  }

  /**
   * Retrieve Client by ID
   *
   * @param clientId the ID of the Client to retrieve
   * @description Returns a single Client if found
   * @responseMessage 200 Found and retrieved Client with given ID
   * @responseMessage 404 Client with given ID not Found
   */
  @GET
  @Path("{clientId}")
  public Response findClientById(
      @Auth AutomationClient automationClient,
      @PathParam("clientId") LongParam clientId) {
    logger.info("Automation ({}) - Looking up an ID {}", automationClient.getName(), clientId);

    Client client = clientDAO.getClientById(clientId.get())
        .orElseThrow(NotFoundException::new);
    ImmutableList<Group> groups = ImmutableList.copyOf(aclDAO.getGroupsFor(client));

    return Response.ok()
        .entity(ClientDetailResponse.fromClient(client, groups, ImmutableList.of()))
        .build();
  }

  /**
   * Retrieve Client by a specified name, or all Clients if no name given
   *
   * @param name the name of the Client to retrieve, if provided
   * @optionalParams name
   * @description Returns a single Client or a set of all Clients
   * @responseMessage 200 Found and retrieved Client(s)
   * @responseMessage 404 Client with given name not found (if name provided)
   */
  @GET
  public Response findClient(
      @Auth AutomationClient automationClient,
      @Nullable @QueryParam("name") String name) {
    logger.info("Automation ({}) - Looking up a name {}", automationClient.getName(), name);

    Response response;
    if (name == null) {
      Set<Client> clients = clientDAO.getClients();
      clients.stream()
          .map(c -> ClientDetailResponse.fromClient(c, ImmutableList.copyOf(aclDAO.getGroupsFor(c)),
              ImmutableList.of()))
          .collect(Collectors.toSet());
      response = Response.ok().entity(clients).build();
    } else {
      Client client = clientDAO.getClient(name)
          .orElseThrow(NotFoundException::new);
      ImmutableList<Group> groups = ImmutableList.copyOf(aclDAO.getGroupsFor(client));

      response = Response.ok()
          .entity(ClientDetailResponse.fromClient(client, groups, ImmutableList.of()))
          .build();
    }

    return response;
  }

  /**
   * Create Client
   *
   * @param clientRequest the JSON client request used to formulate the Client
   * @description Creates a Client with the name from a valid client request
   * @responseMessage 200 Successfully created Client
   * @responseMessage 409 Client with given name already exists
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public ClientDetailResponse createClient(
      @Auth AutomationClient automationClient,
      @Valid CreateClientRequest clientRequest) {

    Optional<Client> client = clientDAO.getClient(clientRequest.name);
    if (client.isPresent()) {
      logger.info("Automation ({}) - Client {} already exists", automationClient.getName(),
          clientRequest.name);
      throw new ConflictException("Client name already exists.");
    }

    long id =
        clientDAO.createClient(clientRequest.name, automationClient.getName(), Optional.empty());
    client = clientDAO.getClientById(id);

    return ClientDetailResponse.fromClient(client.get(), ImmutableList.of(), ImmutableList.of());
  }

  /**
   * Deletes a client
   *
   * @param clientId the ID of the client to delete
   * @description Deletes a single client by id
   * @responseMessage 200 Deleted client
   * @responseMessage 404 Client not found by id
   */
  @DELETE
  @Path("{clientId}")
  public Response deleteGroup(@Auth AutomationClient automationClient,
      @PathParam("clientId") LongParam clientId) {
    Client client = clientDAO.getClientById(clientId.get()).orElseThrow(NotFoundException::new);
    clientDAO.deleteClient(client);
    return Response.ok().build();
  }
}
