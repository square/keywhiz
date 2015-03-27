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
package keywhiz.auth.mutualssl;

import com.google.auto.value.AutoValue;
import java.security.Principal;

/** Simple principal wraps a string as a Principal object. */
@AutoValue
public abstract class SimplePrincipal implements Principal {
  public static SimplePrincipal of(String name) {
    return new AutoValue_SimplePrincipal(name);
  }

  public abstract String name();

  @Override public String getName() {
    return name();
  }
}
