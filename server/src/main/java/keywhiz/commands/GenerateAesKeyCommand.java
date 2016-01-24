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

package keywhiz.commands;

import com.google.common.annotations.VisibleForTesting;
import io.dropwizard.cli.Command;
import io.dropwizard.setup.Bootstrap;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;

/**
 * Generates an AES key in a keystore. Particularly, this is useful for a base derivation key.
 */
public class GenerateAesKeyCommand extends Command {
  private static final Logger logger = LoggerFactory.getLogger(GenerateAesKeyCommand.class);
  public GenerateAesKeyCommand() {
    super("gen-aes", "Generates a new AES key in a keystore");
  }

  @Override public void configure(Subparser parser) {
    parser.addArgument("--keystore")
        .dest("keystore")
        .type(Path.class)
        .setDefault(Paths.get("derivation.jceks"))
        .help("keystore file name");

    parser.addArgument("--storepass")
        .dest("storepass")
        .type(String.class)
        .setDefault("CHANGE")
        .help("keystore password");

    parser.addArgument("--keysize")
        .dest("keysize")
        .type(Integer.class)
        .choices(128, 256)
        .setDefault(128)
        .help("keysize in bits");

    parser.addArgument("--alias")
        .dest("alias")
        .setDefault("baseKey")
        .help("keystore entry alias");
  }

  @Override public void run(Bootstrap<?> bootstrap, Namespace namespace) throws Exception {
    char[] password = namespace.getString("storepass").toCharArray();
    Path destination = namespace.get("keystore");
    int keySize = namespace.getInt("keysize");
    String alias = namespace.getString("alias");

    generate(password, destination, keySize, alias);
    logger.info(format("Generated a %d-bit AES key at %s with alias %s", keySize,
        destination.toAbsolutePath(), alias));
  }

  @VisibleForTesting
  static void generate(char[] password, Path destination, int keySize, String alias) throws Exception {
    KeyGenerator generator = KeyGenerator.getInstance("AES");
    generator.init(keySize, SecureRandom.getInstanceStrong());
    SecretKey key = generator.generateKey();

    KeyStore keyStore = KeyStore.getInstance("JCEKS");
    keyStore.load(null); // KeyStores must be initialized before use.
    keyStore.setKeyEntry(alias, key, password, null);
    try (OutputStream out = Files.newOutputStream(destination)) {
      keyStore.store(out, password);
    }
  }
}
