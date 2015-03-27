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

package keywhiz.jdbi;

import com.codahale.metrics.jdbi.strategies.DelegatingStatementNameStrategy;
import com.codahale.metrics.jdbi.strategies.NameStrategies;
import io.dropwizard.jdbi.DBIFactory;

/** Directly pulled from {@link DBIFactory}. Copied because original class has private access. */
public class SanerNamingStrategy extends DelegatingStatementNameStrategy {
  public SanerNamingStrategy() {
    super(NameStrategies.CHECK_EMPTY,
        NameStrategies.CONTEXT_CLASS,
        NameStrategies.CONTEXT_NAME,
        NameStrategies.SQL_OBJECT,
        statementContext -> "raw-sql");
  }
}
