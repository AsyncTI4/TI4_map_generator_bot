package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Vanguard;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.transaction.SendPromissoryService;

@UtilityClass
public class VanguardPromissoryHandler {
    private static final String GUILD_CALL = "thpnvanguard";

    public static void returnGuildCallAtOwnerTurnStart(GenericInteractionCreateEvent event, Game game, Player player) {
        if (game.getPNOwner(GUILD_CALL) != player) {
            return;
        }
        for (Player holder : game.getPlayers().values()) {
            if (holder != player && holder.getPromissoryNotesInPlayArea().contains(GUILD_CALL)) {
                SendPromissoryService.returnPromissoryFromPlayAreaToOwner(event, game, holder, player, GUILD_CALL);
            }
        }
    }
}
