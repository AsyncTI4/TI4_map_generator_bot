package ti4.website;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import ti4.json.JsonMapperManager;
import tools.jackson.databind.JsonNode;

/**
 * Two rejections from the TI4 Ultimate API are expected rather than exceptional, and used to ping Lazik in bot-log and
 * tell the table to contact him: re-reporting a game that was already reported, and a player using a stats command
 * before their profile exists. The duplicate body below was captured from the live API on 2026-09-23.
 *
 * <p>Lazik asked for problemDetails to be ignored, because report-game repeats the same text there and parsing both
 * printed it twice. The published OpenAPI spec shows why that cannot be applied everywhere: report-game's data object
 * is a GameReportResult, which carries errorTitle and errorMessage, but player-settings returns an
 * AsyncPlayerSettingsResponseDto, whose schema has no error fields at all - so there the reason can only ever arrive
 * under problemDetails. Messages are therefore composed from the data fields alone, which is what stops the
 * duplication, and problemDetails is read only to recognise a missing profile on the player-settings call.
 */
class UltimateStatisticsWebsiteHelperTest {

    @Test
    void recognisesADuplicateReportFromTheLiveResponseBody() throws Exception {
        assertThat(UltimateStatisticsWebsiteHelper.isAlreadyReported(fixture())).isTrue();
        assertThat(UltimateStatisticsWebsiteHelper.describeExpectedRejection(fixture(), false))
                .startsWith("This game was already reported to TIGL")
                .contains("fow638");
    }

    // Profiles are created when a game reaches round 2, so a new player using a stats command legitimately has none.
    @Test
    void recognisesAMissingPlayerProfile() {
        JsonNode node = parse("""
                {"success":false,"data":{"success":false,"errorTitle":"Unable to update player settings!",\
                "errorMessage":"Player profile with Discord ID 1537559248404221984 not found."}}""");

        assertThat(UltimateStatisticsWebsiteHelper.isMissingPlayerProfile(node, false))
                .isTrue();
        assertThat(UltimateStatisticsWebsiteHelper.describeExpectedRejection(node, false))
                .contains("do not have a TI4 Ultimate profile yet");
    }

    // The body /statistics opt_in and opt_out actually return, quoted by Lazik from bot-log on 2026-09-19. The data
    // object is null and the reason appears only under problemDetails, so reading data alone found nothing, fell
    // through to the generic failure path and pinged him about something he cannot act on.
    @Test
    void recognisesAMissingPlayerProfileFromThePlayerSettingsResponse() {
        JsonNode node = parse("""
                {"success":false,"data":null,"problemDetails":{"type":"",\
                "title":"Unable to update player settings!","status":null,\
                "detail":"Player profile with Discord ID 1537559248404221984 not found.","instance":""}}""");

        assertThat(UltimateStatisticsWebsiteHelper.isMissingPlayerProfile(node, true))
                .isTrue();
        assertThat(UltimateStatisticsWebsiteHelper.describeExpectedRejection(node, true))
                .contains("do not have a TI4 Ultimate profile yet");
    }

    @Test
    void leavesGenuineFailuresToTheNormalErrorPath() {
        JsonNode authFailure = parse("{\"message\":\"Invalid or missing API Key\"}");
        assertThat(UltimateStatisticsWebsiteHelper.describeExpectedRejection(authFailure, true))
                .isNull();

        JsonNode serverError = parse("{\"data\":{\"errorTitle\":\"Internal Server Error\",\"errorMessage\":\"boom\"}}");
        assertThat(UltimateStatisticsWebsiteHelper.describeExpectedRejection(serverError, true))
                .isNull();
        assertThat(UltimateStatisticsWebsiteHelper.isAlreadyReported(serverError))
                .isFalse();
        assertThat(UltimateStatisticsWebsiteHelper.isMissingPlayerProfile(serverError, true))
                .isFalse();
    }

    // Where report-game repeats the text under problemDetails, only the data fields are read, so the rendered
    // message says it once. Parsing both is what duplicated it.
    @Test
    void doesNotRepeatTextThatAppearsUnderBothDataAndProblemDetails() throws Exception {
        String rendered = UltimateStatisticsWebsiteHelper.describeError(fixture());

        assertThat(rendered)
                .isEqualTo("This game was already reported - A game report for game with ID fow638"
                        + " already exists in the database.");
        assertThat(StringUtils.countMatches(rendered, "already exists in the database"))
                .isEqualTo(1);
    }

    // problemDetails is consulted for the player-settings call only. Reading it for every endpoint would let a
    // report-game rejection that mentions a missing profile pass as an expected one: the dropped TIGL game report
    // would then be logged at info with no ping, and the table told - in the second person - about somebody else's
    // missing profile. A failed game report has to stay on the loud path.
    @Test
    void aFailedGameReportIsNeverExcusedByProblemDetails() {
        JsonNode problemDetailsOnly = parse("""
                {"success":false,"data":null,"problemDetails":{"title":"Unable to report game!",\
                "detail":"Player profile with Discord ID 1537559248404221984 not found."}}""");

        assertThat(UltimateStatisticsWebsiteHelper.isMissingPlayerProfile(problemDetailsOnly, false))
                .isFalse();
        assertThat(UltimateStatisticsWebsiteHelper.describeExpectedRejection(problemDetailsOnly, false))
                .isNull();

        // The same body from the player-settings call is the case Lazik asked to silence.
        assertThat(UltimateStatisticsWebsiteHelper.isMissingPlayerProfile(problemDetailsOnly, true))
                .isTrue();
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
