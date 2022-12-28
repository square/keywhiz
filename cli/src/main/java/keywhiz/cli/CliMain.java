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
import keywhiz.cli.configs.ListVersionsActionConfig;
import keywhiz.cli.configs.LoginActionConfig;
import keywhiz.cli.configs.RenameActionConfig;
import keywhiz.cli.configs.RollbackActionConfig;
import keywhiz.cli.configs.UnassignActionConfig;
import keywhiz.cli.configs.UpdateActionConfig;
import keywhiz.cli.configs.GetActionConfig;

/** Keywhiz ACL Command Line Management Utility */
public class CliMain {
  public static void main(String[] args) throws Exception {
    CommandLineParsingContext parsingContext = new CommandLineParsingContext();

    JCommander commander = parsingContext.getCommander();

    try {
      commander.parse(args);
    } catch (ParameterException e) {
      System.err.println("Invalid command: " + e.getMessage());
      commander.usage();
      System.exit(1);
    }

    String command = commander.getParsedCommand();
    JCommander specificCommander = commander.getCommands().get(command);
    Injector injector = Guice.createInjector(
        new CliModule(
            parsingContext.getConfiguration(),
            commander,
            specificCommander,
            command,
            parsingContext.getCommands()));
    CommandExecutor executor = injector.getInstance(CommandExecutor.class);
    executor.executeCommand();
  }

  static class CommandLineParsingContext {
    private final CliConfiguration config;
    private final JCommander commander;
    private final Map<String, Object> commands;

    CommandLineParsingContext() {
      config = new CliConfiguration();

      commander = new JCommander();
      commander.setProgramName("KeyWhiz Configuration Utility");
      commander.addObject(config);

      commands = newCommandMap();
      for (Map.Entry<String, Object> entry : commands.entrySet()) {
        commander.addCommand(entry.getKey(), entry.getValue());
      }
    }

    public CliConfiguration getConfiguration() { return config; }

    public JCommander getCommander() { return commander; }

    public Map<String, Object> getCommands() { return commands; }

    static Map<String, Object> newCommandMap() {
      Map<String, Object> commands = ImmutableMap.<String, Object>builder()
          .put("add", new AddActionConfig())
          .put("assign", new AssignActionConfig())
          .put("delete", new DeleteActionConfig())
          .put("describe", new DescribeActionConfig())
          .put("list", new ListActionConfig())
          .put("login", new LoginActionConfig())
          .put("rename", new RenameActionConfig())
          .put("rollback", new RollbackActionConfig())
          .put("unassign", new UnassignActionConfig())
          .put("update", new UpdateActionConfig())
          .put("versions", new ListVersionsActionConfig())
	  .put("get", new GetActionConfig())
          .build();

      return commands;
    }
  }
}
