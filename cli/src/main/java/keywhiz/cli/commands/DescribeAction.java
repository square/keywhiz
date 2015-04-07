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
import java.util.Arrays;
import java.util.List;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.cli.Printing;
import keywhiz.cli.configs.DescribeActionConfig;
import keywhiz.client.KeywhizClient;
import keywhiz.client.KeywhizClient.NotFoundException;

import static java.lang.String.format;
import static keywhiz.api.model.Secret.splitNameAndVersion;
import static keywhiz.cli.Utilities.VALID_NAME_PATTERN;
import static keywhiz.cli.Utilities.validName;

public class DescribeAction implements Runnable {

  private final DescribeActionConfig describeActionConfig;
  private final KeywhizClient keywhizClient;
  private final Printing printing;

  public DescribeAction(DescribeActionConfig describeActionConfig, KeywhizClient client,
      Printing printing) {
    this.describeActionConfig = describeActionConfig;
    this.keywhizClient = client;
    this.printing = printing;
  }

  @Override public void run() {
    List<String> describeType = describeActionConfig.describeType;

    if (describeType == null || describeType.isEmpty()) {
      throw new IllegalArgumentException("Must specify a single type to describe.");
    }

    if (describeActionConfig.name == null || !validName(describeActionConfig.name)) {
      throw new IllegalArgumentException(format("Invalid name, must match %s", VALID_NAME_PATTERN));
    }

    String firstType = describeType.get(0).toLowerCase().trim();
    String name = describeActionConfig.name;

    switch (firstType) {

      case "group":
        try {
          Group group = keywhizClient.getGroupByName(name);
          printing.printGroupWithDetails(group, Arrays.asList("clients", "secrets"));
        } catch (NotFoundException e) {
          throw new AssertionError("Group not found.");
        } catch (IOException e) {
          throw Throwables.propagate(e);
        }
        break;

      case "client":
        try {
          Client client = keywhizClient.getClientByName(name);
          printing.printClientWithDetails(client, Arrays.asList("groups", "secrets"));
        } catch (NotFoundException e) {
          throw new AssertionError("Client not found.");
        } catch (IOException e) {
          throw Throwables.propagate(e);
        }
        break;

      case "secret":
        SanitizedSecret sanitizedSecret;
        try {
          String[] parts = splitNameAndVersion(name);
          sanitizedSecret = keywhizClient.getSanitizedSecretByNameAndVersion(parts[0], parts[1]);

          printing.printSanitizedSecretWithDetails(sanitizedSecret,
              Arrays.asList("groups", "clients", "metadata"));
        } catch (NotFoundException e) {
          throw new AssertionError("Secret not found.");
        } catch (ParseException e) {
          throw new IllegalArgumentException(format("Invalid secret name: '%s'", name), e);
        } catch (IOException e) {
          throw Throwables.propagate(e);
        }
        break;

      default:
        throw new IllegalArgumentException("Invalid describe type specified: " + firstType);
    }
  }
}