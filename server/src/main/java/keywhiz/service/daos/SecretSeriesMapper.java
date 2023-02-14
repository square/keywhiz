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
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import keywhiz.api.ApiDate;
import keywhiz.api.model.Group;
import keywhiz.api.model.SecretSeries;
import keywhiz.jooq.tables.records.SecretsRecord;
import org.jooq.DSLContext;
import org.jooq.RecordMapper;

public class SecretSeriesMapper implements RecordMapper<SecretsRecord, SecretSeries> {
  private static final TypeReference<Map<String, String>> MAP_STRING_STRING_TYPE =
      new TypeReference<>() {};

  private final ObjectMapper mapper;
  private final GroupDAO groupDAO;

  public SecretSeriesMapper(
      ObjectMapper mapper,
      GroupDAO groupDAO) {
    this.mapper = mapper;
    this.groupDAO = groupDAO;
  }

  public SecretSeries map(SecretsRecord r) {
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

  public static class SecretSeriesMapperFactory {
    private final ObjectMapper mapper;
    private final GroupDAO.GroupDAOFactory groupDAOFactory;

    @Inject
    public SecretSeriesMapperFactory(
        ObjectMapper mapper,
        GroupDAO.GroupDAOFactory groupDAOFactory) {
      this.mapper = mapper;
      this.groupDAOFactory = groupDAOFactory;
    }

    public SecretSeriesMapper using(DSLContext context) {
      return new SecretSeriesMapper(
          mapper,
          groupDAOFactory.using(context));
    }
  }
}
