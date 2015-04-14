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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.auth.Auth;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import keywhiz.api.model.AutomationClient;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.generators.SecretGenerator;
import keywhiz.service.exceptions.UnprocessableEntityException;

/**
 * @parentEndpointName secrets-generator-automation
 *
 * @resourceDescription Create secrets with a generator
 */
@Path("/automation/secrets/generators")
@Produces(MediaType.APPLICATION_JSON)
public class AutomationSecretGeneratorsResource {
  private final ObjectMapper mapper;
  private final Map<String, SecretGenerator> generatorMap;

  @Inject
  public AutomationSecretGeneratorsResource(ObjectMapper mapper, Map<String, SecretGenerator> generatorMap) {
    this.mapper = mapper;
    this.generatorMap = generatorMap;
  }

  /**
   * Generate Secrets from a single generator request
   *
   * @param generatorName the name of the generator to use for creating secrets
   *
   * @description Creates Secrets from a single generator request, using the specified generator
   * @responseMessage 200 Successfully generated secrets according to the request
   * @responseMessage 400 Malformed or incorrect request parameters provided
   * @responseMessage 422 Request was formed correctly but was semantically incorrect
   */
  @Path("{generatorName}")
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public List<SanitizedSecret> generate(@Auth AutomationClient client,
      @PathParam("generatorName") String generatorName, String requestBody) {
    SecretGenerator generator = getGeneratorOrThrow(generatorName);

    Object requestParams;
    try {
      requestParams = mapper.readValue(requestBody, generator.getRequestType());
    } catch (IOException e) {
      throw new UnprocessableEntityException(e.getMessage());
    }

    return SanitizedSecret.fromSecrets(generator.generate(client.getName(), requestParams));
  }

  /**
   * Generate Secrets from a batch of generator requests
   *
   * @param generatorName the name of the generator to use for creating secrets
   *
   * @description Creates Secrets from a batch of generator requests, using the specified generator
   * @responseMessage 200 Successfully generated secrets according to the request
   * @responseMessage 400 Malformed or incorrect request parameters provided
   * @responseMessage 422 Request was formed correctly but was semantically incorrect, batch may have been empty
   */
  @Path("{generatorName}/batch")
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public List<SanitizedSecret> batchGenerate(@Auth AutomationClient client,
      @PathParam("generatorName") String generatorName, String requestBody) {
    SecretGenerator generator = getGeneratorOrThrow(generatorName);

    List<Object> requestParams;
    try {
      requestParams = mapper.readValue(requestBody,
          mapper.getTypeFactory().constructCollectionType(List.class, generator.getRequestType()));
    } catch (IOException e) {
      throw new UnprocessableEntityException(e.getMessage());
    }

    if (requestParams.isEmpty()) {
      throw new BadRequestException("Batch was empty.");
    }

    return SanitizedSecret.fromSecrets(generator.batchGenerate(client.getName(), requestParams));
  }

  private SecretGenerator getGeneratorOrThrow(String generatorName) {
    if (!generatorMap.containsKey(generatorName)) {
      throw new NotFoundException();
    }
    return generatorMap.get(generatorName);
  }
}
