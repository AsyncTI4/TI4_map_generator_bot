package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Vanguard;

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
public class VanguardLeadersHandler {
    private static final String UNLOCK = "unlockVanguardCommander";

    public static Button getCommanderUnlockButton(Player player) {
        if (player == null || !player.hasLeader("vanguardcommander") || player.hasLeaderUnlocked("vanguardcommander")) {
            return null;
        }
        return Buttons.green(
                player.factionButtonChecker() + UNLOCK, "Unlock Vanguard Commander", FactionEmojis.vanguard);
    }

    @ButtonHandler(UNLOCK)
    public static void unlockCommander(ButtonInteractionEvent event, Game game, Player player) {
        if (player == null
                || game == null
                || !player.hasLeader("vanguardcommander")
                || player.hasLeaderUnlocked("vanguardcommander")) {
            return;
        }
        UnlockLeaderService.unlockLeader("vanguardcommander", game, player);
        ButtonHelper.deleteMessage(event);
    }
}
