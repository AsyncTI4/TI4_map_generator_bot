package ti4.commands.game;

import net.dv8tion.jda.api.entities.User;
import ti4.helpers.Constants;
import ti4.map.Map;

public class Join extends JoinLeave {

    public Join() {
        super(Constants.JOIN, "Join map as player");
    }

    @Override
    protected String getResponseMessage(Map activeMap, User user) {
        return "Joined map: " + activeMap.getName() + " successful";
    }

    @Override
    protected void action(Map activeMap, User user) {
        activeMap.addPlayer(user.getId(), user.getName());
    }
}