package ti4.service.fow.setup;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.discord.interactions.routing.HandlerGuard;
import ti4.game.Game;
import ti4.helpers.FoWHelper;
import ti4.message.componentsV2.ComponentPanel;

final class FowSetupPanelGuard implements HandlerGuard {

    static final String NOT_A_GAME = "This wizard only works in its game's channels.";
    static final String NOT_A_GM = "Only a GM can use this wizard.";

    @Override
    public boolean allows(GenericInteractionCreateEvent event, Game game) {
        if (game == null) {
            ComponentPanel.tell(event, NOT_A_GAME);
            return false;
        }
        if (!FoWHelper.isGameMaster(event.getUser().getId(), game)) {
            ComponentPanel.tell(event, NOT_A_GM);
            return false;
        }
        return true;
    }
}
