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

package keywhiz.auth;

import java.security.Provider;
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class used to ensure the BouncyCastle security provider is initialized. Can be called multiple
 * times.
 */
public class BouncyCastle {
  private static final Logger logger = LoggerFactory.getLogger(BouncyCastle.class);

  static {
    Provider provider = new BouncyCastleProvider();
    if (Security.getProvider(provider.getName()) == null) {
      logger.debug("Registering new crypto provider {}", provider.getName());
      Security.addProvider(provider);
    }

  }
  public static void require() {}
}
