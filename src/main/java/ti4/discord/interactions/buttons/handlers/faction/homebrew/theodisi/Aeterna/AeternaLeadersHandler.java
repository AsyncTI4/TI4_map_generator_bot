package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Aeterna;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.UnlockLeaderService;

@UtilityClass
public class AeternaLeadersHandler {
    private static final String UNLOCK = "unlockAeternaCommander";
    
    public static Button offerAeternaCommanderUnlockButton(Player player) {
        if (player == null || !player.hasLeader("aeternacommander") || player.hasLeaderUnlocked("aeternacommander")) {
            return null;
        }

        return Buttons.green(player.factionButtonChecker() + UNLOCK, "Unlock Vorun Kael", FactionEmojis.aeterna);
    }

    @ButtonHandler(UNLOCK)
    public static void unlockAeternaCommander(ButtonInteractionEvent event, Player player, Game game) {
        if (event == null || player == null || game == null || !player.hasLeader("aeternacommander") || player.hasLeaderUnlocked("aeternacommander")) {
            return;
        }

        UnlockLeaderService.unlockLeader("aeternacommander", game, player);
        ButtonHelper.deleteMessage(event);
    }
}
