// File: BotRuntimeStatsListener.java
// What: Listener that increments bot runtime request statistics on each Discord interaction.
// How: Extends ListenerAdapter and handles GenericInteractionCreateEvent to call
// BotRuntimeStats.incrementRequestCount().

package ti4.discord.interactions.listeners;

import javax.annotation.Nonnull;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.service.statistics.SREStats;
import ti4.spring.service.deploy.ActiveLeaseService;

class BotRuntimeStatsListener extends ListenerAdapter {

    @Override
    public void onGenericInteractionCreate(@Nonnull GenericInteractionCreateEvent event) {
        if (!ActiveLeaseService.shouldHandleCurrentProcessInteraction()) {
            return;
        }
        // Count any interaction request (slash, button, modal, selection, etc.)
        SREStats.incrementRequestCount();
    }
}
