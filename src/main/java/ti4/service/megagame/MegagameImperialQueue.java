package ti4.service.megagame;

import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class MegagameImperialQueue {

    private static final String NOT_DISCARDING = "factionsThatAreNotDiscardingSOs";
    private static final String QUEUE = "queueToDrawSOs";
    private static final String BLOCKERS = "potentialBlockers";

    public static void prepare(Game game, int sc) {
        Player holder = game.getPlayerFromSC(sc);
        List<Player> order = MegagameGroupService.followOrderForSc(game, sc, holder);
        for (Player member : order) {
            removeEntry(game, NOT_DISCARDING, member);
            removeEntry(game, QUEUE, member);
            removeEntry(game, BLOCKERS, member);
        }
        if (!game.isQueueSO()) {
            return;
        }
        for (Player member : order) {
            boolean clear = member.getSecretsCountedTowardLimit() < member.getMaxSOCount()
                    || (member.getSoScored() == member.getMaxSOCount() && !game.isSecretsOnlyMode())
                    || (member == holder && member.controlsMecatol(true));
            addEntry(game, clear ? NOT_DISCARDING : BLOCKERS, member);
        }
    }

    public static List<Player> imperialHolders(Game game) {
        return game.getPlayedSCs().stream()
                .filter(sc -> game.getStrategyCardModelByInitiative(sc)
                        .map(model -> model.usesAutomationForSCID("pok8imperial"))
                        .orElse(false))
                .sorted()
                .map(game::getPlayerFromSC)
                .filter(Objects::nonNull)
                .toList();
    }

    private static void removeEntry(Game game, String key, Player player) {
        game.setStoredValue(key, game.getStoredValue(key).replace(player.getFaction() + "*", ""));
    }

    private static void addEntry(Game game, String key, Player player) {
        game.setStoredValue(key, game.getStoredValue(key) + player.getFaction() + "*");
    }
}
