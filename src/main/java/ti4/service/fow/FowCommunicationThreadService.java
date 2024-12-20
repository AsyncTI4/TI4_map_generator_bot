package ti4.service.fow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class FowCommunicationThreadService {

    private static final String VS_CHAR = "↔";
    private static final String NO_CHAR = "⛔";
    private static final Pattern THREAD_NAME_PATTERN = Pattern.compile("^(\\w+)\\s*" + VS_CHAR + "\\s*(\\w+)");

    public static boolean isActive(Game game) {
        return game.isFowMode() && Boolean.parseBoolean(game.getFowOption(FowConstants.MANAGED_COMMS));
    }

    @ButtonHandler("fowComms_")
    public static void showComms(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        List<Button> buttons = new ArrayList<>();
        for (String color : buttonID.replace("fowComms_", "").split("-")) {
            buttons.add(Buttons.blue("fowCommsSuggest_" + color, StringUtils.capitalize(color)));
        }
        buttons.add(Buttons.DONE_DELETE_BUTTONS);
        String msg = "Press a button to suggest opening a private communications thread to: ";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    @ButtonHandler("fowCommsAccept_")
    public static void acceptComms(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String color = buttonID.replace("fowCommsAccept_", "");
        Player inviteePlayer = game.getPlayerFromColorOrFaction(color);

        String threadName = StringUtils.capitalize(inviteePlayer.getColor()) + " " + VS_CHAR + " " + StringUtils.capitalize(player.getColor());
        game.getMainGameChannel().createThreadChannel(threadName, true).queue(t -> {
            MessageHelper.sendMessageToChannel(t, "## Private communications thread opened\n"
                + "Players: " + inviteePlayer.getRepresentationUnfogged() + " and " + player.getRepresentationUnfogged() + "\n"
                + "GM ping: " + game.getPlayersWithGMRole().stream().map(gm -> gm.getPing()).collect(Collectors.joining(" ")));
        });

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentationNoPing() 
                + "(You) accepted private communications invitation from " + inviteePlayer.getRepresentationNoPing());
        event.getMessage().delete().queue();
    }

    @ButtonHandler("fowCommsSuggest_")
    public static void suggestComms(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String color = buttonID.replace("fowCommsSuggest_", "");
        Player targetPlayer = game.getPlayerFromColorOrFaction(color);
        if (targetPlayer != null) {
            String msg = targetPlayer.getRepresentationUnfogged() + " " + player.getRepresentationNoPing() + " wants to open private communications thread with you."; 
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("fowCommsAccept_" + player.getColor(), "Accept"));
            buttons.add(Buttons.DONE_DELETE_BUTTONS);
            MessageHelper.sendMessageToChannelWithButtons(targetPlayer.getCorrectChannel(), msg, buttons);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentationNoPing() 
                + "(You) sent an invitation to open communications with " + targetPlayer.getRepresentationNoPing());
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player '" + color + "' was not found.");
        }
    }

    public static Set<Player> checkCommThreadsAndNewNeighbors(Game game, Player player) {
        Set<Player> neighbors = player.getNeighbouringPlayers();
        Map<ThreadChannel, Player> commThreads = findCommThreads(game, player);
        
        //Check existing threads
        validateNeighborshipStatus(player, neighbors, commThreads);

        //Check if can find neighbors without a comm thread
        Set<Player> newNeighbors = new HashSet<>();
        if (neighbors.size() > commThreads.size()) {
            newNeighbors = checkNewNeighbors(player, neighbors, commThreads);
        }
        return newNeighbors;
    }

    private static Map<ThreadChannel, Player> findCommThreads(Game game, Player player) {
        Map<ThreadChannel, Player> threads = new HashMap<>();
        for (ThreadChannel threadChannel : game.getMainGameChannel().getThreadChannels()) {
            Matcher matcher = THREAD_NAME_PATTERN.matcher(threadChannel.getName());
            if (matcher.find()) {
                Player p1 = game.getPlayerFromColorOrFaction(matcher.group(1));
                Player p2 = game.getPlayerFromColorOrFaction(matcher.group(2));
                if (player.equals(p1) || player.equals(p2)) {
                    threads.put(threadChannel, player.equals(p1) ? p2 : p1);
                }
            }
        }
        return threads;
    }

    private static void validateNeighborshipStatus(Player player, Set<Player> neighbors, Map<ThreadChannel, Player> commThreads) {
        for (Entry<ThreadChannel, Player> thread : commThreads.entrySet()) {
            ThreadChannel threadChannel = thread.getKey();
            String threadName = thread.getKey().getName();
            Player otherPlayer = thread.getValue();

            boolean areNeighbors = neighbors.contains(otherPlayer);
            boolean threadLocked = threadName.endsWith(NO_CHAR);

            String notice = "### " + player.getRepresentationNoPing() + " and " + otherPlayer.getRepresentationNoPing();
            if (areNeighbors && threadLocked) {
                //Allow talking
                threadChannel.getManager().setName(threadName.replace(NO_CHAR, "").trim()).queue();
                MessageHelper.sendMessageToChannel(threadChannel,  notice + " are neighbors. Talking allowed.");

            } else if (!areNeighbors && !threadLocked) {
                //Deny talking
                threadChannel.getManager().setName(threadName + " " + NO_CHAR).queue();
                MessageHelper.sendMessageToChannel(threadChannel, notice + " are not neighbors. Talking denied.");
            }
        }
    }

    private static Set<Player> checkNewNeighbors(Player player, Set<Player> neighbors, Map<ThreadChannel, Player> commThreads) {
        Set<Player> newNeighbors = new HashSet<>();
        for (Player neighbor : neighbors) {
            boolean hasExistingThread = commThreads.values().stream().anyMatch(p -> p.equals(neighbor));
            if (!hasExistingThread) {
                newNeighbors.add(neighbor);
            }
        }

        return newNeighbors;
    }

}
