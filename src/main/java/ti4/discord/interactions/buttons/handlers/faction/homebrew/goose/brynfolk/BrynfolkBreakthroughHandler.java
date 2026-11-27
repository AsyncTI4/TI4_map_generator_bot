package ti4.discord.interactions.buttons.handlers.faction.homebrew.goose.brynfolk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
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
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;
import ti4.json.JsonMapperManager;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.CombatRollType;
import ti4.service.unit.MoveUnitService;

@UtilityClass
public class BrynfolkBreakthroughHandler {
    private static final String BERSERKER_DROP_PODS = "brynfolkbt";
    private static final String USE_DROP_PODS = "useBerserkerDropPods";
    private static final String SELECT_PLANET = "selectBerserkerDropPodsPlanet_";
    private static final String CANCEL_HITS = "cancelBerserkerDropPodsHits";
    private static final String DECLINE_CANCEL_HITS = "declineBerserkerDropPodsHits";
    private static final String ROLL_CONTEXT = "brynfolkBtBombardment_";

    public static void offerBerserkerDropPods(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile) {
        if (!player.hasReadyBreakthrough(BERSERKER_DROP_PODS)
                || tile == null
                || tile.getPlanetUnitHolders().isEmpty()) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", you may use _Berserker Drop-Pods_. Only exhaust it if you have units with BOMBARDMENT in the active system.",
                Buttons.green(player.factionButtonChecker() + USE_DROP_PODS, "Use Berserker Drop-Pods"));
    }

    @ButtonHandler(USE_DROP_PODS)
    public static void useBerserkerDropPods(ButtonInteractionEvent event, Game game, Player player) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        if (tile == null || !player.hasReadyBreakthrough(BERSERKER_DROP_PODS)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = tile.getPlanetUnitHolders().stream()
                .map(planet -> Buttons.green(
                        player.factionButtonChecker() + SELECT_PLANET + planet.getName(),
                        Helper.getPlanetRepresentation(planet.getName(), game)))
                .toList();
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        BreakthroughCommandHelper.exhaustBreakthrough(player, BERSERKER_DROP_PODS);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), player.getRepresentation() + ", choose a planet to bombard.", buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_PLANET)
    public static void selectBombardmentPlanet(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planet = buttonID.substring(SELECT_PLANET.length());
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        if (tile == null || tile.getUnitHolderFromPlanet(planet) == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.setStoredValue(getRollContextKey(player), "pending|" + tile.getPosition() + "|" + planet);
        game.setStoredValue(
                "assignedBombardment" + player.getFaction(),
                JsonMapperManager.basic().writeValueAsString(buildAssignments(player, game, tile, planet)));
        game.setStoredValue("bombardmentTarget" + player.getFaction(), planet);
        try {
            CombatRollService.secondHalfOfCombatRoll(
                    player, game, event, tile, "space", CombatRollType.bombardment, false);
        } finally {
            game.removeStoredValue("assignedBombardment" + player.getFaction());
            game.removeStoredValue("bombardmentTarget" + player.getFaction());
        }
        if (game.getStoredValue(getRollContextKey(player)).startsWith("pending|")) {
            game.removeStoredValue(getRollContextKey(player));
        }
        ButtonHelper.deleteMessage(event);
    }

    public static boolean offerHitReplacement(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile, String planet, int hits) {
        String expected = "pending|" + tile.getPosition() + "|" + planet;
        if (!expected.equals(game.getStoredValue(getRollContextKey(player)))) {
            return false;
        }
        if (hits < 1) {
            game.removeStoredValue(getRollContextKey(player));
            return false;
        }
        game.setStoredValue(getRollContextKey(player), "decision|" + tile.getPosition() + "|" + planet + "|" + hits);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + " produced " + hits + " BOMBARDMENT "
                        + (hits == 1 ? "hit" : "hits")
                        + " with _Berserker Drop-Pods_. You may cancel all hits to commit up to that many infantry to "
                        + Helper.getPlanetRepresentation(planet, game) + ".",
                List.of(
                        Buttons.green(player.factionButtonChecker() + CANCEL_HITS, "Cancel Hits and Commit Infantry"),
                        Buttons.red(player.factionButtonChecker() + DECLINE_CANCEL_HITS, "Assign Hits Normally")));
        return true;
    }

    @ButtonHandler(DECLINE_CANCEL_HITS)
    public static void declineCancelHits(ButtonInteractionEvent event, Game game, Player player) {
        RollContext context = getContext(game, player);
        Tile tile = context == null ? null : game.getTileByPosition(context.position());
        if (context != null && tile != null) {
            for (Player target : ButtonHelper.getPlayersWithUnitsOnAPlanet(game, tile, context.planet())) {
                if (target == player) {
                    continue;
                }
                List<Button> buttons =
                        ButtonHelper.getButtonsForRemovingAllUnitsInSystem(target, game, tile, "bombardment").stream()
                                .filter(button -> button.getCustomId() != null
                                        && button.getCustomId().contains("_" + context.planet() + "_"))
                                .toList();
                if (!buttons.isEmpty()) {
                    MessageHelper.sendMessageToChannelWithButtons(
                            event.getMessageChannel(),
                            target.getRepresentationUnfogged() + ", assign up to " + context.hits() + " BOMBARDMENT "
                                    + (context.hits() == 1 ? "hit" : "hits") + " on "
                                    + Helper.getPlanetRepresentation(context.planet(), game) + ".",
                            buttons);
                }
            }
        }
        game.removeStoredValue(getRollContextKey(player));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(CANCEL_HITS)
    public static void cancelHits(ButtonInteractionEvent event, Game game, Player player) {
        RollContext context = getContext(game, player);
        Tile tile = context == null ? null : game.getTileByPosition(context.position());
        if (context == null || tile == null || tile.getUnitHolderFromPlanet(context.planet()) == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        int infantry =
                Math.min(context.hits(), tile.getSpaceUnitHolder().getUnitCount(UnitType.Infantry, player.getColor()));
        if (infantry > 0) {
            MoveUnitService.moveUnits(
                    event, tile, game, player.getColor(), infantry + " infantry", tile, context.planet());
        }
        game.removeStoredValue(getRollContextKey(player));
        List<Button> retreatButtons =
                ButtonHelperModifyUnits.getRetreatSystemButtons(player, game, context.position(), false, false);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " canceled " + context.hits() + " BOMBARDMENT "
                        + (context.hits() == 1 ? "hit" : "hits") + " and committed " + infantry + " infantry to "
                        + Helper.getPlanetRepresentation(context.planet(), game) + " with _Berserker Drop-Pods_.");
        if (!retreatButtons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", retreat all ships in the active system.",
                    retreatButtons);
        }
        ButtonHelper.deleteMessage(event);
    }

    private static List<BombardmentAssignment> buildAssignments(Player player, Game game, Tile tile, String planet) {
        List<BombardmentAssignment> assignments = new ArrayList<>();
        Map<Pair<UnitModel, UnitHolder>, Integer> units = CombatRollService.getUnitsInBombardment(tile, player, null);
        for (Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry : units.entrySet()) {
            int galvanized = entry.getKey()
                    .getRight()
                    .getGalvanizedUnitCount(entry.getKey().getLeft().getUnitType(), player.getColorID());
            for (int count = 0; count < entry.getValue(); count++) {
                assignments.add(new BombardmentAssignment(
                        entry.getKey().getLeft().getAsyncId(),
                        planet,
                        galvanized-- > 0,
                        BombardmentAssignmentType.UNIT));
            }
        }
        if (player.hasTech("ps") || player.hasTech("absol_ps")) {
            assignments.add(new BombardmentAssignment("plasmascoring", planet, false, BombardmentAssignmentType.TECH));
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "argentcommander") || player.hasTech("tf-zealous")) {
            assignments.add(
                    new BombardmentAssignment("argentcommander", planet, false, BombardmentAssignmentType.LEADER));
        }
        return assignments;
    }

    private static String getRollContextKey(Player player) {
        return ROLL_CONTEXT + player.getFaction();
    }

    private static RollContext getContext(Game game, Player player) {
        String[] values = game.getStoredValue(getRollContextKey(player)).split("\\|", 4);
        if (values.length != 4 || !"decision".equals(values[0])) {
            return null;
        }
        try {
            return new RollContext(values[1], values[2], Integer.parseInt(values[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record RollContext(String position, String planet, int hits) {}
}
