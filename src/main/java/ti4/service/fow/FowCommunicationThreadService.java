package ti4.service.fow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.apache.commons.lang3.StringUtils;
import ti4.buttons.Buttons;
import ti4.helpers.ThreadArchiveHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.option.FOWOptionService.FOWOption;

public class FowCommunicationThreadService {

    private static final String YES_CHAR = "↔";
    private static final String NO_CHAR = "X";
    private static final Pattern THREAD_NAME_PATTERN = Pattern.compile("^(\\w+)\\s*(?:"+YES_CHAR+"|"+NO_CHAR+")\\s*(\\w+)");

    public static boolean isActive(Game game) {
        return game.isFowMode() && game.getFowOption(FOWOption.MANAGED_COMMS);
    }

    public static void checkAllCommThreads(Game game) {
        checkNewNeighbors(game, null);
    }

    public static void checkNewNeighbors(Game game, Player player) {
        if (!isActive(game)) return;

        Set<Set<Player>> checkedPairs = new HashSet<>();
        getGameThreadChannels(game).thenAccept(threads -> {
            for (Player p : game.getRealPlayers()) {
                Set<Player> neighbors = getNeighbors(game, p);
                Map<ThreadChannel, Player> commThreadsWithPlayer = findPlayersCommThreads(game, threads, p);
                validateNeighbors(p, neighbors, commThreadsWithPlayer, checkedPairs, game);

                //If checking from a specific player perspective, check for new neighbors
                if (player != null && player == p) {
                    Set<Player> newNeighbors = checkNewNeighbors(player, neighbors, commThreadsWithPlayer);
                    if (!newNeighbors.isEmpty()) {
                        MessageHelper.sendMessageToChannelWithButton(player.getPrivateChannel(), "New neighbors found", 
                            Buttons.blue("fowComms_" 
                                + newNeighbors.stream().map(Player::getColor).collect(Collectors.joining("-")), 
                                "Open Comms"));
                    }
                }
            }
        });
    }

    private static boolean areAllowedToTalkInAgenda(Game game) {
        return game.getPhaseOfGame().startsWith("agenda") 
            && game.getFowOption(FOWOption.ALLOW_AGENDA_COMMS)
            && !game.isHiddenAgendaMode();
    }

    private static boolean isHiddenAgenda(Game game) {
        return game.getPhaseOfGame().startsWith("agenda") 
            && game.isHiddenAgendaMode();
    }

    private static Set<Player> getNeighbors(Game game, Player player) {
        if (areAllowedToTalkInAgenda(game)) {
            Set<Player> allPlayers = new HashSet<>(game.getRealPlayers());
            allPlayers.remove(player);
            return allPlayers;
        }
        return player.getNeighbouringPlayers(true);
    }

    public static CompletableFuture<List<ThreadChannel>> getGameThreadChannels(Game game) {
        CompletableFuture<List<ThreadChannel>> future = new CompletableFuture<>();

        List<ThreadChannel> result = new ArrayList<>(game.getMainGameChannel().getThreadChannels());

        game.getMainGameChannel()
            .retrieveArchivedPrivateThreadChannels()
            .queue(pagination -> {
                pagination.forEach(result::add);
                future.complete(result);
            }, future::completeExceptionally);

        return future;
    }

    private static Map<ThreadChannel, Player> findPlayersCommThreads(Game game, List<ThreadChannel> threads, Player player) {
        Map<ThreadChannel, Player> threadMap = new HashMap<>();
        for (ThreadChannel thread : threads) {
            Matcher matcher = THREAD_NAME_PATTERN.matcher(thread.getName());
            if (matcher.find()) {
                Player p1 = game.getPlayerFromColorOrFaction(matcher.group(1));
                Player p2 = game.getPlayerFromColorOrFaction(matcher.group(2));
                if (p1 != null && p2 != null && (player.equals(p1) || player.equals(p2))) {
                    threadMap.put(thread, player.equals(p1) ? p2 : p1);
                }
            }
        }
        return threadMap;
    }

