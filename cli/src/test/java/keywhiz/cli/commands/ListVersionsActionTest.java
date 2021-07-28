package keywhiz.cli.commands;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Optional;
import keywhiz.api.ApiDate;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import keywhiz.cli.Printing;
import keywhiz.cli.configs.ListVersionsActionConfig;
import keywhiz.client.KeywhizClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ListVersionsActionTest {
  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock KeywhizClient keywhizClient;
  @Mock Printing printing;

  ListVersionsActionConfig listVersionsActionConfig;
  ListVersionsAction listVersionsAction;

  private static final ApiDate NOW = ApiDate.now();
  Secret secretV2 =
      new Secret(0, "secret", null, null, () -> "c2VjcmV0MQ==", "checksum2", NOW, null, NOW, null, null,
          null, ImmutableMap.of(), 0, 1L, NOW, "creator2");
  Secret secretV1 =
      new Secret(0, "secret", null, null, () -> "c2VjcmV0MQ==", "checksum1", NOW, null, NOW, null, null,
          null, ImmutableMap.of(), 0, -1L, NOW, "creator1");
  SanitizedSecret sanitizedSecretV2 = SanitizedSecret.fromSecret(secretV2);
  SanitizedSecret sanitizedSecretV1 = SanitizedSecret.fromSecret(secretV1);
  List<SanitizedSecret> secretVersions = ImmutableList.of(sanitizedSecretV2, sanitizedSecretV1);

  @Before
  public void setUp() {
    listVersionsActionConfig = new ListVersionsActionConfig();
    listVersionsAction = new ListVersionsAction(listVersionsActionConfig, keywhizClient, printing);
  }

  @Test
  public void listVersionsCallsPrint() throws Exception {
    listVersionsActionConfig.name = secretV2.getDisplayName();
    listVersionsActionConfig.idx = 5;
    listVersionsActionConfig.number = 15;

    when(keywhizClient.getSanitizedSecretByName(secretV2.getDisplayName())).thenReturn(
        sanitizedSecretV2);
    when(keywhizClient.listSecretVersions(eq(secretV2.getName()), anyInt(), anyInt())).thenReturn(
        secretVersions);

    listVersionsAction.run();

    verify(keywhizClient).listSecretVersions(secretV2.getName(), 5, 15);
    verify(printing).printSecretVersions(secretVersions, Optional.of(1L));
  }

  @Test
  public void listVersionsUsesDefaults() throws Exception {
    listVersionsActionConfig.name = secretV2.getDisplayName();

    when(keywhizClient.getSanitizedSecretByName(secretV2.getDisplayName())).thenReturn(
        sanitizedSecretV2);
    when(keywhizClient.listSecretVersions(eq(secretV2.getName()), anyInt(), anyInt())).thenReturn(
        secretVersions);

    listVersionsAction.run();

    verify(keywhizClient).listSecretVersions(secretV2.getName(), 0, 10);
    verify(printing).printSecretVersions(secretVersions, Optional.of(1L));
  }

  @Test(expected = AssertionError.class)
  public void listVersionsThrowsIfSecretDoesNotExist() throws Exception {
    listVersionsActionConfig.name = secretV2.getDisplayName();

    when(keywhizClient.getSanitizedSecretByName(secretV2.getDisplayName())).thenThrow(
        new KeywhizClient.NotFoundException());

    listVersionsAction.run();
  }

  @Test(expected = IllegalArgumentException.class)
  public void listVersionsThrowsIfNoSecretSpecified() {
    listVersionsActionConfig.name = null;
    listVersionsAction.run();
  }

  @Test(expected = IllegalArgumentException.class)
  public void listVersionsValidatesSecretName() {
    listVersionsActionConfig.name = "Invalid Name";
    listVersionsAction.run();
  }
}
