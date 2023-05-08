package ti4.commands.custom;

import java.util.List;
import java.util.Set;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.message.MessageHelper;

public class SCRemoveFromGame extends CustomSubcommandData {

    public SCRemoveFromGame() {
        super(Constants.REMOVE_SC_FROM_GAME, "Remove a Stategy Card # from the game");
        addOptions(new OptionData(OptionType.INTEGER, Constants.STRATEGY_CARD, "Strategy Card to remove").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
		Map activeMap = getActiveMap();
        
        Integer sc = event.getOption(Constants.STRATEGY_CARD, null, OptionMapping::getAsInt);
        if (sc == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "SC was null?");
            return;
        }

        if (activeMap.removeSC(sc)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Removed Strategy Card: " + sc);
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Strategy Card did not exist: " + sc);
        }
    }
    
}
