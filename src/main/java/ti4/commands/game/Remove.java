package ti4.commands.game;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.helpers.Constants;
import ti4.map.Map;

public class Remove extends AddRemovePlayer {

    private StringBuilder sb = new StringBuilder();
    public Remove() {
        super(Constants.REMOVE, "Remove player from game");
    }


    protected String getResponseMessage(Map activeMap, User user) {
        return sb.toString();
    }

    @Override
    protected void action(SlashCommandInteractionEvent event, Map activeMap, User user) {
        sb = new StringBuilder();
        removeUser(event, activeMap, Constants.PLAYER1);
        removeUser(event, activeMap, Constants.PLAYER2);
        removeUser(event, activeMap, Constants.PLAYER3);
        removeUser(event, activeMap, Constants.PLAYER4);
        removeUser(event, activeMap, Constants.PLAYER5);
        removeUser(event, activeMap, Constants.PLAYER6);
        removeUser(event, activeMap, Constants.PLAYER7);
        removeUser(event, activeMap, Constants.PLAYER8);
    }

    private void removeUser(SlashCommandInteractionEvent event, Map activeMap, String playerID) {
        OptionMapping option;
        option = event.getOption(playerID);
        if (option != null){
            User extraUser = option.getAsUser();
            activeMap.removePlayer(extraUser.getId());
            sb.append("Removed player: ").append(extraUser.getName()).append(" from game: ").append(activeMap.getName()).append("\n");
        }
    }
}