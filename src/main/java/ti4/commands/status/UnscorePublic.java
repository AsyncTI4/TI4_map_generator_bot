package ti4.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.*;
import ti4.message.MessageHelper;

public class UnscorePublic extends StatusSubcommandData {
    public UnscorePublic() {
        super(Constants.UNSCORE_OBJECTIVE, "Unscore Public Objective");
        addOptions(new OptionData(OptionType.INTEGER, Constants.PO_ID, "Public Objective ID that is between ()").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which to score Public Objective"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        OptionMapping option = event.getOption(Constants.PO_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Please select what Public Objective to score");
            return;
        }

        String userID = event.getUser().getId();
        OptionMapping playerOption = event.getOption(Constants.PLAYER);
        if (playerOption != null) {
            String playerID = playerOption.getAsUser().getId();
            if (activeMap.getPlayer(playerID) != null) {
                userID = activeMap.getPlayers().get(playerID).getUserID();
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Player:" + playerOption.getAsUser().getName() + " could not be found in map:" + activeMap.getName());
                return;
            }
        }
        
        boolean scored = activeMap.unscorePublicObjective(userID, option.getAsInt());
        if (!scored) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Public Objective ID found, please retry");
        }
    }
}
