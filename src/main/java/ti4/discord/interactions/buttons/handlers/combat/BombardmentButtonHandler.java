package ti4.discord.interactions.buttons.handlers.combat;

import static org.apache.commons.lang3.StringUtils.capitalize;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ExploreHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.BombardmentService;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.CombatRollType;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.TechEmojis;

@UtilityClass
class BombardmentButtonHandler {

    @ButtonHandler("unassignBombardUnit_")
    public static void unassignBombardUnit(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String assignedUnit = buttonID.replace("unassignBombardUnit_", "");
        game.setStoredValue(
                "assignedBombardment" + player.getFaction(),
                game.getStoredValue("assignedBombardment" + player.getFaction())
                        .replace(assignedUnit, "")
                        .replace(";;", ";"));
        List<Button> buttons = getBombardmentAssignmentButtons(player, game);
        event.getMessage()
                .editMessage(getBombardmentSummary(player, game))
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("assignBombardUnit_")
    public static void assignBombardUnit(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String assignedUnit = buttonID.replace("assignBombardUnit_", "");
        game.setStoredValue(
                "assignedBombardment" + player.getFaction(),
                game.getStoredValue("assignedBombardment" + player.getFaction()) + assignedUnit + ";");
        List<Button> buttons = getBombardmentAssignmentButtons(player, game);
        event.getMessage()
                .editMessage(getBombardmentSummary(player, game))
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("bombardConfirm_")
    public static void bombardConfirm(ButtonInteractionEvent event, Player player, Game game) {
        if (game.getActiveSystem() == null) {
            return;
        }
        if (game.getTileByPosition(game.getActiveSystem()).isScar(game)
                && !player.hasUnlockedBreakthrough("nivynbt")
                && !player.hasTech("tf-singularitypoint")) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    player.getRepresentation()
                            + ", you cannot use BOMBARDMENT (or any other unit abilities) in an entropic scar.");
            return;
        }
        if (BombardmentService.getBombardablePlanets(player, game, game.getTileByPosition(game.getActiveSystem()))
                .isEmpty()) {
            String message = player.getRepresentation()
                    + ", there are no planets in this system that you can legally use BOMBARDMENT against. "
                    + "You cannot use BOMBARDMENT against planets you own";
            message += ButtonHelper.isLawInPlay(game, "conventions")
                    ? ", and you cannot bombard cultural planets while the _Conventions of War_ law is in play."
                    : ".";
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
            return;
        }

        BombardmentService.autoAssignAllBombardmentToAPlanet(player, game);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                player.getRepresentation() + " is assigning units to bombard as follows:\n"
                        + getBombardmentSummary(player, game),
                getBombardmentAssignmentButtons(player, game));
    }

    private static List<Button> getBombardmentAssignmentButtons(Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        if (tile == null) {
            return buttons;
        }
        Map<UnitModel, Integer> bombardUnits = CombatRollService.getUnitsInBombardment(tile, player, null);
        String assignedUnits = game.getStoredValue("assignedBombardment" + player.getFaction());
        List<String> usedLabels = new ArrayList<>();
        for (Map.Entry<UnitModel, Integer> entry : bombardUnits.entrySet()) {
            UnitModel mod = entry.getKey();
            for (int x = 0; x < entry.getValue(); x++) {
                String name = mod.getAsyncId() + "_" + x;
                if (assignedUnits.contains(name)) {
                    for (String assignedUnit : assignedUnits.split(";")) {

                        if (assignedUnit.contains(name)) {
                            String planet = assignedUnit.split("_")[2];
                            String label = "Unassign " + capitalize(mod.getBaseType()) + " From "
                                    + Helper.getPlanetRepresentationNoResInf(planet, game);
                            if (!usedLabels.contains(label)) {
                                buttons.add(
                                        Buttons.red("unassignBombardUnit_" + assignedUnit, label, mod.getUnitEmoji()));
                                usedLabels.add(label);
                            }
                        }
                    }
                } else {
                    for (String planet : BombardmentService.getBombardablePlanets(player, game, tile)) {
                        String label = "Assign " + capitalize(mod.getBaseType()) + " To "
                                + Helper.getPlanetRepresentationNoResInf(planet, game);
                        if (!usedLabels.contains(label)) {
                            buttons.add(Buttons.green(
                                    "assignBombardUnit_" + name + "_" + planet, label, mod.getUnitEmoji()));
                            usedLabels.add(label);
                        }
                    }
                }
            }
        }
        if (player.hasTech("ps") || player.hasTech("absol_ps")) {
            if (assignedUnits.contains("plasma")) {
                for (String assignedUnit : assignedUnits.split(";")) {
                    if (assignedUnit.contains("plasma")) {
                        String planet = assignedUnit.split("_")[2];
                        buttons.add(Buttons.red(
                                "unassignBombardUnit_" + assignedUnit,
                                "Unassign Plasma Scoring Die From "
                                        + Helper.getPlanetRepresentationNoResInf(planet, game),
                                TechEmojis.WarfareTech));
                    }
                }
            } else {
                for (String planet : BombardmentService.getBombardablePlanets(player, game, tile)) {
                    buttons.add(Buttons.green(
                            "assignBombardUnit_plasma_99_" + planet,
                            "Assign Plasma Scoring Die To " + Helper.getPlanetRepresentationNoResInf(planet, game),
                            TechEmojis.WarfareTech));
                }
            }
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "argentcommander") || player.hasTech("tf-zealous")) {
            if (assignedUnits.contains("argent")) {
                for (String assignedUnit : assignedUnits.split(";")) {
                    if (assignedUnit.contains("argent")) {
                        String planet = assignedUnit.split("_")[2];
                        buttons.add(Buttons.red(
                                "unassignBombardUnit_" + assignedUnit,
                                "Unassign Argent Commander Die from "
                                        + Helper.getPlanetRepresentationNoResInf(planet, game),
                                FactionEmojis.Argent));
                    }
                }
            } else {
                for (String planet : BombardmentService.getBombardablePlanets(player, game, tile)) {
                    buttons.add(Buttons.green(
                            "assignBombardUnit_argentcommander_99_" + planet,
                            "Assign Argent Commander Die to " + Helper.getPlanetRepresentationNoResInf(planet, game),
                            FactionEmojis.Argent));
                }
            }
        }
        buttons.add(Buttons.blue(
                "combatRoll_" + tile.getPosition() + "_space_" + CombatRollType.bombardment + "_deleteTheseButtons",
                "Done Assigning"));
        return buttons;
    }

    private static String getBombardmentSummary(Player player, Game game) {
        StringBuilder summary = new StringBuilder();
        String assignedUnits = game.getStoredValue("assignedBombardment" + player.getFaction());
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        if (tile == null) {
            return summary.toString();
        }
        for (String planet : BombardmentService.getBombardablePlanets(player, game, tile)) {
            summary.append("### ")
                    .append(player.fogSafeEmoji())
                    .append(" BOMBARDMENT of ")
                    .append(Helper.getPlanetRepresentationNoResInf(planet, game))
                    .append(":\n");
            for (Player p2 : game.getRealAndEliminatedPlayers()) {
                if (p2 == player) {
                    continue;
                }
                if (FoWHelper.playerHasUnitsOnPlanet(p2, game.getUnitHolderFromPlanet(planet))) {
                    summary.append("-# ")
                            .append(p2.fogSafeEmoji())
                            .append(" currently has ")
                            .append(ExploreHelper.getUnitListEmojisOnPlanetForHazardousExplorePurposes(
                                    game, p2, planet))
                            .append('\n');
                    break;
                }
            }

            for (String assignedUnit : assignedUnits.split(";")) {
                if (assignedUnit.endsWith(planet)) {
                    if (assignedUnit.contains("99")) {
                        if (assignedUnit.contains("argent")) {
                            summary.append("- Trrakan Aun Zulok die\n");
                        } else {
                            summary.append("- _Plasma Scoring_ die\n");
                        }
                    } else {
                        String asyncID = assignedUnit.split("_")[0];
                        UnitModel mod = player.getUnitFromAsyncID(asyncID);
                        summary.append("- ").append(mod.getUnitEmoji()).append('\n');
                    }
                }
            }
        }
        return summary.toString();
    }
}
