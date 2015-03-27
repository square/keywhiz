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

package keywhiz.api.validation;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ValidBase64ValidatorTest {
  private final ValidBase64Validator validator = new ValidBase64Validator();

  @Test
  public void acceptsBase64() {
    assertThat(validator.isValid("asdfasdfasdf", null)).isTrue();
  }

  @Test
  public void acceptsNull() {
    assertThat(validator.isValid(null, null)).isTrue();
  }

  @Test
  public void acceptsEmpty() {
    assertThat(validator.isValid("", null)).isTrue();
  }

  @Test
  public void rejectsWhitespace() {
    assertThat(validator.isValid("ab cd", null)).isFalse();
  }

  @Test
  public void rejectsNonBase64() {
    assertThat(validator.isValid("+#$", null)).isFalse();
  }

  @Test
  public void rejectsUrlSafeBase64() {
    assertThat(validator.isValid("a+/", null)).isTrue();
    // KeywhizFS assumes normal base64, not the url safe variant.
    assertThat(validator.isValid("b-_", null)).isFalse();
  }
}
