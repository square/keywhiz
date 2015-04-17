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

import keywhiz.api.model.Client;
import org.jooq.Record;
import org.jooq.RecordMapper;

import static keywhiz.jooq.tables.Clients.CLIENTS;

/**
 * Jooq has the ability to map records to classes using Reflection. We however need a mapper because
 * the constructor's parameter and the columns in the database do not share the same order.
 *
 * In general, I feel having a mapper is cleaner, so it might not be a bad thing.
 *
 * The way jooq built their generic API is somewhat broken, so we need to implement
 * RecordMapper<Record, Client> instead of RecordMapper<ClientsRecord, Client>. I'll file a task
 * and follow up on this issue.
 *
 * Also, when doing JOINS, I don't know if there's a good way to preserve the right Record type.
 */
class ClientMapper implements RecordMapper<Record, Client> {
  public Client map(Record r) {
    // Lots of :(
    return new Client(
        r.getValue(CLIENTS.ID),
        r.getValue(CLIENTS.NAME),
        r.getValue(CLIENTS.DESCRIPTION),
        r.getValue(CLIENTS.CREATEDAT),
        r.getValue(CLIENTS.CREATEDBY),
        r.getValue(CLIENTS.UPDATEDAT),
        r.getValue(CLIENTS.UPDATEDBY),
        r.getValue(CLIENTS.ENABLED),
        r.getValue(CLIENTS.AUTOMATIONALLOWED));
  }
}
