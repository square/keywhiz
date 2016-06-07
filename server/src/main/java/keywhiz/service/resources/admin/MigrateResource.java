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
import com.google.common.collect.ImmutableList;
import keywhiz.api.model.SecretSeriesAndContent;
import keywhiz.service.daos.SecretDAO;
import keywhiz.service.daos.SecretSeriesDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * @resourcePath /admin/migrate
 * @resourceDescription Migrate data
 */
@Path("/admin/migrate")
@Produces(APPLICATION_JSON)
public class MigrateResource {
  private static final Logger logger = LoggerFactory.getLogger(MigrateResource.class);

  private final SecretDAO secretDAO;
  private final SecretSeriesDAO secretSeriesDAO;

  @Inject public MigrateResource(SecretDAO.SecretDAOFactory secretDAOFactory,
                                 SecretSeriesDAO.SecretSeriesDAOFactory secretSeriesDAOFactory) {
    this.secretDAO = secretDAOFactory.readwrite();
    this.secretSeriesDAO = secretSeriesDAOFactory.readwrite();
  }

  /**
   * Attempts to set current for every secret between min and max.
   * Only sets current if the follow conditions are met:
   * 1. current is not set (i.e. won't overwrite data)
   * 2. there is only one secret content (i.e. it's unambiguous)
   */
  @Timed @ExceptionMetered
  @POST
  @Path("current")
  public Response setCurrent(@QueryParam("min") long min, @QueryParam("max") long max) {
    Map<Long, String> result = new HashMap<>();

    logger.info(format("setCurrent called with min: %d, max: %d", min, max));

    for (long i = min; i <= max; i++) {
      String r = doSetCurrentVersion(i);
      logger.info(format("setCurrent: %d %s", i, r));
      result.put(i, r);
    }
    return Response.ok().entity(result).build();
  }

  private String doSetCurrentVersion(long secretId) {
    ImmutableList<SecretSeriesAndContent> secrets = secretDAO.getSecretsById(secretId);
    if (secrets.isEmpty()) {
      return "not found";
    }
    if (secrets.size() > 1) {
      return format("multiple secrets found: %d", secrets.size());
    }
    SecretSeriesAndContent content = secrets.get(0);
    if (content.series().currentVersion().isPresent()) {
      return "already set";
    }
    secretSeriesDAO.setCurrentVersion(secretId, content.content().id());
    return format("set to %d", content.content().id());
  }

  /**
   * Validates
   */
  @Timed @ExceptionMetered
  @GET
  @Path("current")
  public Response verifyCurrent(@QueryParam("min") long min, @QueryParam("max") long max) {
    Map<Long, String> result = new HashMap<>();

    for (long i = min; i <= max; i++) {
      String r = doVerifyCurrent(i);
      logger.info(format("checkCurrent: %d %s", i, r));
      result.put(i, r);
    }
    return Response.ok().entity(result).build();
  }

  private String doVerifyCurrent(long secretId) {
    ImmutableList<SecretSeriesAndContent> secrets = secretDAO.getSecretsById(secretId);
    if (secrets.isEmpty()) {
      return "not found";
    }
    if (secrets.size() > 1) {
      return format("multiple secrets found: %d", secrets.size());
    }
    SecretSeriesAndContent content = secrets.get(0);
    if (!content.series().currentVersion().isPresent()) {
      return "not set";
    }
    if (content.series().currentVersion().get() != content.content().id()) {
      return format("invalid, expecting %d but found %d", content.content().id(), content.series().currentVersion().get());
    }
    return "ok";
  }

}
