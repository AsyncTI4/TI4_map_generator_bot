package ti4.helpers.thirtyk;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.image.Mapper;
import ti4.model.ActionCardModel;
import ti4.model.DeckModel;
import ti4.model.ExploreModel;
import ti4.model.RelicModel;
import ti4.model.SecretObjectiveModel;
import ti4.model.Source.ComponentSource;

@UtilityClass
public class ThirtyKCards {

    static final String IN_USE_KEY = "thirtyKInUse";
    private static final Pattern COPY_NUMBER = Pattern.compile("(30k|jmsc)\\d*(extra\\d)?$");
    private static final Map<String, Boolean> DECK_HAS_THIRTYK = new ConcurrentHashMap<>();

    public static boolean inUse(Game game) {
        if (game == null) return false;
        if (!game.getStoredValue(IN_USE_KEY).isBlank()) return true;
        boolean usesThirtyKDeck = Stream.of(
                        game.getAcDeckID(), game.getSoDeckID(), game.getRelicDeckID(), game.getExplorationDeckID())
                .anyMatch(ThirtyKCards::deckHasThirtyKCards);
        if (usesThirtyKDeck) game.setStoredValue(IN_USE_KEY, "true");
        return usesThirtyKDeck;
    }

    private static boolean deckHasThirtyKCards(String deckID) {
        if (deckID == null) return false;
        return DECK_HAS_THIRTYK.computeIfAbsent(deckID, id -> {
            DeckModel deck = Mapper.getDeck(id);
            return deck != null && deck.getCardIDs().stream().anyMatch(ThirtyKCards::isThirtyKCard);
        });
    }

    private static boolean isThirtyKCard(String cardID) {
        return isActionCard(cardID) || isExplore(cardID) || isRelic(cardID) || isSecretObjective(cardID);
    }

    public static String baseId(String cardId) {
        if (cardId == null) return "";
        return COPY_NUMBER.matcher(cardId).replaceAll("$1");
    }

    public static boolean isActionCard(String alias) {
        ActionCardModel card = Mapper.getActionCard(alias);
        return card != null && card.getSource() == ComponentSource.thirtyk;
    }

    public static boolean isExplore(String id) {
        ExploreModel card = Mapper.getExplore(id);
        return card != null && card.getSource() == ComponentSource.thirtyk;
    }

    public static boolean isRelic(String alias) {
        RelicModel card = Mapper.getRelic(alias);
        return card != null && card.getSource() == ComponentSource.thirtyk;
    }

    public static boolean isSecretObjective(String alias) {
        SecretObjectiveModel card = Mapper.getSecretObjective(alias);
        return card != null && card.getSource() == ComponentSource.thirtyk;
    }
}
