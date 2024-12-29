package ti4.commands2.game;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class Leave extends GameStateSubcommand {

    public Leave() {
        super(Constants.LEAVE, "Leave map as player", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Game name").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        User user = event.getUser();
        if (game.getPlayer(user.getId()).isRealPlayer()) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "You are a real player, and thus should not do `/game leave`."
                + " You should do `/game eliminate`, or `/game replace`, depending on what you are looking for.");
            return;
        }
        game.removePlayer(user.getId());

        MessageHelper.replyToMessage(event, getResponseMessage(game, user));
    }

    private String getResponseMessage(Game game, User user) {
        if (game.getPlayer(user.getId()) != null && game.getPlayer(user.getId()).isRealPlayer()) {
            return "Did not leave game: " + game.getName() + ". Try a different method or set status to dummy. ";
        }
        return "Left map: " + game.getName() + " successful";
    }
}
