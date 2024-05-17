package ti4.commands.game;

import net.dv8tion.jda.api.entities.User;
import ti4.helpers.Constants;
import ti4.map.Game;

public class Join extends JoinLeave {

    public Join() {
        super(Constants.JOIN, "Join map as player");
    }

    @Override
    protected String getResponseMessage(Game game, User user) {
        return "Joined map: " + game.getName() + " successful";
    }

    @Override
    protected void action(Game game, User user) {
        game.addPlayer(user.getId(), user.getName());
    }
}