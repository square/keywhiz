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
import java.util.List;
import java.util.Optional;
import keywhiz.api.model.Client;
import keywhiz.jooq.tables.records.ClientsRecord;
import org.jooq.DSLContext;

import static com.google.common.base.Preconditions.checkNotNull;
import static keywhiz.jooq.tables.Clients.CLIENTS;

public class ClientDAO {
  public long createClient(DSLContext dslContext, String name, String user, Optional<String> description) {
    checkNotNull(dslContext);

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

  public void deleteClient(DSLContext dslContext, Client client) {
    checkNotNull(dslContext);

    dslContext
        .delete(CLIENTS)
        .where(CLIENTS.ID.eq(Math.toIntExact(client.getId())))
        .execute();
  }

  public Optional<Client> getClient(DSLContext dslContext, String name) {
    checkNotNull(dslContext);

    ClientsRecord r = dslContext.fetchOne(CLIENTS, CLIENTS.NAME.eq(name));
    return Optional.ofNullable(r).map(
        rec -> new ClientMapper().map(rec));
  }

  public Optional<Client> getClientById(DSLContext dslContext, long id) {
    checkNotNull(dslContext);

    ClientsRecord r = dslContext.fetchOne(CLIENTS, CLIENTS.ID.eq(Math.toIntExact(id)));
    return Optional.ofNullable(r).map(
        rec -> new ClientMapper().map(rec));
  }

  public ImmutableSet<Client> getClients(DSLContext dslContext) {
    checkNotNull(dslContext);

    List<Client> r = dslContext
        .selectFrom(CLIENTS)
        .fetch()
        .map(new ClientMapper());
    return ImmutableSet.copyOf(r);
  }
}
