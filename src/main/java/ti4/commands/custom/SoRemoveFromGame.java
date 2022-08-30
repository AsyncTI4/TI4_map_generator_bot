package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.message.MessageHelper;

public class SoRemoveFromGame extends CustomSubcommandData {
    public SoRemoveFromGame() {
        super(Constants.SO_REMOVE_FROM_GAME, "SO remove from game");
        addOptions(new OptionData(OptionType.STRING, Constants.SO_ID, "Secret ID").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();

        OptionMapping soOption = event.getOption(Constants.SO_ID);
        if (soOption == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify SO");
            return;
        }
        boolean removed = activeMap.removeSOFromGame(soOption.getAsString());
        if (removed) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "SO removed from game deck");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "SO not found in game deck");
        }
    }
}
