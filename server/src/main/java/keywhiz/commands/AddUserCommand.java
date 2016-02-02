/*
 * Copyright (C) 2016 Square, Inc.
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
package keywhiz.commands;

import com.codahale.metrics.MetricRegistry;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import keywhiz.KeywhizConfig;
import keywhiz.service.daos.UserDAO;
import keywhiz.utility.DSLContexts;
import net.sourceforge.argparse4j.inf.Namespace;
import org.jooq.DSLContext;

import javax.sql.DataSource;
import java.io.Console;

public class AddUserCommand extends ConfiguredCommand<KeywhizConfig> {
  public AddUserCommand() {
    super("add-user", "Adds a new admin user");
  }

  @Override protected void run(Bootstrap<KeywhizConfig> bootstrap, Namespace namespace,
                               KeywhizConfig config) throws Exception {
    DataSource dataSource = config.getDataSourceFactory()
      .build(new MetricRegistry(), "add-user-datasource");

    Console console = System.console();
    System.out.format("New username:");
    String user = console.readLine();
    System.out.format("password for '%s': ", user);
    char[] password = console.readPassword();
    DSLContext dslContext = DSLContexts.databaseAgnostic(dataSource);
    new UserDAO(dslContext).createUser(user, new String(password));
  }
}
