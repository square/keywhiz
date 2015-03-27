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
package keywhiz.cli;

import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import keywhiz.api.ClientDetailResponse;
import keywhiz.api.GroupDetailResponse;
import keywhiz.api.SecretDetailResponse;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.client.KeywhizClient;

public class Printing {
  private final KeywhizClient keywhizClient;

  private static final String INDENT = Strings.repeat("\t", 2);

  @Inject
  public Printing(final KeywhizClient keywhizClient) {
    this.keywhizClient = keywhizClient;
  }

  public void printClient(Client client, List<String> options) {
    System.out.println(client.getName());
    ClientDetailResponse clientDetails;
    try {
      clientDetails = keywhizClient.clientDetailsForId((int) client.getId());
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }

    if (options.contains("groups")) {
      System.out.println("\tGroups:");
      clientDetails.groups.stream()
          .sorted(Comparator.comparing(Group::getName))
          .forEach(g -> System.out.println(INDENT + g.getName()));
    }

    if (options.contains("secrets")) {
      System.out.println("\tSecrets:");
      clientDetails.secrets.stream()
          .sorted(Comparator.comparing(SanitizedSecret::name))
          .forEach(s -> System.out.println(INDENT + SanitizedSecret.displayName(s)));
    }
  }

  public void printGroup(Group group, List<String> options) {
    System.out.println(group.getName());
    GroupDetailResponse groupDetails;
    try {
      groupDetails = keywhizClient.groupDetailsForId((int) group.getId());
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }

    if (options.contains("clients")) {
      System.out.println("\tClients:");
      groupDetails.getClients().stream()
          .sorted(Comparator.comparing(Client::getName))
          .forEach(c -> System.out.println(INDENT + c.getName()));
      }

    if (options.contains("secrets")) {
      System.out.println("\tSecrets:");
      groupDetails.getSecrets().stream()
          .sorted(Comparator.comparing(SanitizedSecret::name))
          .forEach(s -> System.out.println(INDENT + SanitizedSecret.displayName(s)));
    }
  }

  public void printSanitizedSecret(SanitizedSecret secret, List<String> options) {
    System.out.println(SanitizedSecret.displayName(secret));
    SecretDetailResponse secretDetails;
    try {
      secretDetails = keywhizClient.secretDetailsForId((int) secret.id());
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }

    if (options.contains("groups")) {
      System.out.println("\tGroups:");
      secretDetails.groups.stream()
          .sorted(Comparator.comparing(Group::getName))
          .forEach(g -> System.out.println(INDENT + g.getName()));
    }

    if (options.contains("clients")) {
      System.out.println("\tClients:");
      secretDetails.clients.stream()
          .sorted(Comparator.comparing(Client::getName))
          .forEach(c -> System.out.println(INDENT + c.getName()));
    }

    if (options.contains("metadata")) {
      System.out.println("\tMetadata:");
      if(!secret.metadata().isEmpty()) {
        System.out.println(INDENT + secret.metadata().toString());
      }
    }
  }

  public void printAllClients(List<Client> clients, List<String> options) {
    clients.stream()
        .sorted(Comparator.comparing(Client::getName))
        .forEach(c -> printClient(c, options));
  }

  public void printAllGroups(List<Group> groups, List<String> options) {
    groups.stream()
        .sorted(Comparator.comparing(Group::getName))
        .forEach(g -> printGroup(g, options));
  }

  public void printAllSanitizedSecrets(List<SanitizedSecret> secrets, List<String> options) {
    secrets.stream()
        .sorted(Comparator.comparing(SanitizedSecret::name))
        .forEach(s -> printSanitizedSecret(s, options));
  }
}
