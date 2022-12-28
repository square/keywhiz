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
package keywhiz.cli;

import com.beust.jcommander.JCommander;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.inject.name.Named;
import java.io.IOException;
import java.net.HttpCookie;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import keywhiz.cli.commands.AddAction;
import keywhiz.cli.commands.AssignAction;
import keywhiz.cli.commands.DeleteAction;
import keywhiz.cli.commands.DescribeAction;
import keywhiz.cli.commands.ListAction;
import keywhiz.cli.commands.GetAction;
import keywhiz.cli.commands.ListVersionsAction;
import keywhiz.cli.commands.RenameAction;
import keywhiz.cli.commands.RollbackAction;
import keywhiz.cli.commands.UnassignAction;
import keywhiz.cli.commands.UpdateAction;
import keywhiz.cli.configs.AddActionConfig;
import keywhiz.cli.configs.AssignActionConfig;
import keywhiz.cli.configs.CliConfiguration;
import keywhiz.cli.configs.DeleteActionConfig;
import keywhiz.cli.configs.DescribeActionConfig;
import keywhiz.cli.configs.ListActionConfig;
import keywhiz.cli.configs.GetActionConfig;
import keywhiz.cli.configs.ListVersionsActionConfig;
import keywhiz.cli.configs.RenameActionConfig;
import keywhiz.cli.configs.RollbackActionConfig;
import keywhiz.cli.configs.UnassignActionConfig;
import keywhiz.cli.configs.UpdateActionConfig;
import keywhiz.client.KeywhizClient;
import keywhiz.client.KeywhizClient.UnauthorizedException;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

import static com.google.common.base.StandardSystemProperty.USER_HOME;
import static com.google.common.base.StandardSystemProperty.USER_NAME;
import static java.lang.String.format;

public class CommandExecutor {
  public static final String APP_VERSION = "2.1";

  public enum Command {
    ADD,
    ASSIGN,
    DELETE,
    DESCRIBE,
    LIST,
    LOGIN,
    RENAME,
    ROLLBACK,
    UNASSIGN,
    UPDATE,
    VERSIONS,
    GET,
  }

  private final Path cookieDir = Paths.get(USER_HOME.value());

  private final String command;
  private final Map commands;
  private final JCommander parentCommander;
  private final JCommander commander;
  private final CliConfiguration config;
  private final ObjectMapper mapper;

  @Inject
  public CommandExecutor(
      CliConfiguration config,
      @Named("Command") @Nullable String command,
      @Named("CommandMap") Map commands,
      @Named("ParentCommander") JCommander parentCommander,
      @Named("Commander") @Nullable JCommander commander,
      ObjectMapper mapper) {
    this.command = command;
    this.commands = commands;
    this.parentCommander = parentCommander;
    this.commander = commander;
    this.config = config;
    this.mapper = mapper;
  }

  public void executeCommand() throws IOException {
    if (command == null) {
      if (config.version) {
        System.out.println("Version: " + APP_VERSION);
      } else {
        System.err.println("Must specify a command.");
        parentCommander.usage();
      }

      return;
    }

    HttpUrl url;
    if (Strings.isNullOrEmpty(config.url)) {
      url = HttpUrl.parse("https://localhost:4444");
      System.out.println("Server URL not specified (--url flag), assuming " + url);
    } else {
      url = HttpUrl.parse(config.url);
      if (url == null) {
        System.err.print("Invalid URL " + config.url);
        return;
      }
    }

    KeywhizClient client;
    OkHttpClient httpClient;

    String user = config.getUser().orElse(USER_NAME.value());
    Path cookiePath = cookieDir.resolve(format(".keywhiz.%s.cookie", user));

    try {
      List<HttpCookie> cookieList = ClientUtils.loadCookies(cookiePath);
      httpClient = ClientUtils.sslOkHttpClient(config.getDevTrustStore(), cookieList);
      client = new KeywhizClient(mapper, httpClient, url);

      // Try a simple get request to determine whether or not the cookies are still valid
      if(!client.isLoggedIn()) {
        throw new UnauthorizedException();
      }
    } catch (IOException ioe) {
      // Either could not find the cookie file, or the cookies were expired -- must login manually.
      httpClient = ClientUtils.sslOkHttpClient(config.getDevTrustStore(), ImmutableList.of());
      client = new KeywhizClient(mapper, httpClient, url);
      char[] password = null;
      try{
        password = config.getPassword();

      } catch (Exception e){
        password = ClientUtils.readPassword(user);
      }
      client.login(user, password);
      Arrays.fill(password, '\0');
    }
    // Save/update the cookies if we logged in successfully
    ClientUtils.saveCookies(cookiePath);

    Printing printing = new Printing(client);

    Command cmd = Command.valueOf(command.toUpperCase().trim());
    switch (cmd) {
      case LIST:
        new ListAction((ListActionConfig) commands.get(command), client, printing).run();
        break;

      case DESCRIBE:
        new DescribeAction((DescribeActionConfig) commands.get(command), client, printing).run();
        break;

      case ADD:
        new AddAction((AddActionConfig) commands.get(command), client, mapper).run();
        break;

      case UPDATE:
        new UpdateAction((UpdateActionConfig) commands.get(command), client, mapper).run();
        break;

      case DELETE:
        new DeleteAction((DeleteActionConfig) commands.get(command), client).run();
        break;

      case ASSIGN:
        new AssignAction((AssignActionConfig) commands.get(command), client).run();
        break;

      case UNASSIGN:
        new UnassignAction((UnassignActionConfig) commands.get(command), client).run();
        break;

      case VERSIONS:
        new ListVersionsAction((ListVersionsActionConfig) commands.get(command), client, printing).run();
        break;

      case ROLLBACK:
        new RollbackAction((RollbackActionConfig) commands.get(command), client).run();
        break;

      case RENAME:
        new RenameAction((RenameActionConfig) commands.get(command), client).run();

      case GET:
        new GetAction((GetActionConfig) commands.get(command), client, printing).run();
        break;

      case LOGIN:
        // User is already logged in at this point
        break;

      default:
        commander.usage();
    }
  }
}

