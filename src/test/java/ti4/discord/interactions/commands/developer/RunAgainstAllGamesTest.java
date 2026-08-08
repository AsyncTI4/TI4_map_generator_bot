package ti4.discord.interactions.commands.developer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.GameStats;
import ti4.json.JsonMapperManager;
import ti4.testUtils.BaseTi4Test;

class RunAgainstAllGamesTest extends BaseTi4Test {

    @Test
    void migrateActionCardTargetsMovesLegacySabotageTargetsOntoTheCanceledPlay() throws JsonProcessingException {
        Game game = new Game();
        game.setGameStats(legacyStats());

        boolean changed = RunAgainstAllGames.migrateActionCardTargets(game);

        assertThat(changed).isTrue();
        assertThat(game.getGameStats().getActionCardPlays())
                .extracting(GameStats.ActionCardPlay::isCanceled)
                .containsExactly(true, false);

        // Running again makes no further changes
        assertThat(RunAgainstAllGames.migrateActionCardTargets(game)).isFalse();
    }

    // A save from before cancels were flagged: the Sabotage play carries the name of the card it
    // canceled. Deliberately free of Overrule targets so the migration stays off the database.
    private static GameStats legacyStats() throws JsonProcessingException {
        return JsonMapperManager.basic().readValue("""
                        {"actionCardPlays":[
                            {"actionCard":"Flank Speed","playerId":"player1"},
                            {"actionCard":"Sabotage","playerId":"player2","target":"Flank Speed"}
                        ]}""", GameStats.class);
    }
}
