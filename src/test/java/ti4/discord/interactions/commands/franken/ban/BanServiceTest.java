package ti4.discord.interactions.commands.franken.ban;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.testUtils.BaseTi4Test;

class BanServiceTest extends BaseTi4Test {

    private final BanService banService = new BanService();

    @Test
    void applyOptionDeduplicatesStoredBans() {
        Game game = new Game();

        banService.applyOption(game, Constants.BAN_FACTION, "atokera");
        banService.applyOption(game, Constants.BAN_FACTION, "atokera");

        assertThat(game.getStoredValue("bannedFactions")).isEqualTo("atokera");
    }
}
