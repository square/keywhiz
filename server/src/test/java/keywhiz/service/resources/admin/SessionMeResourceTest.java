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

package keywhiz.service.resources.admin;

import keywhiz.KeywhizTestRunner;
import keywhiz.auth.User;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(KeywhizTestRunner.class)
public class SessionMeResourceTest {
  SessionMeResource sessionMeResource;

  @Before
  public void setUp() throws Exception {
    sessionMeResource = new SessionMeResource();
  }

  @Test
  public void returnsTheCorrectUser() throws Exception {
    User user = User.named("Me");
    User returnedUser = sessionMeResource.getInformation(user);

    assertThat(returnedUser).isEqualTo(user);
  }
}
