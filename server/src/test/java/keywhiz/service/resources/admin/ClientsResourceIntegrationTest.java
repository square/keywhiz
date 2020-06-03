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
package keywhiz.service.resources.admin;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.List;
import javax.annotation.Nullable;
import keywhiz.IntegrationTestRule;
import keywhiz.TestClients;
import keywhiz.api.ClientDetailResponse;
import keywhiz.api.model.Client;
import keywhiz.client.KeywhizClient;
import keywhiz.commands.DbSeedCommand;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

public class ClientsResourceIntegrationTest {
  KeywhizClient keywhizClient;

  @ClassRule public static final RuleChain chain = IntegrationTestRule.rule();

  @Before public void setUp() {
    keywhizClient = TestClients.keywhizClient();
  }

  private static List<String> clientsToNames(List<Client> response) {
    return Lists.transform(response, new Function<Client, String>() {
      @Override public String apply(@Nullable Client client) {
        return (client == null) ? null : client.getName();
      }
    });
  }

  @Test public void listsClients() throws IOException {
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());

    assertThat(clientsToNames(keywhizClient.allClients()))
        .contains("CN=User1", "CN=User2", "CN=User3", "CN=User4");
  }

  @Test(expected = KeywhizClient.UnauthorizedException.class)
  public void adminRejectsNonKeywhizUsers() throws IOException {
    keywhizClient.login("username", "password".toCharArray());
    keywhizClient.allClients();
  }

  @Test(expected = KeywhizClient.UnauthorizedException.class)
  public void adminRejectsClientCerts() throws IOException {
    keywhizClient.allClients();
  }

  @Test public void retrievesClientInfoById() throws IOException {
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());
    ClientDetailResponse client = keywhizClient.clientDetailsForId(768);
    assertThat(client.name).isEqualTo("client");
  }

  @Test public void retrievesClientInfoByName() throws IOException {
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());
    Client client = keywhizClient.getClientByName("client");
    assertThat(client.getId()).isEqualTo(768);
  }

  @Test public void createsClient() throws IOException {
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());

    assertThat(clientsToNames(keywhizClient.allClients())).doesNotContain("kingpin");
    ClientDetailResponse clientDetails = keywhizClient.createClient("kingpin", "", "");
    assertThat(clientDetails.name).isEqualTo("kingpin");
    assertThat(clientsToNames(keywhizClient.allClients())).contains("kingpin");
  }

  @Test(expected = KeywhizClient.ConflictException.class)
  public void createDuplicateClients() throws IOException {
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());

    keywhizClient.createClient("varys", "", "");
    keywhizClient.createClient("varys", "", "");
  }

  @Test(expected = KeywhizClient.NotFoundException.class)
  public void notFoundOnMissingId() throws IOException {
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());
    keywhizClient.clientDetailsForId(900000);
  }

  @Test(expected = KeywhizClient.NotFoundException.class)
  public void notFoundOnMissingName() throws IOException {
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());
    keywhizClient.getClientByName("non-existent-client");
  }

  @Test(expected = KeywhizClient.UnauthorizedException.class)
  public void adminRejectsWithoutCookie() throws IOException {
    keywhizClient.clientDetailsForId(768);
  }

  @Test public void deletesClients() throws IOException {
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());
    long clientId = keywhizClient.createClient("deletesClientTest", "", "").id;

    keywhizClient.deleteClientWithId(clientId);

    try {
      keywhizClient.clientDetailsForId(clientId);
      failBecauseExceptionWasNotThrown(KeywhizClient.NotFoundException.class);
    } catch (KeywhizClient.NotFoundException e) {
      // Client not found, since it was deleted
    }
  }
}
