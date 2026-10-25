package ti4.discord.interactions.buttons.handlers.actioncards.bluereverie;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie.SarcosaHeroHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.tactical.TacticalActionOutputService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class PirateHordeBrActionCardHandler {
    private static final String RESOLVE = "resolvePirateHordeBr";
    private static final String ACTIVE = "pirateHordeActive_";

    public static void offerResolution(Game game, Player player, String message) {
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                message,
                List.of(Buttons.green(player.factionButtonChecker() + RESOLVE, "Resolve Pirate Horde")));
    }

    @ButtonHandler(RESOLVE)
    public static void resolvePirateHorde(ButtonInteractionEvent event, Game game, Player player) {
        Player neutral = game.getNeutral();
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        if (!player.isActivePlayer() || neutral == null || activeSystem == null || !hasNeutralShips(game, neutral)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "_Pirate Horde_ can no longer be resolved here.");
            return;
        }
        if (!SarcosaHeroHandler.beginNeutralControl(game, player)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "The neutral player is not available.");
            return;
        }
        game.setStoredValue(ACTIVE + player.getFaction(), "yes");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " resolved _Pirate Horde_. You may move neutral ships instead of"
                        + " your own, and their move values are doubled. Only up to 5 neutral ships may move.");
        TacticalActionOutputService.refreshButtonsAndMessageForChoosingTile(null, game, player);
        ButtonHelper.deleteMessage(event);
    }

    public static boolean isActive(Game game, Player player) {
        return game != null && player != null && "yes".equals(game.getStoredValue(ACTIVE + player.getFaction()));
    }

    public static boolean hasNeutralShips(Game game, Tile tile) {
        Player neutral = game.getNeutral();
        return neutral != null && hasNeutralShips(tile, neutral);
    }

    public static void completeTacticalAction(GenericInteractionCreateEvent event, Game game, Player player) {
        if (!isActive(game, player)) {
            return;
        }
        Player neutral = game.getNeutral();
        if (neutral != null) {
            for (Tile tile : game.getTileMap().values()) {
                if (!tile.isFracture() || containsOtherPlayersShips(game, tile, neutral)) continue;
                replenishNeutralShips(event, game, tile, neutral);
            }
        }
        game.removeStoredValue(ACTIVE + player.getFaction());
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " replenished eligible neutral ships for _Pirate Horde_.");
    }

    private static boolean hasNeutralShips(Game game, Player neutral) {
        return game.getTileMap().values().stream().anyMatch(tile -> hasNeutralShips(tile, neutral));
    }

    private static boolean hasNeutralShips(Tile tile, Player neutral) {
        return tile.getSpaceUnitHolder().getUnitKeys().stream()
                .filter(neutral::unitBelongsToPlayer)
                .map(neutral::getUnitFromUnitKey)
                .anyMatch(unit -> unit != null && unit.getIsShip());
    }

    private static boolean containsOtherPlayersShips(Game game, Tile tile, Player neutral) {
        return tile.getSpaceUnitHolder().getUnitKeys().stream()
                .anyMatch(unitKey -> isOtherPlayerShip(game, neutral, unitKey));
    }

    private static boolean isOtherPlayerShip(Game game, Player neutral, UnitKey unitKey) {
        Player owner = game.getPlayerFromColorOrFaction(unitKey.colorID());
        if (owner == null || owner == neutral) return false;
        UnitModel unit = owner.getUnitFromUnitKey(unitKey);
        return unit != null && unit.getIsShip();
    }

    private static void replenishNeutralShips(
            GenericInteractionCreateEvent event, Game game, Tile tile, Player neutral) {
        switch (tile.getTileID()) {
            case "fracture1" -> replenish(event, game, tile, neutral, UnitType.Cruiser, 2);
            case "fracture4" -> {
                replenish(event, game, tile, neutral, UnitType.Dreadnought, 2);
                replenish(event, game, tile, neutral, UnitType.Destroyer, 1);
            }
            case "fracture7" -> {
                replenish(event, game, tile, neutral, UnitType.Carrier, 1);
                replenish(event, game, tile, neutral, UnitType.Fighter, 4);
            }
            default -> {}
        }
    }

    private static void replenish(
            GenericInteractionCreateEvent event,
            Game game,
            Tile tile,
            Player neutral,
            UnitType unitType,
            int expectedCount) {
        int missing = expectedCount - tile.getSpaceUnitHolder().getUnitCount(unitType, neutral);
        if (missing > 0) {
            AddUnitService.addUnits(event, tile, game, neutral.getColor(), missing + " " + unitType.value);
        }
    }
}
