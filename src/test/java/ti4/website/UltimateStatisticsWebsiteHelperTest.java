package ti4.website;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import ti4.json.JsonMapperManager;
import tools.jackson.databind.JsonNode;

/**
 * Two rejections from the TI4 Ultimate API are expected rather than exceptional, and used to ping Lazik in bot-log and
 * tell the table to contact him: re-reporting a game that was already reported, and a player using a stats command
 * before their profile exists. The duplicate body below was captured from the live API on 2026-09-23.
 *
 * <p>Per Lazik, only data.errorTitle / data.errorMessage are parsed. The API repeats the same text under
 * problemDetails, which produced the message twice; the data fields have never been missing in a year of use.
 */
class UltimateStatisticsWebsiteHelperTest {

    @Test
    void recognisesADuplicateReportFromTheLiveResponseBody() throws Exception {
        assertThat(UltimateStatisticsWebsiteHelper.isAlreadyReported(fixture())).isTrue();
        assertThat(UltimateStatisticsWebsiteHelper.describeExpectedRejection(fixture()))
                .startsWith("This game was already reported to TIGL")
                .contains("fow638");
    }

    // Profiles are created when a game reaches round 2, so a new player using a stats command legitimately has none.
    @Test
    void recognisesAMissingPlayerProfile() {
        JsonNode node = parse("""
                {"success":false,"data":{"success":false,"errorTitle":"Unable to update player settings!",\
                "errorMessage":"Player profile with Discord ID 1537559248404221984 not found."}}""");

        assertThat(UltimateStatisticsWebsiteHelper.isMissingPlayerProfile(node)).isTrue();
        assertThat(UltimateStatisticsWebsiteHelper.describeExpectedRejection(node))
                .contains("do not have a TI4 Ultimate profile yet");
    }

    @Test
    void leavesGenuineFailuresToTheNormalErrorPath() {
        JsonNode authFailure = parse("{\"message\":\"Invalid or missing API Key\"}");
        assertThat(UltimateStatisticsWebsiteHelper.describeExpectedRejection(authFailure))
                .isNull();

        JsonNode serverError = parse("{\"data\":{\"errorTitle\":\"Internal Server Error\",\"errorMessage\":\"boom\"}}");
        assertThat(UltimateStatisticsWebsiteHelper.describeExpectedRejection(serverError))
                .isNull();
        assertThat(UltimateStatisticsWebsiteHelper.isAlreadyReported(serverError))
                .isFalse();
        assertThat(UltimateStatisticsWebsiteHelper.isMissingPlayerProfile(serverError))
                .isFalse();
    }

    // problemDetails carries the same text as the data fields; parsing both is what duplicated it in the output.
    @Test
    void ignoresProblemDetailsEntirely() {
        JsonNode problemDetailsOnly = parse("""
                {"success":false,"data":null,"problemDetails":{"title":"This game was already reported",\
                "detail":"A game report for game with ID fow638 already exists in the database."}}""");

        assertThat(UltimateStatisticsWebsiteHelper.isAlreadyReported(problemDetailsOnly))
                .isFalse();
    }

    private static JsonNode parse(String json) {
        return JsonMapperManager.basic().readTree(json);
    }

    private static JsonNode fixture() throws Exception {
        try (InputStream in =
                UltimateStatisticsWebsiteHelperTest.class.getResourceAsStream("/tigl/report-game-duplicate.json")) {
            return JsonMapperManager.basic().readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
