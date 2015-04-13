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

import com.google.common.collect.ImmutableList;
import java.util.Map;
import java.util.Optional;
import keywhiz.api.model.SecretContent;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.GetGeneratedKeys;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.customizers.SingleValueResult;

/** Interacts with 'secrets_content' table and actions on {@link SecretContent} entities. */
@RegisterMapper(SecretContentMapper.class)
public interface SecretContentDAO {
  @GetGeneratedKeys
  @SqlUpdate("INSERT INTO secrets_content (secretId, encrypted_content, version, createdBy, updatedBy, metadata) " +
             "VALUES (:secretId, :encryptedContent, :version, :creator, :creator, :metadata)")
  long createSecretContent(@Bind("secretId") long secretId,
      @Bind("encryptedContent") String encryptedContent, @Bind("version") String version,
      @Bind("creator") String creator, @Bind("metadata") Map<String, String> metadata);

  @SingleValueResult(SecretContent.class)
  @SqlQuery("SELECT id, secretId, encrypted_content, version, createdAt, createdBy, updatedAt, updatedBy, metadata " +
            "FROM secrets_content WHERE id = :id")
  Optional<SecretContent> getSecretContentById(@Bind("id") long id);

  @SingleValueResult(SecretContent.class)
  @SqlQuery("SELECT id, secretId, encrypted_content, version, createdAt, createdBy, updatedAt, updatedBy, metadata " +
            "FROM secrets_content WHERE secretId = :secretId AND version = :version")
  Optional<SecretContent> getSecretContentBySecretIdAndVersion(
      @Bind("secretId") long secretId, @Bind("version") String version);

  @SqlQuery("SELECT id, secretId, encrypted_content, version, createdAt, createdBy, updatedAt, updatedBy, metadata " +
            "FROM secrets_content WHERE secretId = :secretId")
  ImmutableList<SecretContent> getSecretContentsBySecretId(@Bind("secretId") long secretId);

  @SqlUpdate("DELETE FROM secrets_content WHERE secretId = :secretId AND version = :version")
  void deleteSecretContentBySecretIdAndVersion(
      @Bind("secretId") long secretId, @Bind("version") String version);

  @SqlQuery("SELECT version FROM secrets_content WHERE secretId = :secretId ORDER BY createdAt DESC")
  ImmutableList<String> getVersionFromSecretId(@Bind("secretId") long secretId);
}
