package ti4.commands.special;

import java.util.Collections;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.ColorModel;
import ti4.service.emoji.ColorEmojis;
import ti4.service.game.GameColorsService;

public class SetupNeutralPlayer extends GameStateSubcommand {

    public SetupNeutralPlayer() {
        super("setup_neutral_player", "Setup neutral player units", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.COLOR, "Color for neutral units").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        String color = event.getOption(Constants.COLOR, null, OptionMapping::getAsString);
        if (color == null) {
            color = pickNeutralColor(game);
        } else if (!getUnusedColors(game).contains(color)) {
            MessageHelper.replyToMessage(event, "Selected color is in use.");
            return;
        }

        game.setupNeutralPlayer(color);
        MessageHelper.replyToMessage(
                event,
                "Neutral player has been set as " + color + "**"
                        + ColorEmojis.getColorEmoji(color).toString().toUpperCase() + "**.");
    }

    public static String pickNeutralColor(Game game) {
        List<String> unusedColors = getUnusedColors(game);
        if (unusedColors.contains("aberration")) {
            return "aberration";
        }
        if (unusedColors.contains("gray")) {
            return "gray";
        }

        Collections.shuffle(unusedColors);

        for (String color : unusedColors) {
            if (!color.contains("split")) {
                return color;
            }
        }

        return unusedColors.getFirst();
    }

    private static List<String> getUnusedColors(Game game) {
        return GameColorsService.getUnusedColors(game).stream()
                .map(ColorModel::getName)
                .toList();
    }
}
