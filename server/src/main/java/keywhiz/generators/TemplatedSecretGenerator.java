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
import com.google.common.collect.ImmutableMap;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import keywhiz.api.TemplatedSecretsGeneratorRequest;
import keywhiz.api.model.Secret;
import keywhiz.api.model.VersionGenerator;
import keywhiz.service.daos.SecretController;
import keywhiz.utility.SecretTemplateCompiler;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * SecretGenerator for creating templated secrets using the SecretTemplateCompiler.
 */
public class TemplatedSecretGenerator extends SecretGenerator<TemplatedSecretsGeneratorRequest> {
  private static final Logger logger = LoggerFactory.getLogger(TemplatedSecretGenerator.class);

  private final SecureRandom secureRandom;

  @Inject
  public TemplatedSecretGenerator(SecretController secretController,SecureRandom secureRandom) {
    super(secretController);
    this.secureRandom = secureRandom;
  }

  @Override public List<Secret> generate(String creatorName, TemplatedSecretsGeneratorRequest request)
      throws BadRequestException {

    String secretName = request.getName();
    String secretContent;

    try {
      secretContent = Base64.getEncoder().encodeToString(
          new SecretTemplateCompiler(secureRandom)
              .compile(request.getTemplate())
              .getBytes(UTF_8));
    } catch (IllegalArgumentException e) {
      logger.warn("Cannot compile template {}: {}", request.getTemplate(), e);
      throw new BadRequestException("Cannot compile secret template.");
    }

    logger.info("User '{}' creating templated secret '{}' {} versioning.",
        creatorName, request.getName(), request.isWithVersion() ? "with" : "without");

    SecretController.SecretBuilder builder =
        secretController.builder(secretName, secretContent, creatorName)
        .withDescription(request.getDescription().orElse(""))
        .withMetadata(request.getMetadata())
        .withType("templated")
        .withGenerationOptions(ImmutableMap.of("template", request.getTemplate()));

    if (request.isWithVersion()) {
      builder.withVersion(VersionGenerator.now().toHex());
    }

    Secret secret;
    try {
      secret = builder.build();
    } catch (DataAccessException e) {
      logger.warn("Cannot create secret {}: {}", secretName, e);
      throw new BadRequestException(String.format("Cannot create secret '%s'.", secretName));
    }

    return ImmutableList.of(secret);
  }

  @Override public Class<TemplatedSecretsGeneratorRequest> getRequestType() {
    return TemplatedSecretsGeneratorRequest.class;
  }
}
