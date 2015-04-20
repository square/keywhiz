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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import io.dropwizard.jackson.Jackson;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import keywhiz.KeywhizService;
import keywhiz.api.model.SecretSeries;
import keywhiz.jooq.tables.records.SecretsRecord;
import org.jooq.DSLContext;
import org.jooq.tools.json.JSONObject;

import static keywhiz.jooq.tables.Secrets.SECRETS;

public class SecretSeriesJooqDao {
  private final DSLContext dslContext;
  private final ObjectMapper
      mapper = KeywhizService.customizeObjectMapper(Jackson.newObjectMapper());

  @Inject
  public SecretSeriesJooqDao(DSLContext dslContext) {
    this.dslContext = dslContext;
  }

  long createSecretSeries(String name, String creator, String description, @Nullable String type,
      @Nullable Map<String, String> generationOptions) {
    SecretsRecord r =  dslContext.newRecord(SECRETS);

    r.setName(name);;
    r.setDescription(description);
    r.setCreatedby(creator);
    r.setUpdatedby(creator);
    r.setType(type);
    if (generationOptions != null) {
      try {
        r.setOptions(mapper.writeValueAsString(generationOptions));
      } catch (JsonProcessingException e) {
        // Serialization of a Map<String, String> can never fail.
        throw Throwables.propagate(e);
      }
    } else {
      r.setOptions("{}");
    }
    r.store();

    return r.getId();
  }

  public Optional<SecretSeries> getSecretSeriesById(long id) {
    SecretsRecord r = dslContext.fetchOne(SECRETS, SECRETS.ID.eq(Math.toIntExact(id)));
    return Optional.ofNullable(r).map(
        (rec) -> rec.map(new SecretSeriesJooqMapper()));
  }

  public Optional<SecretSeries> getSecretSeriesByName(String name) {
    SecretsRecord r = dslContext.fetchOne(SECRETS, SECRETS.NAME.eq(name));
    return Optional.ofNullable(r).map(
        (rec) -> rec.map(new SecretSeriesJooqMapper()));
  }

  public ImmutableList<SecretSeries> getSecretSeries() {
    List<SecretSeries> r = dslContext
        .select()
        .from(SECRETS)
        .fetch()
        .map(new SecretSeriesJooqMapper());

    return ImmutableList.copyOf(r);
  }

  public void deleteSecretSeriesByName(String name) {
    dslContext
        .delete(SECRETS)
        .where(SECRETS.NAME.eq(name))
        .execute();
  }

  public void deleteSecretSeriesById(long id) {
    dslContext.delete(SECRETS)
        .where(SECRETS.ID.eq(Math.toIntExact(id)))
        .execute();
  }
}
