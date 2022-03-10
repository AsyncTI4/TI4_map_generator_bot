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
import ti4.map.MapStatus;
import ti4.message.MessageHelper;

public class Join  extends JoinLeave {

    @Override
    public String getActionID() {
        return Constants.JOIN;
    }

    @Override
    protected String getActionDescription() {
        return "Join map as player";
    }

    @Override
    protected void action(Map map, User user) {
        map.addPlayer(user.getId(), user.getName());
    }
}