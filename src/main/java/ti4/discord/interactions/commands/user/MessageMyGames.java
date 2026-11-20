package ti4.discord.interactions.commands.user;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.JdaService;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.SearchGameHelper;
import ti4.message.MentionGuard;
import ti4.message.MessageHelper;

class MessageMyGames extends Subcommand {

    public MessageMyGames() {
        super(Constants.MESSAGE_MY_GAMES, "Message all your active games");
        addOptions(new OptionData(OptionType.STRING, Constants.MESSAGE, "Message").setRequired(true));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.PING_GAME, "Ping the game role (default false)"));
        addOptions(
                new OptionData(OptionType.USER, Constants.PLAYER, "User who is sending the message (bothelpers only)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean pingGame = event.getOption(Constants.PING_GAME, Boolean.FALSE, OptionMapping::getAsBoolean);
        User user = event.getOption(Constants.PLAYER, event.getUser(), OptionMapping::getAsUser);
        if (!user.equals(event.getUser()) && !CommandHelper.hasRole(event, JdaService.bothelperRoles)) {
            MessageHelper.replyToMessage(event, "Only bothelpers can send messages on behalf of another user.");
            return;
        }
        String msg = MentionGuard.withoutRoleOrMassMentions(
                event.getOption(Constants.MESSAGE, "", OptionMapping::getAsString));
        SearchGameHelper.msgGames(user, false, false, true, true, pingGame, msg);
    }
}
