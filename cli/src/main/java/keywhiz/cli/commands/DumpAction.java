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
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import keywhiz.api.SecretDetailResponse;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.cli.configs.DumpActionConfig;
import keywhiz.client.KeywhizClient;

import static java.lang.String.format;
import static keywhiz.api.model.Secret.splitNameAndVersion;
import static keywhiz.cli.Utilities.VALID_NAME_PATTERN;
import static keywhiz.cli.Utilities.validName;

/**
 * Dumps a secret.
 *
 * The keywhiz client API does not expose a method to dump secrets because we don't want to bypass
 * the security guarantees provided by unix permissions + keywhiz-fs certificates.
 *
 * This code therefore assigns the secret to a group, fetches the data from keywhiz-fs and
 * unassigns the secret. This works as long as the following two assumptions are met:
 * - the current hostname exists as a keywhiz group.
 * - keywhiz-fs is running on the current host.
 *
 * Note: in the future, we could make things less "magic" by having the code look at the mountpoint,
 * figure out which keywhiz-fs client is mapped there, create a temporary group, assign the
 * client+secret to the temporary group and then revert everything.
 *
 * TODO: is there a risk of dumping stale secrets? How can we ensure we have the right content?
 */
public class DumpAction implements Runnable {

  private final DumpActionConfig dumpActionConfig;
  private final KeywhizClient keywhizClient;

  public DumpAction(DumpActionConfig dumpActionConfig, KeywhizClient client) {
    this.dumpActionConfig = dumpActionConfig;
    this.keywhizClient = client;
  }

  @Override public void run() {
    String secretName = dumpActionConfig.secret;

    if (secretName == null || !validName(secretName)) {
      throw new IllegalArgumentException(format("Invalid name, must match %s", VALID_NAME_PATTERN));
    }

    try {
      // Find secret id
      String[] parts = splitNameAndVersion(secretName);
      SanitizedSecret sanitizedSecret = keywhizClient.getSanitizedSecretByNameAndVersion(parts[0],
          parts[1]);

      // Map this host to a group
      String hostname = InetAddress.getLocalHost().getHostName();
      Group host = keywhizClient.getGroupByName(hostname);

      SecretDetailResponse secretDetails = keywhizClient.secretDetailsForId(sanitizedSecret.id());
      boolean hasSecret = false;
      for (Group group : secretDetails.groups) {
        if (group.getId() == host.getId()) {
          hasSecret = true;
          break;
        }
      }
      if (!hasSecret) {
        // assign it
        keywhizClient.grantSecretToGroupByIds(sanitizedSecret.id(), host.getId());
      }
      try {
        // display the file's content
        System.out.print(new String(
            Files.readAllBytes(Paths.get(dumpActionConfig.mountPoint, secretName))));
      } finally {
        // TODO: use try-with-resource
        if (!hasSecret) {
          // unassign it
          keywhizClient.revokeSecretFromGroupByIds(sanitizedSecret.id(), host.getId());
        }
      }
    } catch (ParseException e) {
      throw new IllegalArgumentException(format("Invalid secret name: '%s'", secretName), e);
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }
}
