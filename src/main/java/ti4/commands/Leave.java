package ti4.commands;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.message.MessageHelper;

public class Leave extends JoinLeave {

    @Override
    public String getActionID() {
        return Constants.LEAVE;
    }

    @Override
    protected String getActionDescription() {
        return "Leave map as player";
    }

    @Override
    protected void action(Map map, User user) {
        map.removePlayer(user.getId());
    }
}
