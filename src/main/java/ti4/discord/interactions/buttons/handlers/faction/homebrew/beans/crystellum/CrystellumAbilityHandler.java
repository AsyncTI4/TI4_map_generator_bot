package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.crystellum;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.ParsedUnit;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class CrystellumAbilityHandler {
    private static final String REFRACTION = "refraction";
    private static final String OFFER_REFRACTION = "crystellumOfferRefraction";
    private static final String REFRACTION_TARGET_PREFIX = "crystellumRefractionTarget_";
    private static final String FRAGMENTATION = "fragmentation";
    private static final String OFFER_FRAGMENTATION = "crystellumOfferFragmentation_";

    private static String getRefractionRoundPrefix(Player player, Tile combatTile) {
        return player.getFaction() + "_refraction_" + combatTile.getPosition() + "_round_";
    }

    private static String getRefractionRoundKey(Player player, Tile combatTile, Game game) {
        String round =
                game.getStoredValue("combatRoundTracker" + player.getFaction() + combatTile.getPosition() + "space");
        if (round.isBlank()) {
            round = "1";
        }
        return getRefractionRoundPrefix(player, combatTile) + round;
    }

    public static void resetRefractionForCombat(Game game, Player player, Tile combatTile) {
        if (game == null || player == null || combatTile == null) {
            return;
        }
        String keyPrefix = getRefractionRoundPrefix(player, combatTile);
        List<String> keys = new ArrayList<>(game.getStoredValueMap().keySet());
        for (String key : keys) {
            if (key.startsWith(keyPrefix)) {
                game.removeStoredValue(key);
            }
        }
    }

    public static void addRefractionButtonIfRelevant(
            List<Button> buttons, Player player, Game game, Tile combatTile, int hits) {
        if (buttons == null || player == null || game == null || combatTile == null) {
            return;
        }
        if (!player.hasAbility(REFRACTION)) {
            return;
        }
        if (hits < 1) {
            return;
        }
        String key = getRefractionRoundKey(player, combatTile, game);
        if (!game.getStoredValue(key).isBlank()) {
            return;
        }

        boolean hasAdjacentNonFighterShip =
                FoWHelper.getAdjacentTiles(game, combatTile.getPosition(), player, false, true).stream()
                        .filter(pos -> !combatTile.getPosition().equals(pos))
                        .map(game::getTileByPosition)
                        .filter(Objects::nonNull)
                        .anyMatch(tile -> tile.hasPlayerNonFighterShips(player));

        if (!hasAdjacentNonFighterShip) {
            return;
        }

        buttons.add(Buttons.red(
                player.factionButtonChecker() + OFFER_REFRACTION + "_" + combatTile.getPosition() + "_" + hits,
                "Use Refraction"));
    }

    @ButtonHandler(OFFER_REFRACTION)
    public static void resolveOfferRefraction(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        if (event == null || player == null || game == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!player.hasAbility(REFRACTION)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String payload = buttonID.substring((OFFER_REFRACTION + "_").length());
        String[] parts = payload.split("_", 2);
        if (parts.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String combatPos = parts[0];
        int hits;
        try {
            hits = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile combatTile = game.getTileByPosition(combatPos);
        if (combatTile == null || hits < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = getRefractionTargetButtons(player, game, combatTile, hits);
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String msg = player.getRepresentationUnfogged()
                + ", choose the adjacent system whose non-fighter ship will take the refraction hit.";

        event.getMessage()
                .editMessage(msg)
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                .queue();
    }

    private static List<Button> getRefractionTargetButtons(Player player, Game game, Tile combatTile, int hits) {
        List<Button> buttons = new ArrayList<>();
        if (player == null || game == null || combatTile == null) {
            return buttons;
        }

        for (String pos : FoWHelper.getAdjacentTiles(game, combatTile.getPosition(), player, false, true)) {
            if (combatTile.getPosition().equals(pos)) {
                continue;
            }

            Tile adjacentTile = game.getTileByPosition(pos);
            if (adjacentTile == null) {
                continue;
            }

            if (!adjacentTile.hasPlayerNonFighterShips(player)) {
                continue;
            }

            buttons.add(Buttons.red(
                    player.factionButtonChecker()
                            + REFRACTION_TARGET_PREFIX
                            + combatTile.getPosition() + "|" + adjacentTile.getPosition() + "|" + hits,
                    "Assign hit to ship in " + adjacentTile.getRepresentationForButtons(game, player)));
        }

        buttons.add(Buttons.red("deleteButtons", "Decline"));

        return buttons;
    }

    @ButtonHandler(REFRACTION_TARGET_PREFIX)
    public static void resolveRefractionTarget(
            ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        if (event == null || player == null || game == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!player.hasAbility(REFRACTION)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String payload = buttonID.substring(REFRACTION_TARGET_PREFIX.length());
        String[] parts = payload.split("\\|", 3);
        if (parts.length != 3) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String combatPos = parts[0];
        String targetPos = parts[1];

        int hits;
        try {
            hits = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (hits < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile combatTile = game.getTileByPosition(combatPos);
        Tile targetTile = game.getTileByPosition(targetPos);
        if (combatTile == null || targetTile == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (combatTile.getPosition().equals(targetTile.getPosition())) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        boolean isAdjacent = FoWHelper.getAdjacentTiles(game, combatTile.getPosition(), player, false, true)
                .contains(targetTile.getPosition());
        if (!isAdjacent) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (!targetTile.hasPlayerNonFighterShips(player)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.setStoredValue(getRefractionRoundKey(player, combatTile, game), "used");

        int remainingHits = hits - 1;

        String msg = "\n" + player.getRepresentationUnfogged()
                + " used _Refraction_ to assign 1 hit to a non-fighter ship in "
                + targetTile.getRepresentationForButtons(game, player) + ".";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);

        List<Button> updatedButtons = new ArrayList<>();
        String factionChecker = player.factionButtonChecker();

        if (remainingHits > 0) {
            updatedButtons.add(Buttons.green(
                    factionChecker + "autoAssignSpaceHits_" + combatTile.getPosition() + "_" + remainingHits,
                    "Auto-assign Hit" + (remainingHits == 1 ? "" : "s")));
            updatedButtons.add(Buttons.red(
                    "getDamageButtons_" + combatTile.getPosition() + "deleteThis_spacecombat",
                    "Manually Assign Hit" + (remainingHits == 1 ? "" : "s")));
            updatedButtons.add(Buttons.gray(
                    factionChecker + "cancelSpaceHits_" + combatTile.getPosition() + "_" + remainingHits,
                    "Cancel a Hit"));

            String msg2 = player.getRepresentationNoPing() + ", you may automatically assign "
                    + (remainingHits == 1 ? "the hit" : "hits") + ". "
                    + ButtonHelperModifyUnits.autoAssignSpaceCombatHits(
                            player, game, combatTile, remainingHits, event, true);

            event.getMessage()
                    .editMessage(msg2)
                    .setComponents(ButtonHelper.turnButtonListIntoActionRowList(updatedButtons))
                    .queue();
        } else {
            event.getMessage()
                    .editMessage(player.getRepresentationNoPing() + " has no remaining space combat hits to assign.")
                    .setComponents()
                    .queue();
        }

        List<Button> redirectedButtons =
                ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, game, targetTile, "spacecombat");

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", assign 1 _Refraction_ hit to your non-fighter ship in "
                        + targetTile.getRepresentationForButtons(game, player) + ".",
                redirectedButtons);
    }

    public static void offerFragmentationForBatchIfRelevant(
            GenericInteractionCreateEvent event, Game game, List<RemovedUnit> units, boolean combat) {
        if (event == null || game == null || units == null || units.isEmpty() || !combat) {
            return;
        }
        for (Player player : game.getRealPlayersNNeutral()) {
            if (player == null || !player.hasAbility(FRAGMENTATION)) {
                continue;
            }

            Tile triggerTile = null;
            for (RemovedUnit unit : units) {
                if (!player.unitBelongsToPlayer(unit.unitKey())) {
                    continue;
                }

                ParsedUnit parsedUnit = new ParsedUnit(
                        unit.unitKey(), unit.getTotalRemoved(), unit.uh().getName());
                if (!isFragmentationTriggerRelevant(game, player, unit.tile(), parsedUnit, true)) {
                    continue;
                }

                triggerTile = unit.tile();
                break;
            }

            if (triggerTile == null) {
                continue;
            }

            Button button = Buttons.green(
                    player.factionButtonChecker() + OFFER_FRAGMENTATION + triggerTile.getPosition(),
                    "Use Fragmentation");

            String msg = player.getRepresentationUnfogged()
                    + ", one of your non-fighter ships was destroyed in the active system. "
                    + "You may use _Fragmentation_ to place 1 fighter form your reinforcements in this system.";

            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(), msg, List.of(button, Buttons.red("deleteButtons", "Decline")));
        }
    }

    private static boolean isFragmentationTriggerRelevant(
            Game game, Player player, Tile tile, ParsedUnit unit, boolean combat) {
        if (game == null || player == null || tile == null || unit == null) {
            return false;
        }
        if (!combat) {
            return false;
        }
        if (!player.hasAbility(FRAGMENTATION)) {
            return false;
        }
        if (!tile.getPosition().equals(game.getActiveSystem())) {
            return false;
        }
        if (!Constants.SPACE.equalsIgnoreCase(unit.location())) {
            return false;
        }
        UnitModel unitModel = player.getUnitFromUnitKey(unit.unitKey());
        if (unitModel == null) {
            return false;
        }
        if (!unitModel.getIsShip()) {
            return false;
        }
        if (unit.unitKey().unitType() == UnitType.Fighter) {
            return false;
        }
        return true;
    }

    @ButtonHandler(OFFER_FRAGMENTATION)
    public static void resolveOfferFragmentation(
            ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        if (event == null || player == null || game == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!player.hasAbility(FRAGMENTATION)) {
            return;
        }

        String pos = buttonID.substring(OFFER_FRAGMENTATION.length());
        Tile tile = game.getTileByPosition(pos);
        if (tile == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (!tile.getPosition().equals(game.getActiveSystem())) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 fighter");

        String msg = player.getRepresentationUnfogged()
                + ", used _Fragmentation_ to place 1 fighter in "
                + tile.getRepresentationForButtons() + ".";

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }
}
