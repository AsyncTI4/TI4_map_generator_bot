package ti4.commands.fow;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.PlayerGameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SetFogFilter extends PlayerGameStateSubcommand {

    public SetFogFilter() {
        super(Constants.SET_FOG_FILTER, "Set the color of the fog tiles for your view of the map.", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.FOG_FILTER, "How you want the tile to be labeled").setAutoComplete(true).setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping fogColorMapping = event.getOption(Constants.FOG_FILTER);
        if (fogColorMapping == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify color");
            return;
        }

        String color_suffix = null;
        switch (fogColorMapping.getAsString()) {
            case Constants.FOW_FILTER_DARK_GREY, "default", "darkgrey", "grey", "gray" -> color_suffix = "default";
            case Constants.FOW_FILTER_SEPIA, "sepia" -> color_suffix = "sepia";
            case Constants.FOW_FILTER_WHITE, "white" -> color_suffix = "white";
            case Constants.FOW_FILTER_PINK, "pink" -> color_suffix = "pink";
            case Constants.FOW_FILTER_PURPLE, "purple" -> color_suffix = "purple";
            case Constants.FOW_FILTER_FROG, "frog" -> color_suffix = "frog";
        }

        Player player = getPlayer();
        player.setFogFilter(color_suffix);
    }
}
