package ti4.service.game;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.message.MessageHelper;

@UtilityClass
public class MegagameUndoService {

    public static final String MEGAGAME_TAG = "megagame";
    private static final String GM_ROLE_NAME = "pbd100three GM";
    private static final List<String> UNDO_ROLE_NAMES = List.of(GM_ROLE_NAME, "bothelper");

    public static boolean mayUndo(Game game, Member member) {
        if (!isMegagame(game)) {
            return true;
        }
        return member != null
                && member.getRoles().stream()
                        .anyMatch(role -> UNDO_ROLE_NAMES.stream().anyMatch(role.getName()::equalsIgnoreCase));
    }

    public static void pingGmsForUndo(GenericInteractionCreateEvent event, Game game) {
        String requester = event.getMember() == null
                ? event.getUser().getName()
                : event.getMember().getEffectiveName();
        MessageHelper.sendMessageToEventChannel(
                event, undoRequestMessage(event.getGuild(), requester, game.getLatestCommand()));
    }

    static String undoRequestMessage(Guild guild, String requester, String latestCommand) {
        return gmMention(guild) + " Undo has been disabled please wait for a GM to handle your request.\n"
                + "-# Requested by " + requester + ". Latest action: " + latestCommand;
    }

    private static String gmMention(Guild guild) {
        if (guild == null) {
            return "@" + GM_ROLE_NAME;
        }
        return guild.getRolesByName(GM_ROLE_NAME, true).stream()
                .findFirst()
                .map(Role::getAsMention)
                .orElse("@" + GM_ROLE_NAME);
    }

    private static boolean isMegagame(Game game) {
        return game.getTags().stream().anyMatch(MEGAGAME_TAG::equalsIgnoreCase);
    }
}
