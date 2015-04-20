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
import javax.inject.Inject;
import keywhiz.KeywhizService;
import keywhiz.api.model.SecretContent;
import keywhiz.jooq.tables.records.SecretsContentRecord;
import org.jooq.DSLContext;

import static keywhiz.jooq.tables.SecretsContent.SECRETS_CONTENT;

/**
 * Interacts with 'secrets_content' table and actions on {@link SecretContent} entities.
 */
public class SecretContentDAO {
  private final DSLContext dslContext;
  private final ObjectMapper
      mapper = KeywhizService.customizeObjectMapper(Jackson.newObjectMapper());

  @Inject
  public SecretContentDAO(DSLContext dslContext) {
    this.dslContext = dslContext;
  }

  public long createSecretContent(long secretId, String encryptedContent, String version,
      String creator, Map<String, String> metadata) {
    SecretsContentRecord r = dslContext.newRecord(SECRETS_CONTENT);

    String jsonMetadata;
    try {
      jsonMetadata = mapper.writeValueAsString(metadata);
    } catch (JsonProcessingException e) {
      // Serialization of a Map<String, String> can never fail.
      throw Throwables.propagate(e);
    }

    r.setSecretid(Math.toIntExact(secretId));
    r.setEncryptedContent(encryptedContent);
    r.setVersion(version);
    r.setCreatedby(creator);
    r.setUpdatedby(creator);
    r.setMetadata(jsonMetadata);
    r.store();

    return r.getId();
  }

  public Optional<SecretContent> getSecretContentById(long id) {
    SecretsContentRecord r = dslContext.fetchOne(SECRETS_CONTENT,
        SECRETS_CONTENT.ID.eq(Math.toIntExact(id)));
    return Optional.ofNullable(r).map(
        (rec) -> rec.map(new SecretContentMapper()));
  }

  public Optional<SecretContent> getSecretContentBySecretIdAndVersion(long secretId,
      String version) {
    SecretsContentRecord r = dslContext.fetchOne(SECRETS_CONTENT,
        SECRETS_CONTENT.SECRETID.eq(Math.toIntExact(secretId))
            .and(SECRETS_CONTENT.VERSION.eq(version)));
    return Optional.ofNullable(r).map((rec) -> rec.map(new SecretContentMapper()));
  }

  public ImmutableList<SecretContent> getSecretContentsBySecretId(long secretId) {
    List<SecretContent> r = dslContext
        .select()
        .from(SECRETS_CONTENT)
        .where(SECRETS_CONTENT.SECRETID.eq(Math.toIntExact(secretId)))
        .fetch()
        .map(new SecretContentMapper());

    return ImmutableList.copyOf(r);
  }

  public void deleteSecretContentBySecretIdAndVersion(long secretId, String version) {
    dslContext
        .delete(SECRETS_CONTENT)
        .where(SECRETS_CONTENT.SECRETID.eq(Math.toIntExact(secretId))
            .and(SECRETS_CONTENT.VERSION.eq(version)))
        .execute();
  }

  public ImmutableList<String> getVersionFromSecretId(long secretId) {
    List<String> r = dslContext
        .select(SECRETS_CONTENT.VERSION)
        .from(SECRETS_CONTENT)
        .where(SECRETS_CONTENT.SECRETID.eq(Math.toIntExact(secretId)))
        .fetch(SECRETS_CONTENT.VERSION);

    return ImmutableList.copyOf(r);
  }
}
