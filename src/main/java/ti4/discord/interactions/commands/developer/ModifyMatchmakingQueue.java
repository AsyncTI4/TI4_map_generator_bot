package ti4.discord.interactions.commands.developer;

import java.util.List;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.spring.service.statistics.matchmaking.queue.GroupVerificationResult;
import ti4.spring.service.statistics.matchmaking.queue.MatchmakerService;

class ModifyMatchmakingQueue extends Subcommand {

    private static final String OPTION_ACTION = "action";

    private static final String ACTION_REMOVE_PLAYER = "remove_player";
    private static final String ACTION_CLEAR = "clear";
    private static final String ACTION_VERIFY = "verify";

    ModifyMatchmakingQueue() {
        super("modify_matchmaking_queue", "Remove a player, clear, or verify the matchmaking queue.");
        addOptions(
                new OptionData(OptionType.STRING, OPTION_ACTION, "What to do to the queue.")
                        .setRequired(true)
                        .addChoice("Remove a player (and their group)", ACTION_REMOVE_PLAYER)
                        .addChoice("Clear the whole queue", ACTION_CLEAR)
                        .addChoice("Verify every group is valid", ACTION_VERIFY),
                new OptionData(OptionType.USER, Constants.USER, "Player to remove (required for remove_player)."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (MatchmakerService.isQueueingDisabled()) {
            MessageHelper.sendMessageToEventChannel(event, "Queueing is currently disabled.");
            return;
        }

        String action = event.getOption(OPTION_ACTION).getAsString();
        switch (action) {
            case ACTION_REMOVE_PLAYER -> removePlayer(event);
            case ACTION_CLEAR -> clearQueue(event);
            case ACTION_VERIFY -> verifyGroups(event);
            default -> MessageHelper.sendMessageToEventChannel(event, "Unknown action: `" + action + "`");
        }
    }

    private static void removePlayer(SlashCommandInteractionEvent event) {
        OptionMapping userOption = event.getOption(Constants.USER);
        if (userOption == null) {
            MessageHelper.sendMessageToEventChannel(event, "Choose a `user` to remove from the queue.");
            return;
        }

        User user = userOption.getAsUser();
        List<String> removed = MatchmakerService.get().removePlayer(user.getId());
        if (removed.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, user.getAsMention() + " is not in the matchmaking queue.");
            return;
        }

        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                "Removed " + user.getAsMention() + " and their group (" + removed.size() + " player"
                        + (removed.size() == 1 ? "" : "s") + "): " + mentionList(removed));
    }

    private static void clearQueue(SlashCommandInteractionEvent event) {
        long cleared = MatchmakerService.get().clearQueue();
        MessageHelper.sendMessageToChannel(
                event.getChannel(), "Cleared the matchmaking queue (" + cleared + " parties removed).");
    }

    private static void verifyGroups(SlashCommandInteractionEvent event) {
        List<GroupVerificationResult> results = MatchmakerService.get().verifyGroups();
        if (results.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "There are no groups in the queue to verify.");
            return;
        }

        List<GroupVerificationResult> invalid =
                results.stream().filter(result -> !result.valid()).toList();
        if (invalid.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "All " + results.size() + " queued group(s) are valid.");
            return;
        }

        StringBuilder message =
                new StringBuilder("Found " + invalid.size() + " invalid group(s) out of " + results.size() + ":\n");
        for (GroupVerificationResult result : invalid) {
            message.append("- Party ")
                    .append(result.partyId())
                    .append(" (")
                    .append(mentionList(result.memberIds()))
                    .append(") can never match with restriction(s): ")
                    .append(String.join(", ", result.invalidRestrictions()))
                    .append("\n");
        }
        MessageHelper.sendMessageToEventChannel(event, message.toString());
    }

    private static String mentionList(List<String> userIds) {
        return userIds.stream().map(id -> "<@" + id + ">").collect(Collectors.joining(", "));
    }
}
