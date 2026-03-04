package ti4.map.helper;

import java.util.List;
import ti4.image.Mapper;
import ti4.map.Player;

public interface TwilightFallDeckFuncs {

    List<Player> getRealPlayers();

    String getStoredValue(String val);

    boolean isVeiledHeartMode();

    String getAbilitySpliceDeckID();

    String getGenomeSpliceDeckID();

    String getParadigmSpliceDeckID();

    String getUnitSpliceDeckID();

    default List<String> getAbilitySpliceDeck(boolean includeVeiledCards) {
        List<String> allCards = Mapper.getDeck(getAbilitySpliceDeckID()).getNewShuffledDeck();
        for (Player p : getRealPlayers()) {
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
        List<String> allCards = Mapper.getDeck(getGenomeSpliceDeckID()).getNewShuffledDeck();
        for (Player p : getRealPlayers()) {
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
        List<String> allCards = Mapper.getDeck(getUnitSpliceDeckID()).getNewShuffledDeck();
        for (Player p : getRealPlayers()) {
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
        List<String> allCards = Mapper.getDeck(getParadigmSpliceDeckID()).getNewShuffledDeck();
        List<String> alreadyDrawn = List.of(getStoredValue("savedParadigms").split("_"));
        for (String card : alreadyDrawn) {
            // savedParadigms includes veiled paradigms, which should only be removed if includeVeiledCards is false
            boolean shouldRemove = true;
            if (isVeiledHeartMode() & includeVeiledCards) {
                for (Player p2 : getRealPlayers()) {
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
            for (Player p : getRealPlayers()) {
                if (getStoredValue("veiledCards" + p.getFaction()).contains(card))
                    ;
                cards.remove(card);
            }
        }
    }
}
