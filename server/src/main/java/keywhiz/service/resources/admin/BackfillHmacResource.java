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
import java.util.Optional;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import keywhiz.api.model.SecretContent;
import keywhiz.api.model.SecretSeriesAndContent;
import keywhiz.service.crypto.ContentCryptographer;
import keywhiz.service.daos.SecretDAO;
import keywhiz.service.daos.SecretSeriesDAO;
import keywhiz.service.daos.SecretSeriesDAO.SecretSeriesDAOFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * @resourcePath /admin/hmac
 * @resourceDescription Fill in HMAC column in databases
 */
@Path("/admin/hmac")
@Produces(APPLICATION_JSON)
public class BackfillHmacResource {
  private static final Logger logger = LoggerFactory.getLogger(BackfillHmacResource.class);

  private final SecretDAO secretDAO;
  private final SecretSeriesDAO secretSeriesDAO;
  private final ContentCryptographer cryptographer;

  @Inject public BackfillHmacResource(SecretDAO secretDAO, SecretSeriesDAOFactory secretSeriesDAOFactory, ContentCryptographer cryptographer) {
    this.secretDAO = secretDAO;
    this.secretSeriesDAO = secretSeriesDAOFactory.readwrite();
    this.cryptographer = cryptographer;
  }

  /**
   * Fills in the HMAC column on a given secret ID; returns true if the operation was
   * successful (the HMAC was filled in by this call or the HMAC was already present)
   */
  @Timed @ExceptionMetered
  @GET
  @Path("backfillhmac")
  public Response backfillHmac(@QueryParam("id") long id) {
    boolean success = _backfillHmac(id);
    return Response.ok().entity(success).build();
  }

  protected boolean _backfillHmac(long id) {
    Optional<SecretSeriesAndContent> secret = secretDAO.getSecretById(id);

    if (!secret.isPresent()) {
      return false;
    }
    logger.info("backfill-hmac {}: processing secret", id);

    SecretContent secretContent = secret.get().content();
    if (!secretContent.hmac().isEmpty()) {
      return true; // No need to backfill
    }
    String hmac = cryptographer.computeHmac(cryptographer.decrypt(secretContent.encryptedContent()).getBytes(UTF_8));
    return secretSeriesDAO.setHmac(secretContent.id(), hmac) == 1; // We expect only one row to be changed
  }
}