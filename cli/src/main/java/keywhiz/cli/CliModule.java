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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Names;
import com.google.inject.util.Providers;
import io.dropwizard.jackson.Jackson;
import java.util.Map;
import keywhiz.cli.configs.CliConfiguration;

public class CliModule extends AbstractModule {
  private final CliConfiguration config;
  private final JCommander parentCommander;
  private final JCommander commander;
  private final String command;
  private final Map commands;

  public CliModule(CliConfiguration config, JCommander parentCommander, JCommander commander,
      String command, Map<String, Object> commands) {
    this.config = config;
    this.parentCommander = parentCommander;
    this.commander = commander;
    this.command = command;
    this.commands = commands;
  }

  @Override protected void configure() {
    bind(CliConfiguration.class).toInstance(config);

    bind(JCommander.class).annotatedWith(Names.named("ParentCommander"))
        .toInstance(parentCommander);

    if (commander == null) {
      bind(JCommander.class).annotatedWith(Names.named("Commander"))
          .toProvider(Providers.of((JCommander) null));
      bind(String.class).annotatedWith(Names.named("Command"))
          .toProvider(Providers.of((String) null));
    } else {
      bind(JCommander.class).annotatedWith(Names.named("Commander")).toInstance(commander);
      bindConstant().annotatedWith(Names.named("Command")).to(command);
    }

    bind(Map.class).annotatedWith(Names.named("CommandMap")).toInstance(commands);
  }

  @Provides public ObjectMapper generalMapper() {
    /**
     * Customizes ObjectMapper for common settings.
     *
     * @param objectMapper to be customized
     * @return customized input factory
     */
    ObjectMapper objectMapper = Jackson.newObjectMapper();
    objectMapper.registerModule(new Jdk8Module());
    objectMapper.registerModules(new JavaTimeModule());
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    return objectMapper;
  }
}