    private static void validateNeighbors(Player player, Set<Player> neighbors, Map<ThreadChannel, Player> commThreads, Set<Set<Player>> checkedPairs, Game game) {
        boolean areAllowedToTalkInAgenda = areAllowedToTalkInAgenda(game);
        ThreadArchiveHelper.checkThreadLimitAndArchive(game.getGuild());
        for (Entry<ThreadChannel, Player> thread : commThreads.entrySet()) {
            ThreadChannel threadChannel = thread.getKey();
            String threadName = thread.getKey().getName();
            Player otherPlayer = thread.getValue();

            Set<Player> playerPair = Set.of(player, otherPlayer);
            if (checkedPairs.contains(playerPair)) {
                continue; // Skip if we already checked this pair
            }
            checkedPairs.add(playerPair);

            boolean areNeighbors = neighbors.contains(otherPlayer);
            boolean threadLocked = threadName.contains(NO_CHAR);

            String notice = "Attention! " + player.getRepresentationNoPing() + " and " + otherPlayer.getRepresentationNoPing();
            if (!threadLocked && isHiddenAgenda(game)) {
                //Reminder of Hidden Agenda mode
                threadChannel.getManager().setArchived(false).queue(success -> 
                    threadChannel.sendMessage("⚠️ Reminder that during Hidden Agenda **only** speaker is allowed to speak.").queue());
            } else if (areNeighbors && threadLocked) {
                //Allow talking
                threadChannel.getManager().setArchived(false).queue(success -> threadChannel.getManager().setName(threadName.replace(NO_CHAR, YES_CHAR))
                    .queue(nameUpdated -> threadChannel.sendMessage(notice + (areAllowedToTalkInAgenda
                        ? " **may** communicate in Agenda phase."
                        : " are neighbors again and **may** communicate.")).queue()));

            } else if (!areNeighbors && !threadLocked) {
                //Deny talking
                threadChannel.getManager().setArchived(false).queue(success -> threadChannel.getManager().setName(threadName.replace(YES_CHAR, NO_CHAR))
                    .queue(nameUpdated -> threadChannel.sendMessage(notice + " are no longer neighbors and should **not** communicate.").queue()));
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
    
    @ButtonHandler("fowComms_")
    public static void showComms(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        List<Button> buttons = new ArrayList<>();
        for (String color : buttonID.replace("fowComms_", "").split("-")) {
            buttons.add(Buttons.blue("fowCommsSuggest_" + color, StringUtils.capitalize(color)));
        }
        buttons.add(Buttons.DONE_DELETE_BUTTONS);
        String msg = "Suggest opening a private communications thread to: ";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    @ButtonHandler("fowCommsAccept_")
    public static void acceptComms(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String color = buttonID.replace("fowCommsAccept_", "");
        Player inviteePlayer = game.getPlayerFromColorOrFaction(color);

        ThreadArchiveHelper.checkThreadLimitAndArchive(game.getGuild());
        String threadName = StringUtils.capitalize(inviteePlayer.getColor()) + " " + YES_CHAR + " " + StringUtils.capitalize(player.getColor());
        game.getMainGameChannel().createThreadChannel(threadName, true)
            .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_WEEK)
            .queue(t -> MessageHelper.sendMessageToChannel(t, "## Private communications thread opened\n"
                + "Players: " + inviteePlayer.getRepresentation(true, true, false, true)
                + " " + player.getRepresentation(true, true, false, true) + "\n"
                + "GM ping: " + GMService.gmPing(game)));

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentationNoPing() 
                + "(You) accepted private communications invitation from " + inviteePlayer.getRepresentationNoPing());
        event.getMessage().delete().queue();
    }

    @ButtonHandler("fowCommsSuggest_")
    public static void suggestComms(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String color = buttonID.replace("fowCommsSuggest_", "");
        Player targetPlayer = game.getPlayerFromColorOrFaction(color);
        if (targetPlayer != null) {
            String msg = targetPlayer.getRepresentationUnfogged() + " " + player.getRepresentationNoPing() + " wishes to open private communications thread with you."; 
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
}
