package ti4.website.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.DeckModel;

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

        // Secret Objectives - send all unscored secrets (full deck minus scored)
        DeckModel soDeckModel = Mapper.getDeck(game.getSoDeckID());
        List<String> unscoredSecrets = new ArrayList<>();
        if (soDeckModel != null) {
            Set<String> scoredSecrets = new HashSet<>();
            for (Player player : game.getRealPlayers()) {
                scoredSecrets.addAll(player.getSecretsScored().keySet());
            }
            unscoredSecrets = soDeckModel.getNewDeck().stream()
                    .filter(so -> !scoredSecrets.contains(so))
                    .collect(Collectors.toList());
        }
        cardPool.setSecretObjectiveDeck(shuffledCopy(unscoredSecrets));
        cardPool.setSecretObjectiveFullDeckSize(game.getSecretObjectiveFullDeckSize());

        // Action Cards
        cardPool.setActionCardDeck(new ArrayList<>());
        cardPool.setActionCardDiscard(
                new ArrayList<>(game.getDiscardActionCards().keySet()));
        cardPool.setActionCardFullDeckSize(game.getActionCardFullDeckSize());

        // Exploration Cards
        cardPool.setCulturalExploreDeck(shuffledCopy(game.getExploreDeck(Constants.CULTURAL)));
        cardPool.setCulturalExploreDiscard(new ArrayList<>(game.getExploreDiscard(Constants.CULTURAL)));
        cardPool.setCulturalExploreFullDeckSize(game.getCulturalExploreFullDeckSize());

        cardPool.setIndustrialExploreDeck(shuffledCopy(game.getExploreDeck(Constants.INDUSTRIAL)));
        cardPool.setIndustrialExploreDiscard(new ArrayList<>(game.getExploreDiscard(Constants.INDUSTRIAL)));
        cardPool.setIndustrialExploreFullDeckSize(game.getIndustrialExploreFullDeckSize());

        cardPool.setHazardousExploreDeck(shuffledCopy(game.getExploreDeck(Constants.HAZARDOUS)));
        cardPool.setHazardousExploreDiscard(new ArrayList<>(game.getExploreDiscard(Constants.HAZARDOUS)));
        cardPool.setHazardousExploreFullDeckSize(game.getHazardousExploreFullDeckSize());

        cardPool.setFrontierExploreDeck(shuffledCopy(game.getExploreDeck(Constants.FRONTIER)));
        cardPool.setFrontierExploreDiscard(new ArrayList<>(game.getExploreDiscard(Constants.FRONTIER)));
        cardPool.setFrontierExploreFullDeckSize(game.getFrontierExploreFullDeckSize());

        // Relics
        cardPool.setRelicDeck(shuffledCopy(game.getAllRelics()));
        cardPool.setRelicFullDeckSize(game.getRelicFullDeckSize());

        // Agendas
        cardPool.setAgendaDeck(shuffledCopy(game.getAgendas()));
        cardPool.setAgendaDiscard(new ArrayList<>(game.getDiscardAgendas().keySet()));
        cardPool.setAgendaFullDeckSize(game.getAgendaFullDeckSize());

        return cardPool;
    }

    private static List<String> shuffledCopy(List<String> cards) {
        List<String> shuffled = new ArrayList<>(cards);
        Collections.shuffle(shuffled);
        return shuffled;
    }
}
