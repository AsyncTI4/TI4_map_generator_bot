package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.combat.StartCombatService;
import ti4.service.fow.BlindSelectionService;
import ti4.service.fow.PlanetTargetService;
import ti4.service.fow.PlanetTargetService.PlanetTargetSpec;
import ti4.service.unit.AddUnitService;

@UtilityClass
class IxthianGiftAcd2ButtonHandler {

    /** Shared by the fog list and by resolution, so a blind-typed target obeys the same rules. */
    static PlanetTargetSpec giftSpec(Game game, Player player) {
        return PlanetTargetSpec.of(
                        player.factionButtonChecker() + "ixthianGiftPlanet_" + BlindSelectionService.TBD_FACTION)
                .excludingSelf()
                .where(p -> game.getTileFromPlanet(p.getName()) != null
                        && !game.getTileFromPlanet(p.getName()).isHomeSystem(game));
    }

    @ButtonHandler("resolveIxthianGift")
    public static void resolveIxthianGift(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        if (game.isFowMode()) {
            // The player step was fog-safe; the planet step then listed that player's whole holding list.
            // Skip straight to planets this player knows about. "Non-home" is public map info, so it can
            // still filter the list.
            buttons = PlanetTargetService.targetButtons(game, player, giftSpec(game, player), buttons);
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", choose the planet for _Ixthian Gift_.",
                    buttons);
            return;
        }
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            String id = player.factionButtonChecker() + "ixthianGiftPlayer_" + p2.getFaction();
            buttons.add(FoWHelper.fogSafeTargetButton(id, "gray", p2));
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
        List<Button> buttons = new ArrayList<>();
        for (String planet : target.getPlanets()) {
            Tile tile = game.getTileFromPlanet(planet);
            if (tile == null || tile.isHomeSystem(game)) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "ixthianGiftPlanet_" + target.getFaction() + "_" + planet,
                    Helper.getPlanetRepresentation(planet, game)));
        }
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
        String planet = parts[1];
        Player target = BlindSelectionService.ownerOf(game, parts[0], planet);
        Tile tile = game.getTileFromPlanet(planet);
        Planet unitHolder = game.getUnitHolderFromPlanet(planet);
        // Non-home is a builder filter, so it has to be re-checked for blind-typed targets.
        boolean legal = tile != null && unitHolder != null && !tile.isHomeSystem(game) && target != player;
        if (target == null || !legal) {
            // "Could not resolve" told the actor their guess was wrong. Use the shared pool so an illegal
            // target is indistinguishable from a legal one that achieved nothing.
            PlanetTargetService.fizzle(player);
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
