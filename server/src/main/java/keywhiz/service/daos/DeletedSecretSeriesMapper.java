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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import javax.inject.Inject;
import keywhiz.api.model.SecretSeries;
import keywhiz.jooq.tables.records.DeletedSecretsRecord;
import org.jooq.DSLContext;
import org.jooq.RecordMapper;

public class DeletedSecretSeriesMapper implements RecordMapper<DeletedSecretsRecord, SecretSeries> {
  private static final TypeReference<Map<String, String>> MAP_STRING_STRING_TYPE =
      new TypeReference<>() {};

  private final ObjectMapper mapper;
  private final GroupDAO groupDAO;

  public DeletedSecretSeriesMapper(
      ObjectMapper mapper,
      GroupDAO groupDAO) {
    this.mapper = mapper;
    this.groupDAO = groupDAO;
  }

  public SecretSeries map(DeletedSecretsRecord r) {
    return SharedSecretSeriesMapper.map(
        r.getOwner(),
        r.getName(),
        r.getId(),
        r.getDescription(),
        r.getCreatedat(),
        r.getCreatedby(),
        r.getUpdatedat(),
        r.getUpdatedby(),
        r.getType(),
        r.getOptions(),
        r.getCurrent(),
        groupDAO,
        mapper
    );
  }

  public static class DeletedSecretSeriesMapperFactory {
    private final ObjectMapper mapper;
    private final GroupDAO.GroupDAOFactory groupDAOFactory;

    @Inject
    public DeletedSecretSeriesMapperFactory(
        ObjectMapper mapper,
        GroupDAO.GroupDAOFactory groupDAOFactory) {
      this.mapper = mapper;
      this.groupDAOFactory = groupDAOFactory;
    }

    public DeletedSecretSeriesMapper using(DSLContext context) {
      return new DeletedSecretSeriesMapper(
          mapper,
          groupDAOFactory.using(context));
    }
  }
}
