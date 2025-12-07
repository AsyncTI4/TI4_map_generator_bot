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
import java.util.stream.Stream;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
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
    public static final Pattern THREAD_NAME_PATTERN =
            Pattern.compile("^(\\w+)\\s*(?:" + YES_CHAR + "|" + NO_CHAR + ")\\s*(\\w+)");

    private static Map<String, Boolean> validationRunning = new HashMap<>();

    public static boolean isActive(Game game) {
        return game.isFowMode() && game.getFowOption(FOWOption.MANAGED_COMMS);
    }

    public static void checkAllCommThreads(Game game) {
        checkNewCommPartners(game, null);
    }

    public static void checkNewCommPartners(Game game, Player player) {
        if (!isActive(game)) return;

        ThreadArchiveHelper.checkThreadLimitAndArchive(game.getGuild());
        Set<String> checkedPairs = new HashSet<>();
        getGameThreadChannels(game).thenAccept(threads -> {
            for (Player p : game.getRealPlayers()) {
                Set<Player> commPartners = getCommPartners(game, p);
                Map<ThreadChannel, Player> commThreadsWithPlayer = findPlayersCommThreads(game, threads, p);
                validateCommPartners(p, commPartners, commThreadsWithPlayer, checkedPairs, game);

                // If checking from a specific player perspective, check for new comm partners
                if (player != null && player == p) {
                    Set<Player> newCommPartners = findNewCommPartners(player, commPartners, commThreadsWithPlayer);
                    if (!newCommPartners.isEmpty()) {
                        MessageHelper.sendMessageToChannelWithButton(
                                player.getPrivateChannel(),
                                "New Comms Available",
                                Buttons.blue(
                                        "fowComms_"
                                                + newCommPartners.stream()
                                                        .map(Player::getColor)
                                                        .collect(Collectors.joining("-")),
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
        return game.getPhaseOfGame().startsWith("agenda") && game.isHiddenAgendaMode();
    }

    private static Set<Player> getCommPartners(Game game, Player player) {
        if (areAllowedToTalkInAgenda(game)) {
            Set<Player> allPlayers = new HashSet<>(game.getRealPlayers());
            allPlayers.remove(player);
            return allPlayers;
        }
        Set<Player> commPartners = new HashSet<>(player.getNeighbouringPlayers(true));
        if (player.hasSpaceStation()) {
            game.getRealPlayers().stream()
                    .filter(p -> p.hasSpaceStation() && !p.equals(player))
                    .forEach(commPartners::add);
        }
        return commPartners;
    }

    public static CompletableFuture<List<ThreadChannel>> getGameThreadChannels(Game game) {
        CompletableFuture<List<ThreadChannel>> future = new CompletableFuture<>();

        List<ThreadChannel> result = new ArrayList<>(game.getMainGameChannel().getThreadChannels());

        game.getMainGameChannel()
                .retrieveArchivedPrivateThreadChannels()
                .queue(
                        pagination -> {
                            result.addAll(pagination);
                            future.complete(result);
                        },
                        future::completeExceptionally);

        return future;
    }

    private static Map<ThreadChannel, Player> findPlayersCommThreads(
            Game game, List<ThreadChannel> threads, Player player) {
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

    private static void validateCommPartners(
            Player player,
            Set<Player> commPartners,
            Map<ThreadChannel, Player> commThreads,
            Set<String> checkedPairs,
            Game game) {
        if (validationRunning.getOrDefault(game.getName(), false)) {
            return;
        }
        validationRunning.put(game.getName(), true);
        try {
            boolean areAllowedToTalkInAgenda = areAllowedToTalkInAgenda(game);
            for (Entry<ThreadChannel, Player> thread : commThreads.entrySet()) {
                ThreadChannel threadChannel = thread.getKey();
                String threadName = thread.getKey().getName();
                Player otherPlayer = thread.getValue();

                String pairKey = Stream.of(player.getColor(), otherPlayer.getColor())
                        .sorted()
                        .collect(Collectors.joining("-"));
                if (checkedPairs.contains(pairKey)) {
                    continue; // Skip if we already checked this pair
                }
                checkedPairs.add(pairKey);

                boolean areAbleToCommunicate = commPartners.contains(otherPlayer);
                boolean threadLocked = threadName.contains(NO_CHAR);

                String notice = "Attention! " + player.getRepresentationNoPing() + " and "
                        + otherPlayer.getRepresentationNoPing();
                if (!threadLocked && isHiddenAgenda(game)) {
                    // Reminder of Hidden Agenda mode
                    threadChannel.getManager().setArchived(false).queue(success -> threadChannel
                            .sendMessage(
                                    "⚠️ Reminder that during Hidden Agenda __only__ the speaker is allowed to speak.")
                            .queue());
                } else if (areAbleToCommunicate && threadLocked) {
                    // Allow talking
                    threadChannel.getManager().setArchived(false).queue(success -> threadChannel
                            .getManager()
                            .setName(threadName.replace(NO_CHAR, YES_CHAR))
                            .queue(nameUpdated -> threadChannel
                                    .sendMessage(notice
                                            + (areAllowedToTalkInAgenda
                                                    ? " __may__ communicate in Agenda Phase."
                                                    : " have regained comms and __may__ communicate."))
                                    .queue()));

                } else if (!areAbleToCommunicate && !threadLocked) {
                    // Deny talking
                    threadChannel.getManager().setArchived(false).queue(success -> threadChannel
                            .getManager()
                            .setName(threadName.replace(YES_CHAR, NO_CHAR))
                            .queue(nameUpdated -> threadChannel
                                    .sendMessage(notice + " have lost comms and __may not__ communicate.")
                                    .queue()));
                }
            }
        } finally {
            validationRunning.remove(game.getName());
        }
    }

    private static Set<Player> findNewCommPartners(
            Player player, Set<Player> commPartners, Map<ThreadChannel, Player> commThreads) {
        Set<Player> newCommPartners = new HashSet<>();
        for (Player commPartner : commPartners) {
            boolean hasExistingThread = commThreads.values().stream().anyMatch(p -> p.equals(commPartner));
            if (!hasExistingThread) {
                newCommPartners.add(commPartner);
            }
        }

        return newCommPartners;
    }

    @ButtonHandler("fowComms_")
    public static void showComms(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        List<Button> buttons = new ArrayList<>();
        for (String color : buttonID.replace("fowComms_", "").split("-")) {
            buttons.add(Buttons.blue("fowCommsSuggest_" + color, StringUtils.capitalize(color)));
        }
        buttons.add(Buttons.DONE_DELETE_BUTTONS);
        String msg = "Suggest opening a communications thread to: ";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    @ButtonHandler("fowCommsAccept_")
    public static void acceptComms(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String color = buttonID.replace("fowCommsAccept_", "");
        Player inviteePlayer = game.getPlayerFromColorOrFaction(color);

        ThreadArchiveHelper.checkThreadLimitAndArchive(game.getGuild());
        String threadName = StringUtils.capitalize(inviteePlayer.getColor()) + " " + YES_CHAR + " "
                + StringUtils.capitalize(player.getColor());
        game.getMainGameChannel()
                .createThreadChannel(threadName, true)
                .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_WEEK)
                .queue(t -> MessageHelper.sendMessageToChannel(
                        t,
                        "## Communications opened\n"
                                + "Players: " + inviteePlayer.getRepresentation(true, true, false, true)
                                + " " + player.getRepresentation(true, true, false, true) + "\n"
                                + "GM ping: " + GMService.gmPing(game)));

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + "(You) accepted communications invitation from "
                        + inviteePlayer.getRepresentationNoPing());
        event.getMessage().delete().queue();
    }

    @ButtonHandler("fowCommsSuggest_")
    public static void suggestComms(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String color = buttonID.replace("fowCommsSuggest_", "");
        Player targetPlayer = game.getPlayerFromColorOrFaction(color);
        if (targetPlayer != null) {
            String msg = targetPlayer.getRepresentationUnfogged() + " " + player.getRepresentationNoPing()
                    + " wishes to open communications thread with you.";
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("fowCommsAccept_" + player.getColor(), "Accept"));
            buttons.add(Buttons.DONE_DELETE_BUTTONS);
            MessageHelper.sendMessageToChannelWithButtons(targetPlayer.getCorrectChannel(), msg, buttons);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + "(You) sent an invitation to open communications with "
                            + targetPlayer.getRepresentationNoPing());
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player '" + color + "' was not found.");
        }
    }
}
