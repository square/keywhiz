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

package keywhiz.testing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.dropwizard.jackson.AnnotationSensitivePropertyNamingStrategy;
import io.dropwizard.jackson.DiscoverableSubtypeResolver;
import io.dropwizard.jackson.FuzzyEnumModule;
import io.dropwizard.jackson.GuavaExtrasModule;
import java.io.IOException;

import static io.dropwizard.testing.FixtureHelpers.fixture;

/**
 * A set of helper methods for testing the serialization and deserialization of classes to and from
 * JSON.
 * <p>For example, a test for reading and writing a {@code Person} object as JSON:</p>
 * <pre><code>
 * assertThat("writing a person as JSON produces the appropriate JSON object",
 *            asJson(person),
 *            is(jsonFixture("fixtures/person.json"));
 *
 * assertThat("reading a JSON object as a person produces the appropriate person",
 *            fromJson(jsonFixture("fixtures/person.json"), Person.class),
 *            is(person));
 * </code></pre>
 */
public class JsonHelpers {
  private static final ObjectMapper MAPPER = customizeObjectMapper();

  private JsonHelpers() { /* singleton */ }

  /**
   * Converts the given object into a canonical JSON string.
   *
   * @param object an object
   * @return {@code object} as a JSON string
   * @throws JsonProcessingException if there is an error encoding {@code object}
   */
  public static String asJson(Object object) throws JsonProcessingException {
    return MAPPER.writeValueAsString(object);
  }

  /**
   * Converts the given JSON string into an object of the given type.
   *
   * @param json     a JSON string
   * @param klass    the class of the type that {@code json} should be converted to
   * @param <T>      the type that {@code json} should be converted to
   * @return {@code json} as an instance of {@code T}
   * @throws IOException if there is an error reading {@code json} as an instance of {@code T}
   */
  public static <T> T fromJson(String json, Class<T> klass) throws IOException {
    return MAPPER.readValue(json, klass);
  }

  /**
   * Loads the given fixture resource as a normalized JSON string.
   *
   * @param filename    the filename of the fixture
   * @return the contents of {@code filename} as a normalized JSON string
   * @throws IOException if there is an error parsing {@code filename}
   */
  public static String jsonFixture(String filename) throws IOException {
    return MAPPER.writeValueAsString(MAPPER.readValue(fixture(filename), JsonNode.class));
  }

  /**
   * Customized ObjectMapper for common settings.
   *
   * @return customized object mapper
   */
  private static ObjectMapper customizeObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new Jdk8Module());
    mapper.registerModule(new GuavaModule());
    mapper.registerModule(new GuavaExtrasModule());
    mapper.registerModule(new FuzzyEnumModule());
    mapper.setPropertyNamingStrategy(new AnnotationSensitivePropertyNamingStrategy());
    mapper.setSubtypeResolver(new DiscoverableSubtypeResolver());
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    return mapper;
  }
}
