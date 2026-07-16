package ti4.discord.interactions.buttons.handlers.combat;

import static org.apache.commons.lang3.StringUtils.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import org.apache.commons.lang3.tuple.Pair;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.BombardmentAssignment;
import ti4.helpers.BombardmentAssignmentType;
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
import ti4.service.emoji.UnitEmojis;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@UtilityClass
class BombardmentButtonHandler {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @ButtonHandler("unassignBombardUnit_")
    public static void unassignBombardUnit(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        BombardmentAssignment buttonAssignment =
                BombardmentAssignment.decode(buttonID.replace("unassignBombardUnit_", ""));

        List<BombardmentAssignment> assignments = getAssignments(player, game);
        assignments.stream().filter(buttonAssignment::equals).findFirst().ifPresent(assignments::remove);
        saveAssignments(player, game, assignments);

        List<Button> buttons = getBombardmentAssignmentButtons(player, game);
        event.getMessage()
                .editMessage(getBombardmentSummary(player, game))
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("assignBombardUnit_")
    public static void assignBombardUnit(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        BombardmentAssignment buttonAssignment =
                BombardmentAssignment.decode(buttonID.replace("assignBombardUnit_", ""));
        List<BombardmentAssignment> assignments = getAssignments(player, game);
        assignments.add(buttonAssignment);
        saveAssignments(player, game, assignments);

        List<Button> buttons = getBombardmentAssignmentButtons(player, game);
        event.getMessage()
                .editMessage(getBombardmentSummary(player, game))
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("bombardConfirm_")
    public static void bombardConfirm(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String pos = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(pos);
        if (tile.isScar(game) && !player.hasUnlockedBreakthrough("nivynbt") && !player.hasTech("tf-singularitypoint")) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    player.getRepresentation()
                            + ", you cannot use BOMBARDMENT (or any other unit abilities) in an entropic scar.");
            return;
        }
        if (BombardmentService.getBombardablePlanets(player, game, tile).isEmpty()) {
            String message = player.getRepresentation()
                    + ", there are no planets in this system that you can legally use BOMBARDMENT against. "
                    + "You cannot use BOMBARDMENT against planets you own";
            message += ButtonHelper.isLawInPlay(game, "conventions")
                    ? ", and you cannot bombard cultural planets while the _Conventions of War_ law is in play."
                    : ".";
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
            return;
        }

        BombardmentService.autoAssignAllBombardmentToAPlanet(player, game, tile);
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
        Map<Pair<UnitModel, UnitHolder>, Integer> bombardUnits =
                CombatRollService.getUnitsInBombardment(tile, player, null);

        List<BombardmentAssignment> assignedUnits = MAPPER.readValue(
                game.getStoredValue("assignedBombardment" + player.getFaction()),
                new TypeReference<List<BombardmentAssignment>>() {});
        List<String> usedLabels = new ArrayList<>();
        for (Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry : bombardUnits.entrySet()) {

            UnitModel mod = entry.getKey().getKey();
            String sourceId = mod.getAsyncId();
            int totalUnits = entry.getValue();
            int totalGalvanized =
                    entry.getKey().getValue().getGalvanizedUnitCount(mod.getUnitType(), player.getColorID());
            int totalNormal = totalUnits - totalGalvanized;

            List<BombardmentAssignment> assignments = assignedUnits.stream()
                    .filter(a -> a.sourceId().contains(sourceId))
                    .toList();
            int assignedNormal = 0;
            int assignedGalvanized = 0;

            for (BombardmentAssignment assignment : assignments) {

                if (assignment.galvanized()) {
                    assignedGalvanized++;
                } else {
                    assignedNormal++;
                }

                String label = "Unassign "
                        + (assignment.galvanized() ? "Galvanized " : "")
                        + capitalize(mod.getBaseType())
                        + " From "
                        + Helper.getPlanetRepresentationNoResInf(assignment.planet(), game);

                if (!usedLabels.contains(label)) {
                    buttons.add(Buttons.red("unassignBombardUnit_" + assignment.encode(), label, mod.getUnitEmoji()));
                    usedLabels.add(label);
                }
            }

            int remainingNormal = totalNormal - assignedNormal;
            int remainingGalvanized = totalGalvanized - assignedGalvanized;

            if (remainingNormal > 0) {
                for (String planet : BombardmentService.getBombardablePlanets(player, game, tile)) {
                    BombardmentAssignment assignment =
                            new BombardmentAssignment(sourceId, planet, false, BombardmentAssignmentType.UNIT);

                    String label = "Assign "
                            + capitalize(mod.getBaseType())
                            + " To "
                            + Helper.getPlanetRepresentationNoResInf(planet, game);

                    if (!usedLabels.contains(label)) {
                        buttons.add(
                                Buttons.green("assignBombardUnit_" + assignment.encode(), label, mod.getUnitEmoji()));
                        usedLabels.add(label);
                    }
                }
            }

            if (remainingGalvanized > 0) {
                for (String planet : BombardmentService.getBombardablePlanets(player, game, tile)) {
                    BombardmentAssignment assignment =
                            new BombardmentAssignment(sourceId, planet, true, BombardmentAssignmentType.UNIT);

                    String label = "Assign Galvanized "
                            + capitalize(mod.getBaseType())
                            + " To "
                            + Helper.getPlanetRepresentationNoResInf(planet, game);

                    if (!usedLabels.contains(label)) {
                        buttons.add(
                                Buttons.green("assignBombardUnit_" + assignment.encode(), label, mod.getUnitEmoji()));
                        usedLabels.add(label);
                    }
                }
            }
        }
        if (player.hasTech("ps") || player.hasTech("absol_ps")) {
            if (assignedUnits.stream().anyMatch(u -> u.sourceId().contains("plasmascoring"))) {
                for (BombardmentAssignment assignedUnit : assignedUnits) {
                    if (assignedUnit.sourceId().contains("plasmascoring")) {
                        String planet = assignedUnit.planet();
                        buttons.add(Buttons.red(
                                "unassignBombardUnit_" + assignedUnit.encode(),
                                "Unassign Plasma Scoring Die From "
                                        + Helper.getPlanetRepresentationNoResInf(planet, game),
                                TechEmojis.WarfareTech));
                    }
                }
            } else {
                for (String planet : BombardmentService.getBombardablePlanets(player, game, tile)) {
                    BombardmentAssignment assignedUnit =
                            new BombardmentAssignment("plasmascoring", planet, false, BombardmentAssignmentType.TECH);
                    buttons.add(Buttons.green(
                            "assignBombardUnit_" + assignedUnit.encode(),
                            "Assign Plasma Scoring Die To " + Helper.getPlanetRepresentationNoResInf(planet, game),
                            TechEmojis.WarfareTech));
                }
            }
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "argentcommander") || player.hasTech("tf-zealous")) {
            if (assignedUnits.stream().anyMatch(u -> u.sourceId().contains("argentcommander"))) {
                for (BombardmentAssignment assignedUnit : assignedUnits) {
                    if (assignedUnit.sourceId().contains("argentcommander")) {
                        String planet = assignedUnit.planet();
                        buttons.add(Buttons.red(
                                "unassignBombardUnit_" + assignedUnit.encode(),
                                "Unassign Argent Commander Die from "
                                        + Helper.getPlanetRepresentationNoResInf(planet, game),
                                FactionEmojis.Argent));
                    }
                }
            } else {
                for (String planet : BombardmentService.getBombardablePlanets(player, game, tile)) {
                    buttons.add(Buttons.green(
                            "assignBombardUnit_"
                                    + new BombardmentAssignment(
                                                    "argentcommander", planet, false, BombardmentAssignmentType.LEADER)
                                            .encode(),
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
        List<BombardmentAssignment> assignedUnits = getAssignments(player, game);
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

            for (BombardmentAssignment assignedUnit : assignedUnits) {
                if (assignedUnit.planet().equals(planet)) {
                    switch (assignedUnit.type()) {
                        case UNIT -> {
                            String asyncID = assignedUnit.sourceId();
                            UnitModel mod = player.getUnitFromAsyncID(asyncID);
                            summary.append("- ").append(mod.getUnitEmoji());
                            if (assignedUnit.galvanized()) {
                                summary.append(" " + UnitEmojis.Galvanized);
                            }
                            summary.append('\n');
                        }
                        case TECH -> {
                            if (assignedUnit.sourceId().contains("plasmascoring")) {
                                summary.append("- _Plasma Scoring_ die\n");
                            }
                        }
                        case LEADER -> {
                            if (assignedUnit.sourceId().contains("argentcommander")) {
                                summary.append("- Trrakan Aun Zulok die\n");
                            }
                        }
                    }
                }
            }
        }
        return summary.toString();
    }

    private static List<BombardmentAssignment> getAssignments(Player player, Game game) {
        String json = game.getStoredValue("assignedBombardment" + player.getFaction());
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        return MAPPER.readValue(json, new TypeReference<List<BombardmentAssignment>>() {});
    }

    private static void saveAssignments(Player player, Game game, List<BombardmentAssignment> assignments) {
        game.setStoredValue("assignedBombardment" + player.getFaction(), MAPPER.writeValueAsString(assignments));
    }
}
