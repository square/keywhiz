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

import com.google.common.base.Throwables;
import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.cli.configs.AssignActionConfig;
import keywhiz.client.KeywhizClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;
import static keywhiz.api.model.Secret.splitNameAndVersion;
import static keywhiz.cli.Utilities.VALID_NAME_PATTERN;
import static keywhiz.cli.Utilities.validName;

public class AssignAction implements Runnable {
  private static final Logger logger = LoggerFactory.getLogger(AssignAction.class);

  private final AssignActionConfig assignActionConfig;
  private final KeywhizClient keywhizClient;

  public AssignAction(AssignActionConfig assignActionConfig, KeywhizClient client) {
    this.assignActionConfig = assignActionConfig;
    this.keywhizClient = client;
  }

  @Override public void run() {
    List<String> assignType = assignActionConfig.assignType;

    if (assignType == null || assignType.isEmpty()) {
      throw new IllegalArgumentException("Must specify a single type to assign.");
    }

    if (assignActionConfig.name == null || !validName(assignActionConfig.name) ||
        assignActionConfig.group == null || !validName(assignActionConfig.group)) {
      throw new IllegalArgumentException(format("Invalid name, must match %s", VALID_NAME_PATTERN));
    }
    Group group;
    try {
      group = keywhizClient.getGroupByName(assignActionConfig.group);
    } catch (KeywhizClient.NotFoundException e) {
      throw new AssertionError("Group doesn't exist.");
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }

    String type = assignType.get(0).toLowerCase().trim();
    switch (type) {
      case "client":
        Client client = null;
        boolean createClient = false;
        try {
          client = keywhizClient.getClientByName(assignActionConfig.name);
        } catch (KeywhizClient.NotFoundException e) {
          logger.info("Creating client '{}'.", assignActionConfig.name);
          createClient = true;
        } catch (IOException e) {
          throw Throwables.propagate(e);
        }

        if (createClient) {
          try {
            keywhizClient.createClient(assignActionConfig.name);
            client = keywhizClient.getClientByName(assignActionConfig.name);
          } catch (IOException e) {
            throw Throwables.propagate(e);
          }
        }

        try {
          if (keywhizClient.groupDetailsForId(Math.toIntExact(group.getId())).getClients().contains(client)) {
            throw new AssertionError(
                format("Client '%s' already assigned to group '%s'", assignActionConfig.name,
                    group.getName()));
          }
          logger.info("Enrolling client '{}' in group '{}'.", client.getName(), group.getName());
          keywhizClient.enrollClientInGroupByIds(Math.toIntExact(client.getId()), Math.toIntExact(group.getId()));
        } catch (IOException e) {
          throw Throwables.propagate(e);
        }
        break;

      case "secret":

        try {
          int groupId = Math.toIntExact(group.getId());
          String[] parts = splitNameAndVersion(assignActionConfig.name);
          SanitizedSecret sanitizedSecret =
                keywhizClient.getSanitizedSecretByNameAndVersion(parts[0], parts[1]);
          if (keywhizClient.groupDetailsForId(groupId).getSecrets().contains(sanitizedSecret)) {
            throw new AssertionError(
                format("Secret '%s' already assigned to group '%s'", assignActionConfig.name,
                    group.getName()));
          }
          logger.info("Allowing group '{}' access to secret '{}'.", group.getName(), sanitizedSecret.name());
          keywhizClient.grantSecretToGroupByIds(Math.toIntExact(sanitizedSecret.id()), groupId);
        } catch (KeywhizClient.NotFoundException e) {
          throw new AssertionError("Secret doesn't exist.");
        } catch (ParseException e) {
          throw new IllegalArgumentException(e);
        } catch (IOException e) {
          throw Throwables.propagate(e);
        }
        break;

      default:
        throw new IllegalArgumentException("Invalid assign type specified: " + type);
    }
  }
}
