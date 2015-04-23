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

package keywhiz.service.config;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.auto.service.AutoService;
import com.google.common.io.Resources;
import io.dropwizard.jetty.ConnectorFactory;
import io.dropwizard.jetty.HttpsConnectorFactory;
import java.io.File;
import java.nio.file.Paths;
import org.eclipse.jetty.util.ssl.SslContextFactory;

@AutoService(ConnectorFactory.class)
@JsonTypeName("resources-https")
@SuppressWarnings("unused")
public class ResourcesHttpsConnectorFactory extends HttpsConnectorFactory {
  @Override public String getKeyStorePath() {
    return resolveResource(super.getKeyStorePath());
  }

  @Override public String getTrustStorePath() {
    return resolveResource(super.getTrustStorePath());
  }

  @Override public File getCrlPath() {
    return resolveResource(super.getCrlPath());
  }

  @Override protected SslContextFactory buildSslContextFactory() {
    SslContextFactory factory = super.buildSslContextFactory();
    factory.setKeyStorePath(resolveResource(factory.getKeyStorePath()));
    factory.setTrustStorePath(resolveResource(factory.getTrustStore()));
    factory.setCrlPath(resolveResource(getCrlPath().getName()));
    return factory;
  }

  private static String resolveResource(String resource) {
    return Resources.getResource(resource).getPath();
  }

  private static File resolveResource(File resource) {
    return Paths.get(Resources.getResource(resource.getPath()).getPath()).toFile();
  }
}
