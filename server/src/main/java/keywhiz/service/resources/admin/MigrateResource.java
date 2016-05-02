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
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import keywhiz.api.model.Secret;
import keywhiz.service.daos.SecretController;
import keywhiz.utility.ReplaceIntermediateCertificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * @resourcePath /admin/migrate
 * @resourceDescription Migrate data
 */
@Path("/admin/migrate")
@Produces(APPLICATION_JSON)
public class MigrateResource {
  private static final Logger logger = LoggerFactory.getLogger(MigrateResource.class);

  private final SecretController secretController;
  private final ReplaceIntermediateCertificate replaceIntermediateCertificate;

  @Inject public MigrateResource(SecretController secretController,
      ReplaceIntermediateCertificate replaceIntermediateCertificate) {
    this.secretController = secretController;
    this.replaceIntermediateCertificate = replaceIntermediateCertificate;
  }

  /**
   * Calls ReplaceIntermediateCertificate on a given secret id.
   */
  @Timed @ExceptionMetered
  @GET
  @Path("replace-intermediate-certificate")
  public Response replaceIntermediateCertificate(@QueryParam("id") long id) {
    List<String> updates = _replaceIntermediateCertificate(id);
    return Response.ok().entity(updates).build();
  }

  protected List<String> _replaceIntermediateCertificate(long id) {
    List<Secret> secrets = secretController.getSecretsById(id);
    List<String> updates = new ArrayList<>();
    logger.info("replace-intermediate-certificate %d: processing %d secrets", id, secrets.size());
    ReplaceIntermediateCertificate.KeyStoreType type;

    for (Secret secret : secrets) {
      // secret.getName() is constant for all the values of a secret. It's however easier to
      // map the name to the keystore type inside this loop than outside.
      if (secret.getName().endsWith(".crt")) {
        type = ReplaceIntermediateCertificate.KeyStoreType.PEM;
      } else if (secret.getName().endsWith(".jceks")) {
        type = ReplaceIntermediateCertificate.KeyStoreType.JCEKS;
      } else if (secret.getName().endsWith(".p12")) {
        type = ReplaceIntermediateCertificate.KeyStoreType.P12;
      } else {
        updates.add("not a keystore");
        continue;
      }
      try {
        logger.info("replace-intermediate-certificate %d: processing %s", id, secret.getVersion());
        String newData = replaceIntermediateCertificate.process(secret.getSecret(), type);
        if (newData != null) {
          logger.info("replace-intermediate-certificate %d: updating %s", id, secret.getVersion());
          updates.add(String.valueOf(secretController.update(secret, newData)));
        } else {
          updates.add("no match");
        }
      } catch (IOException | NoSuchAlgorithmException | CertificateException | KeyStoreException | UnrecoverableEntryException e) {
        logger.warn("replace-intermediate-certificate %d: failed %s", id, secret.getVersion(), e);
        updates.add("exception");
      }
    }
    return updates;
  }
}
