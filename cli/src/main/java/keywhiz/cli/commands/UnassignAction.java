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
import java.util.List;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.cli.configs.UnassignActionConfig;
import keywhiz.client.KeywhizClient;
import keywhiz.client.KeywhizClient.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;
import static keywhiz.cli.Utilities.VALID_NAME_PATTERN;
import static keywhiz.cli.Utilities.validName;

public class UnassignAction implements Runnable {
  private static final Logger logger = LoggerFactory.getLogger(UnassignAction.class);

  private final UnassignActionConfig unassignActionConfig;
  private final KeywhizClient keywhizClient;

  public UnassignAction(UnassignActionConfig unassignActionConfig, KeywhizClient client) {
    this.unassignActionConfig = unassignActionConfig;
    this.keywhizClient = client;
  }

  @Override public void run() {
    List<String> unassignType = unassignActionConfig.unassignType;

    if (unassignType == null || unassignType.isEmpty()) {
      throw new IllegalArgumentException("Must specify a single type to unassign.");
    }

    if (unassignActionConfig.name == null || !validName(unassignActionConfig.name) ||
        unassignActionConfig.group == null || !validName(unassignActionConfig.group)) {
      throw new IllegalArgumentException(format("Invalid name, must match %s", VALID_NAME_PATTERN));
    }
    Group group;
    try {
      group = keywhizClient.getGroupByName(unassignActionConfig.group);
      if (group == null) {
        throw new AssertionError("Group doesn't exist.");
      }
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }

    String firstType = unassignType.get(0).toLowerCase().trim();

    switch (firstType) {
      case "client":

        try {
          Client client = keywhizClient.getClientByName(unassignActionConfig.name);
          if (!keywhizClient.groupDetailsForId(group.getId()).getClients().contains(client)) {
            throw new AssertionError(
                format("Client '%s' not assigned to group '%s'.", unassignActionConfig.name,
                    group));
          }

          logger.info("Evicting client '{}' from group '{}'.", client.getName(), group.getName());
          keywhizClient.evictClientFromGroupByIds(client.getId(), group.getId());
        } catch (NotFoundException e) {
          throw new AssertionError("Client or group doesn't exist.");
        } catch (IOException e) {
          throw Throwables.propagate(e);
        }
        break;

      case "secret":
        try {
          long groupId = group.getId();
          SanitizedSecret sanitizedSecret =
              keywhizClient.getSanitizedSecretByNameAndVersion(unassignActionConfig.name, "");
          if (!keywhizClient.groupDetailsForId(groupId).getSecrets().contains(sanitizedSecret)) {
            throw new AssertionError(
                format("Secret '%s' not assigned to group '%s'", unassignActionConfig.name, group));
          }
          logger.info("Revoke group '{}' access to secret '{}'.", group.getName(),
              SanitizedSecret.displayName(sanitizedSecret));
          keywhizClient.revokeSecretFromGroupByIds(sanitizedSecret.id(), groupId);
        } catch (NotFoundException e) {
          throw new AssertionError("Secret or group doesn't exist.");
        } catch (IOException e) {
          throw Throwables.propagate(e);
        }
        break;

      default:
        throw new IllegalArgumentException("Invalid unassign type specified: " + firstType);
    }
  }
}
