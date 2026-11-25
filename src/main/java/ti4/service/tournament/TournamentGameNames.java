package ti4.service.tournament;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.game.persistence.GameManager;
import ti4.service.game.CreateGameService;
import ti4.spring.service.tournament.Tournament;

@UtilityClass
public class TournamentGameNames {

    public static final int MAX_PREFIX_LENGTH = 16;
    private static final Pattern VALID_PREFIX = Pattern.compile("^[a-z][a-z0-9]{1," + (MAX_PREFIX_LENGTH - 1) + "}$");

    public static String normalize(String prefix) {
        return prefix == null ? "" : prefix.trim().toLowerCase(Locale.ROOT);
    }

    public static Optional<String> problem(String prefix) {
        if (!VALID_PREFIX.matcher(prefix).matches()) {
            return Optional.of("Use 2-" + MAX_PREFIX_LENGTH
                    + " lowercase letters and digits, starting with a letter. No spaces or dashes.");
        }
        if (prefix.startsWith("pbd") || prefix.startsWith("fow")) {
            return Optional.of("Names starting with pbd or fow are kept for the bot's own numbering.");
        }
        return Optional.empty();
    }

    public static boolean usesCustomNames(Tournament tournament) {
        return !tournament.isFogMode() && StringUtils.isNotBlank(tournament.getGameNamePrefix());
    }

    public static String customName(Tournament tournament, int group) {
        return tournament.getGameNamePrefix() + String.format("%02d", group);
    }

    public static String example(Tournament tournament) {
        if (!usesCustomNames(tournament)) return tournament.isFogMode() ? "fow###" : "pbd####";
        return customName(tournament, 1) + ", " + customName(tournament, 2) + ", …";
    }

    public static boolean isTaken(String gameName) {
        return GameManager.isValid(gameName) || CreateGameService.gameOrRoleAlreadyExists(gameName);
    }
}
