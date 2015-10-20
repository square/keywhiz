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

package keywhiz.api;

import io.dropwizard.testing.junit.ResourceTestRule;
import javax.validation.ConstraintViolationException;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.ProcessingException;
import org.junit.ClassRule;
import org.junit.Test;

import static javax.ws.rs.client.Entity.entity;
import static keywhiz.testing.JsonHelpers.fromJson;
import static keywhiz.testing.JsonHelpers.jsonFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class CreateClientRequestTest {
  @ClassRule public static final ResourceTestRule resources = ResourceTestRule.builder()
      .addResource(new Resource())
      .build();

  @Test public void deserializesCorrectly() throws Exception {
    CreateClientRequest createClientRequest = new CreateClientRequest("client-name");
    assertThat(fromJson(
        jsonFixture("fixtures/createClientRequest.json"), CreateClientRequest.class))
        .isEqualTo(createClientRequest);
  }

  @Test public void emptyNameFailsValidation() throws Exception {
    CreateClientRequest createClientRequest = new CreateClientRequest("");
    Throwable exception = catchThrowable(() ->
        resources.client().target("/").request()
        .post(entity(createClientRequest, "application/json")));

    assertThat(exception)
        .isInstanceOf(ProcessingException.class)
        .hasCauseInstanceOf(ConstraintViolationException.class);
  }

  @Path("/") public static class Resource {
    @POST @Consumes("application/json") public String method(@Valid CreateClientRequest request) {
      throw new UnsupportedOperationException();
    }
  }
}
