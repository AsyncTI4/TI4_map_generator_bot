package ti4.commands.map;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.MapGenerator;
import ti4.commands.Command;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.message.MessageHelper;

public class DeleteGame implements Command {


    @Override
    public String getActionID() {
        return Constants.DELETE_GAME;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        if (!event.getName().equals(getActionID())) {
            return false;
        }
        String mapName = event.getOptions().get(0).getAsString();
        if (!MapManager.getInstance().getMapList().containsKey(mapName)) {
            MessageHelper.replyToMessage(event, "Map with such name not found");
            return false;
        }
        String confirm = event.getOptions().get(1).getAsString();
        if (!"YES".equals(confirm)){
            MessageHelper.replyToMessage(event, "Need to confirm map deletion");
        }
        return true;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {

        Member member = event.getInteraction().getMember();
        if (member == null) {
            MessageHelper.replyToMessage(event, "Caller ID not found");
            return;
        }
        String mapName = event.getOptions().get(0).getAsString().toLowerCase();
        Map map = MapManager.getInstance().getMap(mapName);
        if (map == null) {
            MessageHelper.replyToMessage(event, "Map: " + mapName + " was not found.");
            return;
        }
        Member member_ = event.getMember();
        boolean isAdmin = false;
        if (member_ != null) {
            java.util.List<Role> roles = member_.getRoles();
            for (Role role : MapGenerator.adminRoles) {
                if (roles.contains(role)) {
                    isAdmin = true;
                }
            }
        }
        if (!map.getOwnerID().equals(member.getId()) && !isAdmin){
            MessageHelper.replyToMessage(event, "Map: " + mapName + " can be deleted by it's creator or admin.");
            return;
        }

        if (MapSaveLoadManager.deleteMap(mapName)) {
            MapManager.getInstance().deleteMap(mapName);
            MessageHelper.replyToMessage(event, "Map: " + mapName + " deleted.");
        } else {
            MessageHelper.replyToMessage(event, "Map could not be deleted");
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), "Delete selected map")
                        .addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Map name")
                                .setRequired(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Type in YES")
                                .setRequired(true))
        );
    }
}
