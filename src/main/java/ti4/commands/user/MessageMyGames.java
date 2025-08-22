package ti4.commands.user;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.SearchGameHelper;

class MessageMyGames extends GameStateSubcommand {

    public MessageMyGames() {
        super(Constants.MESSAGE_MY_GAMES, "Take the 5 question survey about preferences", false, false);
        addOptions(new OptionData(OptionType.STRING, Constants.MESSAGE, "Message").setRequired(true));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.PING_GAME, "Ping the game role (default false)"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "User who is sending the message"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean pingGame = event.getOption(Constants.PING_GAME, false, OptionMapping::getAsBoolean);
        User user = event.getOption(Constants.PLAYER, event.getUser(), OptionMapping::getAsUser);
        String msg = event.getOption(Constants.MESSAGE, "", OptionMapping::getAsString);
        SearchGameHelper.msgGames(user, event, false, false, false, true, false, true, true, pingGame, msg);
    }
}
