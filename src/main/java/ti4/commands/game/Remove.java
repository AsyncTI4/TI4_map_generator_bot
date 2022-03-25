package ti4.commands.game;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Map;

public class Remove extends AddRemovePlayer {

    public Remove() {
        super(Constants.REMOVE, "Remove player to game");
    }


    protected String getResponseMessage(Map map, User user) {
        return "Removed player: " +user.getName()+" to game: " + map.getName() + " successful";
    }

    @Override
    protected void action(SlashCommandInteractionEvent event, Map map, User user) {
        map.removePlayer(user.getId());
    }
}