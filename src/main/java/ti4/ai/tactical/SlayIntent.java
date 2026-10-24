package ti4.ai.tactical;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.scoring.WinRace;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class SlayIntent {

    static final String KEY = "aiSlay|";
    private static final String ROUND = "@";
    private static final String SEPARATOR = ",";

    public static Optional<AiDecision> announce(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        if (game.isFowMode()) return Optional.empty();
        List<Player> winners = WinRace.winnersBefore(game, seat);
        String targets = winners.stream().map(Player::getFaction).sorted().collect(Collectors.joining(SEPARATOR));
        if (targets.equals(declared(game, seat))) return Optional.empty();
        game.setStoredValue(KEY + seat.getFaction(), game.getRound() + ROUND + targets);
        if (winners.isEmpty()) return Optional.empty();
        return Optional.of(new AiDecision.Announce(message(seat, winners)));
    }

    static String message(Player seat, List<Player> winners) {
        String names = winners.stream().map(Player::getRepresentationNoPing).collect(Collectors.joining(", "));
        return "🤖 " + seat.getRepresentationNoPing() + ": We must slay " + names + ".";
    }

    public static List<String> targetsOf(Game game, Player aggressor) {
        String targets = declared(game, aggressor);
        return targets.isEmpty() ? List.of() : Arrays.asList(targets.split(SEPARATOR));
    }

    static boolean hunts(Game game, Player aggressor, Player target) {
        return targetsOf(game, aggressor).contains(target.getFaction());
    }

    static boolean huntsOthers(Game game, Player aggressor, Player seat) {
        List<String> targets = targetsOf(game, aggressor);
        return !targets.isEmpty() && !targets.contains(seat.getFaction());
    }

    private static String declared(Game game, Player seat) {
        String stored = game.getStoredValue(KEY + seat.getFaction());
        if (!stored.startsWith(game.getRound() + ROUND)) return "";
        return StringUtils.substringAfter(stored, ROUND);
    }
}
