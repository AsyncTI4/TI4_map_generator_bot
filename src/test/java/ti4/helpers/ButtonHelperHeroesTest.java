package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.testUtils.BaseTi4Test;

class ButtonHelperHeroesTest extends BaseTi4Test {

    @Test
    void drawAugerHeroObjectivesReturnsDrawnCardsToBottomOfDeck() {
        Game game = new Game();
        game.setPublicObjectives1(new ArrayList<>(List.of("po1", "po2", "po3", "po4", "po5")));

        List<String> drawnObjectives = ButtonHelperHeroes.drawAugerHeroObjectives(game, 1);

        assertThat(drawnObjectives).containsExactly("po1", "po2", "po3");
        assertThat(game.getPublicObjectives1()).containsExactly("po4", "po5", "po1", "po2", "po3");
    }

    @Test
    void putAugerHeroObjectiveNextReplacesPeekableObjectiveOutsideAgendaPhase() {
        Game game = new Game();
        game.setPhaseOfGame("status");
        game.setPublicObjectives1(new ArrayList<>(List.of("po1", "po2", "po3")));
        game.setPublicObjectives1Peekable(new ArrayList<>(List.of("peek1", "peek2")));

        ButtonHelperHeroes.putAugerHeroObjectiveNext(game, 1, "po2");

        assertThat(game.getPublicObjectives1Peekable()).containsExactly("po2", "peek2");
        assertThat(game.getPublicObjectives1()).containsExactly("po1", "po3", "peek1");
    }

    @Test
    void putAugerHeroObjectiveNextUsesDeckTopWhenNoPeekableObjectiveExists() {
        Game game = new Game();
        game.setPhaseOfGame("status");
        game.setPublicObjectives1(new ArrayList<>(List.of("po1", "po2", "po3")));
        game.setPublicObjectives1Peekable(new ArrayList<>());

        ButtonHelperHeroes.putAugerHeroObjectiveNext(game, 1, "po2");

        assertThat(game.getPublicObjectives1()).containsExactly("po2", "po1", "po3");
        assertThat(game.getPublicObjectives1Peekable()).isEmpty();
    }

    @Test
    void putAugerHeroObjectiveNextUsesDeckTopDuringAgendaPhase() {
        Game game = new Game();
        game.setPhaseOfGame("agenda");
        game.setPublicObjectives1(new ArrayList<>(List.of("po1", "po2", "po3")));
        game.setPublicObjectives1Peekable(new ArrayList<>(List.of("peek1", "peek2")));

        ButtonHelperHeroes.putAugerHeroObjectiveNext(game, 1, "po2");

        assertThat(game.getPublicObjectives1()).containsExactly("po2", "po1", "po3");
        assertThat(game.getPublicObjectives1Peekable()).containsExactly("peek1", "peek2");
    }
}
