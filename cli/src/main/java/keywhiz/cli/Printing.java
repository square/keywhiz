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
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import javax.inject.Inject;
import keywhiz.api.ClientDetailResponse;
import keywhiz.api.GroupDetailResponse;
import keywhiz.api.SecretDetailResponse;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.client.KeywhizClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Printing {

  private static final Logger logger = LoggerFactory.getLogger(Printing.class);
    
  private final KeywhizClient keywhizClient;

  private static final String INDENT = Strings.repeat("\t", 2);

  @Inject
  public Printing(final KeywhizClient keywhizClient) {
    this.keywhizClient = keywhizClient;
  }

  public void printClientWithDetails(Client client, List<String> options) {
    logger.info(client.getName());
    ClientDetailResponse clientDetails;
    try {
      clientDetails = keywhizClient.clientDetailsForId(client.getId());
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }

    if (options.contains("groups")) {
      logger.info("\tGroups:");
      clientDetails.groups.stream()
          .sorted(Comparator.comparing(Group::getName))
          .forEach(g -> logger.info(INDENT + g.getName()));
    }

    if (options.contains("secrets")) {
      logger.info("\tSecrets:");
      clientDetails.secrets.stream()
          .sorted(Comparator.comparing(SanitizedSecret::name))
          .forEach(s -> logger.info(INDENT + SanitizedSecret.displayName(s)));
    }
  }

  public void printGroupWithDetails(Group group, List<String> options) {
    logger.info(group.getName());
    GroupDetailResponse groupDetails;
    try {
      groupDetails = keywhizClient.groupDetailsForId(group.getId());
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }

    if (options.contains("clients")) {
      logger.info("\tClients:");
      groupDetails.getClients().stream()
          .sorted(Comparator.comparing(Client::getName))
          .forEach(c -> logger.info(INDENT + c.getName()));
      }

    if (options.contains("secrets")) {
      logger.info("\tSecrets:");
      groupDetails.getSecrets().stream()
          .sorted(Comparator.comparing(SanitizedSecret::name))
          .forEach(s -> logger.info(INDENT + SanitizedSecret.displayName(s)));
    }
  }

  public void printSanitizedSecretWithDetails(SanitizedSecret secret, List<String> options) {
    logger.info(SanitizedSecret.displayName(secret));
    SecretDetailResponse secretDetails;
    try {
      secretDetails = keywhizClient.secretDetailsForId(secret.id());
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }

    if (options.contains("groups")) {
      logger.info("\tGroups:");
      secretDetails.groups.stream()
          .sorted(Comparator.comparing(Group::getName))
          .forEach(g -> logger.info(INDENT + g.getName()));
    }

    if (options.contains("clients")) {
      logger.info("\tClients:");
      secretDetails.clients.stream()
          .sorted(Comparator.comparing(Client::getName))
          .forEach(c -> logger.info(INDENT + c.getName()));
    }

    if (options.contains("metadata")) {
      logger.info("\tMetadata:");
      if(!secret.metadata().isEmpty()) {
        logger.info(INDENT + secret.metadata().toString());
      }
    }
  }

  public void printAllClients(List<Client> clients) {
    clients.stream()
        .sorted(Comparator.comparing(Client::getName))
        .forEach(c -> logger.info(c.getName()));
  }

  public void printAllGroups(List<Group> groups) {
    groups.stream()
        .sorted(Comparator.comparing(Group::getName))
        .forEach(g -> logger.info(g.getName()));
  }

  public void printAllSanitizedSecrets(List<SanitizedSecret> secrets) {
    secrets.stream()
        .sorted(Comparator.comparing(SanitizedSecret::name))
        .forEach(s -> logger.info(SanitizedSecret.displayName(s)));
  }
}
