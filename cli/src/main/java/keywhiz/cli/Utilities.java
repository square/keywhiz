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

public class Utilities {

  public static final String VALID_NAME_PATTERN = "^[a-zA-Z_0-9\\-.:]+$";

  public static boolean validName(String name) {
    // "." is allowed at any position but the first.
    return !name.startsWith(".") && name.matches(VALID_NAME_PATTERN);
  }
}
