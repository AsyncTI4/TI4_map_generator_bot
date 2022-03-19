package ti4.commands.game;

import net.dv8tion.jda.api.entities.User;
import ti4.helpers.Constants;
import ti4.map.Map;

public class Leave extends JoinLeave {

    public Leave() {
        super(Constants.LEAVE, "Leave map as player");
    }

    @Override
    protected String getResponseMessage(Map map, User user) {
        return "Left map: " + map.getName() + " successful";
    }

    @Override
    protected void action(Map map, User user) {
        map.removePlayer(user.getId());
    }
}
