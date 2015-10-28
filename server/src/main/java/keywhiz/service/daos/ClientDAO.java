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
import keywhiz.service.config.ShadowWrite;
import keywhiz.shadow_write.jooq.tables.Clients;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static keywhiz.jooq.tables.Clients.CLIENTS;
import static keywhiz.jooq.tables.Memberships.MEMBERSHIPS;

public class ClientDAO {
  private static final Logger logger = LoggerFactory.getLogger(ClientDAO.class);

  private final DSLContext dslContext;
  private final ClientMapper clientMapper;
  private final DSLContext shadowWriteDslContext;

  private ClientDAO(DSLContext dslContext, ClientMapper clientMapper,
      DSLContext shadowWriteDslContext) {
    this.dslContext = dslContext;
    this.clientMapper = clientMapper;
    this.shadowWriteDslContext = shadowWriteDslContext;
  }

  public long createClient(String name, String user, String description) {
    ClientsRecord r = dslContext.newRecord(CLIENTS);

    long now = OffsetDateTime.now().toEpochSecond();

    r.setName(name);
    r.setCreatedby(user);
    r.setCreatedat(now);
    r.setUpdatedby(user);
    r.setUpdatedat(now);
    r.setDescription(description);
    r.setEnabled(true);
    r.setAutomationallowed(false);
    r.store();

    long id = r.getId();

    try {
      keywhiz.shadow_write.jooq.tables.records.ClientsRecord shadowR = shadowWriteDslContext.newRecord(Clients.CLIENTS);
      shadowR.setId(id);
      shadowR.setName(name);
      shadowR.setCreatedby(user);
      shadowR.setCreatedat(now);
      shadowR.setUpdatedby(user);
      shadowR.setUpdatedat(now);
      shadowR.setDescription(description);
      shadowR.setEnabled(true);
      shadowR.setAutomationallowed(false);
      shadowR.store();
    } catch (DataAccessException e) {
      logger.error("shadowWrite: failure to create client clientId {}", e, id);
    }
    return id;
  }

  public void deleteClient(Client client) {
    long id = client.getId();

    shadowWriteDslContext.transaction(shadowWriteConfiguration -> {
      dslContext.transaction(configuration -> {
        DSL.using(configuration)
            .delete(CLIENTS)
            .where(CLIENTS.ID.eq(id))
            .execute();

        DSL.using(configuration)
            .delete(MEMBERSHIPS)
            .where(MEMBERSHIPS.CLIENTID.eq(id))
            .execute();
      });
      try {
        DSL.using(shadowWriteConfiguration)
            .delete(CLIENTS)
            .where(CLIENTS.ID.eq(id))
            .execute();

        DSL.using(shadowWriteConfiguration)
            .delete(MEMBERSHIPS)
            .where(MEMBERSHIPS.CLIENTID.eq(id))
            .execute();
      } catch (DataAccessException e) {
        logger.error("shadowWrite: failure to delete client clientId {}", e, id);
      }
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
    private final DSLContext shadowWriteJooq;

    @Inject public ClientDAOFactory(DSLContext jooq, @Readonly DSLContext readonlyJooq,
        ClientMapper clientMapper,
        @ShadowWrite DSLContext shadowWriteJooq) {
      this.jooq = jooq;
      this.readonlyJooq = readonlyJooq;
      this.clientMapper = clientMapper;
      this.shadowWriteJooq = shadowWriteJooq;
    }

    @Override public ClientDAO readwrite() {
      return new ClientDAO(jooq, clientMapper, shadowWriteJooq);
    }

    @Override public ClientDAO readonly() {
      return new ClientDAO(readonlyJooq, clientMapper, null);
    }

    @Override public ClientDAO using(Configuration configuration,
        Configuration shadowWriteConfiguration) {
      DSLContext dslContext = DSL.using(checkNotNull(configuration));
      DSLContext shadowWriteDslContext = null;
      if (shadowWriteConfiguration != null) {
        shadowWriteDslContext = DSL.using(checkNotNull(shadowWriteConfiguration));
      }
      return new ClientDAO(dslContext, clientMapper, shadowWriteDslContext);
    }
  }
}
