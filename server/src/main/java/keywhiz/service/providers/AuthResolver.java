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
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import keywhiz.api.model.AutomationClient;
import keywhiz.api.model.Client;
import keywhiz.auth.User;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.InjectionResolver;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.TypeLiteral;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.server.internal.inject.AbstractContainerRequestValueFactory;
import org.glassfish.jersey.server.internal.inject.AbstractValueFactoryProvider;
import org.glassfish.jersey.server.internal.inject.MultivaluedParameterExtractorProvider;
import org.glassfish.jersey.server.internal.inject.ParamInjectionResolver;
import org.glassfish.jersey.server.model.Parameter;
import org.glassfish.jersey.server.spi.internal.ValueFactoryProvider;

/**
 * Responsible for injecting container method attributes annotated with {@link Auth} and the
 * dependencies necessary for fulfilling those injected objects. This is modeled after
 * io.dropwizard.auth.AuthFactoryProvider, which may be usable in the future.
 * See https://github.com/dropwizard/dropwizard/issues/864.
 */
@Singleton
public class AuthResolver {
  public static class AuthInjectionResolver extends ParamInjectionResolver<Auth> {
    public AuthInjectionResolver() {
      super(AuthValueFactoryProvider.class);
    }
  }

  @Singleton
  public static class AuthValueFactoryProvider extends AbstractValueFactoryProvider {
    private final ClientAuthFactory clientAuthFactory;
    private final AutomationClientAuthFactory automationClientAuthFactory;
    private final UserAuthFactory userAuthFactory;

    @Inject
    public AuthValueFactoryProvider(final MultivaluedParameterExtractorProvider extractorProvider,
        final ServiceLocator injector, ClientAuthFactory clientAuthFactory,
        AutomationClientAuthFactory automationClientAuthFactory, UserAuthFactory userAuthFactory) {
      super(extractorProvider, injector, Parameter.Source.UNKNOWN);
      this.clientAuthFactory = clientAuthFactory;
      this.automationClientAuthFactory = automationClientAuthFactory;
      this.userAuthFactory = userAuthFactory;
    }

    @Override protected Factory<?> createValueFactory(final Parameter parameter) {
      final Class<?> classType = parameter.getRawType();
      final Auth auth = parameter.getAnnotation(Auth.class);

      if (auth == null) {
        return null;
      }

      if (classType.equals(Client.class)) {
        return new AbstractContainerRequestValueFactory<Client>() {
          @Context
          private HttpServletRequest httpRequest;

          @Override public Client provide() {
            return clientAuthFactory.provide(getContainerRequest(), httpRequest);
          }
        };
      }

      if (classType.equals(AutomationClient.class)) {
        return new AbstractContainerRequestValueFactory<AutomationClient>() {
          @Context
          private HttpServletRequest httpRequest;

          @Override public AutomationClient provide() {
            return automationClientAuthFactory.provide(getContainerRequest(), httpRequest);
          }
        };
      }

      if (classType.equals(User.class)) {
        return new AbstractContainerRequestValueFactory<User>() {
          @Override public User provide() {
            return userAuthFactory.provide(getContainerRequest());
          }
        };
      }

      return null;
    }
  }

  public static class Binder extends AbstractBinder {
    private final ClientAuthFactory clientAuthFactory;
    private final AutomationClientAuthFactory automationClientAuthFactory;
    private final UserAuthFactory userAuthFactory;

    public Binder(ClientAuthFactory clientAuthFactory,
        AutomationClientAuthFactory automationClientAuthFactory, UserAuthFactory userAuthFactory) {
      this.clientAuthFactory = clientAuthFactory;
      this.automationClientAuthFactory = automationClientAuthFactory;
      this.userAuthFactory = userAuthFactory;
    }

    @Override protected void configure() {
      bind(clientAuthFactory);
      bind(automationClientAuthFactory);
      bind(userAuthFactory);
      bind(AuthValueFactoryProvider.class).to(ValueFactoryProvider.class).in(Singleton.class);
      bind(AuthInjectionResolver.class).to(new TypeLiteral<InjectionResolver<Auth>>() {}).in(Singleton.class);
    }
  }
}
