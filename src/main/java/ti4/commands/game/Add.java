package ti4.commands.game;

import net.dv8tion.jda.api.entities.User;
import ti4.helpers.Constants;
import ti4.map.Map;

public class Add extends AddRemovePlayer {

    public Add() {
        super(Constants.ADD, "Add player to game");
    }


    protected String getResponseMessage(Map map, User user) {
        return "Added player: " +user.getName()+" to game: " + map.getName() + " successful";
    }

    @Override
    protected void action(Map map, User user) {
        map.addPlayer(user.getId(), user.getName());
    }
}