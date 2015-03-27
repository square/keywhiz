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

import java.util.Optional;
import java.util.Set;
import keywhiz.api.model.Client;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.GetGeneratedKeys;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.customizers.SingleValueResult;

@RegisterMapper(ClientMapper.class)
public interface ClientDAO {
  @GetGeneratedKeys
  @SqlUpdate("INSERT INTO clients (name, createdBy, updatedBy, description, enabled, automationAllowed) " +
      "VALUES (:name, :user, :user, :desc, true, false)")
  public long createClient(@Bind("name") String name, @Bind("user") String user,
      @Bind("desc") Optional<String> description);

  @SqlUpdate("DELETE FROM clients WHERE id = :id")
  public void deleteClient(@BindBean Client client);

  @SingleValueResult(Client.class)
  @SqlQuery("SELECT id, name, description, createdAt, createdBy, updatedAt, updatedBy, enabled, automationAllowed " +
            "FROM clients WHERE name = :name")
  public Optional<Client> getClient(@Bind("name") String name);

  @SingleValueResult(Client.class)
  @SqlQuery("SELECT id, name, description, createdAt, createdBy, updatedAt, updatedBy, enabled, automationAllowed " +
      "FROM clients WHERE id = :id")
  public Optional<Client> getClientById(@Bind("id") long id);

  // Write update methods as needed.

  @SqlQuery("SELECT id, name, description, createdAt, createdBy, updatedAt, updatedBy, enabled, automationAllowed " +
            "FROM clients")
  public Set<Client> getClients();
}
