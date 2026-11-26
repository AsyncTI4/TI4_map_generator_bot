package ti4.ai;

import java.util.List;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.helpers.RandomHelper;

@UtilityClass
public class AiSeats {

    public static final String ID_PREFIX = "7100000";
    private static final int ID_LENGTH = 16;
    private static final int RANDOM_DIGITS = ID_LENGTH - ID_PREFIX.length();
    private static final long ID_SPAN = (long) Math.pow(10, RANDOM_DIGITS);
    private static final long GAME_SALT = 1_000_003L;
    private static final int MAX_ATTEMPTS = 1000;

    public static boolean isAiSeatId(@Nullable String userId) {
        return userId != null
                && userId.length() == ID_LENGTH
                && userId.startsWith(ID_PREFIX)
                && userId.chars().allMatch(Character::isDigit);
    }

    public static boolean isAiSeat(@Nullable Player player) {
        return player != null && isAiSeatId(player.getUserID());
    }

    public static boolean isActiveAiSeat(@Nullable Player player) {
        return isAiSeat(player) && player.isRealPlayer() && !player.isEliminated();
    }

    public static List<Player> aiSeats(Game game) {
        return game.getPlayers().values().stream().filter(AiSeats::isAiSeat).toList();
    }

    public static List<Player> activeAiSeats(Game game) {
        return game.getPlayers().values().stream()
                .filter(AiSeats::isActiveAiSeat)
                .toList();
    }

    public static boolean hasAiSeat(Game game) {
        return game.getPlayers().keySet().stream().anyMatch(AiSeats::isAiSeatId);
    }

    public static boolean hasAiSeat(@Nullable ManagedGame managedGame) {
        return managedGame != null && managedGame.getPlayerIds().stream().anyMatch(AiSeats::isAiSeatId);
    }

    public static boolean isSelfPlay(Game game) {
        List<Player> real = game.getRealPlayers();
        return !real.isEmpty() && real.stream().allMatch(AiSeats::isAiSeat);
    }

    public static String newSeatId(Game game) {
        long first = Math.floorMod(randomNumber() + GAME_SALT * game.getName().hashCode(), ID_SPAN);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = ID_PREFIX + digits(Math.floorMod(first + attempt, ID_SPAN));
            if (game.getPlayer(candidate) == null && GameManager.getManagedPlayer(candidate) == null) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique AI seat id");
    }

    private static long randomNumber() {
        long number = 0;
        for (int i = 0; i < RANDOM_DIGITS; i++)
            number = number * 10 + RandomHelper.current().nextInt(10);
        return number;
    }

    private static String digits(long number) {
        return StringUtils.leftPad(Long.toString(number), RANDOM_DIGITS, "0");
    }
}
