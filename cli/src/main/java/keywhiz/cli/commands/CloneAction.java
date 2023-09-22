/*
 * Copyright (C) 2023 Square, Inc.
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

import keywhiz.api.SecretDetailResponse;
import keywhiz.api.model.Group;
import keywhiz.cli.Printing;
import keywhiz.cli.configs.CloneActionConfig;
import keywhiz.client.KeywhizClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static java.lang.String.format;
import static keywhiz.cli.Utilities.VALID_NAME_PATTERN;
import static keywhiz.cli.Utilities.validName;

public class CloneAction implements Runnable {
  private static final Logger logger = LoggerFactory.getLogger(CloneAction.class);

  private final CloneActionConfig cloneActionConfig;
  private final KeywhizClient keywhizClient;
  private final Printing printing;

  public CloneAction(CloneActionConfig cloneActionConfig, KeywhizClient client, Printing printing) {
    this.cloneActionConfig = cloneActionConfig;
    this.keywhizClient = client;
    this.printing = printing;
  }

  @Override public void run() {
    if (cloneActionConfig.newName == null || !validName(cloneActionConfig.newName)) {
      throw new IllegalArgumentException(format("Invalid name, must match %s", VALID_NAME_PATTERN));
    }

    try {
      logger.info("Cloning secret '{}' to new name '{}'", cloneActionConfig.name, cloneActionConfig.newName);
      long existingId = keywhizClient.getSanitizedSecretByName(cloneActionConfig.name).id();
      SecretDetailResponse existingSecretDetails = keywhizClient.secretDetailsForId(existingId);

      long newId = keywhizClient.cloneSecret(cloneActionConfig.name, cloneActionConfig.newName).id;
      for (Group group : existingSecretDetails.groups) {
          keywhizClient.grantSecretToGroupByIds(newId, group.getId());
      }
      printing.printSecretWithDetails(newId);
    } catch (KeywhizClient.NotFoundException e) {
      throw new AssertionError("Source secret doesn't exist.");
    } catch (KeywhizClient.ConflictException e) {
      throw new AssertionError("New secret name is already in use.");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
