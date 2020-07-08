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

package keywhiz.service.providers;

import io.dropwizard.auth.Auth;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import keywhiz.api.model.AutomationClient;
import keywhiz.api.model.Client;
import keywhiz.auth.User;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.internal.inject.AbstractValueParamProvider;
import org.glassfish.jersey.server.internal.inject.MultivaluedParameterExtractorProvider;
import org.glassfish.jersey.server.model.Parameter;
import org.glassfish.jersey.server.spi.internal.ValueParamProvider;

/**
 * Responsible for injecting container method attributes annotated with {@link Auth} and the
 * dependencies necessary for fulfilling those injected objects. This is modeled after
 * io.dropwizard.auth.AuthFactoryProvider, which may be usable in the future. See
 * https://github.com/dropwizard/dropwizard/issues/864.
 */
@Singleton
public class AuthResolver extends AbstractValueParamProvider {
  private final ClientAuthFactory clientAuthFactory;
  private final AutomationClientAuthFactory automationClientAuthFactory;
  private final UserAuthFactory userAuthFactory;

  @Inject
  public AuthResolver(final MultivaluedParameterExtractorProvider extractorProvider,
      ClientAuthFactory clientAuthFactory,
      AutomationClientAuthFactory automationClientAuthFactory,
      UserAuthFactory userAuthFactory) {
    super(() -> extractorProvider, Parameter.Source.UNKNOWN);
    this.clientAuthFactory = clientAuthFactory;
    this.automationClientAuthFactory = automationClientAuthFactory;
    this.userAuthFactory = userAuthFactory;
  }

  @Override
  protected Function<ContainerRequest, ?> createValueProvider(final Parameter parameter) {
    final Class<?> classType = parameter.getRawType();
    final Auth auth = parameter.getAnnotation(Auth.class);

    if (auth == null) {
      return null;
    }

    if (classType.equals(Client.class)) {
      return new Function<ContainerRequest, Client>() {
        @Context
        private HttpServletRequest httpRequest;

        @Override public Client apply(ContainerRequest containerRequest) {
          return clientAuthFactory.provide(containerRequest, httpRequest);
        }
      };
    }

    if (classType.equals(AutomationClient.class)) {
      return new Function<ContainerRequest, AutomationClient>() {
        @Context
        private HttpServletRequest httpRequest;

        @Override public AutomationClient apply(ContainerRequest containerRequest) {
          return automationClientAuthFactory.provide(containerRequest, httpRequest);
        }
      };
    }

    if (classType.equals(User.class)) {
      return new Function<ContainerRequest, User>() {
        @Context
        private HttpServletRequest httpRequest;

        @Override public User apply(ContainerRequest containerRequest) {
          return userAuthFactory.provide(containerRequest);
        }
      };
    }

    return null;
  }

  public static class Binder extends AbstractBinder {
    private final ClientAuthFactory clientAuthFactory;
    private final AutomationClientAuthFactory automationClientAuthFactory;
    private final UserAuthFactory userAuthFactory;

    @Inject
    public Binder(ClientAuthFactory clientAuthFactory,
        AutomationClientAuthFactory automationClientAuthFactory,
        UserAuthFactory userAuthFactory) {
      this.clientAuthFactory = clientAuthFactory;
      this.automationClientAuthFactory = automationClientAuthFactory;
      this.userAuthFactory = userAuthFactory;
    }

    @Override protected void configure() {
      bind(clientAuthFactory);
      bind(automationClientAuthFactory);
      bind(userAuthFactory);
      bind(AuthResolver.class).to(ValueParamProvider.class).in(Singleton.class);
    }
  }
}
