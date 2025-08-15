package ti4.commands.user;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;

class SetFowFilter extends GameStateSubcommand {

    public SetFowFilter() {
        super(Constants.SET_FOG_FILTER, "Set the color of the fow tiles for your view of the map.", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.FOG_FILTER, "Color of the filter")
                .setAutoComplete(true)
                .setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping fogColorMapping = event.getOption(Constants.FOG_FILTER);
        if (fogColorMapping == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify color");
            return;
        }

        String colorSuffix = null;
        switch (fogColorMapping.getAsString()) {
            case Constants.FOW_FILTER_DARK_GREY, "default", "darkgrey", "grey", "gray" -> colorSuffix = "default";
            case Constants.FOW_FILTER_SEPIA, "sepia" -> colorSuffix = "sepia";
            case Constants.FOW_FILTER_WHITE, "white" -> colorSuffix = "white";
            case Constants.FOW_FILTER_PINK, "pink" -> colorSuffix = "pink";
            case Constants.FOW_FILTER_PURPLE, "purple" -> colorSuffix = "purple";
            case Constants.FOW_FILTER_FROG, "frog" -> colorSuffix = "frog";
        }

        Player player = getPlayer();
        player.setFogFilter(colorSuffix);
    }
}
