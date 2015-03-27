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
import io.dropwizard.jackson.Jackson;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.sql.Date;
import keywhiz.KeywhizService;
import keywhiz.api.model.Secret;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;
import org.skife.jdbi.v2.sqlobject.BindingAnnotation;

@BindingAnnotation(BindSecret.SecretBinderFactory.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface BindSecret {

  public static class SecretBinderFactory implements BinderFactory {
    private static final ObjectMapper mapper = KeywhizService.customizeObjectMapper(Jackson.newObjectMapper());

    @Override public Binder build(Annotation annotation) {
      return new Binder<BindSecret, Secret>() {

        @Override
        public void bind(SQLStatement<?> q, BindSecret bindSecret, Secret secret) {
          q.bind("name", secret.getName());
          q.bind("version", secret.getVersion());
          q.bind("description", secret.getDescription().orElse(null));
          q.bind("secret", secret.getSecret());
          q.bind("createdAt", Date.from(secret.getCreatedAt().toInstant()));
          q.bind("createdBy", secret.getCreatedBy());
          q.bind("updatedAt", Date.from(secret.getUpdatedAt().toInstant()));
          q.bind("updatedBy", secret.getUpdatedBy());
          try {
            q.bind("metadata", mapper.writeValueAsString(secret.getMetadata()));
          } catch (JsonProcessingException e) {
            throw Throwables.propagate(e);
          }
        }
      };
    }
  }
}
