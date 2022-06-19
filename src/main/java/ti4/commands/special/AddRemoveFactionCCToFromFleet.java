package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.*;
import ti4.message.MessageHelper;

import java.util.ArrayList;
import java.util.StringTokenizer;

abstract public class AddRemoveFactionCCToFromFleet extends SpecialSubcommandData {
    public AddRemoveFactionCCToFromFleet(String id, String description) {
        super(id, description);
        addOptions(new OptionData(OptionType.STRING, Constants.COLOR, "Faction or Color for CC")
                .setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getPlayer(activeMap, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        OptionMapping option = event.getOption(Constants.COLOR);
        ArrayList<String> colors = new ArrayList<>();
        if (option != null) {
            String colorString = option.getAsString().toLowerCase();
            colorString = colorString.replace(" ", "");
            StringTokenizer colorTokenizer = new StringTokenizer(colorString, ",");
            while (colorTokenizer.hasMoreTokens()) {
                String color = Helper.getColorFromString(activeMap, colorTokenizer.nextToken());
                if (!colors.contains(color)) {
                    colors.add(color);
                    if (!Mapper.isColorValid(color)) {
                        MessageHelper.replyToMessage(event, "Color/faction not valid: " + color);
                        return;
                    }
                }
            }
            action(event, colors, activeMap, player);
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Need to specify CC's");
        }
    }

    abstract void action(SlashCommandInteractionEvent event, ArrayList<String> color, Map activeMap, Player player);
}
