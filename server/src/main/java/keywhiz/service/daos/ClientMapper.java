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

import keywhiz.api.ApiDate;
import keywhiz.api.model.Client;
import keywhiz.jooq.tables.records.ClientsRecord;
import org.jooq.RecordMapper;
import java.util.Optional;

/**
 * Jooq has the ability to map records to classes using Reflection. We however need a mapper because
 * the constructor's parameter and the columns in the database do not share the same order.
 *
 * In general, I feel having a mapper is cleaner, so it might not be a bad thing.
 */
class ClientMapper implements RecordMapper<ClientsRecord, Client> {
  public Client map(ClientsRecord r) {
    ApiDate lastSeen = Optional.ofNullable(r.getLastseen()).map(ApiDate::new).orElse(null);
    ApiDate expiration = Optional.ofNullable(r.getExpiration()).map(ApiDate::new).orElse(null);

    return new Client(
        r.getId(),
        r.getName(),
        r.getDescription(),
        new ApiDate(r.getCreatedat()),
        r.getCreatedby(),
        new ApiDate(r.getUpdatedat()),
        r.getUpdatedby(),
        lastSeen,
        expiration,
        r.getEnabled(),
        r.getAutomationallowed()
    );
  }
}
