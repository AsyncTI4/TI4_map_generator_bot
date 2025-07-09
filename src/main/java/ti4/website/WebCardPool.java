package ti4.website;

import lombok.Data;
import ti4.helpers.Constants;
import ti4.map.Game;

import java.util.ArrayList;
import java.util.List;

@Data
public class WebCardPool {

    // Secret Objectives
    private List<String> secretObjectiveDeck;
    private int secretObjectiveFullDeckSize;

    // Action Cards
    private List<String> actionCardDeck;
    private List<String> actionCardDiscard;
    private int actionCardFullDeckSize;

    // Exploration Cards
    private List<String> culturalExploreDeck;
    private List<String> culturalExploreDiscard;
    private int culturalExploreFullDeckSize;

    private List<String> industrialExploreDeck;
    private List<String> industrialExploreDiscard;
    private int industrialExploreFullDeckSize;

    private List<String> hazardousExploreDeck;
    private List<String> hazardousExploreDiscard;
    private int hazardousExploreFullDeckSize;

    private List<String> frontierExploreDeck;
    private List<String> frontierExploreDiscard;
    private int frontierExploreFullDeckSize;

    // Relics
    private List<String> relicDeck;
    private int relicFullDeckSize;

    // Agendas
    private List<String> agendaDeck;
    private List<String> agendaDiscard;
    private int agendaFullDeckSize;


    public static WebCardPool fromGame(Game game) {
        WebCardPool cardPool = new WebCardPool();

        // Secret Objectives
        List<String> allSecretObjectives = new ArrayList<>(game.getSecretObjectives());
        allSecretObjectives.addAll(getPlayerSecrets(game));
        allSecretObjectives.sort(new AlphanumericComparator());
        cardPool.setSecretObjectiveDeck(allSecretObjectives);
        cardPool.setSecretObjectiveFullDeckSize(game.getSecretObjectiveFullDeckSize());

        // Action Cards
        cardPool.setActionCardDeck(getDeckCopySorted(game.getActionCards()));
        cardPool.setActionCardDiscard(new ArrayList<>(game.getDiscardActionCards().keySet()));
        cardPool.setActionCardFullDeckSize(game.getActionCardFullDeckSize());

        // Exploration Cards
        cardPool.setCulturalExploreDeck(getDeckCopySorted(game.getExploreDeck(Constants.CULTURAL)));
        cardPool.setCulturalExploreDiscard(new ArrayList<>(game.getExploreDiscard(Constants.CULTURAL)));
        cardPool.setCulturalExploreFullDeckSize(game.getCulturalExploreFullDeckSize());

        cardPool.setIndustrialExploreDeck(getDeckCopySorted(game.getExploreDeck(Constants.INDUSTRIAL)));
        cardPool.setIndustrialExploreDiscard(new ArrayList<>(game.getExploreDiscard(Constants.INDUSTRIAL)));
        cardPool.setIndustrialExploreFullDeckSize(game.getIndustrialExploreFullDeckSize());

        cardPool.setHazardousExploreDeck(getDeckCopySorted(game.getExploreDeck(Constants.HAZARDOUS)));
        cardPool.setHazardousExploreDiscard(new ArrayList<>(game.getExploreDiscard(Constants.HAZARDOUS)));
        cardPool.setHazardousExploreFullDeckSize(game.getHazardousExploreFullDeckSize());

        cardPool.setFrontierExploreDeck(getDeckCopySorted(game.getExploreDeck(Constants.FRONTIER)));
        cardPool.setFrontierExploreDiscard(new ArrayList<>(game.getExploreDiscard(Constants.FRONTIER)));
        cardPool.setFrontierExploreFullDeckSize(game.getFrontierExploreFullDeckSize());

        // Relics
        cardPool.setRelicDeck(getDeckCopySorted(game.getAllRelics()));
        cardPool.setRelicFullDeckSize(game.getRelicFullDeckSize());

        // Agendas
        cardPool.setAgendaDeck(getDeckCopySorted(game.getAgendas()));
        cardPool.setAgendaDiscard(new ArrayList<>(game.getDiscardAgendas().keySet()));
        cardPool.setAgendaFullDeckSize(game.getAgendaFullDeckSize());

        return cardPool;
    }

    private static List<String> getDeckCopySorted(List<String> deck) {
        List<String> deckCopy = new ArrayList<>(deck);
        deckCopy.sort(new AlphanumericComparator());
        return deckCopy;
    }

    private static List<String> getPlayerSecrets(Game game) {
        return game.getPlayers().values().stream()
            .flatMap(player -> player.getSecrets().keySet().stream()).toList();
    }
}