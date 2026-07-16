package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.lunarium;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;

@UtilityClass
public class LunariumBreakthroughHandler {

    public static void offerDarkSideExploitationButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + "lunariumDarkSideAC",
                "Draw 1 Action Card",
                CardEmojis.getACEmoji(game)));
        buttons.add(Buttons.blue(player.factionButtonChecker() + "lunariumDarkSideTG", "Gain 1 Trade Good"));
        String msg = player.getRepresentation() + ", use buttons to choose a reward from _Dark Side Exploitation_.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    @ButtonHandler("lunariumDarkSideAC")
    public static void resolveDarkSideAC(Player player, ButtonInteractionEvent event) {
        ActionCardHelper.drawActionCards(player, 1);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " drew 1 action card using _Dark Side Exploitation_.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("lunariumDarkSideTG")
    public static void resolveDarkSideTG(Player player, ButtonInteractionEvent event) {
        player.gainTG(1, true);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " gained 1 trade good using _Dark Side Exploitation_.");
        ButtonHelper.deleteMessage(event);
    }

    public static void offerReadyPlanetButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        String factionCheckerPrefix = player.factionButtonChecker();
        for (String planet : player.getExhaustedPlanets()) {
            buttons.add(Buttons.gray(
                    factionCheckerPrefix + "lunariumReadyPlanet_" + planet,
                    Helper.getPlanetRepresentation(planet, game)));
        }
        if (buttons.isEmpty()) return;
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        String msg = player.getRepresentation()
                + ", if you discarded that secret objective to use _Dark Side Exploitation_, you may ready 1 planet you control.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    @ButtonHandler("lunariumReadyPlanet_")
    public static void resolveReadyPlanet(Player player, String buttonID, Game game, ButtonInteractionEvent event) {
        String planet = buttonID.replace("lunariumReadyPlanet_", "");
        player.refreshPlanet(planet);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " readied " + Helper.getPlanetRepresentation(planet, game)
                        + " using _Dark Side Exploitation_.");
        ButtonHelper.deleteMessage(event);
    }
}
