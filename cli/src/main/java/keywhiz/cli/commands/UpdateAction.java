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

package keywhiz.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import keywhiz.cli.configs.AddOrUpdateActionConfig;
import keywhiz.cli.configs.UpdateActionConfig;
import keywhiz.client.KeywhizClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;
import static keywhiz.cli.Utilities.VALID_NAME_PATTERN;
import static keywhiz.cli.Utilities.validName;

public class UpdateAction implements Runnable {
  private static final Logger logger = LoggerFactory.getLogger(UpdateAction.class);

  private final UpdateActionConfig config;
  private final KeywhizClient keywhizClient;
  private final ObjectMapper mapper;

  InputStream stream = System.in;

  public UpdateAction(UpdateActionConfig config, KeywhizClient client,
      ObjectMapper mapper) {
    this.config = config;
    this.keywhizClient = client;
    this.mapper = mapper;
  }

  @Override public void run() {
    String secretName = config.name;

    if (secretName == null || !validName(secretName)) {
      throw new IllegalArgumentException(format("Invalid name, must match %s", VALID_NAME_PATTERN));
    }

    byte[] content = {};
    if (config.contentProvided) {
      content = readSecretContent();
    }
    partialUpdateSecret(secretName, content, config);

    // If it appears that content was piped in but --content was not specified, print a warning
    if (!config.contentProvided) {
      try {
        InputStreamReader reader = new InputStreamReader(stream, "UTF-8");
        if (reader.ready()) {
          System.out.println("\nWarning: Specify the --content flag to update a secret's content.");
          System.out.println("The secret has not been updated with any provided content.");
        }
      } catch (IOException e) {
        logger.warn("Unexpected error trying to create an InputStreamReader for stdin: '{}'", e.getMessage());
      }
    }
  }

  private void partialUpdateSecret(String secretName, byte[] content,
      AddOrUpdateActionConfig config) {
    try {
      keywhizClient.updateSecret(secretName, config.description != null,
          config.getDescription(), content.length > 0, content, config.json != null,
          config.getMetadata(mapper), config.expiry != null, config.getExpiry());
      logger.info("partialUpdate secret '{}'.", secretName);
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }

  private byte[] readSecretContent() {
    try {
      return ByteStreams.toByteArray(stream);
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }
}
