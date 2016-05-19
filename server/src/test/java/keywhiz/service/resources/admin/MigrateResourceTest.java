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

import com.google.common.collect.ImmutableList;
import java.util.List;
import keywhiz.api.ApiDate;
import keywhiz.api.model.Secret;
import keywhiz.service.daos.SecretController;
import keywhiz.utility.ReplaceIntermediateCertificate;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MigrateResourceTest {
  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock SecretController secretController;
  @Mock ReplaceIntermediateCertificate replaceIntermediateCertificate;

  ApiDate now = ApiDate.now();
  Secret secret1 = new Secret(1, "secret.xyz", "", "a test secret", "foo1", now, "test", now, "test", null, null, null);
  Secret secret2 = new Secret(1, "secret.crt", "", "a test secret", "foo2", now, "test", now, "test", null, null, null);

  MigrateResource resource;

  @Before public void setUp() {
    resource = new MigrateResource(secretController, replaceIntermediateCertificate);
  }

  @Test public void replaceIntermediateCertificateDoesntProcessRandomFiles() throws Exception {
    when(secretController.getSecretsById(1)).thenReturn(ImmutableList.of(secret1));
    List<String> updates = resource._replaceIntermediateCertificate(1);
    assertThat(updates).containsOnly("not a keystore");
    verify(replaceIntermediateCertificate, times(0)).process(anyString(), any());
  }

  @Test public void replaceIntermediateCertificateReturnsNoMatch() throws Exception {
    when(secretController.getSecretsById(1)).thenReturn(ImmutableList.of(secret2));
    List<String> updates = resource._replaceIntermediateCertificate(1);
    assertThat(updates).containsOnly("no match");
    verify(replaceIntermediateCertificate, times(1)).process(anyString(), any());
  }

  @Test public void replaceIntermediateCertificateMatches() throws Exception {
    when(secretController.getSecretsById(1)).thenReturn(ImmutableList.of(secret2));
    when(replaceIntermediateCertificate.process(anyString(), any())).thenReturn("foo2");
    when(secretController.update(any(), anyString())).thenReturn(1);

    List<String> updates = resource._replaceIntermediateCertificate(1);
    assertThat(updates).containsOnly("1");
    verify(replaceIntermediateCertificate, times(1)).process(anyString(), any());
  }
}
