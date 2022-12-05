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
import java.net.URI;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import keywhiz.api.model.Client;
import keywhiz.auth.mutualssl.CertificatePrincipal;
import keywhiz.jooq.tables.Groups;
import keywhiz.jooq.tables.records.ClientsRecord;
import keywhiz.jooq.tables.records.GroupsRecord;
import keywhiz.service.config.Readonly;
import keywhiz.service.crypto.RowHmacGenerator;
import org.jooq.Condition;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Param;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.impl.DSL;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.time.Instant.EPOCH;
import static keywhiz.jooq.tables.Clients.CLIENTS;
import static keywhiz.jooq.tables.Groups.GROUPS;
import static keywhiz.jooq.tables.Memberships.MEMBERSHIPS;
import static org.jooq.impl.DSL.greatest;
import static org.jooq.impl.DSL.when;

public class ClientDAO {
  private static final Groups CLIENT_OWNERS = GROUPS.as("owners");
  private static final Duration LAST_SEEN_THRESHOLD = Duration.ofSeconds(24 * 60 * 60);
  private static final Long NO_OWNER = null;

  private final DSLContext dslContext;
  private final ClientMapper clientMapper;
  private final RowHmacGenerator rowHmacGenerator;

  private ClientDAO(DSLContext dslContext, ClientMapper clientMapper,
      RowHmacGenerator rowHmacGenerator) {
    this.dslContext = dslContext;
    this.clientMapper = clientMapper;
    this.rowHmacGenerator = rowHmacGenerator;
  }

  public long createClient(
      String name,
      String user,
      String description,
      @Nullable URI spiffeId) {
    return createClient(
        name,
        user,
        description,
        spiffeId,
        NO_OWNER);
  }

  public long createClient(
      String name,
      String user,
      String description,
      @Nullable URI spiffeId,
      @Nullable Long ownerId) {
    ClientsRecord r = dslContext.newRecord(CLIENTS);

    long now = OffsetDateTime.now().toEpochSecond();

    long generatedId = rowHmacGenerator.getNextLongSecure();
    String rowHmac = rowHmacGenerator.computeRowHmac(
        CLIENTS.getName(), List.of(name, generatedId));

    // Do not allow empty spiffe URIs
    String spiffeStr = null;
    if (spiffeId != null && !spiffeId.toASCIIString().isEmpty()) {
      spiffeStr = spiffeId.toASCIIString();
    }

    r.setId(generatedId);
    r.setName(name);
    r.setCreatedby(user);
    r.setCreatedat(now);
    r.setUpdatedby(user);
    r.setUpdatedat(now);
    r.setLastseen(null);
    r.setDescription(description);
    r.setEnabled(true);
    r.setAutomationallowed(false);
    r.setSpiffeId(spiffeStr);
    r.setRowHmac(rowHmac);
    r.setOwner(ownerId);
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

  public void sawClient(Client client, @Nullable Principal principal) {
    Instant now = Instant.now();

    Instant lastSeen = Optional.ofNullable(client.getLastSeen())
        .map(ls -> Instant.ofEpochSecond(ls.toEpochSecond()))
        .orElse(EPOCH);

    final Instant expiration;
    if (principal instanceof CertificatePrincipal) {
      expiration = ((CertificatePrincipal) principal).getCertificateExpiration();
    } else {
      expiration = EPOCH;
    }

    // Only update last seen if it's been more than `lastSeenThreshold` seconds
    // this way we can have less granularity on lastSeen and save DB writes
    if (now.isAfter(lastSeen.plus(LAST_SEEN_THRESHOLD))) {
      dslContext.transaction(configuration -> {
        Param<Long> lastSeenValue = DSL.val(now.getEpochSecond(), CLIENTS.LASTSEEN);
        Param<Long> expirationValue = DSL.val(expiration.getEpochSecond(), CLIENTS.EXPIRATION);

        DSL.using(configuration)
            .update(CLIENTS)
            .set(CLIENTS.LASTSEEN,
                when(CLIENTS.LASTSEEN.isNull(), lastSeenValue)
                    .otherwise(greatest(CLIENTS.LASTSEEN, lastSeenValue)))
            .set(CLIENTS.EXPIRATION, expirationValue)
            .where(CLIENTS.ID.eq(client.getId()))
            .execute();
      });
    }
  }

  public Optional<Client> getClientByName(String name) {
    return getClient(CLIENTS.NAME.eq(name));
  }

  public Optional<Client> getClientBySpiffeId(URI spiffeId) {
    return getClient(CLIENTS.SPIFFE_ID.eq(spiffeId.toASCIIString()));
  }

  public Optional<Client> getClientById(long id) {
    return getClient(CLIENTS.ID.eq(id));
  }

  private Optional<Client> getClient(Condition condition) {
    Record record = dslContext
        .select(CLIENTS.fields())
        .select(CLIENT_OWNERS.ID, CLIENT_OWNERS.NAME)
        .from(CLIENTS)
        .leftJoin(CLIENT_OWNERS)
        .on(CLIENTS.OWNER.eq(CLIENT_OWNERS.ID))
        .where(condition)
        .fetchOne();

    return Optional.ofNullable(recordToClient(record));
  }

  private Client recordToClient(Record record) {
    if (record == null) {
      return null;
    }

    ClientsRecord clientRecord = record.into(CLIENTS);
    GroupsRecord ownerRecord = record.into(CLIENT_OWNERS);

    boolean danglingOwner = clientRecord.getOwner() != null && ownerRecord.getId() == null;
    if (danglingOwner) {
      throw new IllegalStateException(
          String.format(
              "Owner %s for client %s is missing.",
              clientRecord.getOwner(),
              clientRecord.getName()));
    }

    Client client = clientMapper.map(clientRecord);
    if (ownerRecord != null) {
      client.setOwner(ownerRecord.getName());
    }

    return client;
  }

  public ImmutableSet<Client> getClients() {
    List<Client> clients = dslContext
        .select(CLIENTS.fields())
        .select(CLIENT_OWNERS.NAME)
        .from(CLIENTS)
        .leftJoin(CLIENT_OWNERS)
        .on(CLIENTS.OWNER.eq(CLIENT_OWNERS.ID))
        .fetch()
        .map(this::recordToClient);

    return ImmutableSet.copyOf(clients);
  }

  public static class ClientDAOFactory implements DAOFactory<ClientDAO> {
    private final DSLContext jooq;
    private final DSLContext readonlyJooq;
    private final ClientMapper clientMapper;
    private final RowHmacGenerator rowHmacGenerator;

    @Inject public ClientDAOFactory(DSLContext jooq, @Readonly DSLContext readonlyJooq,
        ClientMapper clientMapper, RowHmacGenerator rowHmacGenerator) {
      this.jooq = jooq;
      this.readonlyJooq = readonlyJooq;
      this.clientMapper = clientMapper;
      this.rowHmacGenerator = rowHmacGenerator;
    }

    @Override public ClientDAO readwrite() {
      return new ClientDAO(jooq, clientMapper, rowHmacGenerator);
    }

    @Override public ClientDAO readonly() {
      return new ClientDAO(readonlyJooq, clientMapper, rowHmacGenerator);
    }

    @Override public ClientDAO using(Configuration configuration) {
      DSLContext dslContext = DSL.using(checkNotNull(configuration));
      return new ClientDAO(dslContext, clientMapper, rowHmacGenerator);
    }
  }
}
