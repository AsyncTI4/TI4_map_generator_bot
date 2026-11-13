package ti4.discord.interactions.buttons.handlers.agenda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class VoiceOfArvidHandlerTest extends BaseTi4Test {

    @Test
    void megagameRelicIsInItsOwnDeck() {
        assertTrue(Mapper.getRelics().containsKey(VoiceOfArvidHandler.RELIC_ID));
        assertTrue(Mapper.getDecks().get("relics_megagame").getNewDeck().contains(VoiceOfArvidHandler.RELIC_ID));
    }

    @Test
    void electLawWithoutLawsHasNoValidTarget() {
        Game game = new Game();
        assertFalse(VoiceOfArvidHandler.hasValidTarget(game, "abolishment"));
        assertFalse(VoiceOfArvidHandler.hasValidTarget(game, "absol_emergency1"));
        assertTrue(VoiceOfArvidHandler.hasValidTarget(game, "mutiny"));
    }

    @Test
    void keepsDrawingPastTheTopFiveUntilOneHasAValidTarget() {
        Game game = new Game();
        // Top 5 are all elect-law agendas with no laws in play; the 7th is valid.
        List<String> deck =
                new ArrayList<>(List.of("abolishment", "miscount", "abolishment", "miscount", "abolishment"));
        deck.addAll(List.of("miscount", "mutiny", "covert"));
        game.setAgendas(deck);
        assertEquals(deck.subList(0, 7), VoiceOfArvidHandler.agendasToLookAt(game));

        game.setAgendas(
                new ArrayList<>(List.of("mutiny", "abolishment", "covert", "miscount", "abolishment", "covert")));
        assertEquals(5, VoiceOfArvidHandler.agendasToLookAt(game).size());
    }

    @Test
    void hiddenAndRevoteAgendasGoToTheGm() {
        assertTrue(VoiceOfArvidHandler.needsManualResolution("covert"));
        assertTrue(VoiceOfArvidHandler.needsManualResolution("miscount"));
        assertFalse(VoiceOfArvidHandler.needsManualResolution("mutiny"));
    }
}
