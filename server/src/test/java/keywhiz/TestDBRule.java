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
package keywhiz;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.java8.jdbi.OptionalContainerFactory;
import io.dropwizard.java8.jdbi.args.LocalDateTimeArgumentFactory;
import io.dropwizard.java8.jdbi.args.LocalDateTimeMapper;
import io.dropwizard.java8.jdbi.args.OptionalArgumentFactory;
import io.dropwizard.jdbi.ImmutableListContainerFactory;
import io.dropwizard.jdbi.ImmutableSetContainerFactory;
import io.dropwizard.jdbi.args.JodaDateTimeArgumentFactory;
import io.dropwizard.jdbi.args.JodaDateTimeMapper;
import keywhiz.service.daos.MapArgumentFactory;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcConnectionPool;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.RenderNameStyle;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.junit.rules.ExternalResource;
import org.skife.jdbi.v2.DBI;

import static com.google.common.base.Preconditions.checkState;

/**
 * Compatible with JUnit {@link org.junit.Rule} or {@link org.junit.ClassRule}, this class provides
 * a DBI instance for an H2 in-memory database. The database is migrated at startup and
 * automatically closed down.
 */
public class TestDBRule extends ExternalResource {
  private final ObjectMapper mapper = KeywhizService.customizeObjectMapper(Jackson.newObjectMapper());
  private DSLContext dslContext;
  private DBI dbi;
  private JdbcConnectionPool dataSource;

  @Override public void before() throws Throwable {
    super.before();
    dataSource = JdbcConnectionPool.create("jdbc:h2:mem:test", "", "");

    Flyway flyway = new Flyway();
    flyway.setDataSource(dataSource);
    flyway.migrate();

    dslContext = DSL.using(dataSource, SQLDialect.H2,
        new Settings()
            .withRenderSchema(false)
            .withRenderNameStyle(RenderNameStyle.AS_IS));

    dbi = new DBI(dataSource);
    dbi.registerArgumentFactory(new MapArgumentFactory(mapper));
    dbi.registerArgumentFactory(
        new io.dropwizard.jdbi.args.OptionalArgumentFactory("org.h2.Driver"));
    dbi.registerContainerFactory(new ImmutableListContainerFactory());
    dbi.registerContainerFactory(new ImmutableSetContainerFactory());
    dbi.registerContainerFactory(new io.dropwizard.jdbi.OptionalContainerFactory());
    dbi.registerArgumentFactory(new JodaDateTimeArgumentFactory());
    dbi.registerMapper(new JodaDateTimeMapper());
    dbi.registerArgumentFactory(new OptionalArgumentFactory("org.h2.Driver"));
    dbi.registerContainerFactory(new OptionalContainerFactory());
    dbi.registerArgumentFactory(new LocalDateTimeArgumentFactory());
    dbi.registerMapper(new LocalDateTimeMapper());
  }

  @Override public void after() {
    dataSource.dispose();
    super.after();
  }

  public DSLContext jooqContext() {
    checkState(dslContext != null, "JOOQ DSLContext not yet initialized.");
    return dslContext;
  }

  /**
   * @return a ready-to-go DBI instance after before() has run.
   */
  public DBI getDbi() {
    checkState(dbi != null, "DBI not yet initialized.");
    return dbi;
  }
}
