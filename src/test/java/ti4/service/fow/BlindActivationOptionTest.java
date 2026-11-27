package ti4.service.fow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.service.option.FOWOptionService;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.testUtils.BaseTi4Test;

class BlindActivationOptionTest extends BaseTi4Test {

    private static final String EMPTY_POSITION = "999";

    private Game game;

    @BeforeEach
    void setUpFogGame() {
        game = new Game();
        game.setName("blind-activation-test");
        game.setFowMode(true);
    }

    @Test
    void plainFogKeepsFreeActivationAndNoVoid() {
        assertThat(FOWPlusService.blindActivationActive(game)).isFalse();
        assertThat(FOWPlusService.isVoid(game, EMPTY_POSITION)).isFalse();
        assertThat(FOWPlusService.canActivatePosition("101", null, game, Set.of()))
                .isTrue();
    }

    // The point of the option: FoW+ play rules without FoW+'s forced options or explore deck.
    @Test
    void theOptionAloneGivesTheFowPlusPlayRules() {
        game.setFowOption(FOWOption.BLIND_ACTIVATION, true);

        assertThat(FOWPlusService.isActive(game)).isFalse();
        assertThat(FOWPlusService.isVoid(game, EMPTY_POSITION)).isTrue();
        assertThat(FOWPlusService.canActivatePosition("101", null, game, Set.of("102")))
                .isFalse();
        assertThat(FOWPlusService.canActivatePosition("102", null, game, Set.of("102")))
                .isTrue();
        assertThat(game.getFowOption(FOWOption.HIDE_STATS_VIA_SFTT)).isFalse();
        assertThat(game.getExplorationDeckID()).isNotEqualTo(FOWPlusService.FOWPLUS_EXPLORE_DECK);
    }

    @Test
    void warfareStillActivatesAnySystem() {
        game.setFowOption(FOWOption.BLIND_ACTIVATION, true);
        game.setWarfareAction(true);

        assertThat(FOWPlusService.canActivatePosition("101", null, game, Set.of()))
                .isTrue();
    }

    @Test
    void theOptionCanBeToggledWhileFowPlusIsOff() {
        FOWOptionService.applyToggle(game, "FLOW_true_" + FOWOption.BLIND_ACTIVATION);
        assertThat(FOWPlusService.blindActivationActive(game)).isTrue();

        FOWOptionService.applyToggle(game, "FLOW_false_" + FOWOption.BLIND_ACTIVATION);
        assertThat(FOWPlusService.blindActivationActive(game)).isFalse();
    }

    // Games that turned FoW+ on before the option existed have no stored value for it.
    @Test
    void anOlderFowPlusGameReadsTheOptionAsOn() {
        game.setFowOption(FOWOption.FOW_PLUS, true);

        assertThat(FOWPlusService.blindActivationActive(game)).isTrue();
        assertThat(FOWOptionService.isEnabled(game, FOWOption.BLIND_ACTIVATION)).isTrue();
    }

    // FoW+ on then off is the in-panel way to keep only the play rules.
    @Test
    void turningFowPlusOffKeepsBlindActivationAndUnlocksTheRest() {
        String originalDeck = game.getExplorationDeckID();
        FOWPlusService.enable(game);
        FOWPlusService.disable(game);

        assertThat(FOWPlusService.isActive(game)).isFalse();
        assertThat(FOWPlusService.blindActivationActive(game)).isTrue();
        assertThat(game.getExplorationDeckID()).isEqualTo(originalDeck);

        FOWOptionService.applyToggle(game, "PLAYERSTATS_false_" + FOWOption.HIDE_STATS_VIA_SFTT);
        assertThat(game.getFowOption(FOWOption.HIDE_STATS_VIA_SFTT)).isFalse();
    }
}
