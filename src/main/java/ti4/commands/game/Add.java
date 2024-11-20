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

    protected String getResponseMessage(Game game, User user) {
        return user.getName() + " added players to game: " + game.getName() + " - successful";
    }

    @Override
    protected void action(SlashCommandInteractionEvent event, Game game) {
        addExtraUser(event, game, Constants.PLAYER1);
        addExtraUser(event, game, Constants.PLAYER2);
        addExtraUser(event, game, Constants.PLAYER3);
        addExtraUser(event, game, Constants.PLAYER4);
        addExtraUser(event, game, Constants.PLAYER5);
        addExtraUser(event, game, Constants.PLAYER6);
        addExtraUser(event, game, Constants.PLAYER7);
        addExtraUser(event, game, Constants.PLAYER8);
    }

    private void addExtraUser(SlashCommandInteractionEvent event, Game game, String playerID) {
        OptionMapping option;
        option = event.getOption(playerID);
        if (option != null) {
            User extraUser = option.getAsUser();
            game.addPlayer(extraUser.getId(), extraUser.getName());
        }
    }
}
