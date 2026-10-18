package ti4.discord.interactions.buttons.handlers.planet;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.MiscEmojis;
import ti4.service.turn.EndTurnService;

@UtilityClass
public class VanaheimLegendaryButtonHandler {

    private static final String USE_END_TURN = "vanaheimUseEndTurn";
    private static final String DECLINE_END_TURN = "vanaheimDeclineEndTurn";

    public static boolean offerEndTurnRepair(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasPlanet("vanaheim")
                || player.getExhaustedPlanetsAbilities().contains("vanaheim")) return false;
        List<Button> buttons = List.of(
                Buttons.gray(
                        player.factionButtonChecker() + USE_END_TURN,
                        "Use Freyr's Fortifications",
                        MiscEmojis.LegendaryPlanet),
                Buttons.red(player.factionButtonChecker() + DECLINE_END_TURN, "End Turn"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", you may exhaust _Freyr's Fortifications_ to repair all your units before ending your turn.",
                buttons);
        return true;
    }

    public static void repairAllUnits(Game game, Player player) {
        game.getTileMap().values().stream()
                .flatMap(tile -> tile.getUnitHolders().values().stream())
                .forEach(holder -> holder.removeAllUnitDamage(player.getColorID()));
    }

    @ButtonHandler(USE_END_TURN)
    public static void useEndTurn(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasPlanet("vanaheim")
                || player.getExhaustedPlanetsAbilities().contains("vanaheim")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.exhaustPlanetAbility("vanaheim");
        repairAllUnits(game, player);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " exhausted _Freyr's Fortifications_ to repair all their units.");
        ButtonHelper.deleteMessage(event);
        EndTurnService.endTurnAndUpdateMap(event, game, player);
    }

    @ButtonHandler(DECLINE_END_TURN)
    public static void declineEndTurn(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelper.deleteMessage(event);
        EndTurnService.endTurnAndUpdateMap(event, game, player);
    }
}
