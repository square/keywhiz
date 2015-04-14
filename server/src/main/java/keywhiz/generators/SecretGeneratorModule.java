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

package keywhiz.generators;

import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import javax.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.multibindings.MapBinder;
import java.util.Map;
import keywhiz.service.daos.SecretController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Registers configured {@link SecretGenerator}s and {@link SecretGeneratorFactory}s, with their
 * associated names, using guice. A guice multibinding is used so consumers can request injection of
 * <code>Map&lt;String, SecretGenerator&gt;</code> to get all the instantiated SecretGenerators and names.
 */
public class SecretGeneratorModule extends AbstractModule {
  private static final Logger logger = LoggerFactory.getLogger(SecretGeneratorModule.class);

  private final Map<String, SecretGeneratorFactory<?>> secretGeneratorFactories;
  private final Map<String, SecretGenerator<?>> secretGenerators;

  public SecretGeneratorModule(Map<String, SecretGeneratorFactory<?>> secretGeneratorFactories,
                               Map<String, SecretGenerator<?>> secretGenerators) {
    checkArgument(Sets.intersection(secretGeneratorFactories.keySet(), secretGenerators.keySet()).isEmpty(),
        "SecretGeneratorFactories and SecretGenerators cannot share names.");
    this.secretGeneratorFactories = secretGeneratorFactories;
    this.secretGenerators = secretGenerators;
  }

  @Override protected void configure() {
    MapBinder<String, SecretGenerator> mapBinder =
        MapBinder.newMapBinder(binder(), String.class, SecretGenerator.class);
    // Register the factories
    for (Map.Entry<String, SecretGeneratorFactory<?>> entry : secretGeneratorFactories.entrySet()) {
      logger.info("Binding secret generator provider named {}", entry.getKey());
      mapBinder.addBinding(entry.getKey()).toProvider(new SecretGeneratorProvider(entry.getValue()));
    }

    // Register the instances
    for (Map.Entry<String, SecretGenerator<?>> entry : secretGenerators.entrySet()) {
      logger.info("Binding secret generator named {}", entry.getKey());
      mapBinder.addBinding(entry.getKey()).toInstance(entry.getValue());
    }
  }

  /**
   * Private Provider which creates SecretGenerators from SecretGeneratorFactories. Objects
   * necessary for each factory's <code>create</code> method are injected as fields.
   */
  private static class SecretGeneratorProvider implements Provider<SecretGenerator> {
    @Inject private SecretController secretController;
    private final SecretGeneratorFactory factory;

    public SecretGeneratorProvider(SecretGeneratorFactory factory) {
      this.factory = checkNotNull(factory);
    }

    @Override public SecretGenerator get() {
      return factory.create(secretController);
    }
  }
}
