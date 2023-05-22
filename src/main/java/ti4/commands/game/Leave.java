package ti4.commands.game;

import net.dv8tion.jda.api.entities.User;
import ti4.helpers.Constants;
import ti4.map.Map;

public class Leave extends JoinLeave {

    public Leave() {
        super(Constants.LEAVE, "Leave map as player");
    }

    @Override
    protected String getResponseMessage(Map activeMap, User user) {
        return "Left map: " + activeMap.getName() + " successful";
    }

    @Override
    protected void action(Map activeMap, User user) {
        activeMap.removePlayer(user.getId());
    }
}
