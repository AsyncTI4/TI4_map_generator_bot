package ti4.service.fow;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CombatMessageHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.message.MessageHelper;

@UtilityClass
public class FOWCombatThreadMirroring {

  /*
    Checks that event occured in a valid thread and is a message from a player
    participating in the combat
  */
  public static void mirrorEvent(MessageReceivedEvent event) {
        if (!isFowCombatThread(event.getChannel()) || event.getAuthor().isBot()) {
            return;
        }

        String threadName = event.getChannel().getName();
        String gameName = threadName.substring(0, threadName.indexOf("-"));
        ManagedGame managedGame = GameManager.getManagedGame(gameName);
        if (managedGame == null) {
            return;
        }

        Game game = managedGame.getGame();
        Player player = getCommunityModePlayer(event.getMember(), game);
        Set<Player> combatParticipants = getCombatParticipants((ThreadChannel) event.getChannel(), game);
        if (player == null || !player.isRealPlayer() || !combatParticipants.contains(player)) {
            return;
        }

        String messageText = event.getMessage().getContentRaw();
        String newMessageText = player.getRepresentationNoPing() + " said: " + messageText;

        boolean messageMirrored = mirrorMessage((ThreadChannel) event.getChannel(), player, game, newMessageText);
        if (messageMirrored) {
            MessageHelper.sendMessageToChannel(event.getChannel(), player.getRepresentationNoPing() + "(You) said: " + messageText);
            event.getMessage().delete().queue();
        }
    }

    public static String parseCombatRollMessage(String messageText, Player player) {
        String combat = matchPattern(messageText, "rolls for\\s+([^>]+>)");
        String hits = matchPattern(messageText, "Total hits (\\d+)");

        return player.getRepresentationNoPing() + " rolled for " + combat + ": "
            + CombatMessageHelper.displayHitResults(Integer.valueOf(hits)).replace("\n", "");
    }

    private static boolean isFowCombatThread(Channel eventChannel) {
        String threadName = eventChannel.getName();
        return eventChannel instanceof ThreadChannel && threadName.contains("vs") && threadName.contains("private");
    }

    private static String matchPattern(String input, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static Player getCommunityModePlayer(Member member, Game game) {
        Player player = game.getPlayer(member.getUser().getId());
        if (game.isCommunityMode()) {
            List<Role> roles = member.getRoles();
            for (Player player2 : game.getPlayers().values()) {
                if (roles.contains(player2.getRoleForCommunity())) {
                    player = player2;
                }
            }
        }
        return player;
    }

    private static Set<Player> getCombatParticipants(ThreadChannel combatChannel, Game game) {
        String threadName = combatChannel.getName();
        String systemPos = threadName.split("-")[4];
        Tile tile = game.getTileByPosition(systemPos);

        Player p1 = game.getPlayerFromColorOrFaction(matchPattern(threadName, "(?<=-)([^-]+)(?=-vs-)"));
        Player p2 = game.getPlayerFromColorOrFaction(matchPattern(threadName, "(?<=-vs-)([^-]+)(?=-)"));
        List<Player> playersWithUnits = ButtonHelper.getPlayersWithUnitsInTheSystem(game, tile);
        Set<Player> combatParticipants = new HashSet<>(playersWithUnits);
        if (p1 != null) combatParticipants.add(p1);
        if (p2 != null) combatParticipants.add(p2);

        if (game.isAllianceMode()) {
            for (Player p : playersWithUnits) {
                if (!p.getAllianceMembers().isEmpty()) {
                    for (Player alliancePlayer : game.getRealPlayers()) {
                        if (p.getAllianceMembers().contains(alliancePlayer.getFaction())) {
                            combatParticipants.add(alliancePlayer);
                        }
                    }
                }
            }
        }
        return combatParticipants;
    }

    public static boolean mirrorCombatMessage(GenericInteractionCreateEvent event, Game game, String message) {
        if (!isFowCombatThread(event.getChannel())) return false;

        Player player = getCommunityModePlayer(event.getMember(), game);
        return mirrorMessage((ThreadChannel) event.getChannel(), player, game, parseCombatRollMessage(message, player));
    }

    public static boolean mirrorMessage(GenericInteractionCreateEvent event, Game game, String message) {
        if (!isFowCombatThread(event.getChannel())) return false;

        return mirrorMessage((ThreadChannel) event.getChannel(), getCommunityModePlayer(event.getMember(), game), game, message);
    }

    private static boolean mirrorMessage(ThreadChannel channel, Player player, Game game, String message) {
        boolean messageMirrored = false;
        String threadName = channel.getName();
        Set<Player> combatParticipants = getCombatParticipants(channel, game);

        for (Player playerOther : game.getRealPlayers()) {
            if (player != null && playerOther == player) {
                continue;
            }
            MessageChannel pChannel = playerOther.getPrivateChannel();
            TextChannel pChan = (TextChannel) pChannel;
            if (pChan != null) {
                boolean combatParticipant = combatParticipants.contains(playerOther);
                String newMessage = (combatParticipant ? playerOther.getRepresentation(true, combatParticipant) + " " : "")
                    + StringUtils.substringBefore(message, "\n");

                List<ThreadChannel> threadChannels = pChan.getThreadChannels();
                for (ThreadChannel threadChannel_ : threadChannels) {
                    if (threadChannel_.getName().equals(threadName) && threadChannel_ != channel) {
                        MessageHelper.sendMessageToChannel(threadChannel_, newMessage);
                        messageMirrored = true;
                        break;
                    }
                }
            }
        }
        return messageMirrored;
    }
}
