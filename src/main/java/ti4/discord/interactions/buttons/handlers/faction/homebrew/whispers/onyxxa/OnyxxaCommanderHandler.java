package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.onyxxa;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.RelicHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.UnlockLeaderService;

@UtilityClass
public class OnyxxaCommanderHandler {

    // Commander: Strategist Kreel — unlock condition:
    // "Resolve the primary ability of a strategy card in 1 of your neighbors' play areas."
    public static void offerCommanderUnlockButton(Player player) {
        Button button = Buttons.gray(
                player.factionButtonChecker() + "onyxxaCommanderUnlock",
                "Unlock Strategist Kreel",
                FactionEmojis.onyxxa);
        MessageHelper.sendMessageToChannelWithButton(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged()
                        + ", only click if you resolved the primary of a strategy card in a neighbor's play area.",
                button);
    }

    @ButtonHandler("onyxxaCommanderUnlock")
    public static void handleCommanderUnlock(ButtonInteractionEvent event, Game game, Player player) {
        UnlockLeaderService.unlockLeader("onyxxacommander", game, player);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    public static void onDrawRelic(Player player) {
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", gain 1 command token from _Strategist Kreel_.",
                ButtonHelper.getGainCCButtons(player));
    }

    public static void onGainFracturePlanet(
            GenericInteractionCreateEvent event, Player player, Game game, Player previousOwner) {
        if (previousOwner != null && "onyxxa".equals(previousOwner.getFaction())) return;
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation(false, false) + " gains 1 relic from _Strategist Kreel_.");
        RelicHelper.drawRelicAndNotify(player, event, game);
    }
}
