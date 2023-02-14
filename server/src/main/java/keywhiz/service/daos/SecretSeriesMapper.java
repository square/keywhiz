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

import javax.inject.Inject;
import keywhiz.api.model.SecretSeries;
import keywhiz.jooq.tables.records.SecretsRecord;
import org.jooq.DSLContext;
import org.jooq.RecordMapper;

public class SecretSeriesMapper implements RecordMapper<SecretsRecord, SecretSeries> {
  private final SharedSecretSeriesMapper mapper;

  public SecretSeriesMapper(SharedSecretSeriesMapper mapper) {
    this.mapper = mapper;
  }

  public SecretSeries map(SecretsRecord r) {
    return mapper.map(
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
        r.getCurrent()
    );
  }

  public static class SecretSeriesMapperFactory {
    private final SharedSecretSeriesMapper.SharedSecretSeriesMapperFactory
        sharedSecretSeriesMapperFactory;

    @Inject
    public SecretSeriesMapperFactory(
        SharedSecretSeriesMapper.SharedSecretSeriesMapperFactory sharedSecretSeriesMapperFactory) {
      this.sharedSecretSeriesMapperFactory = sharedSecretSeriesMapperFactory;
    }

    public SecretSeriesMapper using(DSLContext context) {
      return new SecretSeriesMapper(sharedSecretSeriesMapperFactory.using(context));
    }
  }
}
