package keywhiz.service.resources.admin;

import com.google.common.collect.ImmutableMap;
import java.util.UUID;
import javax.inject.Inject;
import keywhiz.KeywhizTestRunner;
import keywhiz.api.automation.v2.PartialUpdateSecretRequestV2;
import keywhiz.api.model.SecretSeriesAndContent;
import keywhiz.auth.User;
import keywhiz.service.config.Readwrite;
import keywhiz.service.daos.GroupDAO;
import keywhiz.service.daos.SecretDAO;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(KeywhizTestRunner.class)
public class SecretsResourceIntegrationTest {
  private static final ImmutableMap<String, String> NO_METADATA = ImmutableMap.of();

  @Inject private SecretsResource resource;

  @Inject @Readwrite private SecretDAO secretDAO;
  @Inject @Readwrite private GroupDAO groupDAO;

  @Test
  public void partialUpdateSecretIgnoresOwnerIfNotPresent() {
    String secretName = createSecretWithOwner(null);

    SecretSeriesAndContent originalSecret = getSecret(secretName);
    assertNull(originalSecret.series().owner());

    String groupName = createGroup();

    PartialUpdateSecretRequestV2 request = PartialUpdateSecretRequestV2.builder()
        .ownerPresent(false)
        .owner(groupName)
        .build();
    resource.partialUpdateSecret(User.named("nobody"), secretName, request);

    SecretSeriesAndContent updatedSecret = getSecret(secretName);
    assertNull(updatedSecret.series().owner());
  }

  @Test
  public void partialUpdateSecretOverwritesNullOwnerWithNonNullValue() {
    String secretName = createSecretWithOwner(null);

    SecretSeriesAndContent originalSecret = getSecret(secretName);
    assertNull(originalSecret.series().owner());

    String groupName = createGroup();

    updateOwner(secretName, groupName);

    SecretSeriesAndContent updatedSecret = getSecret(secretName);
    assertEquals(groupName, updatedSecret.series().owner());
  }

  @Test
  public void partialUpdateSecretOverwritesNonNullOwnerWithNullValue() {
    String groupName = createGroup();

    String secretName = createSecretWithOwner(groupName);

    SecretSeriesAndContent originalSecret = getSecret(secretName);
    assertEquals(groupName, originalSecret.series().owner());

    updateOwner(secretName, null);

    SecretSeriesAndContent updatedSecret = getSecret(secretName);
    assertNull(updatedSecret.series().owner());
  }

  @Test
  public void partialUpdateSecretOverwritesNonNullOwnerWithNonNullValue() {
    String group1 = createGroup();
    String group2 = createGroup();

    String secretName = createSecretWithOwner(group1);

    SecretSeriesAndContent originalSecret = getSecret(secretName);
    assertEquals(group1, originalSecret.series().owner());

    updateOwner(secretName, group2);

    SecretSeriesAndContent updatedSecret = getSecret(secretName);
    assertEquals(group2, updatedSecret.series().owner());
  }

  private void updateOwner(String secretName, String owner) {
    PartialUpdateSecretRequestV2 request = PartialUpdateSecretRequestV2.builder()
        .ownerPresent(true)
        .owner(owner)
        .build();
    resource.partialUpdateSecret(User.named("nobody"), secretName, request);
  }

  private String createSecretWithOwner(String owner) {
    String secretName = UUID.randomUUID().toString();
    secretDAO.createSecret(
        secretName,
        owner,
        "encryptedSecret",
        "hmac",
        "creator",
        NO_METADATA,
        0,
        "description",
        null,
        null);
    return secretName;
  }

  private SecretSeriesAndContent getSecret(String secretName) {
    return secretDAO.getSecretByName(secretName).get();
  }

  private String createGroup() {
    String groupName = UUID.randomUUID().toString();
    groupDAO.createGroup(groupName, "creator", "description", NO_METADATA);
    return groupName;
  }
}
