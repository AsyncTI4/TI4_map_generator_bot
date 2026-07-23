package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Kryxos;

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
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class KryxosAbilityHandler {
    private static final String BATTLE_TESTED_DESIGNS = "battle_tested_designs";
    private static final String EXHAUST_PLANET = "battleTestedDesignsExhaust_";
    private static final String SELECT_SYSTEM = "battleTestedDesignsSystem_";
    private static final String PLACE_UNIT = "battleTestedDesignsPlace_";

    public static void offerBattleTestedDesigns(
            GenericInteractionCreateEvent event, Game game, Player player, TechnologyModel technology) {
        if (game == null
                || player == null
                || technology == null
                || !technology.isUnitUpgrade()
                || !player.hasAbility(BATTLE_TESTED_DESIGNS)
                || Mapper.getUnitModelByTechUpgrade(technology.getAlias()) == null) {
            return;
        }

        List<Button> buttons = player.getReadiedPlanets().stream()
                .map(planet -> Buttons.green(
                        player.factionButtonChecker() + EXHAUST_PLANET + technology.getAlias() + "|" + planet,
                        Helper.getPlanetRepresentation(planet, game)))
                .toList();
        if (buttons.isEmpty()) {
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", you may exhaust 1 planet for _Battle-Tested Designs_ to ignore 1 prerequisite of "
                        + technology.getName() + ", then produce 1 unit of that type for 1 less.",
                buttons);
    }

    @ButtonHandler(EXHAUST_PLANET)
    public static void exhaustPlanetForBattleTestedDesigns(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(EXHAUST_PLANET.length()).split("\\|", 2);
        if (parts.length != 2
                || game == null
                || player == null
                || !player.hasAbility(BATTLE_TESTED_DESIGNS)
                || !player.getReadiedPlanets().contains(parts[1])) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        TechnologyModel technology = Mapper.getTech(parts[0]);
        if (technology == null || !technology.isUnitUpgrade() || !player.hasTech(parts[0])) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.exhaustPlanet(parts[1]);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " exhausted " + Helper.getPlanetRepresentation(parts[1], game)
                        + " for _Battle-Tested Designs_, ignoring 1 prerequisite of " + technology.getName() + ".");

        List<Button> buttons = getEligibleProductionTiles(game, player).stream()
                .map(tile -> Buttons.green(
                        player.factionButtonChecker()
                                + SELECT_SYSTEM
                                + technology.getAlias()
                                + "|"
                                + parts[1]
                                + "|"
                                + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation()
                            + " has no eligible system in which to produce with _Battle-Tested Designs_.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", choose the system in which to resolve _Battle-Tested Designs_:",
                buttons);
    }

    @ButtonHandler(SELECT_SYSTEM)
    public static void selectBattleTestedDesignsSystem(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(SELECT_SYSTEM.length()).split("\\|", 3);
        if (parts.length != 3 || game == null || player == null || !player.hasAbility(BATTLE_TESTED_DESIGNS)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        TechnologyModel technology = Mapper.getTech(parts[0]);
        Tile tile = game.getTileByPosition(parts[2]);
        UnitModel upgradedUnit = Mapper.getUnitModelByTechUpgrade(parts[0]);
        if (technology == null
                || !technology.isUnitUpgrade()
                || !player.hasTech(parts[0])
                || !player.getExhaustedPlanets().contains(parts[1])
                || tile == null
                || upgradedUnit == null
                || !getEligibleProductionTiles(game, player).contains(tile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (upgradedUnit.getIsShip()) {
            placeBattleTestedUnit(event, game, player, upgradedUnit, tile, "space");
            return;
        }

        List<Button> buttons = new java.util.ArrayList<>();
        for (String planet : tile.getPlanetUnitHolders().stream()
                .map(planet -> planet.getName())
                .toList()) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + PLACE_UNIT + technology.getAlias() + "|" + parts[1] + "|"
                            + tile.getPosition() + "|" + planet,
                    Helper.getPlanetRepresentation(planet, game)));
        }
        buttons.add(Buttons.green(
                player.factionButtonChecker() + PLACE_UNIT + technology.getAlias() + "|" + parts[1] + "|"
                        + tile.getPosition() + "|space",
                "Place in space"));

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", choose where to produce the " + upgradedUnit.getBaseType()
                        + " with _Battle-Tested Designs_. Its cost is reduced by 1.",
                buttons);
    }

    @ButtonHandler(PLACE_UNIT)
    public static void placeBattleTestedUnit(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(PLACE_UNIT.length()).split("\\|", 4);
        if (parts.length != 4 || game == null || player == null || !player.hasAbility(BATTLE_TESTED_DESIGNS)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        TechnologyModel technology = Mapper.getTech(parts[0]);
        UnitModel upgradedUnit = Mapper.getUnitModelByTechUpgrade(parts[0]);
        Tile tile = game.getTileByPosition(parts[2]);
        if (technology == null
                || upgradedUnit == null
                || !technology.isUnitUpgrade()
                || !player.hasTech(parts[0])
                || !player.getExhaustedPlanets().contains(parts[1])
                || tile == null
                || !getEligibleProductionTiles(game, player).contains(tile)
                || upgradedUnit.getIsShip()
                || (!"space".equals(parts[3]) && !tile.getUnitHolders().containsKey(parts[3]))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        placeBattleTestedUnit(event, game, player, upgradedUnit, tile, parts[3]);
    }

    private static void placeBattleTestedUnit(
            ButtonInteractionEvent event, Game game, Player player, UnitModel upgradedUnit, Tile tile, String holder) {
        String unit = "1 " + upgradedUnit.getAsyncId();
        if (!"space".equals(holder)) {
            unit += " " + holder;
        }
        AddUnitService.addUnits(event, tile, game, player.getColor(), unit);

        int discountedCost = Math.max(0, (int) Math.ceil(upgradedUnit.getCost()) - 1);
        game.setStoredValue("producedUnitCostFor" + player.getFaction(), Integer.toString(discountedCost));
        player.setTotalExpenses(player.getTotalExpenses() + discountedCost);
        ButtonHelper.deleteMessage(event);

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " produced 1 " + upgradedUnit.getBaseType()
                        + " with _Battle-Tested Designs_.");
        List<Button> paymentButtons =
                new java.util.ArrayList<>(ButtonHelper.getExhaustButtonsWithTG(game, player, "res"));
        paymentButtons.add(Buttons.red("deleteButtons_battleTestedDesigns", "Done Exhausting Planets"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + ", please choose the planets you wish to exhaust to pay a cost of "
                        + discountedCost + " for _Battle-Tested Designs_ (-1).",
                paymentButtons);
    }

    private static List<Tile> getEligibleProductionTiles(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> FoWHelper.playerHasUnitsInSystem(player, tile))
                .filter(tile -> !FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game))
                .toList();
    }
}
