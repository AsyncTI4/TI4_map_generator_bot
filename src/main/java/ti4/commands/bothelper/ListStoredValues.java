package ti4.commands.bothelper;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.StringHelper;
import ti4.map.Game;
import ti4.message.MessageHelper;

class ListStoredValues extends GameStateSubcommand {
    private static final int NUM_OF_KEY_OPTIONS = 8;

    public ListStoredValues() {
        super(
                "list_stored_values",
                "List all of a game's stored values or specify keys to only list values for those keys.",
                false,
                false);
        addOption(OptionType.STRING, Constants.GAME_NAME, "Game to check", false, true);
        for (int i = 1; i <= NUM_OF_KEY_OPTIONS; i++) {
            addOption(OptionType.STRING, "stored_key_" + i, "Specified Key #" + i, false, false);
        }
    }

    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        List<String> keys = new LinkedList<String>();
        for (int i = 1; i <= NUM_OF_KEY_OPTIONS; i++) {
            String key = event.getOption("stored_key_" + i, "", OptionMapping::getAsString);
            if (!key.isBlank()) {
                keys.add(key);
            }
        }

        Map<String, String> storedMap = game.getMessagesThatICheckedForAllReacts();
        String message;
        if (keys.isEmpty()) {
            message = "No keys specified. Listing all the game's stored key-value-pairs:\n"
                    + String.join(
                            "\n",
                            storedMap.entrySet().stream()
                                    .sorted(Map.Entry.comparingByKey())
                                    .map(e -> "- `" + e.getKey() + "`: `" + StringHelper.unescape(e.getValue()) + "`")
                                    .toList());
        } else {
            if (keys.size() == 1) {
                message = "1 key specified. Checking value for specified key:\n";
            } else {
                message = keys.size() + " keys specified. Listing values for the specified keys:\n";
            }
            message += String.join(
                    "\n",
                    keys.stream()
                            .map(k -> {
                                String v = storedMap.get(k);
                                if (v == null || v.isEmpty()) {
                                    return "- Found no stored value for key `" + k + "`.";
                                }
                                return "- `" + k + "`: `" + StringHelper.unescape(v) + "`";
                            })
                            .toList());
        }

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
    }
}
