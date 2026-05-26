package ti4.discord.interactions.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class UnrevealPublic extends GameStateSubcommand {

    UnrevealPublic() {
        super(Constants.UNREVEAL_OBJECTIVE, "Unreveal a Public Objective", true, false);
        addOptions(new OptionData(OptionType.INTEGER, Constants.PO_ID, "Public Objective ID that is between ()")
                .setRequired(true)
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        OptionMapping option = event.getOption(Constants.PO_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Please choose what public objective to unreveal");
            return;
        }

        boolean unrevealed = game.unrevealPublicObjective(option.getAsInt());
        if (!unrevealed) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Public Objective ID found, please retry");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Public Objective unrevealed: " + option.getAsInt());
        }
    }
}
