package keywhiz.service.resources.automation.v2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jackson.Jackson;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import keywhiz.IntegrationTestRule;
import keywhiz.KeywhizService;
import keywhiz.TestClients;
import keywhiz.api.SecretDeliveryResponse;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.assertj.core.util.Strings;
import org.c02e.jpgpj.Decryptor;
import org.c02e.jpgpj.Key;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static com.google.common.net.MediaType.OCTET_STREAM;
import static java.lang.String.format;
import static keywhiz.TestClients.clientRequest;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class BackupResourceTest {
  private static final ObjectMapper mapper = KeywhizService.customizeObjectMapper(Jackson.newObjectMapper());

  private static final String TEST_EXPORT_KEY_PRIVATE =
      "-----BEGIN PGP PRIVATE KEY BLOCK-----\n\n"
      + "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\n"
      + "=mgrq\n"
      + "-----END PGP PRIVATE KEY BLOCK-----\n";

  private OkHttpClient mutualSslClient;

  @ClassRule public static final RuleChain chain = IntegrationTestRule.rule();

  @Before public void setUp() {
    mutualSslClient = TestClients.mutualSslClient();
  }

  @Test public void backupInvalidKey() throws Exception {
    Response httpResponse = backup("asdf", "Blackops");
    assertThat(httpResponse.code()).isEqualTo(404);
  }

  @Test public void backupInvalidGroup() throws Exception {
    Response httpResponse = backup("test", "asdf");
    assertThat(httpResponse.code()).isEqualTo(404);
  }

  @Test public void backupSuccess() throws Exception {
    Response httpResponse = backup("test", "Blackops");
    assertThat(httpResponse.code()).isEqualTo(200);

    InputStream ciphertext = httpResponse.body().byteStream();
    ByteArrayOutputStream plaintext = new ByteArrayOutputStream();

    Key key = new Key(TEST_EXPORT_KEY_PRIVATE, "password");

    // Decrypt and make sure we have expected data in the encrypted backup
    Decryptor decryptor = new Decryptor(key);
    decryptor.setVerificationRequired(false);
    decryptor.decrypt(ciphertext, plaintext);

    List<SecretDeliveryResponse> output = mapper.readValue(
        plaintext.toByteArray(),
        new TypeReference<List<SecretDeliveryResponse>>() {});

    assertThat(output)
        .extracting(SecretDeliveryResponse::getName)
        .containsExactlyInAnyOrder("Hacking_Password", "General_Password");

    assertThat(output)
        .extracting(SecretDeliveryResponse::getSecret)
        .allMatch(s -> !Strings.isNullOrEmpty(s));
  }

  Response backup(String key, String group) throws IOException {
    Request get = clientRequest(format("/automation/v2/backup/%s/group/%s", key, group))
        .addHeader("Accept", OCTET_STREAM.toString())
        .get()
        .build();

    return mutualSslClient.newCall(get).execute();
  }
}
