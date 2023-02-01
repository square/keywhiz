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
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import keywhiz.api.ApiDate;
import keywhiz.api.ClientDetailResponse;
import keywhiz.api.GroupDetailResponse;
import keywhiz.api.SecretDetailResponse;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.SecretSeries;
import keywhiz.client.KeywhizClient;

public class Printing {
  private final KeywhizClient keywhizClient;

  private static final String INDENT = "\t";
  private static final String DOUBLE_INDENT = Strings.repeat(INDENT, 2);

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

    System.out.println(INDENT + "Groups:");
    clientDetails.groups.stream()
        .sorted(Comparator.comparing(Group::getName))
        .forEach(g -> System.out.println(DOUBLE_INDENT + g.getName()));

    System.out.println(INDENT + "Secrets:");
    clientDetails.secrets.stream()
        .sorted(Comparator.comparing(SanitizedSecret::name))
        .forEach(s -> System.out.println(DOUBLE_INDENT + SanitizedSecret.displayName(s)));

    if (clientDetails.lastSeen == null) {
      System.out.println(INDENT + "Last Seen: never");
    } else {
      Date d = new Date(clientDetails.lastSeen.toEpochSecond() * 1000);
      System.out.printf(INDENT + "Last Seen: %s%n", DateFormat.getDateTimeInstance().format(d));
    }

    if (!clientDetails.description.isEmpty()) {
      System.out.println(INDENT + "Description:");
      System.out.println(DOUBLE_INDENT + clientDetails.description);
    }

    if (clientDetails.spiffeId != null && !clientDetails.spiffeId.isEmpty()) {
      System.out.println(INDENT + "Spiffe ID:");
      System.out.println(DOUBLE_INDENT + clientDetails.spiffeId);
    }

    if (!clientDetails.createdBy.isEmpty()) {
      System.out.println(INDENT + "Created by:");
      System.out.println(DOUBLE_INDENT + clientDetails.createdBy);
    }

    System.out.println(INDENT + "Created at:");
    Date d = new Date(clientDetails.creationDate.toEpochSecond() * 1000);
    System.out.println(DOUBLE_INDENT + DateFormat.getDateTimeInstance().format(d));

    if (!clientDetails.updatedBy.isEmpty()) {
      System.out.println(INDENT + "Updated by:");
      System.out.println(DOUBLE_INDENT + clientDetails.updatedBy);
    }

