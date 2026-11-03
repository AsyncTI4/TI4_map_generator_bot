package ti4.service.game;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.routing.ComponentIdEnvelope;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.service.strategycard.PlayStrategyCardService;
import ti4.service.testbed.TestBedService;

@UtilityClass
public class MegagameUndoService {

    public static final String MEGAGAME_TAG = "megagame";
    public static final String STRICT_UNDO_TAG = "megagame-strict-undo";
    private static final String GM_ROLE_NAME = "pbd100three GM";
    private static final List<String> UNDO_ROLE_NAMES = List.of(GM_ROLE_NAME, "bothelper");
    private static final String BUTTON_MARKER = " pressed button: ";
    private static final String SLASH_MARKER = " used: ";
    private static final List<String> TURN_ENDING_BUTTONS = List.of("turnEnd", "endTurn", "passForRound");
    private static final List<String> FOLLOW_BUTTONS = List.of("sc_follow", "sc_no_follow", "sc_trade_follow");
    private static final List<String> TURN_ENDING_SLASH_COMMANDS = List.of("/player turn_end", "/player pass");

    public static boolean mayUndo(Game game, Member member) {
        return !hasTag(game, MEGAGAME_TAG) || hasUndoRole(member);
    }

    public static boolean mayUndoLatestStep(Game game, Player player, Member member) {
        if (!hasTag(game, STRICT_UNDO_TAG) || !isProtectedStep(game)) {
            return true;
        }
        boolean testBedSeat = player != null && TestBedService.isVirtualSeatInTestBed(game, player);
        return !testBedSeat && hasUndoRole(member);
    }

    private static boolean hasUndoRole(Member member) {
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

    static boolean isProtectedStep(Game game) {
        return StringUtils.startsWithIgnoreCase(game.getPhaseOfGame(), "agenda")
                || isProtectedCommand(game, game.getLatestCommand());
    }

    private static boolean isProtectedCommand(Game game, String latestCommand) {
        if (StringUtils.contains(latestCommand, SLASH_MARKER)) {
            return isTurnEndingSlashCommand(StringUtils.substringAfter(latestCommand, SLASH_MARKER));
        }
        if (!StringUtils.contains(latestCommand, BUTTON_MARKER)) {
            return false;
        }
        String handlerId = ComponentIdEnvelope.decode(buttonId(latestCommand)).handlerId();
        return StringUtils.startsWithAny(handlerId, TURN_ENDING_BUTTONS.toArray(String[]::new))
                || StringUtils.startsWithAny(handlerId, FOLLOW_BUTTONS.toArray(String[]::new))
                || strategyCardSecondaryIds(game).contains(handlerId);
    }

    private static String buttonId(String latestCommand) {
        return StringUtils.removeEnd(StringUtils.substringAfterLast(latestCommand, "("), ")");
    }

    private static boolean isTurnEndingSlashCommand(String commandString) {
        return TURN_ENDING_SLASH_COMMANDS.stream()
                .anyMatch(command -> commandString.equals(command) || commandString.startsWith(command + " "));
    }

    private static Set<String> strategyCardSecondaryIds(Game game) {
        Player anyPlayer = game.getRealPlayers().stream().findFirst().orElse(null);
        if (anyPlayer == null) {
            return Set.of();
        }
        return game.getSCList().stream()
                .flatMap(sc -> PlayStrategyCardService.getStrategyCardButtons(sc, game, anyPlayer).stream())
                .map(Button::getCustomId)
                .map(id -> ComponentIdEnvelope.decode(id).handlerId())
                .filter(id -> !StringUtils.containsIgnoreCase(id, "primary"))
                .collect(Collectors.toSet());
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

    private static boolean hasTag(Game game, String tag) {
        return game.getTags().stream().anyMatch(tag::equalsIgnoreCase);
    }
}
