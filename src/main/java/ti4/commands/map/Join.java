package ti4.commands.map;

import net.dv8tion.jda.api.entities.User;
import ti4.helpers.Constants;
import ti4.map.Map;

public class Join  extends JoinLeave {

    @Override
    public String getActionID() {
        return Constants.JOIN;
    }

    @Override
    protected String getActionDescription() {
        return "Join map as player";
    }

    @Override
    protected void action(Map map, User user) {
        map.addPlayer(user.getId(), user.getName());
    }
}