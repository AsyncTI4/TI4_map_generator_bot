package ti4.service.tournament.panel;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.discord.interactions.routing.HandlerGuard;
import ti4.game.Game;
import ti4.message.componentsV2.ComponentPanel;
import ti4.service.tournament.TournamentAccess;

final class TournamentPanelGuard implements HandlerGuard {

    @Override
    public boolean allows(GenericInteractionCreateEvent event, Game game) {
        String refusal = TournamentAccess.refusal(event.getMember());
        if (refusal == null) return true;
        ComponentPanel.tell(event, refusal);
        return false;
    }
}
