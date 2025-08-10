package ti4.commands.special;

import java.util.List;
import java.util.Random;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.ColorModel;

public class SetupNeutralPlayer extends GameStateSubcommand {

    public SetupNeutralPlayer() {
        super("setup_neutral_player", "Setup neutral player units", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.COLOR, "Color for neutral units").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        List<String> unusedColors =
                game.getUnusedColors().stream().map(ColorModel::getName).toList();
        if (unusedColors.isEmpty()) {
            MessageHelper.replyToMessage(event, "Unable to find an unused color. This is probably a bug?");
            return;
        }

        String color = event.getOption(Constants.COLOR, null, OptionMapping::getAsString);
        if (color == null) {
            color = pickNeutralColor(unusedColors);
        } else if (!unusedColors.contains(color)) {
            MessageHelper.replyToMessage(event, "Selected color is in use.");
            return;
        }

        game.setupNeutralPlayer(color);
        MessageHelper.replyToMessage(event, "Neutral player has been set as " + color + ".");
    }

    public String pickNeutralColor(List<String> unusedColors) {
        Random random = new Random();
        int randomIndex = random.nextInt(unusedColors.size());
        return unusedColors.get(randomIndex);
    }
}
