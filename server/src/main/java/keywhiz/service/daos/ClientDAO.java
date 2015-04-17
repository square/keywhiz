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

package keywhiz.service.daos;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import keywhiz.api.model.Client;
import keywhiz.jooq.tables.records.ClientsRecord;
import org.jooq.DSLContext;

import static keywhiz.jooq.tables.Clients.CLIENTS;

public class ClientDAO {
  private final DSLContext dslContext;

  @Inject
  public ClientDAO(DSLContext dslContext) {
    this.dslContext = dslContext;
  }

  public long createClient(String name, String user, Optional<String> description) {
    ClientsRecord r = dslContext.newRecord(CLIENTS);

    r.setName(name);
    r.setCreatedby(user);
    r.setUpdatedby(user);
    r.setDescription(description.orElse(null));
    r.setEnabled(true);
    r.setAutomationallowed(false);
    r.store();

    return r.getId();
  }

  public void deleteClient(Client client) {
    ClientsRecord r = dslContext.fetchOne(CLIENTS, CLIENTS.ID.eq(Math.toIntExact(client.getId())));
    r.delete();
  }

  public Optional<Client> getClient(String name) {
    ClientsRecord r = dslContext.fetchOne(CLIENTS, CLIENTS.NAME.eq(name));
    if (r != null) {
      return Optional.of(r.map(new ClientMapper()));
    }
    return Optional.empty();
  }

  public Optional<Client> getClientById(long id) {
    ClientsRecord r = dslContext.fetchOne(CLIENTS, CLIENTS.ID.eq((int)id));
    if (r != null) {
      return Optional.of(r.map(new ClientMapper()));
    }
    return Optional.empty();
  }

  public Set<Client> getClients() {
    List<Client> r = dslContext.select().from(CLIENTS).fetch().map(new ClientMapper());
    return new HashSet<>(r);
  }
}
