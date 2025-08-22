// File: BotRuntimeStatsListener.java
// What: Listener that increments bot runtime request statistics on each Discord interaction.
// How: Extends ListenerAdapter and handles GenericInteractionCreateEvent to call
// BotRuntimeStats.incrementRequestCount().

package ti4.listeners;

import javax.annotation.Nonnull;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.service.statistics.SREStats;

public class BotRuntimeStatsListener extends ListenerAdapter {

    @Override
    public void onGenericInteractionCreate(@Nonnull GenericInteractionCreateEvent event) {
        // Count any interaction request (slash, button, modal, selection, etc.)
        SREStats.incrementRequestCount();
    }
}
