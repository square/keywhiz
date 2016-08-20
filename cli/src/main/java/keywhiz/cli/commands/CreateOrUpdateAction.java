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
import keywhiz.cli.configs.CreateOrUpdateActionConfig;
import keywhiz.client.KeywhizClient;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

import static java.lang.String.format;
import static keywhiz.cli.Utilities.VALID_NAME_PATTERN;
import static keywhiz.cli.Utilities.validName;

public class CreateOrUpdateAction implements Runnable {
  private static final Logger logger = LoggerFactory.getLogger(CreateOrUpdateAction.class);

  private final CreateOrUpdateActionConfig createOrUpdateActionConfig;
  private final KeywhizClient keywhizClient;
  private final ObjectMapper mapper;

  InputStream stream = System.in;

  public CreateOrUpdateAction(CreateOrUpdateActionConfig createOrUpdateActionConfig, KeywhizClient client, ObjectMapper mapper) {
    this.createOrUpdateActionConfig = createOrUpdateActionConfig;
    this.keywhizClient = client;
    this.mapper = mapper;
  }

  @Override public void run() {
    String secretName = createOrUpdateActionConfig.secretName;

    if (secretName == null || !validName(secretName)) {
      throw new IllegalArgumentException(format("Invalid name, must match %s", VALID_NAME_PATTERN));
    }

    byte[] content = readSecretContent();
    createOrUpdateSecret(secretName, content, getMetadata(), getExpiry());
  }

  private void createOrUpdateSecret(String secretName, byte[] content,
      ImmutableMap<String, String> metadata, long expiry) {
    try {
      keywhizClient.createOrUpdateSecret(secretName, "", content, metadata, expiry);
      logger.info("createOrUpdate secret '{}'.", secretName);
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }

  private ImmutableMap<String, String> getMetadata() {
    ImmutableMap<String, String> metadata = ImmutableMap.of();
    String jsonBlob = createOrUpdateActionConfig.json;
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
    String expiry = createOrUpdateActionConfig.expiry;
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
        if(!key.matches("^[a-zA-Z0-9_]*$")) {
          throw new IllegalArgumentException(
              format("Illegal metadata key %s: metadata keys can only contain letters, numbers, and underscores", key));
        }
      }

      if (key.equals("mode") && !value.matches("0[0-7]+")) {
        throw new IllegalArgumentException(format("mode %s is not proper octal", value));
      }
    }
  }
}
