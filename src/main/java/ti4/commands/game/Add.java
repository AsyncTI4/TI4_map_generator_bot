package ti4.commands.game;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
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
    protected void action(SlashCommandInteractionEvent event, Map map, User user) {
        map.addPlayer(user.getId(), user.getName());
        addExtraUser(event, map, Constants.PLAYER2);
        addExtraUser(event, map, Constants.PLAYER3);
        addExtraUser(event, map, Constants.PLAYER4);
        addExtraUser(event, map, Constants.PLAYER5);
        addExtraUser(event, map, Constants.PLAYER6);
        addExtraUser(event, map, Constants.PLAYER7);
        addExtraUser(event, map, Constants.PLAYER8);
    }

    private void addExtraUser(SlashCommandInteractionEvent event, Map map, String playerID) {
        OptionMapping option;
        option = event.getOption(playerID);
        if (option != null){
            User extraUser = option.getAsUser();
            map.addPlayer(extraUser.getId(), extraUser.getName());
        }
    }
}