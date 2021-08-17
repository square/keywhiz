package keywhiz.service.crypto;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import keywhiz.api.ApiDate;
import keywhiz.api.model.Secret;
import keywhiz.api.model.SecretContent;
import keywhiz.api.model.SecretSeries;
import keywhiz.api.model.SecretSeriesAndContent;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class SecretTransformerTest {
  @Rule
  public MethodRule rule = MockitoJUnit.rule();

  @Mock
  private ContentCryptographer cryptographer;

  private SecretTransformer transformer;

  @Before
  public void before() {
    transformer = new SecretTransformer(cryptographer);

    when(cryptographer.decrypt(any())).thenReturn("");
  }

  @Test
  public void transformsOwner() {
    String ownerName = "foo";

    SecretSeries series = validSeries().toBuilder()
        .owner(ownerName)
        .build();

    SecretContent content = validContent();

    SecretSeriesAndContent seriesAndContent = SecretSeriesAndContent.of(series, content);

    Secret secret = transformer.transform(seriesAndContent);
    assertEquals(ownerName, secret.getOwner());
  }

  private static SecretSeries validSeries() {
    return SecretSeries.builder()
        .id(0)
        .name("name")
        .description("")
        .createdAt(ApiDate.now())
        .createdBy("")
        .updatedAt(ApiDate.now())
        .updatedBy("")
        .generationOptions(ImmutableMap.of())
        .build();

  }

  private static SecretContent validContent() {
    return SecretContent.builder()
        .id(0)
        .secretSeriesId(0)
        .encryptedContent("")
        .hmac("")
        .createdAt(ApiDate.now())
        .createdBy("")
        .updatedAt(ApiDate.now())
        .updatedBy("")
        .metadata(ImmutableMap.of())
        .expiry(0)
        .build();
  }
}
