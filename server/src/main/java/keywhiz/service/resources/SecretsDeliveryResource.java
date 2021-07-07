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

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.annotations.VisibleForTesting;
import io.dropwizard.auth.Auth;
import java.util.List;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import keywhiz.api.SecretDeliveryResponse;
import keywhiz.api.model.Client;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.service.daos.AclDAO;
import keywhiz.service.daos.AclDAO.AclDAOFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static keywhiz.Tracing.setTag;

/**
 * parentEndpointName secrets
 *
 * resourceDescription Retrieve a collection of Secrets
 */
@Path("/secrets")
@Produces(APPLICATION_JSON)
public class SecretsDeliveryResource {
  private static final Logger logger = LoggerFactory.getLogger(SecretsDeliveryResource.class);

  private final AclDAO aclDAO;

  @Inject public SecretsDeliveryResource(AclDAOFactory aclDAOFactory) {
    this.aclDAO = aclDAOFactory.readonly();
  }

  @VisibleForTesting SecretsDeliveryResource(AclDAO aclDAO) {
    this.aclDAO = aclDAO;
  }

  /**
   * @param client the client performing the retrieval
   * @return all secrets available to the input client
   */
  @Timed @ExceptionMetered
  @GET
  public List<SecretDeliveryResponse> getSecrets(@Auth Client client) {
    logger.info("Client {} listed available secrets.", client.getName());
    List<SecretDeliveryResponse> secrets = aclDAO.getSanitizedSecretsFor(client).stream()
        .map(SecretDeliveryResponse::fromSanitizedSecret)
        .collect(toList());
    setTag("nSecrets", secrets.size());
    return secrets;
  }
}
