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
package keywhiz.api.model;

import java.text.ParseException;
import org.junit.Test;

import static keywhiz.api.model.Secret.splitNameAndVersion;
import static org.assertj.core.api.Assertions.assertThat;

public class SecretTest {
  @Test public void splitUnVersionedSecretName() throws Exception {
    assertThat(splitNameAndVersion("secretName")).isEqualTo(new String[] {"secretName", ""});
  }

  @Test public void splitVersionedSecretName() throws Exception {
    assertThat(splitNameAndVersion("secretName..version")).isEqualTo(
        new String[] {"secretName", "version"});
  }

  @Test public void splitNameAndVersion_withEmptyVersion() throws Exception {
    assertThat(splitNameAndVersion("secretName..")).isEqualTo(
        new String[] {"secretName", ""});
  }

  @Test(expected = ParseException.class)
  public void splitRejectsBadSecretName() throws Exception {
    splitNameAndVersion("secretName..notAVersion..version");
  }
}
