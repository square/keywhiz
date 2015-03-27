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
