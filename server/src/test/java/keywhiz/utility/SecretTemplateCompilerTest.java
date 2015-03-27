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

package keywhiz.utility;

import keywhiz.FakeRandom;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecretTemplateCompilerTest {
  private SecretTemplateCompiler compiler = new SecretTemplateCompiler(FakeRandom.create());

  @Test
  public void compilesAlphanumericSecret() throws Exception {
    String secret = compiler.compile("{{#alphanumeric}}10{{/alphanumeric}}");
    assertThat(secret).hasSize(10).matches("^[0-9a-zA-Z]+$");
  }

  @Test
  public void compilesHexadecimalSecret() throws Exception {
    String secret = compiler.compile("{{#hexadecimal}}11{{/hexadecimal}}");
    assertThat(secret).hasSize(11).matches("^[0-9a-f]+$");
  }

  @Test
  public void compilesNumericSecret() throws Exception {
    String secret = compiler.compile("{{#numeric}}32{{/numeric}}");
    assertThat(secret).hasSize(32).matches("^[0-9]+$");
  }

  @Test
  public void compilesTemplatesWithConstantStrings() throws Exception {
    String secret = compiler.compile("password: {{#numeric}}10{{/numeric}}");
    assertThat(secret).matches("^password: [0-9]{10}$");
  }

  @Test
  public void compilesMixedTemplates() throws Exception {
    String secret =
        compiler.compile("{{#alphanumeric}}10{{/alphanumeric}}{{#numeric}}10{{/numeric}}");
    assertThat(secret).hasSize(20);
  }

  @Test
  public void doesNotReturnTheSameOutput() throws Exception {
    String template = "{{#alphanumeric}}42{{/alphanumeric}}";
    assertThat(compiler.compile(template)).isNotEqualTo(compiler.compile(template));
  }

  @Test(expected = IllegalArgumentException.class)
  public void throwsOnNegativeLength() throws Exception {
    compiler.compile("{{#alphanumeric}}-1{{/alphanumeric}}");
  }

  @Test(expected = IllegalArgumentException.class)
  public void throwOnLengthTooLong() throws Exception {
    compiler.compile("{{#alphanumeric}}4097{{/alphanumeric}}");
  }

  @Test(expected = IllegalArgumentException.class)
  public void throwOnLengthTooShort() throws Exception {
    compiler.compile("{{#alphanumeric}}9{{/alphanumeric}}");
  }

  @Test(expected = IllegalArgumentException.class)
  public void alphanumericThrowsOnNonNumericParameters() throws Exception {
    compiler.compile("{{#alphanumeric}}non-num{{/alphanumeric}}");
  }

  @Test(expected = IllegalArgumentException.class)
  public void hexadecimalThrowsOnNonNumericParameters() throws Exception {
    compiler.compile("{{#hexadecimal}}non-num{{/hexadecimal}}");
  }

  @Test(expected = IllegalArgumentException.class)
  public void numericThrowsOnNonNumericParameters() throws Exception {
    compiler.compile("{{#numeric}}non-num{{/numeric}}");
  }

  @Test(expected = IllegalArgumentException.class)
  public void throwsOnMalformedTemplate() throws Exception {
    compiler.compile("{{#nonexistent}}10{{/nonexistent}}");
  }

  @Test(expected = IllegalArgumentException.class)
  public void throwsWhenNoTemplateIsFound() throws Exception {
    compiler.compile("there is no template here");
  }
}
