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

import io.dropwizard.ConfiguredBundle;
import io.dropwizard.servlets.assets.AssetServlet;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Configures an AssetServlet for UI assets based on the running configuration.
 *
 * Specifically, if the {@link keywhiz.KeywhizConfig#getAlternateUiPath()} is present then assets
 * are loaded from files and not from within the JAR.
 */
public class UiAssetsBundle implements ConfiguredBundle<KeywhizConfig> {
  private static final Logger logger = LoggerFactory.getLogger(UiAssetsBundle.class);
  private static final String URL_PATH = "/ui/";
  private static final String INDEX_FILE = "index.html";
  private static final String DEFAULT_RESOURCE_PATH = "ui";

  @Override public void initialize(Bootstrap<?> bootstrap) { /* Do nothing. */ }

  @Override public void run(KeywhizConfig config, Environment environment) throws Exception {
    environment.servlets().addServlet("ui-redirect-servlet", UiRedirectServlet.class)
        .addMapping("" /* Matches https:/host/ */, "/ui");

    if (config.getAlternateUiPath().isPresent()) {
      Path altPath = Paths.get(config.getAlternateUiPath().get());
      logger.info("Configuring alternative UI path: {}", altPath.toAbsolutePath());
      setupFileBasedUi(altPath, environment);
    } else {
      logger.info("Configuring UI path from JAR resources");
      setupResourceBasedUi(environment);
    }
  }

  private void setupFileBasedUi(Path uiDir, Environment env) {
    checkState(Files.exists(uiDir),
        "Cannot find alternate UI files directory: %s", uiDir.toAbsolutePath());
    HttpServlet servlet = new FileAssetServlet(uiDir.toFile(), URL_PATH, INDEX_FILE);
    env.servlets().addServlet("file-asset-servlet", servlet).addMapping(URL_PATH + "*");
  }

  private void setupResourceBasedUi(Environment env) {
    HttpServlet servlet = new AssetServlet(DEFAULT_RESOURCE_PATH, URL_PATH, INDEX_FILE, UTF_8);
    env.servlets().addServlet("asset-servlet", servlet).addMapping(URL_PATH + "*");
  }

  public static class UiRedirectServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
      resp.sendRedirect("/ui/");
    }
  }
}
