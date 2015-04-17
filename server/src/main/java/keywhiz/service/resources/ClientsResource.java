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
import keywhiz.api.ClientDetailResponse;
import keywhiz.api.CreateClientRequest;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.auth.User;
import keywhiz.service.daos.AclDAO;
import keywhiz.service.daos.ClientDAO;
import keywhiz.service.exceptions.ConflictException;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @parentEndpointName clients-admin
 *
 * @resourcePath /admin/clients
 * @resourceDescription Create, retrieve, and delete clients
 */
@Path("/admin/clients")
@Produces(MediaType.APPLICATION_JSON)
public class ClientsResource {
  private static final Logger logger = LoggerFactory.getLogger(ClientsResource.class);

  private final AclDAO aclDAO;
  private final ClientDAO clientDAO;

  @Inject
  public ClientsResource(AclDAO aclDAO, ClientDAO clientDAO) {
    this.aclDAO = aclDAO;
    this.clientDAO = clientDAO;
  }

  /**
   * Retrieve Client by a specified name, or all Clients if no name given
   *
   * @optionalParams name
   * @param name the name of the Client to retrieve, if provided
   *
   * @description Returns a single Client or a set of all Clients for this user.
   * Used by Keywhiz CLI and the web ui.
   * @responseMessage 200 Found and retrieved Client(s)
   * @responseMessage 404 Client with given name not found (if name provided)
   */
  @GET
  public Response findClients(@Auth User user, @DefaultValue("") @QueryParam("name") String name) {
    if (name.isEmpty()) {
      return Response.ok().entity(listClients(user)).build();
    }
    return Response.ok().entity(getClientByName(user, name)).build();
  }

  protected List<Client> listClients(@Auth User user) {
    logger.info("User '{}' listing clients.", user);
    Set<Client> clients = clientDAO.getClients();
    return ImmutableList.copyOf(clients);
  }

  protected Client getClientByName(@Auth User user, String name) {
    logger.info("User '{}' retrieving client name={}.", user, name);
    return clientFromName(name);
  }

  /**
   * Create Client
   *
   * @param createClientRequest the JSON client request used to formulate the Client
   *
   * @description Creates a Client with the name from a valid client request.
   * Used by Keywhiz CLI and the web ui.
   * @responseMessage 200 Successfully created Client
   * @responseMessage 409 Client with given name already exists
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Response createClient(@Auth User user,
      @Valid CreateClientRequest createClientRequest) {

    logger.info("User '{}' creating client '{}'.", user, createClientRequest.name);

    long clientId;
    try {
      clientId = clientDAO.createClient(createClientRequest.name, user.getName(), Optional.empty());
    } catch (DataAccessException e) {
      logger.warn("Cannot create client {}: {}", createClientRequest.name, e);
      throw new ConflictException("Conflict creating client.");
    }

    URI uri = UriBuilder.fromResource(ClientsResource.class).path("{clientId}").build(clientId);
    return Response
        .created(uri)
        .entity(clientDetailResponseFromId(clientId))
        .build();
  }

  /**
   * Retrieve Client by ID
   *
   * @param clientId the ID of the Client to retrieve
   *
   * @description Returns a single Client if found.
   * Used by Keywhiz CLI and the web ui.
   * @responseMessage 200 Found and retrieved Client with given ID
   * @responseMessage 404 Client with given ID not Found
   */
  @Path("{clientId}")
  @GET
  public ClientDetailResponse getClient(@Auth User user,
      @PathParam("clientId") LongParam clientId) {
    logger.info("User '{}' retrieving client id={}.", user, clientId);
    return clientDetailResponseFromId(clientId.get());
  }

  /**
   * Delete Client by ID
   *
   * @param clientId the ID of the Client to be deleted
   *
   * @description Deletes a single Client if found.
   * Used by Keywhiz CLI and the web ui.
   * @responseMessage 200 Found and deleted Client with given ID
   * @responseMessage 404 Client with given ID not Found
   */
  @Path("{clientId}")
  @DELETE
  public Response deleteClient(@Auth User user, @PathParam("clientId") LongParam clientId) {
    logger.info("User '{}' deleting client id={}.", user, clientId);

    Optional<Client> client = clientDAO.getClientById(clientId.get());
    if (!client.isPresent()) {
      throw new NotFoundException("Client not found.");
    }

    clientDAO.deleteClient(client.get());

    return Response.noContent().build();
  }

  private ClientDetailResponse clientDetailResponseFromId(long clientId) {
    Optional<Client> optionalClient = clientDAO.getClientById(clientId);
    if (!optionalClient.isPresent()) {
      throw new NotFoundException("Client not found.");
    }

    Client client = optionalClient.get();
    ImmutableList<Group> groups = ImmutableList.copyOf(aclDAO.getGroupsFor(client));
    ImmutableList<SanitizedSecret> sanitizedSecrets =
        ImmutableList.copyOf(aclDAO.getSanitizedSecretsFor(client));

    return ClientDetailResponse.fromClient(client, groups, sanitizedSecrets);
  }

  private Client clientFromName(String clientName) {
    Optional<Client> optionalClient = clientDAO.getClient(clientName);
    if (!optionalClient.isPresent()) {
      throw new NotFoundException("Client not found.");
    }

    return optionalClient.get();
  }
}
