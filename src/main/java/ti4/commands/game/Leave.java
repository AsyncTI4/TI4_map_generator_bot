package ti4.commands.game;

import net.dv8tion.jda.api.entities.User;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.message.MessageHelper;

public class Leave extends JoinLeave {

    public Leave() {
        super(Constants.LEAVE, "Leave map as player");
    }

    @Override
    protected String getResponseMessage(Map activeMap, User user) {
         if(activeMap.getPlayer(user.getId()) != null && activeMap.getPlayer(user.getId()).isRealPlayer()){
            return "Did not leave game: " + activeMap.getName() + ". Try a different method or set status to dummy. ";
        }
        return "Left map: " + activeMap.getName() + " successful";
    }

    @Override
    protected void action(Map activeMap, User user) {
        if(activeMap.getPlayer(user.getId()).isRealPlayer()){
            MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), "You are a real player, and thus should not do /game leave. You should do /game eliminate, or /game replace, depending on what you are looking for.");
            return;
        }
        activeMap.removePlayer(user.getId());
    }
}
