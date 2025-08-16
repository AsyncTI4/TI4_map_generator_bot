package ti4.commands.special;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.CommandHelper;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

abstract class AddRemoveFactionCCToFromFleet extends GameStateSubcommand {

    AddRemoveFactionCCToFromFleet(String id, String description) {
        super(id, description, true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.COLOR, "Faction Color for command token")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(
                OptionType.STRING, Constants.FACTION_COLOR, "Color/Faction for which we set command tokens"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        List<String> colors = new ArrayList<>();
        String colorString = event.getOption(Constants.COLOR).getAsString().toLowerCase();
        colorString = colorString.replace(" ", "");
        StringTokenizer colorTokenizer = new StringTokenizer(colorString, ",");
        while (colorTokenizer.hasMoreTokens()) {
            String color = CommandHelper.getColorFromString(game, colorTokenizer.nextToken());
            if (!colors.contains(color)) {
                colors.add(color);
                if (!Mapper.isValidColor(color)) {
                    MessageHelper.replyToMessage(event, "Color/faction not valid: " + color);
                    return;
                }
            }
        }
        action(event, colors, game, getPlayer());
    }

    abstract void action(SlashCommandInteractionEvent event, List<String> color, Game game, Player player);
}
