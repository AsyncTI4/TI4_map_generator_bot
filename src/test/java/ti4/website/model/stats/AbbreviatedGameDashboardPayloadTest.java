package ti4.website.model.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import ti4.map.Game;
import ti4.map.Player;
import ti4.testUtils.BaseTi4Test;

class AbbreviatedGameDashboardPayloadTest extends BaseTi4Test {

    @Test
    void basicFieldsAreIncluded() {
        Game game = new Game();
        game.setName("pbd123");
        game.setCustomName("Test Game");
        game.setRound(3);
        game.setVp(12);
        game.setPhaseOfGame("action");
        game.setLastModifiedDate(Instant.parse("2024-05-01T12:34:56Z").toEpochMilli());

        Player player = game.addPlayer("user1", "Alice");
        player.setColor("blue");
        player.setFaction("mentak");

        game.setActivePlayerID(player.getUserID());
        game.setSpeakerUserID(player.getUserID());

        AbbreviatedGameDashboardPayload payload = new AbbreviatedGameDashboardPayload(game);

        assertThat(payload.getAsyncGameID()).isEqualTo("pbd123");
        assertThat(payload.getAsyncFunGameName()).isEqualTo("Test Game");
        assertThat(payload.getRound()).isEqualTo(3);
        assertThat(payload.getScoreboard()).isEqualTo(12);
        assertThat(payload.getPhaseOfGame()).isEqualTo("action");
        assertThat(payload.getTurn()).isEqualTo("blue");
        assertThat(payload.getSpeaker()).isEqualTo("blue");
        assertThat(payload.getTimestamp()).isEqualTo(Instant.parse("2024-05-01T12:34:56Z").getEpochSecond());

        assertThat(payload.getPlayers()).hasSize(1);
        AbbreviatedPlayerDashboardPayload playerPayload = payload.getPlayers().get(0);
        assertThat(playerPayload.getDiscordUserID()).isEqualTo("user1");
        assertThat(playerPayload.getDiscordUsername()).isEqualTo("Alice");
        assertThat(playerPayload.getColor()).isEqualTo("blue");
        assertThat(playerPayload.getFaction()).isEqualTo("The Mentak Coalition");
        assertThat(playerPayload.isActive()).isTrue();
        assertThat(playerPayload.isSpeaker()).isTrue();
        assertThat(playerPayload.getVictoryPoints()).isZero();
    }

    @Test
    void endedTimestampIsNullWhenGameIsOngoing() {
        Game game = new Game();
        game.setHasEnded(false);

        AbbreviatedGameDashboardPayload payload = new AbbreviatedGameDashboardPayload(game);

        assertThat(payload.getEndedTimestamp()).isNull();
    }

    @Test
    void endedTimestampIsProvidedWhenGameHasEnded() {
        Game game = new Game();
        game.setHasEnded(true);
        game.setEndedDate(Instant.parse("2024-06-02T08:30:00Z").toEpochMilli());

        AbbreviatedGameDashboardPayload payload = new AbbreviatedGameDashboardPayload(game);

        assertThat(payload.getEndedTimestamp()).isEqualTo(Instant.parse("2024-06-02T08:30:00Z").getEpochSecond());
    }
}
