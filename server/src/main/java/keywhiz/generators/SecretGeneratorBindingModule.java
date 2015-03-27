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

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.MapBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static keywhiz.utility.SecretTemplateCompiler.validName;

/**
 * Registers {@link SecretGenerator} classes and their associated names with guice. A guice
 * multibinding is used so consumers can request injection of
 * <code>Map&lt;String, SecretGenerator&gt;</code> to get all the instantiated SecretGenerators and names.
 */
public class SecretGeneratorBindingModule extends AbstractModule {
  private static final Logger logger = LoggerFactory.getLogger(SecretGeneratorBindingModule.class);

  private MapBinder<String, SecretGenerator> mapBinder;

  @Override protected void configure() {}

  /**
   * Register a SecretGenerator and its name.
   *
   * @param name Name associated with generator.
   * @param generatorClass Class of type SecretGenerator for guice to instantiate.
   */
  public void bindSecretGenerator(String name, Class<? extends SecretGenerator> generatorClass) {
    checkArgument(validName(name));
    checkNotNull(generatorClass);
    logger.debug("Registering SecretGenerator {} -> {}", name, generatorClass.getSimpleName());

    if (mapBinder == null) {
      mapBinder = MapBinder.newMapBinder(binder(), String.class, SecretGenerator.class);
    }
    mapBinder.addBinding(name).to(generatorClass);
  }
}
