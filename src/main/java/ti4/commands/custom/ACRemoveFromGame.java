package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.message.MessageHelper;

public class ACRemoveFromGame extends CustomSubcommandData {
    public ACRemoveFromGame() {
        super(Constants.REMOVE_AC_FROM_GAME, "AC remove from game");
        addOptions(new OptionData(OptionType.STRING, Constants.AC_ID, "AC ID").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();

        OptionMapping soOption = event.getOption(Constants.AC_ID);
        if (soOption == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify AC");
            return;
        }
        boolean removed = activeMap.removeACFromGame(soOption.getAsString());
        if (removed) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "AC removed from game deck");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "AC not found in game deck");
        }
    }
}
