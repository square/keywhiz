package keywhiz.api.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import keywhiz.api.ApiDate;
import org.junit.Before;
import org.junit.Test;

import static keywhiz.testing.JsonHelpers.asJson;
import static keywhiz.testing.JsonHelpers.fromJson;
import static keywhiz.testing.JsonHelpers.jsonFixture;
import static org.assertj.core.api.Assertions.assertThat;

public class SanitizedSecretWithGroupsListAndCursorTest {
  private SanitizedSecretWithGroupsListAndCursor sanitizedSecretWithGroupsListAndCursor;

  @Before
  public void setUp() throws Exception {
    sanitizedSecretWithGroupsListAndCursor = SanitizedSecretWithGroupsListAndCursor.of(
        ImmutableList.of(
            SanitizedSecretWithGroups.of(
                SanitizedSecret.of(
                    767,
                    "trapdoor",
                    null,
                    "v1",
                    "checksum",
                    ApiDate.parse("2013-03-28T21:42:42.000Z"),
                    "keywhizAdmin",
                    ApiDate.parse("2013-03-28T21:42:42.000Z"),
                    "keywhizAdmin",
                    ImmutableMap.of("owner", "the king"),
                    "password",
                    ImmutableMap.of("favoriteFood", "PB&J sandwich"),
                    1136214245,
                    1L,
                    ApiDate.parse("2013-03-28T21:42:42.000Z"),
                    "keywhizAdmin"),
                ImmutableList.of(
                    new Group(100, "group1", "test-group-1",
                        ApiDate.parse("2013-03-28T21:42:42.000Z"),
                        "keywhizAdmin",
                        ApiDate.parse("2013-03-28T21:42:42.000Z"),
                        "keywhizAdmin",
                        ImmutableMap.of("owner", "the king"),
                        null),
                    new Group(100, "group2", "test-group-1",
                        ApiDate.parse("2013-03-28T21:42:42.000Z"),
                        null,
                        ApiDate.parse("2013-03-28T21:42:42.000Z"), null, null, null))

            )),
        SecretRetrievalCursor.toString(SecretRetrievalCursor.of("test-secret-2", 1234567)));
  }

  @Test public void roundTripSerialization() throws Exception {
    assertThat(fromJson(asJson(sanitizedSecretWithGroupsListAndCursor),
        SanitizedSecretWithGroupsListAndCursor.class)).isEqualTo(
        sanitizedSecretWithGroupsListAndCursor);
  }

  @Test
  public void deserializesCorrectly() throws Exception {
    assertThat(fromJson(jsonFixture("fixtures/sanitizedSecretWithGroupsListAndCursor.json"),
        SanitizedSecretWithGroupsListAndCursor.class)).isEqualTo(
        sanitizedSecretWithGroupsListAndCursor);
  }
}
