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
package keywhiz;

import io.dropwizard.Bundle;
import io.dropwizard.servlets.assets.AssetServlet;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Bundle which creates a servlet for static assets with specific names.
 *
 * Names are specific because urls under the root (/) are served and it should not override
 * endpoints.
 */
public class NamedAssetsBundle implements Bundle {
  @Override public void initialize(Bootstrap<?> bootstrap) {
    // Do nothing.
  }

  @Override public void run(Environment environment) {
    environment.servlets().addServlet("named-assets", new AssetServlet("/assets", "/", null, UTF_8))
        .addMapping("/favicon.ico", "/robots.txt", "/crossdomain.xml");
  }
}
