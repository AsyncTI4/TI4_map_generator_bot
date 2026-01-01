package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

class UnrevealSpecificObjective extends GameStateSubcommand {

    UnrevealSpecificObjective() {
        super(Constants.UNREVEAL_SPECIFIC_OBJECTIVE, "Unreveal a specific public objective", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.PO_ID, "Public objective ID")
                .setRequired(true)
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String objectiveId = event.getOption(Constants.PO_ID).getAsString();
        boolean unrevealed = game.unrevealSpecificObjective(objectiveId);
        if (!unrevealed) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Public objective not found among revealed objectives.");
            return;
        }
        MessageHelper.sendMessageToChannel(
                event.getChannel(), "Public objective moved back to unrevealed objectives.");
    }
}
