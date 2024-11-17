package ti4.commands.special;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

abstract public class AddRemoveFactionCCToFromFleet extends SpecialSubcommandData {
    public AddRemoveFactionCCToFromFleet(String id, String description) {
        super(id, description);
        addOptions(new OptionData(OptionType.STRING, Constants.COLOR, "Faction Color for CC")
            .setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Color/Faction for which we set CC's").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        player = Helper.getPlayerFromEvent(game, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        OptionMapping option = event.getOption(Constants.COLOR);
        List<String> colors = new ArrayList<>();
        if (option != null) {
            String colorString = option.getAsString().toLowerCase();
            colorString = colorString.replace(" ", "");
            StringTokenizer colorTokenizer = new StringTokenizer(colorString, ",");
            while (colorTokenizer.hasMoreTokens()) {
                String color = Helper.getColorFromString(game, colorTokenizer.nextToken());
                if (!colors.contains(color)) {
                    colors.add(color);
                    if (!Mapper.isValidColor(color)) {
                        MessageHelper.replyToMessage(event, "Color/faction not valid: " + color);
                        return;
                    }
                }
            }
            action(event, colors, game, player);
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Need to specify CC's");
        }
    }

    abstract void action(SlashCommandInteractionEvent event, List<String> color, Game game, Player player);
}
