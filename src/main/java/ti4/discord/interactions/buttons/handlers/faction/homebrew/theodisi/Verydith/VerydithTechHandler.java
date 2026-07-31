package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Verydith;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperHeroes;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class VerydithTechHandler {
    private static final String USE_BN = "useBilateralNexus";

    public static void getBilateralNexusButton(GenericInteractionCreateEvent event, Player player, Game game) {
        if (player == null || game == null || !player.hasTechReady("thverydithg")) {
            return;
        }

        MessageHelper.sendMessageToChannelWithButton(
                event.getMessageChannel(),
                player.getRepresentation() + ", you may exhaust _Bilateral Nexus_.",
                Buttons.green(
                        player.factionButtonChecker() + USE_BN, "Exhaust Bilateral Nexus", FactionEmojis.verydith));
    }

    @ButtonHandler(USE_BN)
    public static void sendBilateralNexusStratButtons(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null || !player.hasTechReady("thverydithg")) {
            return;
        }

        player.exhaustTech("thverydithg");

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", please choose the secondary ability to resolve using _Bilateral Nexus_.\n-# You do not spend a command token when doing this. If one is spent, use `/player stats` to gain it back.",
                ButtonHelperHeroes.getSecondaryButtons(game));

        ButtonHelper.deleteMessage(event);
    }
}
