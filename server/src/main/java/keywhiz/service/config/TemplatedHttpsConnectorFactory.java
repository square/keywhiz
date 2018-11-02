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
import io.dropwizard.jetty.ConnectorFactory;
import io.dropwizard.jetty.HttpsConnectorFactory;
import java.io.IOException;
import org.eclipse.jetty.util.ssl.SslContextFactory;

@AutoService(ConnectorFactory.class)
@JsonTypeName("templated-https")
@SuppressWarnings("unused")
public class TemplatedHttpsConnectorFactory extends HttpsConnectorFactory {
  @Override public String getKeyStorePath() {
    String templatedPath = super.getKeyStorePath();
    return convertTemplatedPath(templatedPath);
  }

  @Override protected SslContextFactory configureSslContextFactory(SslContextFactory factory) {
    factory = super.configureSslContextFactory(factory);
    String templatedPath = super.getKeyStorePath();
    factory.setKeyStorePath(convertTemplatedPath(templatedPath));
    return factory;
  }

  private static String convertTemplatedPath(String template) {
    try {
      return Templates.evaluateTemplate(template);
    } catch (IOException e) {
      throw new RuntimeException("Failure resolving https keyStorePath template", e);
    }
  }
}
