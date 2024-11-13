package ti4.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class RemoveCustomPO extends StatusSubcommandData {
    public RemoveCustomPO() {
        super(Constants.REMOVE_CUSTOM, "Remove custom Public Objective");
        addOptions(new OptionData(OptionType.INTEGER, Constants.PO_ID, "Public Objective ID that is between ()").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();

        OptionMapping option = event.getOption(Constants.PO_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Please select what Public Objective to shuffle back in");
            return;
        }
        boolean removedCustomPO = game.removeCustomPO(option.getAsInt());
        if (!removedCustomPO) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Public Objective ID found, please retry");
        }
    }
}
