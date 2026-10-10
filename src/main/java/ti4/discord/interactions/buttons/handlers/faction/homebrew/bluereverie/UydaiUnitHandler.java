package ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.DiceHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.turn.EndTurnService;
import ti4.service.turn.PassService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class UydaiUnitHandler {
    private static final String DEATH_COMMANDOS = "death_commandos3";
    private static final String DESTROY_INFANTRY = "uydaiDeathCommandosDestroyInfantry_";
    private static final String DECLINE_DESTROY_INFANTRY = "uydaiDeathCommandosDeclineDestroyInfantry";
    private static final String PLACE_DEATH_COMMANDO = "uydaiDeathCommandosPlace_";
    private static final String STORED_COMMANDOS = "uydaiDeathCommandos";
    private static final String PASSING_AFTER_PLACEMENT = "uydaiDeathCommandosPassing";

    public static void resolveDeathCommandos(
            GenericInteractionCreateEvent event, Game game, List<RemovedUnit> destroyedUnits) {
        for (RemovedUnit destroyedUnit : destroyedUnits) {
            Player owner =
                    game.getPlayerFromColorOrFaction(destroyedUnit.unitKey().colorID());
            UnitModel unit = owner == null ? null : owner.getUnitFromUnitKey(destroyedUnit.unitKey());
            if (owner == null || unit == null || !DEATH_COMMANDOS.equals(unit.getId())) {
                continue;
            }

            List<DiceHelper.Die> dice = DiceHelper.rollDice(6, destroyedUnit.getTotalRemoved());
            int successes = DiceHelper.countSuccesses(dice);
            if (successes > 0) {
                addStoredCommandos(game, owner, successes);
                owner.setStasisInfantry(owner.getStasisInfantry() + successes);
            }

            String results = dice.stream()
                    .map(DiceHelper.Die::getGreenDieIfSuccessOrRedDieIfFailure)
                    .reduce((left, right) -> left + " " + right)
                    .orElse("");
            MessageHelper.sendMessageToChannel(
                    owner.getCorrectChannel(),
                    owner.getRepresentationNoPing()
                            + " rolled for "
                            + destroyedUnit.getTotalRemoved()
                            + " destroyed **Death Commando"
                            + (destroyedUnit.getTotalRemoved() == 1 ? "**: " : "s**: ")
                            + results
                            + (successes == 0
                                    ? ""
                                    : "\n> "
                                            + successes
                                            + " Death Commando"
                                            + (successes == 1 ? " is" : "s are")
                                            + " in stasis."));

            if (destroyedUnit.uh() instanceof Planet planet) {
                int failures = destroyedUnit.getTotalRemoved() - successes;
                for (int index = 0; index < failures; index++) {
                    offerInfantryDestruction(game, owner, planet);
                }
            }
        }
    }

    public static boolean offerDeathCommandosPlacement(GenericInteractionCreateEvent event, Game game, Player player) {
        if (getStoredCommandos(game, player) < 1) {
            return false;
        }
        List<Button> buttons = getLegendaryPlanetButtons(game, player);
        if (buttons.isEmpty()) {
            return false;
        }
        sendDeathCommandoPlacementPrompt(player, event, game, buttons);
        return true;
    }

    public static boolean offerDeathCommandosPlacementBeforePassing(
            GenericInteractionCreateEvent event, Game game, Player player) {
        boolean offered = offerDeathCommandosPlacement(event, game, player);
        if (offered) {
            game.setStoredValue(PASSING_AFTER_PLACEMENT + player.getFaction(), "true");
        }
        return offered;
    }

    @ButtonHandler(DESTROY_INFANTRY)
    public static void destroyInfantry(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(DESTROY_INFANTRY.length()).split("\\|", 2);
        String planet = values.length == 2 ? values[0] : "";
        Player target = values.length == 2 ? game.getPlayerFromColorOrFaction(values[1]) : null;
        Tile tile = game.getTileFromPlanet(planet);
        UnitHolder unitHolder = game.getUnitHolderFromPlanet(planet);
        UnitKey infantry = target == null ? null : Units.getUnitKey(UnitType.Infantry, target.getColorID());
        if (target == null || tile == null || unitHolder == null || unitHolder.getUnitCount(infantry) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        DestroyUnitService.destroyUnit(event, tile, game, infantry, 1, unitHolder, false);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " destroyed 1 infantry on "
                        + Helper.getPlanetRepresentation(planet, game) + " with **Death Commandos**.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(DECLINE_DESTROY_INFANTRY)
    public static void declineInfantryDestruction(ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE_DEATH_COMMANDO)
    public static void placeDeathCommando(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planet = buttonID.substring(PLACE_DEATH_COMMANDO.length());
        UnitHolder unitHolder = game.getUnitHolderFromPlanet(planet);
        if (getStoredCommandos(game, player) < 1
                || player.getStasisInfantry() < 1
                || !(unitHolder instanceof Planet planetHolder)
                || !player.getPlanets().contains(planet)
                || !planetHolder.isLegendary()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        addStoredCommandos(game, player, -1);
        player.setStasisInfantry(player.getStasisInfantry() - 1);
        AddUnitService.addUnits(event, game.getTileFromPlanet(planet), game, player.getColor(), "1 infantry " + planet);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed 1 **Death Commando** on "
                        + Helper.getPlanetRepresentation(planet, game) + ".");
        ButtonHelper.deleteMessage(event);

        if (offerDeathCommandosPlacement(event, game, player)) {
            return;
        }
        if (!game.getStoredValue(PASSING_AFTER_PLACEMENT + player.getFaction()).isEmpty()) {
            game.removeStoredValue(PASSING_AFTER_PLACEMENT + player.getFaction());
            PassService.passPlayerForRound(event, game, player, false);
        } else {
            EndTurnService.endTurnAndUpdateMap(event, game, player);
        }
    }

    private static void offerInfantryDestruction(Game game, Player player, Planet planet) {
        List<Button> buttons = getInfantryDestructionButtons(game, player, planet);
        if (buttons.isEmpty()) {
            return;
        }
        buttons.add(
                Buttons.red(player.factionButtonChecker() + DECLINE_DESTROY_INFANTRY, "Do Not Destroy An Infantry"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", a **Death Commando** failed its roll. You may destroy up to 1 infantry on "
                        + Helper.getPlanetRepresentation(planet.getName(), game) + ".",
                buttons);
    }

    private static List<Button> getInfantryDestructionButtons(Game game, Player player, Planet planet) {
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<UnitKey, Integer> entry : planet.getUnits().entrySet()) {
            UnitKey unitKey = entry.getKey();
            if (unitKey.unitType() != UnitType.Infantry || entry.getValue() < 1) {
                continue;
            }
            Player owner = game.getPlayerFromColorOrFaction(unitKey.colorID());
            if (owner == null) {
                continue;
            }
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + DESTROY_INFANTRY + planet.getName() + "|" + owner.getFaction(),
                    "Destroy 1 " + owner.getColor() + " Infantry",
                    owner.getFactionEmojiOrColor()));
        }
        return buttons;
    }

    private static void sendDeathCommandoPlacementPrompt(
            Player player, GenericInteractionCreateEvent event, Game game, List<Button> buttons) {
        String message = player.getRepresentationNoPing()
                + ", place 1 Death Commando from stasis on a legendary planet you control. "
                + getStoredCommandos(game, player)
                + " remain in stasis.";
        String prefix = player.factionButtonChecker() + PLACE_DEATH_COMMANDO;
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), message, NewStuffHelper.buttonPagination(buttons, prefix, 0));
    }

    private static List<Button> getLegendaryPlanetButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            UnitHolder unitHolder = game.getUnitHolderFromPlanet(planet);
            if (unitHolder instanceof Planet planetHolder && planetHolder.isLegendary()) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + PLACE_DEATH_COMMANDO + planet,
                        "Place on " + Helper.getPlanetRepresentation(planet, game),
                        FactionEmojis.uydai));
            }
        }
        return buttons;
    }

    private static int getStoredCommandos(Game game, Player player) {
        String value = game.getStoredValue(STORED_COMMANDOS + player.getFaction());
        return value.isEmpty() ? 0 : Integer.parseInt(value);
    }

    private static void addStoredCommandos(Game game, Player player, int amount) {
        int total = getStoredCommandos(game, player) + amount;
        if (total < 1) {
            game.removeStoredValue(STORED_COMMANDOS + player.getFaction());
        } else {
            game.setStoredValue(STORED_COMMANDOS + player.getFaction(), Integer.toString(total));
        }
    }
}
