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

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import javax.ws.rs.core.Response;
import keywhiz.api.ApiDate;
import keywhiz.api.model.SecretContent;
import keywhiz.api.model.SecretSeries;
import keywhiz.api.model.SecretSeriesAndContent;
import keywhiz.service.crypto.ContentCryptographer;
import keywhiz.service.daos.SecretDAO;
import keywhiz.service.daos.SecretSeriesDAO;
import keywhiz.service.daos.SecretSeriesDAO.SecretSeriesDAOFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BackfillHmacResourceTest {
  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock SecretDAO secretDAO;
  @Mock SecretSeriesDAOFactory secretSeriesDAOFactory;
  @Mock SecretSeriesDAO secretSeriesDAO;
  @Mock ContentCryptographer cryptographer;

  private BackfillHmacResource resource;

  private ApiDate now = ApiDate.now();
  private SecretSeriesAndContent sc1 = SecretSeriesAndContent.of(
      SecretSeries.of(1, "secret.xyz", "a test secret", now, "test", now, "test", "testSecret",
          ImmutableMap.of(), 1L),
      SecretContent.of(1L, 1L, "foo1", "", now, "test", now, "test", ImmutableMap.of(), 12345));

  private SecretSeriesAndContent sc2 = SecretSeriesAndContent.of(
      SecretSeries.of(2, "secret.crt", "a test secret", now, "test", now, "test", "testSecret", ImmutableMap.of(), 2L),
      SecretContent.of(2L, 2L, "foo2", "hmac2", now, "test", now, "test", ImmutableMap.of(), 23456));

  @Before public void setUp() {
    when(secretSeriesDAOFactory.readwrite()).thenReturn(secretSeriesDAO);
    resource = new BackfillHmacResource(secretDAO, secretSeriesDAOFactory, cryptographer);

    when(secretDAO.getSecretById(1)).thenReturn(Optional.of(sc1));
    when(cryptographer.computeHmac("foo1".getBytes(UTF_8))).thenReturn("hmac1");
    when(cryptographer.decrypt("foo1")).thenReturn("foo1");
    when(secretSeriesDAO.setHmac(1, "hmac1")).thenReturn(1);

    when(secretDAO.getSecretById(2)).thenReturn(Optional.of(sc2));
    when(cryptographer.computeHmac("foo2".getBytes(UTF_8))).thenReturn("hmac2");
    when(cryptographer.decrypt("foo2")).thenReturn("foo2");
    when(secretSeriesDAO.setHmac(2, "hmac2")).thenReturn(1);
  }

  @Test public void backfillHmacWorksAsExpected() throws Exception {
    Response response = resource.backfillHmac(1);
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getEntity()).isEqualTo(true);
    verify(secretSeriesDAO, times(1)).setHmac(1, "hmac1");
    verify(secretSeriesDAO, never()).setHmac(2, "hmac2");
  }

  @Test public void backfillHmacDoesNotOverwriteProvidedHmac() throws Exception {
    Response response = resource.backfillHmac(2);
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getEntity()).isEqualTo(true);
    verify(secretSeriesDAO, never()).setHmac(2, "hmac2");
    verify(secretSeriesDAO, never()).setHmac(1, "hmac1");
  }
}