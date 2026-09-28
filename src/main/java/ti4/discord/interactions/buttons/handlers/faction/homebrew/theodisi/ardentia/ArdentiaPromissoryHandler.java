package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Ardentia;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class ArdentiaPromissoryHandler {
    private static final String USURPERS_LEASE = "thpnardentia";
    private static final String USE_USURPERS_LEASE = "useUsurpersLease";

    public static Button getUsurpersLeaseButton(Player player) {
        return Buttons.green(
                player.factionButtonChecker() + USE_USURPERS_LEASE, "Use The Usurper's Lease", FactionEmojis.ardentia);
    }

    @ButtonHandler(USE_USURPERS_LEASE)
    public static void useUsurpersLease(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null
                || player == null
                || game.getStoredValue("ledSpend" + player.getFaction()).isEmpty()
                || !player.hasPlayablePromissoryInHand(USURPERS_LEASE)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Player owner = game.getPNOwner(USURPERS_LEASE);
        if (owner == null || owner == player) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        int additionalInfluence = player.getNeighbourCount();
        player.addSpentThing("usurpersLease_" + additionalInfluence);
        player.removePromissoryNote(USURPERS_LEASE);
        owner.setPromissoryNote(USURPERS_LEASE);
        event.getMessage()
                .editMessage(Helper.buildSpentThingsMessage(player, game, "inf"))
                .queue();
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }
}
