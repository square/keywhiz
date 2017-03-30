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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import javax.inject.Inject;
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

  public void printClientWithDetails(Client client) {
    System.out.println(client.getName());
    ClientDetailResponse clientDetails;
    try {
      clientDetails = keywhizClient.clientDetailsForId(client.getId());
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }

    if (clientDetails.lastSeen == null) {
      System.out.println("\tLast Seen: never");
    } else {
      String lastSeen = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss").format(new Date (clientDetails.lastSeen.toEpochSecond()*1000));
      System.out.printf("\tLast Seen: %s%n", lastSeen);
    }

    System.out.println("\tGroups:");
    clientDetails.groups.stream()
        .sorted(Comparator.comparing(Group::getName))
        .forEach(g -> System.out.println(INDENT + g.getName()));

    System.out.println("\tSecrets:");
    clientDetails.secrets.stream()
        .sorted(Comparator.comparing(SanitizedSecret::name))
        .forEach(s -> System.out.println(INDENT + SanitizedSecret.displayName(s)));
  }

  public void printGroupWithDetails(Group group) {
    System.out.println(group.getName());
    GroupDetailResponse groupDetails;
    try {
      groupDetails = keywhizClient.groupDetailsForId(group.getId());
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }

    System.out.println("\tClients:");
    groupDetails.getClients().stream()
        .sorted(Comparator.comparing(Client::getName))
        .forEach(c -> System.out.println(INDENT + c.getName()));

    System.out.println("\tSecrets:");
    groupDetails.getSecrets().stream()
        .sorted(Comparator.comparing(SanitizedSecret::name))
        .forEach(s -> System.out.println(INDENT + SanitizedSecret.displayName(s)));

    System.out.println("\tMetadata:");
    if (!groupDetails.getMetadata().isEmpty()) {
      String metadata;
      try {
        metadata = new ObjectMapper().writeValueAsString(groupDetails.getMetadata());
      } catch (JsonProcessingException e) {
        throw Throwables.propagate(e);
      }
      System.out.println(INDENT + metadata);
    }

    if (!groupDetails.getDescription().isEmpty()) {
      System.out.println("\tDescription:");
      System.out.println(INDENT + groupDetails.getDescription());
    }

    if (!groupDetails.getCreatedBy().isEmpty()) {
      System.out.println("\tCreated by:");
      System.out.println(INDENT + groupDetails.getCreatedBy());
    }

    System.out.println("\tCreated at:");
    Date d = new Date(groupDetails.getCreationDate().toEpochSecond() * 1000);
    System.out.println(INDENT + DateFormat.getDateTimeInstance().format(d));

    if (!groupDetails.getUpdatedBy().isEmpty()) {
      System.out.println("\tUpdated by:");
      System.out.println(INDENT + groupDetails.getUpdatedBy());
    }

    System.out.println("\tUpdated at:");
    d = new Date(groupDetails.getUpdateDate().toEpochSecond() * 1000);
    System.out.println(INDENT + DateFormat.getDateTimeInstance().format(d));
  }

  public void printSanitizedSecretWithDetails(SanitizedSecret secret) {
    System.out.println(SanitizedSecret.displayName(secret));
    SecretDetailResponse secretDetails;
    try {
      secretDetails = keywhizClient.secretDetailsForId(secret.id());
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }

    System.out.println("\tGroups:");
    secretDetails.groups.stream()
        .sorted(Comparator.comparing(Group::getName))
        .forEach(g -> System.out.println(INDENT + g.getName()));

    System.out.println("\tClients:");
    secretDetails.clients.stream()
        .sorted(Comparator.comparing(Client::getName))
        .forEach(c -> System.out.println(INDENT + c.getName()));

    System.out.println("\tMetadata:");
    if (!secret.metadata().isEmpty()) {
      String metadata;
      try {
        metadata = new ObjectMapper().writeValueAsString(secret.metadata());
      } catch (JsonProcessingException e) {
        throw Throwables.propagate(e);
      }
      System.out.println(INDENT + metadata);
    }

    if (secret.expiry() > 0) {
      System.out.println("\tExpiry:");
      Date d = new Date(secret.expiry() * 1000);
      System.out.println(INDENT + DateFormat.getDateTimeInstance().format(d));
    }

    if (!secret.description().isEmpty()) {
      System.out.println("\tDescription:");
      System.out.println(INDENT + secret.description());
    }

    if (!secret.createdBy().isEmpty()) {
      System.out.println("\tCreated by:");
      System.out.println(INDENT + secret.createdBy());
    }

    System.out.println("\tCreated at:");
    Date d = new Date(secret.createdAt().toEpochSecond() * 1000);
    System.out.println(INDENT + DateFormat.getDateTimeInstance().format(d));

    if (!secret.updatedBy().isEmpty()) {
      System.out.println("\tUpdated by:");
      System.out.println(INDENT + secret.updatedBy());
    }

    System.out.println("\tUpdated at:");
    d = new Date(secret.updatedAt().toEpochSecond() * 1000);
    System.out.println(INDENT + DateFormat.getDateTimeInstance().format(d));
  }

  public void printAllClients(List<Client> clients) {
    clients.stream()
        .sorted(Comparator.comparing(Client::getName))
        .forEach(c -> System.out.println(c.getName()));
  }

  public void printAllGroups(List<Group> groups) {
    groups.stream()
        .sorted(Comparator.comparing(Group::getName))
        .forEach(g -> System.out.println(g.getName()));
  }

  public void printAllSanitizedSecrets(List<SanitizedSecret> secrets) {
    secrets.stream()
        .sorted(Comparator.comparing(SanitizedSecret::name))
        .forEach(s -> System.out.println(SanitizedSecret.displayName(s)));
  }

  public void printSecretVersions(List<SanitizedSecret> versions, Long currentVersion) {
    if (versions.isEmpty()) {
      return;
    }

    System.out.println(versions.get(0).name() + "\n");

    if (currentVersion < 0) {
      System.out.println("Current secret version unknown!");
    }

    for (SanitizedSecret secret : versions) {
      if (secret.version().isPresent()) {
        if (secret.version().get().equals(currentVersion)) {
          System.out.println(String.format("*** Current secret version id: %d ***", secret.version().get()));
        } else {
          System.out.println(String.format("Version id for rollback: %d", secret.version().get()));
        }
      } else {
        System.out.println("Version id for rollback: Unknown!");
      }

      if (secret.createdBy().isEmpty()) {
        System.out.println(INDENT + String.format("Created on %s (creator unknown)",
            DateFormat.getDateTimeInstance()
                .format(new Date(secret.createdAt().toEpochSecond() * 1000))));
      } else {
        System.out.println(INDENT + String.format("Created by %s on %s", secret.createdBy(),
            DateFormat.getDateTimeInstance()
                .format(new Date(secret.createdAt().toEpochSecond() * 1000))));
      }

      if (secret.updatedBy().isEmpty()) {
        System.out.println(INDENT + String.format("Updated on %s (updater unknown)",
            DateFormat.getDateTimeInstance()
                .format(new Date(secret.updatedAt().toEpochSecond() * 1000))));
      } else {
        System.out.println(INDENT + String.format("Updated by %s on %s", secret.updatedBy(),
            DateFormat.getDateTimeInstance()
                .format(new Date(secret.updatedAt().toEpochSecond() * 1000))));
      }

      if (secret.expiry() == 0) {
        System.out.println(INDENT + "Does not expire");
      } else {
        System.out.println(INDENT + String.format("Expires on %s", DateFormat.getDateTimeInstance()
            .format(new Date(secret.expiry() * 1000))));
      }

      if (!secret.checksum().isEmpty()) {
        System.out.println(INDENT + String.format("Content HMAC: %s", secret.checksum()));
      }
      System.out.print("\n"); // Add space between the versions
    }
  }
}
