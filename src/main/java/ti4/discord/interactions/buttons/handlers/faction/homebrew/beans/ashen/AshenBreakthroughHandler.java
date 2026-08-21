package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.BombardmentAssignment;
import ti4.helpers.BombardmentAssignmentType;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.BombardmentService;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.CombatRollType;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;
import tools.jackson.databind.ObjectMapper;

@UtilityClass
public class AshenBreakthroughHandler {
    private static final String BT = "ashenbt";
    private static final String SELECT_UNIT = "ashenBtSelectUnit_";
    private static final String SELECT_PLANET = "ashenBtSelectPlanet_";
    private static final String CANCEL_HITS = "ashenBtCancelHits";
    private static final String ASSIGN_HITS = "ashenBtAssignHits";
    private static final String ROLL_CONTEXT = "ashenBtRoll_";

    public static boolean hasEligibleTarget(Game game, Player player) {
        return !getEligibleBombardmentUnits(game, player).isEmpty();
    }

    public static void postInitialButtons(GenericInteractionCreateEvent event, Game game, Player player) {
        List<UnitChoice> choices = getEligibleBombardmentUnits(game, player);
        if (choices.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationUnfogged()
                            + " has no non-flagship BOMBARDMENT unit that can target an adjacent planet.");
            return;
        }
        String message =
                player.getRepresentationUnfogged() + ", choose 1 non-flagship unit to use _From Fire, Resolve_.";
        String prefix = player.factionButtonChecker() + SELECT_UNIT;
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                message,
                NewStuffHelper.buttonPagination(getUnitButtons(player, choices), prefix, 0));
    }

    @ButtonHandler(SELECT_UNIT)
    public static void selectUnit(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<UnitChoice> choices = getEligibleBombardmentUnits(game, player);
        String message =
                player.getRepresentationUnfogged() + ", choose 1 non-flagship unit to use _From Fire, Resolve_.";
        String prefix = player.factionButtonChecker() + SELECT_UNIT;
        List<Button> buttons = getUnitButtons(player, choices);
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, prefix, buttonID)) {
            return;
        }

        String[] values = buttonID.substring(SELECT_UNIT.length()).split("\\|", 2);
        UnitChoice selected = values.length == 2
                ? choices.stream()
                        .filter(choice -> choice.source().getPosition().equals(values[0])
                                && choice.unit().getAsyncId().equals(values[1]))
                        .findFirst()
                        .orElse(null)
                : null;
        if (selected == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<PlanetChoice> planets = getEligiblePlanets(game, player, selected.source());
        String planetMessage = player.getRepresentationUnfogged() + ", choose the adjacent planet to bombard with "
                + selected.unit().getName() + ".";
        String planetPrefix = player.factionButtonChecker() + SELECT_PLANET
                + selected.source().getPosition() + "|" + selected.unit().getAsyncId() + "|";
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                planetMessage,
                NewStuffHelper.buttonPagination(getPlanetButtons(player, selected, planets), planetPrefix, 0));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_PLANET)
    public static void selectPlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(SELECT_PLANET.length()).split("\\|", 4);
        UnitChoice selected = values.length == 4
                ? getEligibleBombardmentUnits(game, player).stream()
                        .filter(choice -> choice.source().getPosition().equals(values[0])
                                && choice.unit().getAsyncId().equals(values[1]))
                        .findFirst()
                        .orElse(null)
                : null;
        List<PlanetChoice> planets = selected == null ? List.of() : getEligiblePlanets(game, player, selected.source());
        String message = selected == null
                ? "Choose an adjacent planet."
                : player.getRepresentationUnfogged() + ", choose the adjacent planet to bombard with "
                        + selected.unit().getName() + ".";
        String prefix = values.length < 2
                ? player.factionButtonChecker() + SELECT_PLANET
                : player.factionButtonChecker() + SELECT_PLANET + values[0] + "|" + values[1] + "|";
        List<Button> buttons = selected == null ? List.of() : getPlanetButtons(player, selected, planets);
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, prefix, buttonID)) {
            return;
        }

        PlanetChoice target = values.length == 4
                ? planets.stream()
                        .filter(choice -> choice.tile().getPosition().equals(values[2])
                                && choice.planet().equals(values[3]))
                        .findFirst()
                        .orElse(null)
                : null;
        if (selected == null || target == null || !player.isBreakthroughExhausted(BT)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.setStoredValue(
                getRollContextKey(player), "pending|" + selected.source().getPosition() + "|" + target.planet());
        game.setStoredValue(
                "assignedBombardment" + player.getFaction(),
                new ObjectMapper()
                        .writeValueAsString(List.of(new BombardmentAssignment(
                                selected.unit().getAsyncId(),
                                target.planet(),
                                false,
                                BombardmentAssignmentType.UNIT))));
        game.setStoredValue("bombardmentTarget" + player.getFaction(), target.planet());
        CombatRollService.secondHalfOfCombatRoll(
                player, game, event, selected.source(), "space", CombatRollType.bombardment, false);
        game.removeStoredValue("assignedBombardment" + player.getFaction());
        game.removeStoredValue("bombardmentTarget" + player.getFaction());
        ButtonHelper.deleteMessage(event);
    }

    public static boolean offerHitReplacement(
            GenericInteractionCreateEvent event, Game game, Player player, Tile source, String planet, int hits) {
        String expected = "pending|" + source.getPosition() + "|" + planet;
        if (!expected.equals(game.getStoredValue(getRollContextKey(player)))) {
            return false;
        }
        if (hits < 1) {
            game.removeStoredValue(getRollContextKey(player));
            return false;
        }

        game.setStoredValue(getRollContextKey(player), "decision|" + source.getPosition() + "|" + planet + "|" + hits);
        List<Button> buttons = List.of(
                Buttons.green(player.factionButtonChecker() + CANCEL_HITS, "Cancel Hits and Place Infantry"),
                Buttons.red(player.factionButtonChecker() + ASSIGN_HITS, "Assign Hits Normally"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + ", _From Fire, Resolve_ produced " + hits + " BOMBARDMENT "
                        + (hits == 1 ? "hit" : "hits") + ". You may cancel all of them to place up to " + hits
                        + " infantry into coexistence on " + Helper.getPlanetRepresentation(planet, game) + ".",
                buttons);
        return true;
    }

    @ButtonHandler(ASSIGN_HITS)
    public static void assignHitsNormally(ButtonInteractionEvent event, Game game, Player player) {
        RollContext context = getContext(game, player, "decision");
        Tile targetTile = context == null ? null : game.getTileFromPlanet(context.planet());
        if (context == null || targetTile == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.removeStoredValue(getRollContextKey(player));
        for (Player target : ButtonHelper.getPlayersWithUnitsOnAPlanet(game, targetTile, context.planet())) {
            if (target == player) {
                continue;
            }
            List<Button> buttons =
                    ButtonHelper.getButtonsForRemovingAllUnitsInSystem(target, game, targetTile, "bombardment").stream()
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
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(CANCEL_HITS)
    public static void cancelHits(ButtonInteractionEvent event, Game game, Player player) {
        RollContext context = getContext(game, player, "decision");
        Tile targetTile = context == null ? null : game.getTileFromPlanet(context.planet());
        if (context == null || targetTile == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        int infantryToPlace = Math.min(context.hits(), player.getNombox().getUnitCount(UnitType.Infantry, player));
        if (infantryToPlace > 0) {
            RemoveUnitService.removeUnits(
                    event, player.getNomboxTile(), game, player.getColor(), infantryToPlace + " infantry");
        }
        game.setStoredValue("coexistFlag", "yes");
        if (infantryToPlace > 0) {
            AddUnitService.addUnits(
                    event, targetTile, game, player.getColor(), infantryToPlace + " infantry " + context.planet());
        }
        game.removeStoredValue("coexistFlag");
        ButtonHelperAbilities.oceanBoundCheck(game);
        game.removeStoredValue(getRollContextKey(player));
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + " canceled " + context.hits() + " BOMBARDMENT "
                        + (context.hits() == 1 ? "hit" : "hits") + " and placed " + infantryToPlace
                        + " infantry into coexistence on " + Helper.getPlanetRepresentation(context.planet(), game)
                        + " with _From Fire, Resolve_.");
        ButtonHelper.deleteMessage(event);
    }

    private static List<UnitChoice> getEligibleBombardmentUnits(Game game, Player player) {
        if (game == null || player == null) {
            return List.of();
        }
        List<UnitChoice> choices = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (getEligiblePlanets(game, player, tile).isEmpty()) {
                continue;
            }
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                for (UnitKey unitKey : holder.getUnitKeys()) {
                    if (!player.unitBelongsToPlayer(unitKey) || holder.getUnitCount(unitKey) < 1) {
                        continue;
                    }
                    UnitModel unit = player.getUnitFromUnitKey(unitKey);
                    if (unit != null
                            && unit.getUnitType() != UnitType.Flagship
                            && unit.getBombardDieCount(player) > 0) {
                        choices.add(new UnitChoice(tile, unit));
                    }
                }
            }
        }
        return choices.stream()
                .distinct()
                .sorted(Comparator.comparing(
                                (UnitChoice choice) -> choice.source().getPosition())
                        .thenComparing(choice -> choice.unit().getName()))
                .toList();
    }

    private static List<PlanetChoice> getEligiblePlanets(Game game, Player player, Tile source) {
        if (source == null) {
            return List.of();
        }
        return FoWHelper.getAdjacentTiles(game, source.getPosition(), player, false, true).stream()
                .map(game::getTileByPosition)
                .filter(tile -> tile != null)
                .flatMap(tile -> BombardmentService.getBombardablePlanets(player, game, tile).stream()
                        .map(planet -> new PlanetChoice(tile, planet)))
                .sorted(Comparator.comparing(
                                (PlanetChoice choice) -> choice.tile().getPosition())
                        .thenComparing(PlanetChoice::planet))
                .toList();
    }

    private static List<Button> getUnitButtons(Player player, List<UnitChoice> choices) {
        return choices.stream()
                .map(choice -> Buttons.green(
                        player.factionButtonChecker() + SELECT_UNIT
                                + choice.source().getPosition() + "|"
                                + choice.unit().getAsyncId(),
                        choice.unit().getName() + " in "
                                + choice.source().getRepresentationForButtons(player.getGame(), player),
                        choice.unit().getUnitEmoji()))
                .toList();
    }

    private static List<Button> getPlanetButtons(Player player, UnitChoice selected, List<PlanetChoice> choices) {
        return choices.stream()
                .map(choice -> Buttons.green(
                        player.factionButtonChecker() + SELECT_PLANET
                                + selected.source().getPosition() + "|"
                                + selected.unit().getAsyncId() + "|"
                                + choice.tile().getPosition() + "|" + choice.planet(),
                        Helper.getPlanetRepresentation(choice.planet(), player.getGame())))
                .toList();
    }

    private static String getRollContextKey(Player player) {
        return ROLL_CONTEXT + player.getFaction();
    }

    private static RollContext getContext(Game game, Player player, String state) {
        String[] values = game.getStoredValue(getRollContextKey(player)).split("\\|", 4);
        if (values.length != 4 || !state.equals(values[0])) {
            return null;
        }
        try {
            return new RollContext(values[1], values[2], Integer.parseInt(values[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record UnitChoice(Tile source, UnitModel unit) {}

    private record PlanetChoice(Tile tile, String planet) {}

    private record RollContext(String sourcePosition, String planet, int hits) {}
}
