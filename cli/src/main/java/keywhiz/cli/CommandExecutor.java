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
import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.OkHttpClient;
import java.io.IOException;
import java.net.CookieManager;
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
import keywhiz.cli.commands.UnassignAction;
import keywhiz.cli.configs.AddActionConfig;
import keywhiz.cli.configs.AssignActionConfig;
import keywhiz.cli.configs.CliConfiguration;
import keywhiz.cli.configs.DeleteActionConfig;
import keywhiz.cli.configs.DescribeActionConfig;
import keywhiz.cli.configs.ListActionConfig;
import keywhiz.cli.configs.UnassignActionConfig;
import keywhiz.client.KeywhizClient;
import keywhiz.client.KeywhizClient.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.StandardSystemProperty.USER_HOME;
import static com.google.common.base.StandardSystemProperty.USER_NAME;
import static java.lang.String.format;

public class CommandExecutor {
    
  private static final Logger logger = LoggerFactory.getLogger(CommandExecutor.class);
    
  public static final String APP_VERSION = "2.0";

  public enum Command { LOGIN, LIST, DESCRIBE, ADD, DELETE, ASSIGN, UNASSIGN }

  private final Path cookieDir = Paths.get(USER_HOME.value());

  private final String command;
  private final Map commands;
  private final JCommander parentCommander;
  private final JCommander commander;
  private final CliConfiguration config;
  private final ObjectMapper mapper;

  @Inject
  public CommandExecutor(CliConfiguration config, @Named("Command") @Nullable String command,
      @Named("CommandMap") Map commands, @Named("ParentCommander") JCommander parentCommander,
      @Named("Commander") @Nullable JCommander commander, ObjectMapper mapper) {
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
        logger.info("Version: " + APP_VERSION);
      } else {
        logger.error("Must specify a command.");
        parentCommander.usage();
      }

      return;
    }

    HttpUrl url;
    if (Strings.isNullOrEmpty(config.url)) {
      url = HttpUrl.parse("https://localhost:4444");
      logger.info("Server URL not specified (--url flag), assuming " + url);
    } else {
      url = HttpUrl.parse(config.url);
      if (url == null) {
        logger.error("Invalid URL " + config.url);
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
    } catch (IOException e) {
      // Either could not find the cookie file, or the cookies were expired -- must login manually.
      httpClient = ClientUtils.sslOkHttpClient(config.getDevTrustStore(), ImmutableList.of());
      client = new KeywhizClient(mapper, httpClient, url);

      char[] password = ClientUtils.readPassword(user);
      client.login(user, password);
      Arrays.fill(password, '\0');
    }
    // Save/update the cookies if we logged in successfully
    ClientUtils.saveCookies((CookieManager) httpClient.getCookieHandler(), cookiePath);

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

      case DELETE:
        new DeleteAction((DeleteActionConfig) commands.get(command), client).run();
        break;

      case ASSIGN:
        new AssignAction((AssignActionConfig) commands.get(command), client).run();
        break;

      case UNASSIGN:
        new UnassignAction((UnassignActionConfig) commands.get(command), client).run();
        break;

      case LOGIN:
        // User is already logged in at this point
        break;

      default:
        commander.usage();
    }
  }
}
