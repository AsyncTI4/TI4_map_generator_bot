package ti4.image.playerarea;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.service.fow.GMService;

public final class PlayerAreaWarnings {

    private static final String SENT_KEY_PREFIX = "playerAreaWarnings_";
    private static final String ENTRY_SEPARATOR = "|";
    private static final String COUNT_SEPARATOR = ":";

    private final List<Warning> warnings = new ArrayList<>();
    private final Set<Player> drawnPlayers = new LinkedHashSet<>();

    private record Warning(
            Player player, String kind, int count, String message, List<Button> buttons, boolean logToGm) {
        String entry() {
            return kind + COUNT_SEPARATOR + count;
        }
    }

    private PlayerAreaWarnings() {}

    public static PlayerAreaWarnings none() {
        return new PlayerAreaWarnings();
    }

    void drawn(Player player) {
        drawnPlayers.add(player);
    }

    void warn(Player player, String kind, int count, String message) {
        warnings.add(new Warning(player, kind, count, message, List.of(), false));
    }

    void warnWithButtons(Player player, String kind, int count, String message, List<Button> buttons) {
        warnings.add(new Warning(player, kind, count, message, buttons, true));
    }

    public void send(Game game) {
        for (Player player : drawnPlayers) {
            List<Warning> current = warnings.stream()
                    .filter(warning -> warning.player() == player)
                    .toList();
            String sentKey = SENT_KEY_PREFIX + player.getColor();
            Set<String> alreadySent = parseEntries(game.getStoredValue(sentKey));
            for (Warning warning : current) {
                if (!alreadySent.contains(warning.entry())) {
                    send(game, warning);
                }
            }
            game.setStoredValue(
                    sentKey, current.stream().map(Warning::entry).collect(Collectors.joining(ENTRY_SEPARATOR)));
        }
    }

    private static Set<String> parseEntries(String stored) {
        if (stored == null || stored.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(Arrays.asList(stored.split("\\" + ENTRY_SEPARATOR)));
    }

    private static void send(Game game, Warning warning) {
        Player player = warning.player();
        if (warning.buttons().isEmpty()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), warning.message());
        } else {
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(), warning.message(), warning.buttons());
        }
        if (warning.logToGm()) {
            GMService.logPlayerActivity(game, player, warning.message());
        }
    }
}
