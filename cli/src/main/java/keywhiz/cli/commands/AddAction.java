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
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import keywhiz.api.SecretDetailResponse;
import keywhiz.api.model.Group;
import keywhiz.cli.configs.AddActionConfig;
import keywhiz.client.KeywhizClient;
import keywhiz.client.KeywhizClient.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;
import static keywhiz.cli.Utilities.VALID_NAME_PATTERN;
import static keywhiz.cli.Utilities.validName;

public class AddAction implements Runnable {
  private static final Logger logger = LoggerFactory.getLogger(AddAction.class);

  private final AddActionConfig config;
  private final KeywhizClient keywhizClient;
  private final ObjectMapper mapper;

  InputStream stream = System.in;

  public AddAction(AddActionConfig config, KeywhizClient client, ObjectMapper mapper) {
    this.config = config;
    this.keywhizClient = client;
    this.mapper = mapper;
  }

  @Override public void run() {
    List<String> types = config.addType;

    if (types == null || types.isEmpty()) {
      throw new IllegalArgumentException("Must specify a single type to add.");
    }

    String firstType = types.get(0).toLowerCase().trim();
    String name = config.name;

    if (name == null || !validName(name)) {
      throw new IllegalArgumentException(format("Invalid name, must match %s", VALID_NAME_PATTERN));
    }

    switch (firstType) {
      case "group":
        try {
          keywhizClient.getGroupByName(name);
          throw new AssertionError("Group already exists.");
        } catch (NotFoundException e) {
          // group does not exist, continue to add it
        } catch (IOException e) {
          throw Throwables.propagate(e);
        }
        try {
          keywhizClient.createGroup(name, config.getDescription(), config.getMetadata(mapper));
          logger.info("Creating group '{}'.", name);
        } catch (IOException e) {
          throw Throwables.propagate(e);
        }
        break;

      case "secret":
        try {
          keywhizClient.getSanitizedSecretByName(name);
          throw new AssertionError("Secret already exists.");
        } catch (NotFoundException e) {
          // secret does not exist, continue to add it
        } catch (IOException e) {
          throw Throwables.propagate(e);
        }
        byte[] content = readSecretContent();
        ImmutableMap<String, String> metadata = config.getMetadata(mapper);

        createAndAssignSecret(name, config.getDescription(), content, metadata, config.getExpiry());
        break;

      case "client":
        try {
          keywhizClient.getClientByName(name);
          throw new AssertionError("Client name already exists.");
        } catch (NotFoundException e) {
          // client does not exist, continue to add it
        } catch (IOException e) {
          throw Throwables.propagate(e);
        }
        try {
          keywhizClient.createClient(name, config.getDescription(), config.getSpiffeId());
          logger.info("Creating client '{}'.", name);
        } catch (IOException e) {
          throw Throwables.propagate(e);
        }
        break;

      default:
        throw new AssertionError("Invalid add type specified: " + firstType);
    }
  }

  private void createAndAssignSecret(String secretName, String description, byte[] content,
      ImmutableMap<String, String> metadata, long expiry) {
    try {
      SecretDetailResponse secretResponse =
          keywhizClient.createSecret(secretName, description, content, metadata, expiry);
      long secretId = secretResponse.id;

      logger.info("Creating secret '{}'.", secretName);

      // Optionally, also assign
      if (config.group != null) {
        assignSecret(secretId, secretName);
      }
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }

  private byte[] readSecretContent() {
    try {
      byte[] content = ByteStreams.toByteArray(stream);
      if (content.length == 0) {
        throw new RuntimeException("Secret content empty!");
      }
      return content;
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }

  private void assignSecret(long secretId, String secretDisplayName) {
    try {
      Group group = keywhizClient.getGroupByName(config.group);
      if (group == null) {
        throw new AssertionError("Group does not exist.");
      }

      logger.info("Allowing group '{}' access to secret '{}'.", group.getName(), secretDisplayName);
      keywhizClient.grantSecretToGroupByIds(secretId, group.getId());
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }
}
