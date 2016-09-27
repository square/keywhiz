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

import com.fasterxml.jackson.core.type.TypeReference;
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
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;
import static keywhiz.cli.Utilities.VALID_NAME_PATTERN;
import static keywhiz.cli.Utilities.validName;

public class AddAction implements Runnable {
  private static final Logger logger = LoggerFactory.getLogger(AddAction.class);

  private final AddActionConfig addActionConfig;
  private final KeywhizClient keywhizClient;
  private final ObjectMapper mapper;

  InputStream stream = System.in;

  public AddAction(AddActionConfig addActionConfig, KeywhizClient client, ObjectMapper mapper) {
    this.addActionConfig = addActionConfig;
    this.keywhizClient = client;
    this.mapper = mapper;
  }

  @Override public void run() {
    List<String> types = addActionConfig.addType;

    if (types == null || types.isEmpty()) {
      throw new IllegalArgumentException("Must specify a single type to add.");
    }

    String firstType = types.get(0).toLowerCase().trim();
    String name = addActionConfig.name;

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
          keywhizClient.createGroup(name, null, null);
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
        ImmutableMap<String, String> metadata = getMetadata();

        createAndAssignSecret(name, content, metadata, getExpiry());
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
          keywhizClient.createClient(name);
          logger.info("Creating client '{}'.", name);
        } catch (IOException e) {
          throw Throwables.propagate(e);
        }
        break;

      default:
        throw new AssertionError("Invalid add type specified: " + firstType);
    }
  }

  private void createAndAssignSecret(String secretName, byte[] content,
      ImmutableMap<String, String> metadata, long expiry) {
    try {
      SecretDetailResponse secretResponse = keywhizClient.createSecret(secretName, "", content, metadata, expiry);
      long secretId = secretResponse.id;

      logger.info("Creating secret '{}'.", secretName);

      // Optionally, also assign
      if (addActionConfig.group != null) {
        assignSecret(secretId, secretName);
      }
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }

  private ImmutableMap<String, String> getMetadata() {
    ImmutableMap<String, String> metadata = ImmutableMap.of();
    String jsonBlob = addActionConfig.json;
    if (jsonBlob != null && !jsonBlob.isEmpty()) {
      TypeReference typeRef = new TypeReference<ImmutableMap<String, String>>() {};
      try {
        metadata = mapper.readValue(jsonBlob, typeRef);
      } catch (IOException e) {
        throw Throwables.propagate(e);
      }
      validateMetadata(metadata);
    }
    return metadata;
  }

  private long getExpiry() {
    String expiry = addActionConfig.expiry;
    if (expiry != null) {
      try {
        return Long.parseLong(expiry);
      } catch (NumberFormatException e) {
      }
      DateTime dt = new DateTime(expiry);
      return dt.getMillis()/1000;
    }
    return 0;
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

  private static void validateMetadata(ImmutableMap<String, String> metadata) {
    for (ImmutableMap.Entry<String, String> entry : metadata.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();

      // We want to perform strong validation of the metadata to make sure it is well formed.
      if (!key.matches("(owner|group|mode)")) {
        if(!key.startsWith("_")) {
          throw new IllegalArgumentException(
              format("Illegal metadata key %s: custom metadata keys must start with an underscore", key));
        }
        if(!key.matches("^[a-zA-Z_0-9\\-.:]+$")) {
          throw new IllegalArgumentException(
              format("Illegal metadata key %s: metadata keys can only contain: a-z A-Z 0-9 _ - . :", key));
        }
      }

      if (key.equals("mode") && !value.matches("0[0-7]+")) {
        throw new IllegalArgumentException(format("mode %s is not proper octal", value));
      }
    }
  }

  private void assignSecret(long secretId, String secretDisplayName) {
    try {
      Group group = keywhizClient.getGroupByName(addActionConfig.group);
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
