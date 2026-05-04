package ti4.game.helper;

import java.util.List;
import ti4.game.Player;
import ti4.image.Mapper;

public interface TwilightFallDeckFuncs {

    List<Player> getRealPlayersNNeutral();

    String getStoredValue(String val);

    boolean isVeiledHeartMode();

    String getAbilitySpliceDeckID();

    String getGenomeSpliceDeckID();

    String getParadigmSpliceDeckID();

    String getUnitSpliceDeckID();

    default List<String> getAbilitySpliceDeck(boolean includeVeiledCards) {
        String deckID = getAbilitySpliceDeckID();
        if (deckID == null || deckID.isEmpty()) {
            deckID = "techs_tf";
        }
        List<String> allCards = Mapper.getDeck(deckID).getNewShuffledDeck();
        for (Player p : getRealPlayersNNeutral()) {
            for (String tech : p.getTechs()) {
                allCards.remove(tech);
            }
            for (String tech : p.getPurgedTechs()) {
                allCards.remove(tech);
            }
        }

        for (String card : List.copyOf(allCards)) {
            if (getStoredValue("purgedAbilities").contains("_" + card)) {
                allCards.remove(card);
            }
        }
        if (isVeiledHeartMode() && !includeVeiledCards) {
            removeVeiledCards(allCards);
        }
        return allCards;
    }

    default List<String> getGenomeSpliceDeck(boolean includeVeiledCards) {
        String deckID = getGenomeSpliceDeckID();
        if (deckID == null || deckID.isEmpty()) {
            deckID = "tf_genome";
        }
        List<String> allCards = Mapper.getDeck(deckID).getNewShuffledDeck();
        for (Player p : getRealPlayersNNeutral()) {
            for (String genome : p.getLeaderIDs()) {
                allCards.remove(genome);
            }
        }
        if (isVeiledHeartMode() && !includeVeiledCards) {
            removeVeiledCards(allCards);
        }
        return allCards;
    }

    default List<String> getUnitSpliceDeck(boolean includeVeiledCards) {
        String deckID = getUnitSpliceDeckID();
        if (deckID == null || deckID.isEmpty()) {
            deckID = "tf_units";
        }
        List<String> allCards = Mapper.getDeck(deckID).getNewShuffledDeck();
        for (Player p : getRealPlayersNNeutral()) {
            for (String unit : p.getUnitsOwned()) {
                allCards.remove(unit);
            }
        }
        if (isVeiledHeartMode() && !includeVeiledCards) {
            removeVeiledCards(allCards);
        }
        return allCards;
    }

    default List<String> getParadigmSpliceDeck(boolean includeVeiledCards) {
        String deckID = getParadigmSpliceDeckID();
        if (deckID == null || deckID.isEmpty()) {
            deckID = "tf_paradigm";
        }
        List<String> allCards = Mapper.getDeck(deckID).getNewShuffledDeck();
        List<String> alreadyDrawn = List.of(getStoredValue("savedParadigms").split("_"));
        for (String card : alreadyDrawn) {
            // savedParadigms includes veiled paradigms, which should only be removed if includeVeiledCards is false
            boolean shouldRemove = true;
            if (isVeiledHeartMode() & includeVeiledCards) {
                for (Player p2 : getRealPlayersNNeutral()) {
                    if (getStoredValue("veiledCards" + p2.getFaction()).contains(card)) {
                        shouldRemove = false;
                        break;
                    }
                }
            }
            if (shouldRemove) {
                if ("hacanhero".equalsIgnoreCase(card)) allCards.remove("sanctionhero");
                allCards.remove(card);
            }
        }
        return allCards;
    }

    private void removeVeiledCards(List<String> cards) {
        for (String card : List.copyOf(cards)) {
            for (Player p : getRealPlayersNNeutral()) {
                if (getStoredValue("veiledCards" + p.getFaction()).contains(card)) cards.remove(card);
            }
        }
    }
}
