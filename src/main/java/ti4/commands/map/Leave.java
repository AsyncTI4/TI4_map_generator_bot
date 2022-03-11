package ti4.commands.map;

import net.dv8tion.jda.api.entities.User;
import ti4.helpers.Constants;
import ti4.map.Map;

public class Leave extends JoinLeave {

    @Override
    public String getActionID() {
        return Constants.LEAVE;
    }

    @Override
    protected String getActionDescription() {
        return "Leave map as player";
    }

    @Override
    protected String getResponseMessage(Map map) {
        return "Left map: " + map.getName() + " successful";
    }

    @Override
    protected void action(Map map, User user) {
        map.removePlayer(user.getId());
    }
}
