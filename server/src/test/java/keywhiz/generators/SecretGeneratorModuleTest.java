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

import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import keywhiz.api.model.Secret;
import keywhiz.service.daos.SecretController;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

@RunWith(MockitoJUnitRunner.class)
public class SecretGeneratorModuleTest {
  private static final Key<Map<String, SecretGenerator>> GENERATOR_MAP_KEY =
      Key.get(new TypeLiteral<Map<String, SecretGenerator>>(){});
  private static final DummySecretGeneratorFactory DUMMY_FACTORY = new DummySecretGeneratorFactory();
  private static final DummySecretGenerator DUMMY_GENERATOR = new DummySecretGenerator();

  @Mock private SecretController secretController;

  // A SecretJooqDao will need to be bound.
  private final Module secretControllerModule = new AbstractModule() {
    @Override protected void configure() {
      bind(SecretController.class).toInstance(secretController);
    }
  };

  @Test public void registersFactoriesAndGenerators() {
    Injector injector = Guice.createInjector(secretControllerModule,
        new SecretGeneratorModule(
            ImmutableMap.of("dummyFactory", DUMMY_FACTORY),
            ImmutableMap.of("dummyGenerator", DUMMY_GENERATOR)));

    Map<String, SecretGenerator> generators = injector.getInstance(GENERATOR_MAP_KEY);
    assertThat(generators)
        .hasSize(2)
        .containsKey("dummyFactory")
        .contains(entry("dummyGenerator", DUMMY_GENERATOR));
  }

  @Test public void picksUpOtherMultiBindingBinds() {
    Module secretGeneratorModule = new SecretGeneratorModule(
        ImmutableMap.of(),
        ImmutableMap.of("dummyGenerator", DUMMY_GENERATOR));

    Module multiBindingModule = new AbstractModule() {
      @Override protected void configure() {
        MapBinder<String, SecretGenerator> mapBinder =
            MapBinder.newMapBinder(binder(), String.class, SecretGenerator.class);
        mapBinder.addBinding("multiBoundDummyGenerator").to(DummySecretGenerator.class);
      }
    };

    Injector injector = Guice.createInjector(secretControllerModule, secretGeneratorModule, multiBindingModule);

    Map<String, SecretGenerator> generators = injector.getInstance(GENERATOR_MAP_KEY);
    assertThat(generators)
        .hasSize(2)
        .contains(entry("dummyGenerator", DUMMY_GENERATOR))
        .containsKey("multiBoundDummyGenerator");
  }

  @Test(expected = RuntimeException.class) // Particular type is not interesting.
  public void conflictingGeneratorNamesCausesException() {
    new SecretGeneratorModule(
        ImmutableMap.of("conflictingName", DUMMY_FACTORY),
        ImmutableMap.of("conflictingName", DUMMY_GENERATOR));
  }

  @Test(expected = CreationException.class)
  public void conflictingMultiBindingNamesCausesException() {
    Module secretGeneratorModule = new SecretGeneratorModule(
        ImmutableMap.of(),
        ImmutableMap.of("conflictingName", DUMMY_GENERATOR));

    Module multiBindingModule = new AbstractModule() {
      @Override protected void configure() {
        MapBinder<String, SecretGenerator> mapBinder =
            MapBinder.newMapBinder(binder(), String.class, SecretGenerator.class);
        mapBinder.addBinding("conflictingName").to(DummySecretGenerator.class);
      }
    };

    Guice.createInjector(secretControllerModule, secretGeneratorModule, multiBindingModule);
  }

  private static class DummySecretGenerator extends SecretGenerator<String> {
    public DummySecretGenerator() { super(null); } // Shouldn't be used.

    @Override public List<Secret> generate(String creatorName, String request) {
      return Arrays.asList(
          secretController.builder("dummyName", "dummy", creatorName).build());
    }

    @Override public Class<String> getRequestType() { return String.class; }
  }

  private static class DummySecretGeneratorFactory implements SecretGeneratorFactory {
    @Override
    public SecretGenerator create(SecretController secretController) {
      return new DummySecretGenerator();
    }
  }
}
