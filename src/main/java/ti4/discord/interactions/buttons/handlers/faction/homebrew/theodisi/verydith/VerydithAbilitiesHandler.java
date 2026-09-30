package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Verydith;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class VerydithAbilitiesHandler {
    public static final String USE_MANDATE = "useMandatePresence";
    private static final String READY_MANDATE_PLANETS = "readyMandatePlanets";

    public static void getMandateButtons(GenericInteractionCreateEvent event, Player player, Game game) {
        if (player == null
                || !player.hasAbility("mandate_of_presence")
                || !player.isPassed()
                || player.getStrategicCC() < 1) {
            return;
        }

        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + USE_MANDATE, "Use Mandate of Presence", FactionEmojis.verydith),
                Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", you may spend 1 command token from your strategy pool to perform the primary effect of the **Diplomacy** strategy card.",
                buttons);
    }

    @ButtonHandler(USE_MANDATE)
    public static void useMandateOfPresence(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null
                || player == null
                || !player.hasAbility("mandate_of_presence")
                || !player.isPassed()
                || player.getStrategicCC() < 1) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Mandate of Presence is no longer available.");
            return;
        }

        player.setStrategicCC(player.getStrategicCC() - 1);
        ButtonHelper.deleteMessage(event);

        List<Button> buttons = List.of(
                Buttons.blue(player.factionButtonChecker() + "diploSystem", "Diplo A System"),
                Buttons.green(player.factionButtonChecker() + READY_MANDATE_PLANETS, "Ready 2 Planets"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", spent 1 command token from their strategy pool to resolve **Mandate of Presence** and perform the primary ability of the **Diplomacy** strategy card.",
                buttons);
    }

    @ButtonHandler(READY_MANDATE_PLANETS)
    public static void readyMandatePlanets(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null || !player.hasAbility("mandate_of_presence")) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>(Helper.getPlanetRefreshButtons(player, game));
        buttons.add(Buttons.red("deleteButtons", "Done Readying Planets"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", please choose up to 2 planets to ready.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }
}
