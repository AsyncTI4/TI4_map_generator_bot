package ti4.commands.uncategorized;

import java.util.List;
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
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
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
        if (!GameManager.getInstance().getGameNameToGame().containsKey(mapName)) {
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
        Game gameToDelete = GameManager.getInstance().getGame(mapName);
        if (gameToDelete == null) {
            MessageHelper.replyToMessage(event, "Map: " + mapName + " was not found.");
            return;
        }
        Member member_ = event.getMember();
        boolean isAdmin = false;
        if (member_ != null) {
            List<Role> roles = member_.getRoles();
            for (Role role : MapGenerator.adminRoles) {
                if (roles.contains(role)) {
                    isAdmin = true;
                    break;
                }
            }
        }
        if (!gameToDelete.getOwnerID().equals(member.getId()) && !isAdmin){
            MessageHelper.replyToMessage(event, "Map: " + mapName + " can be deleted by it's creator or admin.");
            return;
        }

        if (GameSaveLoadManager.deleteGame(mapName)) {
            GameManager.getInstance().deleteGame(mapName);
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
