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
package keywhiz.cli.configs;

import com.beust.jcommander.Parameter;
import java.security.KeyStore;
import java.util.Optional;
import javax.annotation.Nullable;
import keywhiz.cli.DevTrustStore;

/** Global command line configuration. */
public class CliConfiguration {

  @Parameter(names = { "-V", "--version" }, description = "Version")
  public boolean version = false;

  @Parameter(names = { "-U", "--url" }, description = "Base URL of server")
  public String url;

  @Parameter(names = "--user", description = "User to login as")
  private String user;
  
  @Parameter(names = "--password", description = "User password")
  private String password;

  public char[] getPassword() {
    return password.toCharArray();
  }

  public Optional<String> getUser() {
    return Optional.ofNullable(user);
  }

  @Parameter(names = "--devTrustStore", description = "Use development trust store")
  private boolean devTrustStore = false;

  public @Nullable KeyStore getDevTrustStore() {
    if (devTrustStore) {
      return new DevTrustStore().getTrustStore();
    }
    return null;
  }
}
