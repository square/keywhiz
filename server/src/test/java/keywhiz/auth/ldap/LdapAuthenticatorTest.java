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
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import io.dropwizard.auth.basic.BasicCredentials;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import keywhiz.auth.User;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 *  Powermock is broken in JDK 9+, and until version 2 is released with support
 *  these tests will not pass.
 */
//@RunWith(PowerMockRunner.class)
//@PrepareForTest({LDAPConnection.class, SearchResult.class, SearchResultEntry.class})
public class LdapAuthenticatorTest {
  @Mock LdapConnectionFactory ldapConnectionFactory;
  @Mock LDAPConnection ldapConnection;
  @Mock LDAPConnection ldapUserAuthConnection;
  @Mock SearchResult dnSearchResult;
  @Mock SearchResult roleSearchResult;

  private static final String PEOPLE_DN = "cn=sysadmin,ou=users";

  LdapAuthenticator ldapAuthenticator;

  @Before
  public void setup() throws Exception {
    LdapLookupConfig config = new LdapLookupConfig("ou=users,dc=example,dc=com",
        "uid", ImmutableSet.of("admin"), "ou=roles,dc=example,dc=com");
    ldapAuthenticator = new LdapAuthenticator(ldapConnectionFactory, config);

    List<SearchResultEntry> dnResults =
        Arrays.asList(new SearchResultEntry(PEOPLE_DN, new Attribute[]{}));
    List<SearchResultEntry> roleResults =
        Arrays.asList(new SearchResultEntry("cn=admin,ou=roles", new Attribute[]{}));

    when(ldapConnectionFactory.getLDAPConnection()).thenReturn(ldapConnection);

    doAnswer(invocation -> dnSearchResult).when(ldapConnection).search(argThat(
        searchRequest -> Optional.ofNullable(searchRequest)
            .map(SearchRequest::getBaseDN)
            .map(o -> o.equals("ou=users,dc=example,dc=com"))
            .orElse(false)));

    //when(ldapConnection.search(argThat(new IsDnSearch()))).thenReturn(dnSearchResult);
    when(dnSearchResult.getEntryCount()).thenReturn(1);
    when(dnSearchResult.getSearchEntries()).thenReturn(dnResults);

    doAnswer(invocation -> roleSearchResult).when(ldapConnection).search(argThat(
        searchRequest -> Optional.ofNullable(searchRequest)
            .map(SearchRequest::getBaseDN)
            .map(o -> o.equals("ou=roles,dc=example,dc=com"))
            .orElse(false)));
    //when(ldapConnection.search(argThat(new IsRoleSearch()))).thenReturn(roleSearchResult);
    when(roleSearchResult.getEntryCount()).thenReturn(1);
    when(roleSearchResult.getSearchEntries()).thenReturn(roleResults);
  }

  @Ignore
  @Test
  public void ldapAuthenticatorCreatesUserOnSuccess() throws Exception {
    when(ldapConnectionFactory.getLDAPConnection(PEOPLE_DN, "validpass"))
        .thenReturn(ldapUserAuthConnection);

    User user = ldapAuthenticator.authenticate(new BasicCredentials("sysadmin", "validpass"))
        .orElseThrow(RuntimeException::new);
    assertThat(user).isEqualTo(User.named("sysadmin"));
  }

  @Ignore
  @Test
  public void ldapAuthenticatorThrowsWhenAuthFails() throws Exception {
    // Zero results on a search indicates no valid user.
    when(dnSearchResult.getEntryCount()).thenReturn(0);

    Optional<User> missingUser =
        ldapAuthenticator.authenticate(new BasicCredentials("sysadmin", "badpass"));
    assertThat(missingUser.isPresent()).isFalse();
  }

  @Ignore
  @Test
  public void ldapAuthenticatorRejectsInvalidUsername() throws Exception {
    String crazyUsername = "sysadmin)`~!@#$%^&*()+=[]{}\\|;:'\",<>?/\r\n\t";
    Optional<User> missingUser =
        ldapAuthenticator.authenticate(new BasicCredentials(crazyUsername, "badpass"));
    assertThat(missingUser.isPresent()).isFalse();
  }

  @Ignore
  @Test
  public void ldapAuthenticatorRejectsEmptyPassword() throws Exception {
    Optional<User> user = ldapAuthenticator.authenticate(new BasicCredentials("sysadmin", ""));
    assertThat(user.isPresent()).isFalse();
  }
}
