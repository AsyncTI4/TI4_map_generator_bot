package ti4.service.testbed;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.button.ReactionService;

@UtilityClass
class TestBedWaitReasons {

    static String describe(Game game) {
        List<String> reasons = new ArrayList<>();
        for (Player seat : game.getRealPlayers()) {
            List<String> waits = forSeat(game, seat);
            if (!waits.isEmpty()) reasons.add(seat.getFaction() + ": " + String.join(", ", waits));
        }
        String phase = "phase " + game.getPhaseOfGame() + ", round " + game.getRound();
        return reasons.isEmpty() ? phase + "; nothing pending" : phase + "; " + String.join("; ", reasons);
    }

    static List<String> forSeat(Game game, Player seat) {
        List<String> reasons = new ArrayList<>();
        String faction = seat.getFaction();
        String phase = game.getPhaseOfGame() == null ? "" : game.getPhaseOfGame();
        int round = game.getRound();
        if (seat.getUserID().equals(game.getActivePlayerID())) reasons.add("its turn");
        if ("statusScoring".equalsIgnoreCase(phase)) {
            if (game.getStoredValue(faction + "round" + round + "PO").isEmpty()) reasons.add("public scoring");
            if (game.getStoredValue(faction + "round" + round + "SO").isEmpty()) reasons.add("secret scoring");
        }
        if ("statusHomework".equalsIgnoreCase(phase) && !statusHomeworkDone(game, seat)) {
            reasons.add("status homework");
        }
        for (Integer initiative : game.getPlayedSCs()) {
            if (!seat.hasFollowedSC(initiative) && !seat.getSCs().contains(initiative)) {
                reasons.add("following strategy card " + initiative);
            }
        }
        return reasons;
    }

    private static boolean statusHomeworkDone(Game game, Player seat) {
        if (game.isFowMode()) return ReactionService.isFowStatusDone(game, seat);
        String key = "statusHomeworkReactionFor" + seat.getFaction() + "Round" + game.getRound();
        return !game.getStoredValue(key).isEmpty();
    }
}
