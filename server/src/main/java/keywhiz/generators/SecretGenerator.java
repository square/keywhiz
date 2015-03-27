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

package keywhiz.generators;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.List;
import javax.ws.rs.BadRequestException;
import keywhiz.api.model.Secret;
import keywhiz.service.daos.SecretController;
import keywhiz.service.resources.SecretGeneratorsResource;

/**
 * A SecretGenerator is used to generate 1 or more of Secrets based on some provided parameters
 * defined on a per-implementation basis.
 *
 * See {@link TemplatedSecretGenerator} for an example.
 * See {@link SecretGeneratorsResource} to see how this interfaces with the HTTP API.
 */
public abstract class SecretGenerator<RequestType> {
  protected final SecretController secretController;

  public static final int MAX_TEMPLATE_BATCH_SIZE = 100;

  @Inject public SecretGenerator(SecretController secretController) {
    this.secretController = secretController;
  }

  /**
   * Process an entire batch of generator requests - if any of them fail, any secrets created will be
   * rolled back.
   *
   *
   * @param creatorName the name to be recorded as the creator of the secrets
   * @param requests the batch of generator requests
   * @return the list of secrets created
   */
  // TODO(jlfwong): Remove batch generation as a feature of all generators - it should be a
  // generator of its own that just has a different RequestType
  public List<Secret> batchGenerate(String creatorName, List<RequestType> requests) {
    if (requests.size() > MAX_TEMPLATE_BATCH_SIZE) {
      throw new BadRequestException("Batch size too big.");
    }

    ImmutableList.Builder<Secret> secrets = ImmutableList.builder();

    // TODO(jlfwong): An outer transaction causes test failures. This is a result of JDBI not
    // handling nested transactions in the intuitive way. There's no particularly good solution at
    // the moment that I'm aware of. A future release should have something called
    // "CommutingTransactionManager" which might fix this problem.
    for (RequestType request : requests) {
      secrets.addAll(generate(creatorName, request));
    }

    return secrets.build();
  }

  // TODO(jlfwong): Add support for Client generated secrets on top of User generated ones. This will
  // come with adding an automation secret generation endpoint.

  /**
   * Process a single generator request, creating one or more secrets.
   *
   *
   * @param creatorName the name to be recorded as the creator of the secrets
   * @param request the batch of generator requests
   * @return the list of secrets created
   */
  public abstract List<Secret> generate(String creatorName, RequestType request);

  // TODO(jlfwong): There should be a nicer way of doing by just returning RequestType.class or something
  // equivalent
  public abstract Class<RequestType> getRequestType();
}

