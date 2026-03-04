package ti4.commands.bothelper;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.settings.users.UserSettingsManager;

class SetGameLimit extends Subcommand {

    SetGameLimit() {
        super(Constants.SET_GAME_LIMIT, "Set a game limit for a user");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player @playername").setRequired(true));
        addOptions(new OptionData(OptionType.INTEGER, "count", "Count (0 = no limit)").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {

        String userId = event.getOption(Constants.PLAYER).getAsUser().getId();
        var userSettings = UserSettingsManager.get(userId);
        int count = Math.max(event.getOption("count").getAsInt(), 0);
        userSettings.setGameLimit(count);
        UserSettingsManager.save(userSettings);

        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                "Set game limit to " + count + " for "
                        + event.getOption(Constants.PLAYER).getAsUser().getName() + ".");
    }
}
