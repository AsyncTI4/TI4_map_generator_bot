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


    protected String getResponseMessage(Map activeMap, User user) {
        return user.getName() + " added players to game: " + activeMap.getName() + " - successful";
    }

    @Override
    protected void action(SlashCommandInteractionEvent event, Map activeMap, User user) {
        addExtraUser(event, activeMap, Constants.PLAYER1);
        addExtraUser(event, activeMap, Constants.PLAYER2);
        addExtraUser(event, activeMap, Constants.PLAYER3);
        addExtraUser(event, activeMap, Constants.PLAYER4);
        addExtraUser(event, activeMap, Constants.PLAYER5);
        addExtraUser(event, activeMap, Constants.PLAYER6);
        addExtraUser(event, activeMap, Constants.PLAYER7);
        addExtraUser(event, activeMap, Constants.PLAYER8);
    }

    private void addExtraUser(SlashCommandInteractionEvent event, Map activeMap, String playerID) {
        OptionMapping option;
        option = event.getOption(playerID);
        if (option != null){
            User extraUser = option.getAsUser();
            activeMap.addPlayer(extraUser.getId(), extraUser.getName());
        }
    }
}