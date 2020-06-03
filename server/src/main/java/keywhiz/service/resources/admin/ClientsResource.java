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
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import keywhiz.api.ClientDetailResponse;
import keywhiz.api.automation.v2.CreateClientRequestV2;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.auth.User;
import keywhiz.log.AuditLog;
import keywhiz.log.Event;
import keywhiz.log.EventTag;
import keywhiz.service.daos.AclDAO;
import keywhiz.service.daos.AclDAO.AclDAOFactory;
import keywhiz.service.daos.ClientDAO;
import keywhiz.service.daos.ClientDAO.ClientDAOFactory;
import keywhiz.service.exceptions.ConflictException;
import org.apache.http.HttpStatus;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * parentEndpointName clients-admin
 * <p>
 * resourceDescription Create, retrieve, and delete clients
 */
@Path("/admin/clients")
@Produces(APPLICATION_JSON)
public class ClientsResource {
  private static final Logger logger = LoggerFactory.getLogger(ClientsResource.class);

  private final AclDAO aclDAO;
  private final ClientDAO clientDAO;
  private final AuditLog auditLog;

  @Inject public ClientsResource(AclDAOFactory aclDAOFactory, ClientDAOFactory clientDAOFactory,
      AuditLog auditLog) {
    this.aclDAO = aclDAOFactory.readwrite();
    this.clientDAO = clientDAOFactory.readwrite();
    this.auditLog = auditLog;
  }

  @VisibleForTesting ClientsResource(AclDAO aclDAO, ClientDAO clientDAO, AuditLog auditLog) {
    this.aclDAO = aclDAO;
    this.clientDAO = clientDAO;
    this.auditLog = auditLog;
  }

  /**
   * Retrieve Client by a specified name, or all Clients if no name given
   *
   * @param user the admin user retrieving this client
   * @param name the name of the Client to retrieve, if provided
   * @return the named client, or all clients if no name is given
   * <p>
   * description Returns a single Client or a set of all Clients for this user. Used by Keywhiz CLI
   * and the web ui.
   * <p>
   * responseMessage 200 Found and retrieved Client(s)
   * <p>
   * responseMessage 404 Client with given name not found (if name provided)
   */
  @Timed @ExceptionMetered
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
   * @param user                the admin user creating this client
   * @param createClientRequest the JSON client request used to formulate the Client
   * @return 200 if the client is created successfully, 409 if it already exists
   * <p>
   * description Creates a Client with the name from a valid client request. Used by Keywhiz CLI and
   * the web ui.
   * <p>
   * responseMessage 200 Successfully created Client
   * <p>
   * responseMessage 409 Client with given name already exists
   */
  @Timed @ExceptionMetered
  @POST
  @Consumes(APPLICATION_JSON)
  public Response createClient(@Auth User user,
      @Valid CreateClientRequestV2 createClientRequest) {

    logger.info("User '{}' creating client '{}'.", user, createClientRequest.name());

    long clientId;
    try {
      clientId = clientDAO.createClient(createClientRequest.name(), user.getName(),
          createClientRequest.description(), createClientRequest.spiffeId());
    } catch (DataAccessException e) {
      logger.warn("Cannot create client {}: {}", createClientRequest.name(), e);
      throw new ConflictException("Conflict creating client.");
    }

    URI uri = UriBuilder.fromResource(ClientsResource.class).path("{clientId}").build(clientId);
    Response response = Response
        .created(uri)
        .entity(clientDetailResponseFromId(clientId))
        .build();
    if (response.getStatus() == HttpStatus.SC_CREATED) {
      auditLog.recordEvent(new Event(Instant.now(), EventTag.CLIENT_CREATE, user.getName(),
          createClientRequest.name()));
    }
    return response;
  }

  /**
   * Retrieve Client by ID
   *
   * @param user     the admin user retrieving this client
   * @param clientId the ID of the Client to retrieve
   * @return the specified client if found
   * <p>
   * description Returns a single Client if found. Used by Keywhiz CLI and the web ui.
   * <p>
   * responseMessage 200 Found and retrieved Client with given ID
   * <p>
   * responseMessage 404 Client with given ID not Found
   */
  @Path("{clientId}")
  @Timed @ExceptionMetered
  @GET
  public ClientDetailResponse getClient(@Auth User user,
      @PathParam("clientId") LongParam clientId) {
    logger.info("User '{}' retrieving client id={}.", user, clientId);
    return clientDetailResponseFromId(clientId.get());
  }

  /**
   * Delete Client by ID
   *
   * @param user     the admin user deleting this client
   * @param clientId the ID of the Client to be deleted
   * @return 200 if the deletion was successful, 404 if the client was not found
   * <p>
   * description Deletes a single Client if found. Used by Keywhiz CLI and the web ui.
   * <p>
   * responseMessage 200 Found and deleted Client with given ID
   * <p>
   * responseMessage 404 Client with given ID not Found
   */
  @Path("{clientId}")
  @Timed @ExceptionMetered
  @DELETE
  public Response deleteClient(@Auth User user, @PathParam("clientId") LongParam clientId) {
    logger.info("User '{}' deleting client id={}.", user, clientId);

    Optional<Client> client = clientDAO.getClientById(clientId.get());
    if (!client.isPresent()) {
      throw new NotFoundException("Client not found.");
    }

    clientDAO.deleteClient(client.get());

    auditLog.recordEvent(
        new Event(Instant.now(), EventTag.CLIENT_DELETE, user.getName(), client.get().getName()));

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
