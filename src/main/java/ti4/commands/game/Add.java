package ti4.commands.game;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.helpers.Constants;
import ti4.map.Game;

public class Add extends AddRemovePlayer {

    public Add() {
        super(Constants.ADD, "Add player to game");
    }


    protected String getResponseMessage(Game activeGame, User user) {
        return user.getName() + " added players to game: " + activeGame.getName() + " - successful";
    }

    @Override
    protected void action(SlashCommandInteractionEvent event, Game activeGame, User user) {
        addExtraUser(event, activeGame, Constants.PLAYER1);
        addExtraUser(event, activeGame, Constants.PLAYER2);
        addExtraUser(event, activeGame, Constants.PLAYER3);
        addExtraUser(event, activeGame, Constants.PLAYER4);
        addExtraUser(event, activeGame, Constants.PLAYER5);
        addExtraUser(event, activeGame, Constants.PLAYER6);
        addExtraUser(event, activeGame, Constants.PLAYER7);
        addExtraUser(event, activeGame, Constants.PLAYER8);
    }

    private void addExtraUser(SlashCommandInteractionEvent event, Game activeGame, String playerID) {
        OptionMapping option;
        option = event.getOption(playerID);
        if (option != null){
            User extraUser = option.getAsUser();
            activeGame.addPlayer(extraUser.getId(), extraUser.getName());
        }
    }
}