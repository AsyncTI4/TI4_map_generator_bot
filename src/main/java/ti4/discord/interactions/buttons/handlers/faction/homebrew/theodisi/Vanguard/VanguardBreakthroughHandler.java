package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Vanguard;

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
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class VanguardBreakthroughHandler {
    private static final String TRAINING_DUMMIES = "vanguardbt";
    private static final String TRAINING_DUMMIES_TG = "trainingDummiesTg_";
    private static final String USE_TRAINING_DUMMIES_TG = "useTrainingDummiesTg";
    private static final String PLACE_TRAINING_DUMMIES_INFANTRY = "placeTrainingDummiesInfantry_";

    public static void offerTrainingDummiesGroundCombatReward(Player player) {
        if (!player.hasUnlockedBreakthrough(TRAINING_DUMMIES)) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationNoPing()
                        + ", if you win this ground combat, you may gain 2 trade goods due to _Training Dummies_.",
                List.of(
                        Buttons.green(
                                player.factionButtonChecker() + USE_TRAINING_DUMMIES_TG,
                                "Gain 2 Trade Goods (On Win)",
                                FactionEmojis.vanguard),
                        Buttons.red("deleteButtons", "Decline")));
    }

    @ButtonHandler(USE_TRAINING_DUMMIES_TG)
    public static void useTrainingDummiesGroundCombatReward(ButtonInteractionEvent event, Game game, Player player) {
        String key = TRAINING_DUMMIES_TG + player.getFaction();
        if (!player.hasUnlockedBreakthrough(TRAINING_DUMMIES)
                || !game.getStoredValue(key).isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.setStoredValue(key, "used");
        String message = player.getRepresentationNoPing() + " gained 2 trade goods " + player.gainTG(2)
                + " due to _Training Dummies_.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 2);
        ButtonHelper.deleteMessage(event);
    }

    public static void offerTrainingDummiesInfantry(Game game, Player player) {
        if (!player.hasUnlockedBreakthrough(TRAINING_DUMMIES)) {
            return;
        }
        List<Button> buttons = getTrainingDummiesInfantryButtons(game, player);
        if (buttons.isEmpty()) {
            return;
        }
        List<Button> extraButtons = List.of(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                getTrainingDummiesInfantryMessage(player),
                NewStuffHelper.buttonPagination(
                        buttons,
                        extraButtons,
                        player.factionButtonChecker() + PLACE_TRAINING_DUMMIES_INFANTRY,
                        25,
                        0,
                        false));
    }

    private static List<Button> getTrainingDummiesInfantryButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (!tile.isFracture()
                        && planet.getUnitKeysForPlayer(player).stream()
                                .map(player::getUnitFromUnitKey)
                                .anyMatch(unit -> unit != null && unit.getIsGroundForce())) {
                    buttons.add(Buttons.green(
                            player.factionButtonChecker() + PLACE_TRAINING_DUMMIES_INFANTRY + tile.getPosition() + "|"
                                    + planet.getName(),
                            "Place Infantry on " + Helper.getPlanetRepresentation(planet.getName(), game),
                            FactionEmojis.vanguard));
                }
            }
        }
        return buttons;
    }

    private static String getTrainingDummiesInfantryMessage(Player player) {
        return player.getRepresentationNoPing()
                + ", you spent a strategy token. You may place 1 neutral infantry into coexistence on a non-fracture planet containing your ground forces due to _Training Dummies_.";
    }

    @ButtonHandler(PLACE_TRAINING_DUMMIES_INFANTRY)
    public static void placeTrainingDummiesInfantry(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = getTrainingDummiesInfantryButtons(game, player);
        List<Button> extraButtons = List.of(Buttons.red("deleteButtons", "Decline"));
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                extraButtons,
                getTrainingDummiesInfantryMessage(player),
                player.factionButtonChecker() + PLACE_TRAINING_DUMMIES_INFANTRY,
                buttonID)) {
            return;
        }
        String[] payload =
                buttonID.substring(PLACE_TRAINING_DUMMIES_INFANTRY.length()).split("\\|", 2);
        Tile tile = payload.length == 2 ? game.getTileByPosition(payload[0]) : null;
        Planet planet = tile == null || payload.length != 2
                ? null
                : tile.getUnitHolders().get(payload[1]) instanceof Planet p ? p : null;
        if (planet == null
                || tile.isFracture()
                || planet.getUnitKeysForPlayer(player).stream()
                        .map(player::getUnitFromUnitKey)
                        .noneMatch(unit -> unit != null && unit.getIsGroundForce())) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.setStoredValue("coexistFlag", "yes");
        AddUnitService.addUnits(event, tile, game, game.getNeutral().getColor(), "1 infantry " + planet.getName());
        game.removeStoredValue("coexistFlag");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed 1 neutral infantry on " + planet.getRepresentation(game)
                        + " due to _Training Dummies_.");
        ButtonHelper.deleteMessage(event);
    }

    public static void clearTrainingDummiesState(Game game) {
        game.getStoredValueMap().keySet().stream()
                .filter(key -> key.startsWith(TRAINING_DUMMIES_TG))
                .toList()
                .forEach(game::removeStoredValue);
    }
}
