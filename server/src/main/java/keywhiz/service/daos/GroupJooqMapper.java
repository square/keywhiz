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

import keywhiz.api.model.Group;
import org.jooq.Record;
import org.jooq.RecordMapper;

import static keywhiz.jooq.tables.Groups.GROUPS;

/**
 * Comments in {@link ClientMapper} are applicable here.
 */
class GroupJooqMapper implements RecordMapper<Record, Group> {
  public Group map(Record r) {
    return new Group(
        r.getValue(GROUPS.ID),
        r.getValue(GROUPS.NAME),
        r.getValue(GROUPS.DESCRIPTION),
        r.getValue(GROUPS.CREATEDAT),
        r.getValue(GROUPS.CREATEDBY),
        r.getValue(GROUPS.UPDATEDAT),
        r.getValue(GROUPS.UPDATEDBY));
  }
}
