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

import com.google.common.collect.ImmutableSet;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import keywhiz.api.model.Client;
import keywhiz.jooq.tables.records.ClientsRecord;
import keywhiz.service.config.Readonly;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import static com.google.common.base.Preconditions.checkNotNull;
import static keywhiz.jooq.tables.Clients.CLIENTS;

public class ClientDAO {
  private final DSLContext dslContext;
  private final ClientMapper clientMapper;

  private ClientDAO(DSLContext dslContext, ClientMapper clientMapper) {
    this.dslContext = dslContext;
    this.clientMapper = clientMapper;
  }

  public long createClient(String name, String user, Optional<String> description) {
    ClientsRecord r = dslContext.newRecord(CLIENTS);

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    r.setName(name);
    r.setCreatedby(user);
    r.setCreatedat(now);
    r.setUpdatedby(user);
    r.setUpdatedat(now);
    r.setDescription(description.orElse(null));
    r.setEnabled(true);
    r.setAutomationallowed(false);
    r.store();

    return r.getId();
  }

  public void deleteClient(Client client) {
    dslContext
        .delete(CLIENTS)
        .where(CLIENTS.ID.eq(Math.toIntExact(client.getId())))
        .execute();
  }

  public Optional<Client> getClient(String name) {
    ClientsRecord r = dslContext.fetchOne(CLIENTS, CLIENTS.NAME.eq(name));
    return Optional.ofNullable(r).map(clientMapper::map);
  }

  public Optional<Client> getClientById(long id) {
    ClientsRecord r = dslContext.fetchOne(CLIENTS, CLIENTS.ID.eq(Math.toIntExact(id)));
    return Optional.ofNullable(r).map(clientMapper::map);
  }

  public ImmutableSet<Client> getClients() {
    List<Client> r = dslContext
        .selectFrom(CLIENTS)
        .fetch()
        .map(clientMapper);
    return ImmutableSet.copyOf(r);
  }

  public static class ClientDAOFactory implements DAOFactory<ClientDAO> {
    private final DSLContext jooq;
    private final DSLContext readonlyJooq;
    private final ClientMapper clientMapper;

    @Inject public ClientDAOFactory(DSLContext jooq, @Readonly DSLContext readonlyJooq,
        ClientMapper clientMapper) {
      this.jooq = jooq;
      this.readonlyJooq = readonlyJooq;
      this.clientMapper = clientMapper;
    }

    @Override public ClientDAO readwrite() {
      return new ClientDAO(jooq, clientMapper);
    }

    @Override public ClientDAO readonly() {
      return new ClientDAO(readonlyJooq, clientMapper);
    }

    @Override public ClientDAO using(Configuration configuration) {
      DSLContext dslContext = DSL.using(checkNotNull(configuration));
      return new ClientDAO(dslContext, clientMapper);
    }
  }
}
