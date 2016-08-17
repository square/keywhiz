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

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static keywhiz.cli.Utilities.validName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;

public class UtilitiesTest {

  @Test
  public void validateNameReturnsTrueOnValidNames() throws Exception {
    assertThat(validName("hello")).isTrue();
    assertThat(validName("hello_world")).isTrue();
    assertThat(validName("Hello-World")).isTrue();
    assertThat(validName("foo.yml")).isTrue();
    assertThat(validName("I_am_secret.yml")).isTrue();
    assertThat(validName("I-am-secret.yml")).isTrue();
  }

  @Test
  public void validateNameReturnsFalseOnInvalidNames() throws Exception {
    assertThat(validName("hello!")).isFalse();
    assertThat(validName("hello world")).isFalse();
    assertThat(validName("$$ bill yall")).isFalse();
    assertThat(validName("blah/../../../etc/passwd")).isFalse();
    assertThat(validName("bad/I-am-secret.yml")).isFalse();
    assertThat(validName(".almostvalid")).isFalse();
    assertThat(validName("bad\tI-am-secret.yml")).isFalse();

    List<String> specialCharacters = Arrays.asList("&", "|", "(", ")", "[", "]", "{", "}", "^",
        ";", ",", "\\", "/", "<", ">", "\t", "\n", "\r", "`", "'", "\"", "?", "#", "%",
        "*", "+", "=", "\0", "\u0002", "\000");

    for (String name : specialCharacters) {
      assertFalse("Failed character: \"" + name + "\"", validName(name));
    }
  }
}
