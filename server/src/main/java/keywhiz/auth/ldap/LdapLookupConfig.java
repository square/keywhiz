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

package keywhiz.auth.ldap;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;

public class LdapLookupConfig {
  /**
   * LDAP base DN to scope user search with.
   */
  @NotEmpty
  private String userBaseDN;

  /**
   * Type of attribute to match the username to.
   */
  @NotEmpty
  private String userAttribute = "uid";

  /**
   * In addition to successful login, additional LDAP roles (group memberships) can be required.
   * Access is allowed if any one of the roles are met. roleBaseDN is not necessary if requiredRoles
   * is empty.
   */
  @NotNull
  public ImmutableSet<String> requiredRoles = ImmutableSet.of();

  /**
   * LDAP base DN to scope role search with.
   */
  @NotNull
  public String roleBaseDN = "";

  public LdapLookupConfig(String userBaseDN, String userAttribute,
      Set<String> requiredRoles, String roleBaseDN) {
    this.userBaseDN = userBaseDN;
    this.userAttribute = userAttribute;
    this.requiredRoles = ImmutableSet.copyOf(requiredRoles);
    this.roleBaseDN = roleBaseDN;
  }

  // Default dummy constructor to ensure compatibility with Jackson
  public LdapLookupConfig() {}

  public String getUserBaseDN() {
    return userBaseDN;
  }

  public String getUserAttribute() {
    return userAttribute;
  }

  public ImmutableSet<String> getRequiredRoles() {
    return requiredRoles;
  }

  public String getRoleBaseDN() {
    return roleBaseDN;
  }
}
