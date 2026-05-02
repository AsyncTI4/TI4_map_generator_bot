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
        cardPool.secretObjectiveDeck = shuffledCopy(unscoredSecrets);
        cardPool.secretObjectiveFullDeckSize = game.getSecretObjectiveFullDeckSize();

        // Action Cards
        cardPool.actionCardDeck = new ArrayList<>();
        cardPool.actionCardDiscard =
                new ArrayList<>(game.getDiscardActionCards().keySet());
        cardPool.actionCardFullDeckSize = game.getActionCardFullDeckSize();

        // Exploration Cards
        cardPool.culturalExploreDeck = shuffledCopy(game.getExploreDeck(Constants.CULTURAL));
        cardPool.culturalExploreDiscard = new ArrayList<>(game.getExploreDiscard(Constants.CULTURAL));
        cardPool.culturalExploreFullDeckSize = game.getCulturalExploreFullDeckSize();

        cardPool.industrialExploreDeck = shuffledCopy(game.getExploreDeck(Constants.INDUSTRIAL));
        cardPool.industrialExploreDiscard = new ArrayList<>(game.getExploreDiscard(Constants.INDUSTRIAL));
        cardPool.industrialExploreFullDeckSize = game.getIndustrialExploreFullDeckSize();

        cardPool.hazardousExploreDeck = shuffledCopy(game.getExploreDeck(Constants.HAZARDOUS));
        cardPool.hazardousExploreDiscard = new ArrayList<>(game.getExploreDiscard(Constants.HAZARDOUS));
        cardPool.hazardousExploreFullDeckSize = game.getHazardousExploreFullDeckSize();

        cardPool.frontierExploreDeck = shuffledCopy(game.getExploreDeck(Constants.FRONTIER));
        cardPool.frontierExploreDiscard = new ArrayList<>(game.getExploreDiscard(Constants.FRONTIER));
        cardPool.frontierExploreFullDeckSize = game.getFrontierExploreFullDeckSize();

        // Relics
        cardPool.relicDeck = shuffledCopy(game.getAllRelics());
        cardPool.relicFullDeckSize = game.getRelicFullDeckSize();

        // Agendas
        cardPool.agendaDeck = shuffledCopy(game.getAgendas());
        cardPool.agendaDiscard = new ArrayList<>(game.getDiscardAgendas().keySet());
        cardPool.agendaFullDeckSize = game.getAgendaFullDeckSize();

        return cardPool;
    }

    private static List<String> shuffledCopy(List<String> cards) {
        List<String> shuffled = new ArrayList<>(cards);
        Collections.shuffle(shuffled);
        return shuffled;
    }
}
