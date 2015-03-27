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

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheException;
import com.github.mustachejava.MustacheFactory;
import com.github.mustachejava.TemplateFunction;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Range;
import com.google.common.io.BaseEncoding;
import com.google.common.math.IntMath;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.Map;
import org.apache.commons.lang3.RandomStringUtils;

import static com.google.common.base.Preconditions.checkArgument;

public class SecretTemplateCompiler {
  public static final Range<Integer> VALID_SECRET_LENGTH = Range.closed(10, 4096);
  private static final BaseEncoding HEX = BaseEncoding.base16().lowerCase();
  private static final String VALID_NAME_PATTERN = "^[a-zA-Z_0-9\\-.]+$";

  public static boolean validName(String name) {
    // "." is allowed at any position but the first.
    return !name.isEmpty() && !name.startsWith(".") && name.matches(VALID_NAME_PATTERN);
  }

  private boolean hasCompiledRandomness = false;
  private final SecureRandom secureRandom;

  TemplateFunction alphanumeric = new TemplateFunction() {
    @Override public String apply(String s) {
      Integer length = Integer.parseInt(s);
      checkArgument(VALID_SECRET_LENGTH.contains(length),
          "Secret length %s must be within %s.", length, VALID_SECRET_LENGTH);

      hasCompiledRandomness = true;
      return RandomStringUtils.random(length, 0, 0, true, true, null, secureRandom);
    }
  };

  TemplateFunction hexadecimal = new TemplateFunction() {
    @Override public String apply(String s) {
      Integer length = Integer.parseInt(s);
      checkArgument(VALID_SECRET_LENGTH.contains(length),
          "Secret length %s must be within %s.", length, VALID_SECRET_LENGTH);

      hasCompiledRandomness = true;
      // 2 hex chars per byte, so half the bytes
      byte[] random = new byte[IntMath.divide(length, 2, RoundingMode.CEILING)];
      secureRandom.nextBytes(random);
      return HEX.encode(random).substring(0, length); // trims to proper length if odd
    }
  };

  TemplateFunction numeric = new TemplateFunction() {
    @Override public String apply(String s) {
      Integer length = Integer.parseInt(s);
      checkArgument(VALID_SECRET_LENGTH.contains(length), "Secret length %s must be within %s.",
          length, VALID_SECRET_LENGTH);

      hasCompiledRandomness = true;
      return RandomStringUtils.random(length, 0, 0, false, true, null, secureRandom);
    }
  };

  private final Map<String, TemplateFunction> functions = ImmutableMap.of(
      "alphanumeric", alphanumeric,
      "hexadecimal", hexadecimal,
      "numeric", numeric);

  public SecretTemplateCompiler(SecureRandom secureRandom) {
    this.secureRandom = secureRandom;
  }

  public String compile(final String template) {
    StringWriter sw = new StringWriter();

    try {
      MustacheFactory mf = new DefaultMustacheFactory();
      Mustache mustache = mf.compile(new StringReader(template), null);
      mustache.execute(sw, functions).flush();
    } catch (NumberFormatException | IOException | MustacheException e) {
      throw new IllegalArgumentException(e);
    }

    // This means that no randomness was generated. This is not acceptable
    if (!hasCompiledRandomness) {
      throw new IllegalArgumentException();
    }

    return sw.toString();
  }
}
