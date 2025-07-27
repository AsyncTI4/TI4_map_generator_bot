package ti4.website.model;

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
        cardPool.setSecretObjectiveDeck(new ArrayList<>());
        cardPool.setSecretObjectiveFullDeckSize(game.getSecretObjectiveFullDeckSize());

        // Action Cards
        cardPool.setActionCardDeck(new ArrayList<>(game.getActionCards()));
        cardPool.setActionCardDiscard(new ArrayList<>(game.getDiscardActionCards().keySet()));
        cardPool.setActionCardFullDeckSize(game.getActionCardFullDeckSize());

        // Exploration Cards
        cardPool.setCulturalExploreDeck(new ArrayList<>(game.getExploreDeck(Constants.CULTURAL)));
        cardPool.setCulturalExploreDiscard(new ArrayList<>(game.getExploreDiscard(Constants.CULTURAL)));
        cardPool.setCulturalExploreFullDeckSize(game.getCulturalExploreFullDeckSize());

        cardPool.setIndustrialExploreDeck(new ArrayList<>(game.getExploreDeck(Constants.INDUSTRIAL)));
        cardPool.setIndustrialExploreDiscard(new ArrayList<>(game.getExploreDiscard(Constants.INDUSTRIAL)));
        cardPool.setIndustrialExploreFullDeckSize(game.getIndustrialExploreFullDeckSize());

        cardPool.setHazardousExploreDeck(new ArrayList<>(game.getExploreDeck(Constants.HAZARDOUS)));
        cardPool.setHazardousExploreDiscard(new ArrayList<>(game.getExploreDiscard(Constants.HAZARDOUS)));
        cardPool.setHazardousExploreFullDeckSize(game.getHazardousExploreFullDeckSize());

        cardPool.setFrontierExploreDeck(new ArrayList<>(game.getExploreDeck(Constants.FRONTIER)));
        cardPool.setFrontierExploreDiscard(new ArrayList<>(game.getExploreDiscard(Constants.FRONTIER)));
        cardPool.setFrontierExploreFullDeckSize(game.getFrontierExploreFullDeckSize());

        // Relics
        cardPool.setRelicDeck(new ArrayList<>(game.getAllRelics()));
        cardPool.setRelicFullDeckSize(game.getRelicFullDeckSize());

        // Agendas
        cardPool.setAgendaDeck(new ArrayList<>(game.getAgendas()));
        cardPool.setAgendaDiscard(new ArrayList<>(game.getDiscardAgendas().keySet()));
        cardPool.setAgendaFullDeckSize(game.getAgendaFullDeckSize());

        return cardPool;
    }
}