package ti4.discord.interactions.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class PurgePublic extends GameStateSubcommand {

    PurgePublic() {
        super(Constants.PURGE_OBJECTIVE, "Purge Public Objective", true, false);
        addOptions(new OptionData(OptionType.INTEGER, Constants.PO_ID, "Public Objective ID that is between ()")
                .setRequired(true)
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        OptionMapping option = event.getOption(Constants.PO_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Please choose which public objective to purge.");
            return;
        }

        boolean purged = game.purgePublicObjective(option.getAsInt());
        if (!purged) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Public Objective ID found, please retry.");
            return;
        }
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                "Public Objective purged: " + option.getAsInt() + ". Players do not lose points from this purge.");
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
