package ti4.service.fow;

import static org.assertj.core.api.Assertions.assertThat;

import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.testUtils.BaseTi4Test;

class FogStandardServiceTest extends BaseTi4Test {

    private static final String FOWPLUS_DECK = "explores_fowplus";

    private Game game;

    @BeforeEach
    void setUpFogGame() {
        game = new Game();
        game.setName("fog-standard-test");
        game.setFowMode(true);
    }

    @Test
    void theStandardTurnsOnFowPlusItsForcedOptionsAndTheExtras() {
        FogStandardService.apply(game);

        assertThat(FOWPlusService.isActive(game)).isTrue();
        FOWPlusService.FORCED_FOWPLUS_OPTIONS.forEach(forced -> assertThat(game.getFowOption(forced.getLeft()))
                .as(forced.getLeft().name())
                .isEqualTo(forced.getRight()));
        FogStandardService.FOWPLUS_EXTRAS.forEach(option ->
                assertThat(game.getFowOption(option)).as(option.name()).isTrue());
        // Agenda comms used to be on for new fog games; FoW+ forces them off.
        assertThat(game.getFowOption(FOWOption.ALLOW_AGENDA_COMMS)).isFalse();
        assertThat(game.getExplorationDeckID()).isEqualTo(FOWPLUS_DECK);
    }

    @Test
    void turningFowPlusOffBringsBackTheExploreDeckTheGameHadBefore() {
        game.setExplorationDeckID("explores_base");
        FogStandardService.apply(game);

        assertThat(FOWPlusService.disable(game)).isTrue();

        assertThat(FOWPlusService.isActive(game)).isFalse();
        assertThat(game.getExplorationDeckID()).isEqualTo("explores_base");
    }

    @Test
    void applyingTheStandardTwiceStillRemembersTheOriginalDeck() {
        game.setExplorationDeckID("explores_base");
        FogStandardService.apply(game);
        FogStandardService.apply(game);

        FOWPlusService.disable(game);

        assertThat(game.getExplorationDeckID()).isEqualTo("explores_base");
    }

    @Test
    void aDeckTheGmPickedWhileFowPlusWasOnIsLeftAlone() {
        FogStandardService.apply(game);
        game.setExplorationDeckID("explores_pi");

        assertThat(FOWPlusService.disable(game)).isFalse();
        assertThat(game.getExplorationDeckID()).isEqualTo("explores_pi");
    }

    @Test
    void theGmOverviewFitsInOneDiscordMessage() {
        FogStandardService.apply(game);

        MessageEmbed options = FogGameSummaryService.fogOptionsEmbed(game);

        assertThat(FogStandardService.gmOverviewText().length()).isLessThan(2000);
        assertThat(options.getLength()).isLessThanOrEqualTo(MessageEmbed.EMBED_MAX_LENGTH_BOT);
        assertThat(FogStandardService.ghostHexNote()).contains("/user fog_ghost_hexes show:False");
    }
}
