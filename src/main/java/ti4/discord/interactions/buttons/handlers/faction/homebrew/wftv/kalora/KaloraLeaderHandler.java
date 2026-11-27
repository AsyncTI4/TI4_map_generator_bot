package ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.kalora;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.tuple.Pair;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.adjacency.Adjacency;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollService;
import ti4.service.leader.agent.modules.homebrew.KaloraAgent;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class KaloraLeaderHandler {

    public static void offerKaloraAgentButtons(Player player, String msg) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(KaloraAgent.button(player));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                msg
                        + ", a reminder that at the end of this space combat, you may exhaust the Kalora agent to allow a participating player to gain 1 command token.",
                buttons);
    }

    public static void addCommanderBombardmentUnits(
            Player player, Tile tile, Map<Pair<UnitModel, UnitHolder>, Integer> units) {
        var game = player.getGame();
        if (game == null) return;
        String guardKey = "kaloraCommanderBombardResolving_" + player.getFaction();
        if (!game.getStoredValue(guardKey).isEmpty()) return;
        game.setStoredValue(guardKey, "true");
        try {
            for (String adjacentPos : Adjacency.of(game, player).forEffects().into(tile.getPosition())) {
                Tile adjacentTile = game.getTileByPosition(adjacentPos);
                if (adjacentTile == null) continue;
                Map<Pair<UnitModel, UnitHolder>, Integer> adjacentUnits =
                        CombatRollService.getUnitsInBombardment(adjacentTile, player, null);
                adjacentUnits.forEach((pair, count) -> units.merge(pair, count, Integer::sum));
            }
        } finally {
            game.setStoredValue(guardKey, "");
        }
    }

    public static void offerFlashpointButton(
            Game game, Player invader, Player controller, Tile tile, String planetName) {
        UnitHolder planetHolder = tile.getUnitHolderFromPlanet(planetName);
        if (planetHolder == null || planetHolder.getUnitKeysForPlayer(invader).isEmpty()) {
            return;
        }
        boolean anothersHome = tile.isHomeSystem(game) && !tile.equals(controller.getHomeSystemTile());
        if (anothersHome) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                controller.getCorrectChannel(),
                controller.getRepresentationUnfogged() + ", "
                        + FoWHelper.identityOrColorIfCanSeeStats(
                                game, invader, controller, invader.getRepresentationNoPing())
                        + " just committed units to " + Helper.getPlanetRepresentation(planetName, game)
                        + ". You may purge Dreathos, the Kalora hero, to return all of those units to space and"
                        + " place 5 infantry there.",
                List.of(
                        Buttons.red(
                                controller.factionButtonChecker() + "kaloraFlashpoint_" + invader.getColorID() + "_"
                                        + planetName,
                                "Purge Dreathos (Flashpoint)"),
                        Buttons.DECLINE_DELETE_BUTTONS));
    }

    @ButtonHandler("kaloraFlashpoint_")
    public static void resolveFlashpoint(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.replace("kaloraFlashpoint_", "").split("_", 2);
        Player invader = parts.length == 2 ? game.getPlayerFromColorOrFaction(parts[0]) : null;
        String planetName = parts.length == 2 ? parts[1] : null;
        Tile tile = planetName == null ? null : game.getTileFromPlanet(planetName);
        UnitHolder planetHolder = tile == null ? null : tile.getUnitHolderFromPlanet(planetName);
        if (invader == null || planetHolder == null || !player.hasLeaderUnlocked("kalorahero")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        UnitHolder space = tile.getSpaceUnitHolder();
        for (UnitKey key : new ArrayList<>(planetHolder.getUnitKeysForPlayer(invader))) {
            UnitModel model = invader.getPriorityUnitByAsyncID(key.asyncID(), planetHolder);
            int count = planetHolder.getUnitCount(key);
            if (model == null || !model.getIsGroundForce() || count <= 0) {
                continue;
            }
            List<RemovedUnit> removed = RemoveUnitService.removeUnit(
                    event, tile, game, invader, planetHolder, key.unitType(), count, false);
            AddUnitService.addUnits(
                    event,
                    game,
                    removed.stream().map(r -> r.onUnitHolder(space)).toList());
        }
        AddUnitService.addUnits(event, tile, game, player.getColor(), "5 infantry " + planetName);
        player.removeLeader("kalorahero");
        String planetRepresentation = Helper.getPlanetRepresentation(planetName, game);
        FoWHelper.notifyPlayerAndAffectedInFog(
                game,
                player,
                player.getRepresentationUnfogged() + " purged Dreathos, returning "
                        + FoWHelper.identityOrColorIfCanSeeStats(
                                game, invader, player, invader.getRepresentationNoPing())
                        + "'s committed units to space and placing 5 infantry on " + planetRepresentation + ".",
                invader,
                invader.getRepresentationUnfogged() + ", "
                        + FoWHelper.identityOrColorIfCanSeeStats(
                                game, player, invader, player.getRepresentationNoPing())
                        + " purged Dreathos, returning your committed units on " + planetRepresentation
                        + " to space and placing 5 infantry there.");
        ButtonHelper.deleteMessage(event);
    }
}
