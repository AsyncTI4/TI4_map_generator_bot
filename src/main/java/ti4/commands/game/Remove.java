package ti4.commands.game;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.helpers.Constants;
import ti4.map.Map;

public class Remove extends AddRemovePlayer {

    private StringBuilder sb = new StringBuilder();
    public Remove() {
        super(Constants.REMOVE, "Remove player to game");
    }


    protected String getResponseMessage(Map map, User user) {
        return sb.toString();
    }

    @Override
    protected void action(SlashCommandInteractionEvent event, Map map, User user) {
        sb = new StringBuilder();
        removeUser(event, map, Constants.PLAYER1);
        removeUser(event, map, Constants.PLAYER2);
        removeUser(event, map, Constants.PLAYER3);
        removeUser(event, map, Constants.PLAYER4);
        removeUser(event, map, Constants.PLAYER5);
        removeUser(event, map, Constants.PLAYER6);
        removeUser(event, map, Constants.PLAYER7);
        removeUser(event, map, Constants.PLAYER8);
    }

    private void removeUser(SlashCommandInteractionEvent event, Map map, String playerID) {
        OptionMapping option;
        option = event.getOption(playerID);
        if (option != null){
            User extraUser = option.getAsUser();
            map.removePlayer(extraUser.getId());
            sb.append("Removed player: ").append(extraUser.getName()).append(" to game: ").append(map.getName()).append("\n");
        }
    }
}