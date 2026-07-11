package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.combat.StartCombatService;
import ti4.service.planet.PlanetButtonService;
import ti4.service.unit.AddUnitService;

@UtilityClass
class IxthianGiftAcd2ButtonHandler {

    @ButtonHandler("resolveIxthianGift")
    public static void resolveIxthianGift(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            String id = player.factionButtonChecker() + "ixthianGiftPlayer_" + p2.getFaction();
            if (game.isFowMode()) {
                buttons.add(Buttons.gray(id, p2.getColor()));
            } else {
                buttons.add(Buttons.gray(id, p2.getFactionModel().getShortName())
                        .withEmoji(Emoji.fromFormatted(p2.getFactionEmoji())));
            }
        }
        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "No valid target for _Ixthian Gift_.");
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Done"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose the player you resolved a transaction with for _Ixthian Gift_.",
                buttons);
    }

    @ButtonHandler("ixthianGiftPlayer_")
    public static void resolveIxthianGiftPlayer(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.replace("ixthianGiftPlayer_", ""));
        ButtonHelper.deleteMessage(event);
        if (target == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Ixthian Gift_.");
            return;
        }
        List<Button> buttons = PlanetButtonService.buttonsForOwnedPlanets(
                target,
                game,
                location -> !location.tile().isHomeSystem(game),
                ButtonStyle.SUCCESS,
                player.factionButtonChecker() + "ixthianGiftPlanet_" + target.getFaction() + "_");
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    target.getRepresentationNoPing() + " controls no non-home planet for _Ixthian Gift_.");
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Done"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose a non-home planet " + target.getRepresentationNoPing()
                        + " controls to commit 1 infantry to for _Ixthian Gift_.",
                buttons);
    }

    @ButtonHandler("ixthianGiftPlanet_")
    public static void resolveIxthianGiftPlanet(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("ixthianGiftPlanet_", "").split("_", 2);
        ButtonHelper.deleteMessage(event);
        if (parts.length < 2) {
            return;
        }
        Player target = game.getPlayerFromColorOrFaction(parts[0]);
        String planet = parts[1];
        Tile tile = game.getTileContainingPlanet(planet);
        Planet unitHolder = game.getPlanet(planet);
        if (target == null || tile == null || unitHolder == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Ixthian Gift_.");
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 infantry " + planet);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " committed 1 infantry to "
                        + Helper.getPlanetRepresentation(planet, game) + " for _Ixthian Gift_.");

        if (unitHolder.hasGroundForces(target)) {
            boolean combatStarted = StartCombatService.groundCombatCheck(game, unitHolder, tile, event);
            if (combatStarted) {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        "Resolve the ground combat from _Ixthian Gift_. Reminder: this does not trigger PDS.");
            }
        } else {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    target.getRepresentationNoPing() + " has no ground forces on "
                            + Helper.getPlanetRepresentation(planet, game)
                            + ", so no combat occurs for _Ixthian Gift_.");
        }
    }
}
