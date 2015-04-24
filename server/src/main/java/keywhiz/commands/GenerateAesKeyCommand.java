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

import static java.lang.String.format;

/**
 * Generates an AES key in a keystore. Particularly, this is useful for a base derivation key.
 */
public class GenerateAesKeyCommand extends Command {
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
    System.out.println(format("Generated a %d-bit AES key at %s with alias %s", keySize,
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
