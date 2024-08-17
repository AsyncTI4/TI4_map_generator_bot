package ti4.commands.game;

import net.dv8tion.jda.api.entities.User;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class Leave extends JoinLeave {

    public Leave() {
        super(Constants.LEAVE, "Leave map as player");
    }

    @Override
    protected String getResponseMessage(Game game, User user) {
        if (game.getPlayer(user.getId()) != null && game.getPlayer(user.getId()).isRealPlayer()) {
            return "Did not leave game: " + game.getName() + ". Try a different method or set status to dummy. ";
        }
        return "Left map: " + game.getName() + " successful.";
    }

    @Override
    protected void action(Game game, User user) {
        if (game.getPlayer(user.getId()).isRealPlayer()) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "You are a real player, and thus should not do `/game leave`."
                + " You should do `/game eliminate`, or `/game replace`, depending on what you are looking for.");
            return;
        }
        game.removePlayer(user.getId());
    }
}
