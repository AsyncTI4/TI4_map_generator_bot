package ti4.service.fow;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.helpers.ButtonHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.manage.GameManager;
import ti4.map.manage.ManagedGame;
import ti4.message.MessageHelper;

@UtilityClass
public class FOWCombatThreadMirroring {

  /*
    Checks that event occured in a valid thread and is either a message from a player
    participating in the combat or an allowed bot message and relays the message
    to other mirrored combat threads.
  */  
  public static void mirrorEvent(MessageReceivedEvent event) {
        String threadName = event.getChannel().getName();
        boolean isFowCombatThread = event.getChannel() instanceof ThreadChannel
            && threadName.contains("vs")
            && threadName.contains("private");
        if (!isFowCombatThread) {
            return;
        }

        String gameName = threadName.substring(0, threadName.indexOf("-"));
        ManagedGame managedGame = GameManager.getManagedGame(gameName);
        if (!managedGame.isFowMode()) {
            return;
        }

        if (StringUtils.countMatches(threadName, "-") <= 4) {
            return;
        }

        Game game = managedGame.getGame();
        Player player = game.getPlayer(event.getAuthor().getId());
        if (game.isCommunityMode()) {
            List<Role> roles = event.getMember().getRoles();
            for (Player player2 : game.getPlayers().values()) {
                if (roles.contains(player2.getRoleForCommunity())) {
                    player = player2;
                }
            }
        }

        String systemPos = threadName.split("-")[4];
        Tile tile = game.getTileByPosition(systemPos);

        //Players to send real combat messages and accept messages from
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

        String messageText = event.getMessage().getContentRaw();
        boolean isPlayerInvalid = player == null || !player.isRealPlayer() || !combatParticipants.contains(player);
        boolean isBotMessage = event.getAuthor().isBot();
        if ((isPlayerInvalid || isBotMessage) && (!isBotMessage || !isAllowedBotMsg(messageText))) {
            return;
        }
    
        boolean messageMirrored = false;
        for (Player playerOther : game.getRealPlayers()) {
            if (player != null && playerOther == player) {
                continue;
            }
            MessageChannel pChannel = playerOther.getPrivateChannel();
            TextChannel pChan = (TextChannel) pChannel;
            if (pChan != null) {
                boolean combatParticipant = combatParticipants.contains(playerOther);
                String newMessage = combatParticipant ? playerOther.getRepresentation(true, combatParticipant) + " " : "";
                
                //Combat roll
                if (isBotMessage && isCombatRoll(messageText)) {
                    newMessage += parseCombatRollMessage(messageText);
                }

                //Retreat
                else if (isBotMessage && isRetreat(messageText)) {
                    newMessage += "Someone is preparing to retreat";
                }

                //Assign hit
                else if (isBotMessage && isAssignHit(messageText)) {
                    String assignedHits = matchPattern(messageText, "(?i)(?:removed|sustained)\\s+(.+?)(?:\\s+from|$)");
                    newMessage += "Someone assigned hits to " + assignedHits;
                }
                
                //Normal message
                else if (!isBotMessage && player != null) {
                    newMessage += player.getRepresentationNoPing() + " said: " + messageText;  
                }

                List<ThreadChannel> threadChannels = pChan.getThreadChannels();
                for (ThreadChannel threadChannel_ : threadChannels) {
                    if (threadChannel_.getName().contains(threadName) && threadChannel_ != event.getChannel()) {
                        MessageHelper.sendMessageToChannel(threadChannel_, newMessage);
                        messageMirrored = true;
                        break;
                    }
                }
            }
        }

        if (!isBotMessage && player != null && messageMirrored) {
            MessageHelper.sendMessageToChannel(event.getChannel(), player.getRepresentationNoPing() + "(You) said: " + messageText);
            event.getMessage().delete().queue();
        }
    }

    public static String parseCombatRollMessage(String messageText) {
        String combat = matchPattern(messageText, "rolls for\\s+([^>]+>)");
        String hits = matchPattern(messageText, "Total hits (\\d+)");

        return "Someone rolled dice for " + combat
            + " and got a total of **" + hits + " hit" + (hits.equals("1") ? "** " : "s** ")
            + ":boom:".repeat(Math.max(0, Integer.parseInt(hits)));
    }

    private static boolean isAllowedBotMsg(String messageText) {
        return !messageText.contains("said:") &&
            (isCombatRoll(messageText)
            || isRetreat(messageText) 
            || isAssignHit(messageText));
    }

    private static boolean isCombatRoll(String messageText) {
        return messageText.toLowerCase().contains("rolls for") && messageText.toLowerCase().contains("total hits");
    }

    private static boolean isRetreat(String messageText) {
        return messageText.toLowerCase().contains("has announced a retreat");
    }

    private static boolean isAssignHit(String messageText) {
        return messageText.toLowerCase().contains("removed") || messageText.toLowerCase().contains("sustained");
    }

    private static String matchPattern(String input, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);
        return matcher.find() ? matcher.group(1).trim() : null;
    }
}
