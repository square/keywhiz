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
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Injector;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.db.ManagedDataSource;
import io.dropwizard.setup.Bootstrap;
import java.io.Console;
import keywhiz.Environments;
import keywhiz.inject.InjectorFactory;
import keywhiz.KeywhizConfig;
import keywhiz.service.daos.UserDAO;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

public class AddUserCommand extends ConfiguredCommand<KeywhizConfig> {
  public AddUserCommand() {
    super("add-user", "Adds a new admin user");
  }

  private static final class Args {
    private static final String USER = "user";
    private static final String PASSWORD = "password";
  }

  @Override public void configure(Subparser subparser) {
    super.configure(subparser);

    subparser.addArgument("--user")
        .dest(Args.USER)
        .type(String.class)
        .help("User name");

    subparser.addArgument("--password")
        .dest(Args.PASSWORD)
        .type(String.class)
        .help("User password");
  }

  private static String getUser(Namespace namespace) {
    String _user = namespace.getString(Args.USER);
    return _user;
  }

  private static String getPassword(Namespace namespace) {
    String _password = namespace.getString(Args.PASSWORD);
    return _password;
  }

  @Override protected void run(Bootstrap<KeywhizConfig> bootstrap, Namespace namespace,
                               KeywhizConfig config) throws Exception {
    String user = getUser(namespace);
    String password = getPassword(namespace);
    getUserDAO(bootstrap, config).createUser(user, password);
  }

  @VisibleForTesting
  static UserDAO getUserDAO(Bootstrap<KeywhizConfig> bootstrap, KeywhizConfig config) {
    ManagedDataSource dataSource = config.getDataSourceFactory()
        .build(new MetricRegistry(), "add-user-datasource");

    Injector injector = InjectorFactory.createInjector(
        config,
        Environments.fromBootstrap(bootstrap),
        dataSource);

    return injector.getInstance(UserDAO.class);
  }
}

