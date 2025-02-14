package ti4.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.image.Mapper;
import ti4.model.DeckModel.DeckType;
import ti4.testUtils.BaseTi4Test;

public class DeckModelTest extends BaseTi4Test {
    @Test
    public void testDeckModels() {
        for (DeckModel deckModel : Mapper.getDecks().values()) {
            assertTrue(deckModel.isValid(), deckModel.getAlias() + " [" + deckModel.getType() + "]: invalid");
            assertTrue(
                    validateCardIDs(deckModel),
                    deckModel.getAlias() + " [" + deckModel.getType() + "]: Invalid CardIDs");
        }
    }

    private static boolean validateCardIDs(DeckModel deckModel) {
        return switch (deckModel.getType()) {
            case DeckType.TECHNOLOGY -> validateTechDeck(deckModel);
            case DeckType.AGENDA -> validateAgendaDeck(deckModel);
            case DeckType.EVENT -> validateEventDeck(deckModel);
            case DeckType.ACTION_CARD -> validateActionCardDeck(deckModel);
            case DeckType.SECRET_OBJECTIVE -> validateSecretObjectiveDeck(deckModel);
            case DeckType.PUBLIC_STAGE_1_OBJECTIVE, DeckType.PUBLIC_STAGE_2_OBJECTIVE -> validatePublicObjectiveDecks(
                    deckModel);
            case DeckType.RELIC -> validateRelicDeck(deckModel);
            case DeckType.EXPLORE -> validateExploreDecks(deckModel);
            default -> false;
        };
    }

    private static boolean validateTechDeck(DeckModel deckModel) {
        if (Mapper.getTechs().keySet().containsAll(deckModel.getNewDeck())) return true;
        List<String> missingCardIDs = new ArrayList<>();
        for (String cardID : deckModel.getNewDeck()) {
            if (!Mapper.getTechs().containsKey(cardID)) missingCardIDs.add(cardID);
        }
        System.out.println("Deck **" + deckModel.getName() + "** failed validation due to invalid card IDs: `"
                + missingCardIDs + "`");
        return false;
    }

    private static boolean validateAgendaDeck(DeckModel deckModel) {
        if (Mapper.getAgendas().keySet().containsAll(deckModel.getNewDeck())) return true;
        List<String> missingCardIDs = new ArrayList<>();
        for (String cardID : deckModel.getNewDeck()) {
            if (!Mapper.getAgendas().containsKey(cardID)) missingCardIDs.add(cardID);
        }
        System.out.println("Deck **" + deckModel.getName() + "** failed validation due to invalid card IDs: `"
                + missingCardIDs + "`");
        return false;
    }

    private static boolean validateEventDeck(DeckModel deckModel) {
        if (Mapper.getEvents().keySet().containsAll(deckModel.getNewDeck())) return true;
        List<String> missingCardIDs = new ArrayList<>();
        for (String cardID : deckModel.getNewDeck()) {
            if (!Mapper.getEvents().containsKey(cardID)) missingCardIDs.add(cardID);
        }
        System.out.println("Deck **" + deckModel.getName() + "** failed validation due to invalid card IDs: `"
                + missingCardIDs + "`");
        return false;
    }

    private static boolean validateActionCardDeck(DeckModel deckModel) {
        if (Mapper.getActionCards().keySet().containsAll(deckModel.getNewDeck())) return true;
        List<String> missingCardIDs = new ArrayList<>();
        for (String cardID : deckModel.getNewDeck()) {
            if (!Mapper.getActionCards().containsKey(cardID)) missingCardIDs.add(cardID);
        }
        System.out.println("Deck **" + deckModel.getName() + "** failed validation due to invalid card IDs: `"
                + missingCardIDs + "`");
        return false;
    }

    private static boolean validateSecretObjectiveDeck(DeckModel deckModel) {
        if (Mapper.getSecretObjectives().keySet().containsAll(deckModel.getNewDeck())) return true;
        List<String> missingCardIDs = new ArrayList<>();
        for (String cardID : deckModel.getNewDeck()) {
            if (!Mapper.getSecretObjectives().containsKey(cardID)) missingCardIDs.add(cardID);
        }
        System.out.println("Deck **" + deckModel.getName() + "** failed validation due to invalid card IDs: `"
                + missingCardIDs + "`");
        return false;
    }

    private static boolean validatePublicObjectiveDecks(DeckModel deckModel) {
        if (Mapper.getPublicObjectives().keySet().containsAll(deckModel.getNewDeck())) return true;
        List<String> missingCardIDs = new ArrayList<>();
        for (String cardID : deckModel.getNewDeck()) {
            if (!Mapper.getPublicObjectives().containsKey(cardID)) missingCardIDs.add(cardID);
        }
        System.out.println("Deck **" + deckModel.getName() + "** failed validation due to invalid card IDs: `"
                + missingCardIDs + "`");
        return false;
    }

    private static boolean validateRelicDeck(DeckModel deckModel) {
        if (Mapper.getRelics().keySet().containsAll(deckModel.getNewDeck())) return true;
        List<String> missingCardIDs = new ArrayList<>();
        for (String cardID : deckModel.getNewDeck()) {
            if (!Mapper.getRelics().containsKey(cardID)) missingCardIDs.add(cardID);
        }
        System.out.println("Deck **" + deckModel.getName() + "** failed validation due to invalid card IDs: `"
                + missingCardIDs + "`");
        return false;
    }

    private static boolean validateExploreDecks(DeckModel deckModel) {
        if (Mapper.getExplores().keySet().containsAll(deckModel.getNewDeck())) return true;
        List<String> missingCardIDs = new ArrayList<>();
        for (String cardID : deckModel.getNewDeck()) {
            if (!Mapper.getExplores().containsKey(cardID)) missingCardIDs.add(cardID);
        }
        System.out.println("Deck **" + deckModel.getName() + "** failed validation due to invalid card IDs: `"
                + missingCardIDs + "`");
        return false;
    }
}
