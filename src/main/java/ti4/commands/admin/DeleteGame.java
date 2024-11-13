package ti4.commands.admin;

import java.util.List;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.game.GameEnd;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

public class DeleteGame extends AdminSubcommandData {
    DeleteGame() {
        super(Constants.DELETE_GAME, "Delete a game.");
        addOption(OptionType.STRING, Constants.GAME_NAME, "Game to delete", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String mapName = event.getOption(Constants.GAME_NAME, OptionMapping::getAsString);
        if (mapName == null) return;

        Game gameToDelete = GameManager.getGame(mapName);
        if (gameToDelete == null) {
            MessageHelper.replyToMessage(event, "Map: " + mapName + " was not found.");
            return;
        }

        Member member = event.getMember();
        boolean isAdmin = false;
        if (member != null) {
            List<Role> roles = member.getRoles();
            for (Role role : AsyncTI4DiscordBot.adminRoles) {
                if (roles.contains(role)) {
                    isAdmin = true;
                    break;
                }
            }
        }

        if (!isAdmin) {
            MessageHelper.replyToMessage(event, "Map: " + mapName + " can be deleted by admin.");
            return;
        }

        if (GameSaveLoadManager.deleteGame(mapName)) {
            GameEnd.secondHalfOfGameEnd(event, gameToDelete, false, true, false);
            GameManager.deleteGame(mapName);
            MessageHelper.replyToMessage(event, "Map: " + mapName + " deleted.");
        } else {
            MessageHelper.replyToMessage(event, "Map could not be deleted");
        }
    }
}
