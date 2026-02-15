package ti4.service.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.map.Game;

class RoundOneServiceTest {

    @Test
    void getTeActionCardDeckAlias_returnsStandardTeDeckWhenNotUsingAcd2() {
        Game game = new Game();
        game.setAcDeckID("action_cards_pok");

        assertThat(RoundOneService.getTeActionCardDeckAlias(game)).isEqualTo("action_cards_te");
    }

    @Test
    void getTeActionCardDeckAlias_returnsAcd2TeDeckWhenUsingAcd2WithoutPok() {
        Game game = new Game();
        game.setAcDeckID("action_deck_2");
        game.setProphecyOfKings(false);

        assertThat(RoundOneService.getTeActionCardDeckAlias(game)).isEqualTo("action_deck_2_te");
    }

    @Test
    void getTeActionCardDeckAlias_returnsAcd2PokTeDeckWhenUsingAcd2WithPok() {
        Game game = new Game();
        game.setAcDeckID("action_deck_2_pok");
        game.setProphecyOfKings(true);

        assertThat(RoundOneService.getTeActionCardDeckAlias(game)).isEqualTo("action_deck_2_pok_te");
    }
}
