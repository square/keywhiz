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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.cli.configs.DeleteActionConfig;
import keywhiz.client.KeywhizClient;
import keywhiz.client.KeywhizClient.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static keywhiz.cli.Utilities.VALID_NAME_PATTERN;
import static keywhiz.cli.Utilities.validName;

public class DeleteAction implements Runnable {
  private static final Logger logger = LoggerFactory.getLogger(DeleteAction.class);

  private final DeleteActionConfig deleteActionConfig;
  private final KeywhizClient keywhizClient;

  @VisibleForTesting
  InputStream inputStream = System.in;

  public DeleteAction(DeleteActionConfig deleteActionConfig, KeywhizClient client) {
    this.deleteActionConfig = deleteActionConfig;
    this.keywhizClient = client;
  }

  @Override public void run() {
    List<String> type = deleteActionConfig.deleteType;

    if (type == null || type.isEmpty()) {
      throw new IllegalArgumentException("Must specify a single type to delete.");
    }

    if (deleteActionConfig.name == null || !validName(deleteActionConfig.name)) {
      throw new IllegalArgumentException(format("Invalid name, must match %s", VALID_NAME_PATTERN));
    }

    String firstType = type.get(0).toLowerCase().trim();
    switch (firstType) {
      case "group":
        try {
          Group group = keywhizClient.getGroupByName(deleteActionConfig.name);
          logger.info("Deleting group '{}'.", group.getName());
          keywhizClient.deleteGroupWithId(group.getId());
        } catch (NotFoundException e) {
          throw new AssertionError("Group does not exist.");
        } catch (IOException e) {
          throw Throwables.propagate(e);
        }
        break;

      case "client":
        try {
          Client client = keywhizClient.getClientByName(deleteActionConfig.name);
          logger.info("Deleting client '{}'.", client.getName());
          keywhizClient.deleteClientWithId(client.getId());
        } catch (NotFoundException e) {
          throw new AssertionError("Client does not exist.");
        } catch (IOException e) {
          throw Throwables.propagate(e);
        }
        break;

      case "secret":
        try {
          SanitizedSecret sanitizedSecret =
              keywhizClient.getSanitizedSecretByNameAndVersion(deleteActionConfig.name, "");
          BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, UTF_8));
          while (true) {
            System.out.println(
                format("Please confirm deletion of secret '%s': Y/N", sanitizedSecret.name()));
            String line = reader.readLine();

            if (line == null /* EOF */ || line.toUpperCase().startsWith("N")) {
              return;
            } else if (line.toUpperCase().startsWith("Y")) {
              logger.info("Deleting secret '{}'.", sanitizedSecret.name());
              keywhizClient.deleteSecretWithId(sanitizedSecret.id());
              return;
            } // else loop again
          }
        } catch (NotFoundException e) {
          throw new AssertionError("Secret does not exist: " + deleteActionConfig.name);
        } catch (IOException e) {
          throw new AssertionError(e);
        }

      default:
        throw new IllegalArgumentException("Invalid delete type specified: " + type);
    }
  }
}