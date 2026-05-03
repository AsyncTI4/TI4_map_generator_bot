package ti4.discord.interactions.listeners;

import javax.annotation.Nonnull;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.contest.replay.service.CombatReplayHouseService;
import ti4.contest.replay.service.CombatReplayLeaderboardService;
import ti4.discord.JdaService;
import ti4.executors.ExecutorServiceManager;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;
import ti4.spring.service.deploy.ActiveLeaseService;

class LazaxMinigameReactionListener extends ListenerAdapter {

    private static final String SUBSCRIBE_EMOJI = "🟢";
    private static final String UNSUBSCRIBE_EMOJI = "🔴";

    @Override
    public void onMessageReactionAdd(@Nonnull MessageReactionAddEvent event) {
        if (!ActiveLeaseService.shouldHandleCurrentProcessInteraction()) return;
        if (!validateEvent(event.getGuild().getId())) return;
        if (event.getUser() == null || event.getUser().isBot()) return;

        ExecutorServiceManager.runAsync("LazaxMinigameReactionListener task", () -> handleReactionAdd(event));
    }

    private static boolean validateEvent(String guildId) {
        if (!JdaService.isReadyToReceiveCommands()) {
            return false;
        }
        return JdaService.isValidGuild(guildId);
    }

    private void handleReactionAdd(MessageReactionAddEvent event) {
        try {
            event.retrieveMessage()
                    .queue(message -> handleLazaxMinigameReaction(event, message), BotLogger::catchRestError);
        } catch (Exception e) {
            BotLogger.error("Error in `LazaxMinigameReactionListener.handleReactionAdd`", e);
        }
    }

    private void handleLazaxMinigameReaction(MessageReactionAddEvent event, Message message) {
        if (!message.getAuthor().isBot()) return;
        applyHouseAssignmentIfPredictionReaction(event, message);
        applyRoleUpdateIfSubscriptionPrompt(event, message);
    }

    private void applyHouseAssignmentIfPredictionReaction(MessageReactionAddEvent event, Message message) {
        SpringContext.getBean(CombatReplayHouseService.class).assignHouseForPredictionReaction(event, message);
    }

    private void applyRoleUpdateIfSubscriptionPrompt(MessageReactionAddEvent event, Message message) {
        if (!message.getContentRaw().contains(CombatReplayLeaderboardService.LAZAX_MINIGAME_SUBSCRIPTION_MARKER))
            return;

        String emoji = event.getEmoji().getName();
        if (!SUBSCRIBE_EMOJI.equals(emoji) && !UNSUBSCRIBE_EMOJI.equals(emoji)) return;

        Role role =
                event.getGuild().getRolesByName(CombatReplayLeaderboardService.LAZAX_MINIGAME_ROLE_NAME, true).stream()
                        .findFirst()
                        .orElse(null);
        if (role == null) {
            BotLogger.warning("Lazax Minigame role not found in guild: "
                    + event.getGuild().getId());
            return;
        }

        Member member = event.getMember();
        if (member == null) {
            BotLogger.warning("Could not resolve member for Lazax Minigame reaction: " + event.getUserId());
            return;
        }

        if (SUBSCRIBE_EMOJI.equals(emoji)) {
            event.getGuild().addRoleToMember(member, role).queue(null, BotLogger::catchRestError);
            return;
        }
        event.getGuild().removeRoleFromMember(member, role).queue(null, BotLogger::catchRestError);
    }
}
