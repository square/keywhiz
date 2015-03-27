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
import com.beust.jcommander.ParameterException;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.Map;
import keywhiz.cli.configs.AddActionConfig;
import keywhiz.cli.configs.AssignActionConfig;
import keywhiz.cli.configs.CliConfiguration;
import keywhiz.cli.configs.DeleteActionConfig;
import keywhiz.cli.configs.DescribeActionConfig;
import keywhiz.cli.configs.ListActionConfig;
import keywhiz.cli.configs.LoginActionConfig;
import keywhiz.cli.configs.UnassignActionConfig;

/** Keywhiz ACL Command Line Management Utility */
public class CliMain {
  public static void main(String[] args) throws Exception {
    CliConfiguration config = new CliConfiguration();

    JCommander commander = new JCommander();
    Map<String, Object> commands = ImmutableMap.<String, Object>builder()
        .put("login", new LoginActionConfig())
        .put("list", new ListActionConfig())
        .put("describe", new DescribeActionConfig())
        .put("add", new AddActionConfig())
        .put("delete", new DeleteActionConfig())
        .put("assign", new AssignActionConfig())
        .put("unassign", new UnassignActionConfig())
        .build();
    commander.setProgramName("KeyWhiz Configuration Utility");
    commander.addObject(config);

    for (Map.Entry<String, Object> entry : commands.entrySet()) {
      commander.addCommand(entry.getKey(), entry.getValue());
    }

    try {
      commander.parse(args);
    } catch (ParameterException e) {
      System.err.println("Invalid command: " + e.getMessage());
      commander.usage();
      System.exit(1);
    }

    String command = commander.getParsedCommand();
    JCommander specificCommander = commander.getCommands().get(command);
    Injector injector = Guice.createInjector(new CliModule(config, commander, specificCommander,
        command, commands));
    CommandExecutor executor = injector.getInstance(CommandExecutor.class);
    executor.executeCommand();
  }
}
