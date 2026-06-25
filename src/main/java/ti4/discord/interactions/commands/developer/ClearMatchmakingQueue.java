package ti4.discord.interactions.commands.developer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.message.MessageHelper;
import ti4.spring.service.statistics.matchmaking.queue.MatchmakerService;

class ClearMatchmakingQueue extends Subcommand {

    ClearMatchmakingQueue() {
        super("clear_matchmaking_queue", "Remove all parties and players from the matchmaking queue.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (MatchmakerService.isQueueingDisabled()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Queueing is currently disabled; nothing to clear.");
            return;
        }

        long cleared = MatchmakerService.get().clearQueue();
        MessageHelper.sendMessageToChannel(
                event.getChannel(), "Cleared the matchmaking queue (" + cleared + " parties removed).");
    }
}
