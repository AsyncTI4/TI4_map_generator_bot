package ti4.discord.interactions.commands.user;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class SetFowFilter extends GameStateSubcommand {

    private static final Map<String, String> SUFFIX_BY_LABEL = filtersByLabel();

    public SetFowFilter() {
        super(Constants.SET_FOG_FILTER, "Set the color of the fow tiles for your view of the map.", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.FOG_FILTER, "Color of the filter")
                .setAutoComplete(true)
                .setRequired(true));
    }

    public static List<String> filterLabels() {
        return List.copyOf(SUFFIX_BY_LABEL.keySet());
    }

    static String suffixFor(String input) {
        String wanted = input.trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> filter : SUFFIX_BY_LABEL.entrySet()) {
            if (filter.getKey().toLowerCase(Locale.ROOT).equals(wanted)
                    || filter.getValue().equals(wanted)) {
                return filter.getValue();
            }
        }
        return switch (wanted) {
            case "darkgrey", "dark grey", "grey", "gray" -> "default";
            default -> null;
        };
    }

    private static Map<String, String> filtersByLabel() {
        Map<String, String> filters = new LinkedHashMap<>();
        filters.put(Constants.FOW_FILTER_DARK_GREY, "default");
        filters.put(Constants.FOW_FILTER_SEPIA, "sepia");
        filters.put(Constants.FOW_FILTER_WHITE, "white");
        filters.put(Constants.FOW_FILTER_PINK, "pink");
        filters.put(Constants.FOW_FILTER_PURPLE, "purple");
        filters.put(Constants.FOW_FILTER_FROG, "frog");
        return filters;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String input = event.getOption(Constants.FOG_FILTER, "", OptionMapping::getAsString);
        String suffix = suffixFor(input);
        if (suffix == null) {
            MessageHelper.replyToMessage(
                    event, "Unknown fog filter `" + input + "`. Pick one of: " + String.join(", ", filterLabels()));
            return;
        }
        getPlayer().setFogFilter(suffix);
        MessageHelper.replyToMessage(event, "Fog filter set to " + suffix + ".");
    }
}
