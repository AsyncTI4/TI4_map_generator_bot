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
    protected String getResponseMessage(Game activeGame, User user) {
         if(activeGame.getPlayer(user.getId()) != null && activeGame.getPlayer(user.getId()).isRealPlayer()){
            return "Did not leave game: " + activeGame.getName() + ". Try a different method or set status to dummy. ";
        }
        return "Left map: " + activeGame.getName() + " successful";
    }

    @Override
    protected void action(Game activeGame, User user) {
        if(activeGame.getPlayer(user.getId()).isRealPlayer()){
            MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), "You are a real player, and thus should not do /game leave. You should do /game eliminate, or /game replace, depending on what you are looking for.");
            return;
        }
        activeGame.removePlayer(user.getId());
    }
}
