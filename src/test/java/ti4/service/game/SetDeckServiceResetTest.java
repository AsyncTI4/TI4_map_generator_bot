package ti4.service.game;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.model.DeckModel;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class SetDeckServiceResetTest extends BaseTi4Test {

    private Game game;
    private Player player;
    private GenericInteractionCreateEvent event;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setName("set-deck-reset-test");
        game.addPlayer("test-user-id", "winnu");
        player = game.getPlayer("test-user-id");
        event = mock(GenericInteractionCreateEvent.class);
    }

    @Test
    void secretObjectiveResetClearsHandsAndScoredSecrets() {
        DeckModel deck = Mapper.getDecks().get("secret_objectives_pok");
        String handSecret = deck.getNewShuffledDeck().get(0);
        player.getSecrets().put(handSecret, 1);
        player.getSecretsScored().put(deck.getNewShuffledDeck().get(1), 2);
        game.setSecretObjectives(new ArrayList<>());

        assertTrue(SetDeckService.setDeck(event, game, Constants.SO_DECK, deck, true));

        assertTrue(player.getSecrets().isEmpty());
        assertTrue(player.getSecretsScored().isEmpty());
        assertTrue(game.getSecretObjectives().contains(handSecret));
    }

    @Test
    void relicResetRemovesDeckRelicsButKeepsFakeRelics() {
        player.addRelic("dominusorb");
        player.addExhaustedRelic("dominusorb");
        player.addRelic("richhostages30k");

        assertTrue(SetDeckService.setDeck(
                event, game, Constants.RELIC_DECK, Mapper.getDecks().get("relics_pok"), true));

        assertFalse(player.hasRelic("dominusorb"));
        assertTrue(player.getExhaustedRelics().isEmpty());
        assertTrue(player.hasRelic("richhostages30k"));
        assertTrue(game.getAllRelics().contains("dominusorb"));
    }

    @Test
    void agendaResetClearsLawsAndDiscardAndRestoresFullDeck() {
        game.addLaw("warrant", null);
        game.getDiscardAgendas().put("censure", 7);
        game.setAgendas(new ArrayList<>());

        assertTrue(SetDeckService.setDeck(
                event, game, Constants.AGENDA_DECK, Mapper.getDecks().get(game.getAgendaDeckID()), true));

        assertTrue(game.getLaws().isEmpty());
        assertTrue(game.getLawsInfo().isEmpty());
        assertTrue(game.getDiscardAgendas().isEmpty());
        assertFalse(game.getAgendas().isEmpty());
    }

    @Test
    void explorationResetClearsDiscardAndFragments() {
        player.addFragment("crf1");
        game.getAllExploreDiscard().add("cultural1");

        assertTrue(SetDeckService.setDeck(
                event, game, Constants.EXPLORATION_DECKS, Mapper.getDecks().get(game.getExplorationDeckID()), true));

        assertTrue(player.getFragments().isEmpty());
        assertTrue(game.getAllExploreDiscard().isEmpty());
    }
}
