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
import javax.annotation.Nullable;
import keywhiz.api.model.SecretSeries;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.GetGeneratedKeys;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.customizers.SingleValueResult;

/** Interacts with 'secrets' table and actions on {@link SecretSeries} entities. */
@RegisterMapper(SecretSeriesMapper.class)
public interface SecretSeriesDAO {
  @GetGeneratedKeys
  @SqlUpdate("INSERT INTO secrets (name, description, createdBy, updatedBy, metadata, type, options) " +
      "VALUES (:name, :description, :creator, :creator, :metadata, :type, COALESCE(:options,'{}'))")
  long createSecretSeries(@Bind("name") String name, @Bind("creator") String creator,
      @Bind("metadata") Map<String, String> metadata, @Bind("description") String description,
      @Nullable @Bind("type") String type,
      @Nullable @Bind("options") Map<String, String> generationOptions);

  @SingleValueResult(SecretSeries.class)
  @SqlQuery("SELECT id, name, description, createdAt, createdBy, updatedAt, updatedBy, metadata, " +
      "type, options FROM secrets WHERE id = :id")
  Optional<SecretSeries> getSecretSeriesById(@Bind("id") long id);

  @SingleValueResult(SecretSeries.class)
  @SqlQuery("SELECT id, name, description, createdAt, createdBy, updatedAt, updatedBy, metadata, " +
      "type, options FROM secrets WHERE name = :name")
  Optional<SecretSeries> getSecretSeriesByName(@Bind("name") String name);

  @SqlQuery("SELECT id, name, description, createdAt, createdBy, updatedAt, updatedBy, metadata, " +
      "type, options FROM secrets")
  ImmutableList<SecretSeries> getSecretSeries();

  @SqlUpdate("DELETE FROM secrets WHERE name = :name")
  void deleteSecretSeriesByName(@Bind("name") String name);

  @SqlUpdate("DELETE FROM secrets WHERE id = :id")
  void deleteSecretSeriesById(@Bind("id") long id);
}
