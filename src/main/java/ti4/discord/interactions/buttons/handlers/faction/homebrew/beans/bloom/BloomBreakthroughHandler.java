package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.bloom;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.thurviali.ThurvialiAbilityHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class BloomBreakthroughHandler {
    private static final String BREAKTHROUGH = "bloombt";
    private static final String PRODUCE_INFANTRY = "bloomBtProduceInfantry_";

    public static void addMycelialBridgesProductionButtons(
            GenericInteractionCreateEvent event, List<Button> buttons, Game game, Player player, Tile productionTile) {
        if (!player.hasUnlockedBreakthrough(BREAKTHROUGH)) {
            return;
        }
        int initialButtonCount = buttons.size();
        for (String planetName :
                game.getPlanetsPlayerIsCoexistingOn(player).stream().sorted().toList()) {
            Tile targetTile = game.getTileFromPlanet(planetName);
            if (targetTile == null || game.getUnitHolderFromPlanet(planetName) == null) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + PRODUCE_INFANTRY + productionTile.getPosition() + "|" + planetName,
                    "Produce 1 Infantry on " + Helper.getPlanetRepresentation(planetName, game),
                    FactionEmojis.bloom));
        }
        if (buttons.size() > initialButtonCount) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + " may use _Mycelial Bridges_ to place up to 2 produced infantry into coexistence on planets they are coexisting on."
                            + "\n-# Yes, it says produce on the buttons but you are placing them after producing. This was just the easiest way for the bot to handle this.");
        }
    }

    @ButtonHandler(PRODUCE_INFANTRY)
    public static void produceInfantryIntoCoexistence(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(PRODUCE_INFANTRY.length()).split("\\|", 2);
        Tile productionTile = payload.length == 2 ? game.getTileByPosition(payload[0]) : null;
        String planetName = payload.length == 2 ? payload[1] : null;
        Tile targetTile = planetName == null ? null : game.getTileFromPlanet(planetName);
        Planet targetPlanet = planetName == null ? null : game.getUnitHolderFromPlanet(planetName);
        if (!player.hasUnlockedBreakthrough(BREAKTHROUGH)
                || productionTile == null
                || targetTile == null
                || targetPlanet == null
                || !game.getPlanetsPlayerIsCoexistingOn(player).contains(planetName)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }

        String coexistenceFlag = game.getStoredValue("coexistFlag");
        game.setStoredValue("coexistFlag", "yes");
        try {
            AddUnitService.addUnits(event, targetTile, game, player.getColor(), "1 infantry " + planetName);
        } finally {
            if (coexistenceFlag.isEmpty()) {
                game.removeStoredValue("coexistFlag");
            } else {
                game.setStoredValue("coexistFlag", coexistenceFlag);
            }
        }
        player.produceUnit("gf_" + targetTile.getPosition() + "_" + planetName);
        BloomAbilityHandler.syncCoexistingAbilities(game, player);
        ButtonHelperAbilities.oceanBoundCheck(game);
        ThurvialiAbilityHandler.checkRadiantGrafting(game);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed 1 infantry into coexistence on "
                        + Helper.getPlanetRepresentation(planetName, game) + " with _Mycelial Bridges_.");
        event.getMessage()
                .editMessage(Helper.buildProducedUnitsMessage(player, game))
                .queue();
    }
}
