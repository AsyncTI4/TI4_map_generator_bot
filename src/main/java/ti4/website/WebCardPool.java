package ti4.website;

import lombok.Data;
import ti4.map.Game;

@Data
public class WebCardPool {

    // Secret Objectives
    private int secretObjectiveDeckSize;
    private int secretObjectiveFullDeckSize;

    // Action Cards
    private int actionCardDeckSize;
    private int actionCardFullDeckSize;
    private int actionCardDiscardSize;
    private int actionCardPurgedSize;

    // Exploration Cards
    private int culturalExploreDeckSize;
    private int culturalExploreDiscardSize;
    private int culturalExploreFullDeckSize;

    private int industrialExploreDeckSize;
    private int industrialExploreDiscardSize;
    private int industrialExploreFullDeckSize;

    private int hazardousExploreDeckSize;
    private int hazardousExploreDiscardSize;
    private int hazardousExploreFullDeckSize;

    private int frontierExploreDeckSize;
    private int frontierExploreDiscardSize;
    private int frontierExploreFullDeckSize;

    // Relics
    private int relicDeckSize;
    private int relicFullDeckSize;

    // Agendas
    private int agendaDeckSize;
    private int agendaFullDeckSize;
    private int agendaDiscardSize;


    public static WebCardPool fromGame(Game game) {
        WebCardPool cardPool = new WebCardPool();

        // Secret Objectives
        cardPool.setSecretObjectiveDeckSize(game.getSecretObjectiveDeckSize());
        cardPool.setSecretObjectiveFullDeckSize(game.getSecretObjectiveFullDeckSize());

        // Action Cards
        cardPool.setActionCardDeckSize(game.getActionCardDeckSize());
        cardPool.setActionCardFullDeckSize(game.getActionCardFullDeckSize());
        cardPool.setActionCardDiscardSize(game.getDiscardActionCards().size());
        cardPool.setActionCardPurgedSize(game.getPurgedActionCards().size());

        // Exploration Cards
        cardPool.setCulturalExploreDeckSize(game.getCulturalExploreDeckSize());
        cardPool.setCulturalExploreDiscardSize(game.getCulturalExploreDiscardSize());
        cardPool.setCulturalExploreFullDeckSize(game.getCulturalExploreFullDeckSize());

        cardPool.setIndustrialExploreDeckSize(game.getIndustrialExploreDeckSize());
        cardPool.setIndustrialExploreDiscardSize(game.getIndustrialExploreDiscardSize());
        cardPool.setIndustrialExploreFullDeckSize(game.getIndustrialExploreFullDeckSize());

        cardPool.setHazardousExploreDeckSize(game.getHazardousExploreDeckSize());
        cardPool.setHazardousExploreDiscardSize(game.getHazardousExploreDiscardSize());
        cardPool.setHazardousExploreFullDeckSize(game.getHazardousExploreFullDeckSize());

        cardPool.setFrontierExploreDeckSize(game.getFrontierExploreDeckSize());
        cardPool.setFrontierExploreDiscardSize(game.getFrontierExploreDiscardSize());
        cardPool.setFrontierExploreFullDeckSize(game.getFrontierExploreFullDeckSize());

        // Relics
        cardPool.setRelicDeckSize(game.getRelicDeckSize());
        cardPool.setRelicFullDeckSize(game.getRelicFullDeckSize());

        // Agendas
        cardPool.setAgendaDeckSize(game.getAgendaDeckSize());
        cardPool.setAgendaFullDeckSize(game.getAgendaFullDeckSize());
        cardPool.setAgendaDiscardSize(game.getDiscardAgendas().size());

        return cardPool;
    }
}