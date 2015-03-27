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

import javax.security.auth.x500.X500Principal;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/**
 * Validates items annotated with {@link ValidX500Name}.
 *
 * For example: cn=Sample Name,ou=people,dn=squareup,dn=com
 */
public class ValidX500NameValidator implements ConstraintValidator<ValidX500Name, String> {
  @Override public void initialize(ValidX500Name constraintAnnotation) {}

  @Override public boolean isValid(String value, ConstraintValidatorContext ctx) {
    try {
      // Preferred over X500Name. Throws NPE or IllegalArgumentException if invalid.
      new X500Principal(value);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    } catch (NullPointerException e) {
      return false;
    }
  }
}