    System.out.println(INDENT + "Updated at:");
    d = new Date(clientDetails.updateDate.toEpochSecond() * 1000);
    System.out.println(DOUBLE_INDENT + DateFormat.getDateTimeInstance().format(d));
  }

  public void printGroupWithDetails(Group group) {
    System.out.println(group.getName());
    GroupDetailResponse groupDetails;
    try {
      groupDetails = keywhizClient.groupDetailsForId(group.getId());
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }

    System.out.println(INDENT + "Clients:");
    groupDetails.getClients().stream()
        .sorted(Comparator.comparing(Client::getName))
        .forEach(c -> System.out.println(DOUBLE_INDENT + c.getName()));

    System.out.println(INDENT + "Secrets:");
    groupDetails.getSecrets().stream()
        .sorted(Comparator.comparing(SanitizedSecret::name))
        .forEach(s -> System.out.println(DOUBLE_INDENT + SanitizedSecret.displayName(s)));

    System.out.println(INDENT + "Metadata:");
    if (!groupDetails.getMetadata().isEmpty()) {
      String metadata;
      try {
        metadata = new ObjectMapper().writeValueAsString(groupDetails.getMetadata());
      } catch (JsonProcessingException e) {
        throw Throwables.propagate(e);
      }
      System.out.println(DOUBLE_INDENT + metadata);
    }

    if (!groupDetails.getDescription().isEmpty()) {
      System.out.println(INDENT + "Description:");
      System.out.println(DOUBLE_INDENT + groupDetails.getDescription());
    }

    if (!groupDetails.getCreatedBy().isEmpty()) {
      System.out.println(INDENT + "Created by:");
      System.out.println(DOUBLE_INDENT + groupDetails.getCreatedBy());
    }

    System.out.println(INDENT + "Created at:");
    Date d = new Date(groupDetails.getCreationDate().toEpochSecond() * 1000);
    System.out.println(DOUBLE_INDENT + DateFormat.getDateTimeInstance().format(d));

    if (!groupDetails.getUpdatedBy().isEmpty()) {
      System.out.println(INDENT + "Updated by:");
      System.out.println(DOUBLE_INDENT + groupDetails.getUpdatedBy());
    }

    System.out.println(INDENT + "Updated at:");
    d = new Date(groupDetails.getUpdateDate().toEpochSecond() * 1000);
    System.out.println(DOUBLE_INDENT + DateFormat.getDateTimeInstance().format(d));
  }

  public void printDeletedSecretsWithDetails(@Nullable List<SecretSeries> deletedSecrets) {
    int deletedCount = deletedSecrets == null ? 0 : deletedSecrets.size();
    System.out.println(String.format("Deleted Secrets: found %d", deletedCount));
    if (deletedSecrets != null) {
      deletedSecrets.forEach(series -> this.printSecretWithDetails(series.id()));
    }
  }

  public void printNonDeletedSecretWithDetails(@Nullable SanitizedSecret secret) {
    if (secret == null) {
      System.out.println("No non-deleted secret found.");
    } else {
      printSecretWithDetails(secret.id());
    }
  }

  public void printSecretWithDetails(long secretID) {
    SecretDetailResponse secretDetails;
    try {
      secretDetails = keywhizClient.secretDetailsForId(secretID);
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }

    System.out.println(secretDetails.name);

    System.out.println(INDENT + "Internal ID:");
    System.out.println(DOUBLE_INDENT + secretID);

    System.out.println(INDENT + "Owner:");
    if (secretDetails.owner != null) {
      System.out.println(DOUBLE_INDENT + secretDetails.owner);
    }

    System.out.println(INDENT + "Groups:");
    secretDetails.groups.stream()
        .sorted(Comparator.comparing(Group::getName))
        .forEach(g -> System.out.println(DOUBLE_INDENT + g.getName()));

    System.out.println(INDENT + "Clients:");
    secretDetails.clients.stream()
        .sorted(Comparator.comparing(Client::getName))
        .forEach(c -> System.out.println(DOUBLE_INDENT + c.getName()));

    System.out.println(INDENT + "Content HMAC:");
    if (secretDetails.checksum.isEmpty()) {
      System.out.println(DOUBLE_INDENT + "WARNING: Content HMAC not calculated!");
    } else {
      System.out.println(DOUBLE_INDENT + secretDetails.checksum);
    }

    System.out.println(INDENT + "Metadata:");
    if (!secretDetails.metadata.isEmpty()) {
      String metadata;
      try {
        metadata = new ObjectMapper().writeValueAsString(secretDetails.metadata);
      } catch (JsonProcessingException e) {
        throw Throwables.propagate(e);
      }
      System.out.println(DOUBLE_INDENT + metadata);
    }

    if (secretDetails.expiry > 0) {
      System.out.println(INDENT + "Expiry:");
      System.out.println(DOUBLE_INDENT + epochSecondToString(secretDetails.expiry));
    }

    if (!secretDetails.description.isEmpty()) {
      System.out.println(INDENT + "Description:");
      System.out.println(DOUBLE_INDENT + secretDetails.description);
    }

    if (!secretDetails.createdBy.isEmpty()) {
      System.out.println(INDENT + "Created by:");
      System.out.println(DOUBLE_INDENT + secretDetails.createdBy);
    }

    System.out.println(INDENT + "Created at:");
    System.out.println(DOUBLE_INDENT + apiDateToString(secretDetails.createdAt));

    if (!secretDetails.updatedBy.isEmpty()) {
      System.out.println(INDENT + "Updated by:");
      System.out.println(DOUBLE_INDENT + secretDetails.updatedBy);
    }

    System.out.println(INDENT + "Updated at:");
    System.out.println(DOUBLE_INDENT + apiDateToString(secretDetails.updatedAt));

    if (!secretDetails.contentCreatedBy().orElse("").isEmpty()) {
      System.out.println(INDENT + "Content created by:");
      System.out.println(DOUBLE_INDENT + secretDetails.contentCreatedBy().get());
    }

    if (secretDetails.contentCreatedAt().isPresent()) {
      System.out.println(INDENT + "Content created at:");
      System.out.println(DOUBLE_INDENT + apiDateToString(secretDetails.contentCreatedAt().get()));
    }
  }

  private String apiDateToString(ApiDate apiDate) {
    return epochSecondToString(apiDate.toEpochSecond() * 1000);
  }

  private String epochSecondToString(long epochSecond) {
    Date d = new Date(epochSecond);
    return DateFormat.getDateTimeInstance().format(d);
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

  public void printSecretVersions(List<SanitizedSecret> versions, Optional<Long> currentVersion) {
    if (versions.isEmpty()) {
      return;
    }

    System.out.println(versions.get(0).name() + '\n');

    if (currentVersion.isEmpty()) {
      System.out.println("Current secret version unknown!");
    }

    for (SanitizedSecret secret : versions) {
      if (secret.version().isPresent()) {
        if (currentVersion.isPresent() && secret.version().get().equals(currentVersion.get())) {
          System.out.println(
              String.format("*** Current secret version id: %d ***", secret.version().get()));
        } else {
          System.out.println(String.format("Version id for rollback: %d", secret.version().get()));
        }
      } else {
        System.out.println("Version id for rollback: Unknown!");
      }

      if (secret.contentCreatedAt().isPresent()) {
        if (secret.contentCreatedBy().isEmpty()) {
          System.out.println(INDENT + String.format("Created on %s (creator unknown)",
              DateFormat.getDateTimeInstance()
                  .format(new Date(secret.contentCreatedAt().get().toEpochSecond() * 1000))));
        } else {
          System.out.println(
              INDENT + String.format("Created by %s on %s", secret.contentCreatedBy(),
                  DateFormat.getDateTimeInstance()
                      .format(new Date(secret.contentCreatedAt().get().toEpochSecond() * 1000))));
        }
      } else {
        if (secret.contentCreatedBy().isEmpty()) {
          System.out.println(INDENT + "Creator and creation date unknown");
        } else {
          System.out.println(
              INDENT + String.format("Created by %s (date unknown)", secret.contentCreatedBy()));
        }
      }

      if (secret.expiry() == 0) {
        System.out.println(INDENT + "Does not expire");
      } else {
        System.out.println(INDENT + String.format("Expires on %s", DateFormat.getDateTimeInstance()
            .format(new Date(secret.expiry() * 1000))));
      }

      if (!secret.metadata().isEmpty()) {
        System.out.println(INDENT + String.format("Metadata: %s", secret.metadata()));
      }

      if (!secret.checksum().isEmpty()) {
        System.out.println(INDENT + String.format("Content HMAC: %s", secret.checksum()));
      }
      System.out.print("\n"); // Add space between the versions
    }
  }
}
