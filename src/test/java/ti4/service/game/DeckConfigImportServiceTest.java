package ti4.service.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.testUtils.BaseTi4Test;

/**
 * Drives the Deck-editor config importer end-to-end against real deck data: a config's slots must
 * re-point the game's deck ids and materialize the live decks, and its exclusions must reuse the
 * existing per-card removal methods to drop the named cards from those live decks. The mocked event
 * is only touched for messaging channels (null-safe in testing mode) and the absent {@code reset_deck}
 * option (defaults to false), so the real SetDeckService/Game code paths run unchanged.
 */
class DeckConfigImportServiceTest extends BaseTi4Test {

    private Game game;
    private SlashCommandInteractionEvent event;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setName("deck-import-test");
        game.addPlayer("test-user-id", "winnu");
        // getMessageChannel()/getChannel() -> null (MessageHelper is null-safe under testingMode).
        event = mock(SlashCommandInteractionEvent.class);
        // SetDeckService reads the optional "reset_deck" option via JDA's default getOption(name,
        // fallback, resolver). A real event runs that default method and returns the fallback; a
        // Mockito mock returns null instead, so stub it to the fallback the real bot would produce.
        when(event.getOption(eq("reset_deck"), eq(Boolean.FALSE), any())).thenReturn(false);
    }

    @Test
    void slotChangeRepointsDeckIdAndMaterializesLiveDeck() {
        // Default relicDeckID is "relics_pok_te"; switching to "relics_pok" forces the change path.
        String json = """
                {
                  "schema": "ti4-deck-editor-config",
                  "slots": { "relic_deck": "relics_pok" }
                }
                """;

        DeckConfigImportService.importDeckConfig(event, game, json);

        assertEquals("relics_pok", game.getRelicDeckID(), "slot must re-point the game's relic deck id");
        assertFalse(game.getAllRelics().isEmpty(), "setting the relic slot must materialize the live relic deck");
        assertTrue(game.getAllRelics().contains("dominusorb"), "materialized deck must contain the deck's cards");
    }

    @Test
    void exclusionRemovesNamedCardFromLiveDeck() {
        String json = """
                {
                  "schema": "ti4-deck-editor-config",
                  "slots": { "relic_deck": "relics_pok" },
                  "exclusions": { "relics_pok": ["codex"] }
                }
                """;

        DeckConfigImportService.importDeckConfig(event, game, json);

        assertFalse(game.getAllRelics().contains("codex"), "excluded card must be removed from the live deck");
        assertTrue(game.getAllRelics().contains("dominusorb"), "non-excluded cards must remain");
    }

    @Test
    void unknownSlotDeckAndUnsupportedTechExclusionDoNotAbortTheImport() {
        // A bogus slot alias and a technology exclusion (unsupported) must be tolerated while the
        // valid relic slot + exclusion still apply.
        String json = """
                {
                  "schema": "ti4-deck-editor-config",
                  "slots": { "relic_deck": "relics_pok", "ac_deck": "not_a_real_deck" },
                  "exclusions": {
                    "relics_pok": ["codex"],
                    "techs_pok_c4": ["gravitydrive"]
                  }
                }
                """;

        DeckConfigImportService.importDeckConfig(event, game, json);

        assertEquals("relics_pok", game.getRelicDeckID(), "valid slot must still apply despite a bad sibling slot");
        assertFalse(
                game.getAllRelics().contains("codex"), "valid exclusion must still apply despite an unsupported one");
        assertEquals(
                "action_cards_pok",
                game.getAcDeckID(),
                "the unknown ac_deck alias must be skipped, leaving the default untouched");
    }
}
