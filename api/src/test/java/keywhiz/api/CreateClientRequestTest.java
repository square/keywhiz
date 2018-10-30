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
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.junit.ClassRule;
import org.junit.Test;

import static javax.ws.rs.client.Entity.entity;
import static keywhiz.testing.JsonHelpers.fromJson;
import static keywhiz.testing.JsonHelpers.jsonFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.jetty.http.HttpStatus.Code.UNPROCESSABLE_ENTITY;

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

  @Test public void emptyNameFailsValidation() {
    CreateClientRequest createClientRequest = new CreateClientRequest("");
    Response response = resources.client().target("/").request()
        .post(entity(createClientRequest, "application/json"));

    assertThat(response.getStatus()).isEqualTo(UNPROCESSABLE_ENTITY.getCode());
  }

  @Path("/") public static class Resource {
    @POST @Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON)
    public String method(@Valid CreateClientRequest request) {
      throw new UnsupportedOperationException();
    }
  }
}
