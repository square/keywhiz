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
import static keywhiz.jooq.tables.Memberships.MEMBERSHIPS;

public class ClientDAO {
  private final DSLContext dslContext;
  private final ClientMapper clientMapper;

  private ClientDAO(DSLContext dslContext, ClientMapper clientMapper) {
    this.dslContext = dslContext;
    this.clientMapper = clientMapper;
  }

  public long createClient(String name, String user, String description) {
    ClientsRecord r = dslContext.newRecord(CLIENTS);

    long now = OffsetDateTime.now().toEpochSecond();

    r.setName(name);
    r.setCreatedby(user);
    r.setCreatedat(now);
    r.setUpdatedby(user);
    r.setUpdatedat(now);
    r.setLastseen(null);
    r.setDescription(description);
    r.setEnabled(true);
    r.setAutomationallowed(false);
    r.store();

    return r.getId();
  }

  public void deleteClient(Client client) {
    dslContext.transaction(configuration -> {
      DSL.using(configuration)
          .delete(CLIENTS)
          .where(CLIENTS.ID.eq(client.getId()))
          .execute();

      DSL.using(configuration)
          .delete(MEMBERSHIPS)
          .where(MEMBERSHIPS.CLIENTID.eq(client.getId()))
          .execute();
    });
  }

  public void sawClient(Client client) {
    long now = OffsetDateTime.now().toEpochSecond();

    dslContext.transaction(configuration -> {
      DSL.using(configuration)
          .update(CLIENTS)
          .set(CLIENTS.LASTSEEN, now)
          .where(CLIENTS.ID.eq(client.getId()))
          .execute();
    });
  }

  public Optional<Client> getClient(String name) {
    ClientsRecord r = dslContext.fetchOne(CLIENTS, CLIENTS.NAME.eq(name));
    return Optional.ofNullable(r).map(clientMapper::map);
  }

  public Optional<Client> getClientById(long id) {
    ClientsRecord r = dslContext.fetchOne(CLIENTS, CLIENTS.ID.eq(id));
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
