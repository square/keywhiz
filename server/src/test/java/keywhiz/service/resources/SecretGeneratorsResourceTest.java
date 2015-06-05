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

package keywhiz.service.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.time.OffsetDateTime;
import java.util.List;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import keywhiz.auth.User;
import keywhiz.generators.SecretGenerator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

public class SecretGeneratorsResourceTest {
  private static final OffsetDateTime NOW = OffsetDateTime.now();

  private static final User user = User.named("creator");
  private static final ImmutableMap<String, String> emptyMap = ImmutableMap.of();
  private static final Secret secret = new Secret(22, "name", "version", "desc", "secret", NOW,
      "creator", NOW, "updater", emptyMap, null, null);

  private static final String generatorName = "sample-generator-name";
  private static final Integer params = 321;

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock SecretGenerator<Integer> generator;

  SecretGeneratorsResource resource;

  @Before public void setUp() {
    resource = new SecretGeneratorsResource(objectMapper,
        ImmutableMap.of(generatorName, generator));
    when(generator.getRequestType()).thenReturn(Integer.class);
  }

  @Test public void generates() throws Exception {
    when(generator.generate(eq(user.getName()), eq(params))).thenReturn(ImmutableList.of(secret));
    List<SanitizedSecret> sanitizedSecrets =
        resource.generate(user, generatorName, objectMapper.writeValueAsString(params));
    assertThat(sanitizedSecrets).containsOnly(SanitizedSecret.fromSecret(secret));
  }
}
