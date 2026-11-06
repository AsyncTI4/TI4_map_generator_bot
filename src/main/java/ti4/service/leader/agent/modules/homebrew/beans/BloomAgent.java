package ti4.service.leader.agent.modules.homebrew.beans;

import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.bloom.BloomAbilityHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.thurviali.ThurvialiAbilityHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;
import ti4.service.unit.AddUnitService;

public final class BloomAgent extends TargetedAgent {

    static final String ID = "bloomagent";
    private static final String READY_PLANET = "bloomAgentReadyPlanet_";
    private static final String PLACE_INFANTRY = "bloomAgentPlaceInfantry_";

    public BloomAgent() {
        super(ID, "Oo'ka, the Bloom");
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Player target) {
        if (target == user) {
            return Optional.of("Oo'ka must target another player");
        }
        if (readyPlanetButtons(game, user, target).isEmpty()) {
            return Optional.of(target.getFactionEmojiOrColor() + " has no exhausted planets to ready");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        return AgentOutcome.of(Message.addressedTo(
                        target,
                        ", choose 1 exhausted planet to ready with " + use.agentNameFor(target) + ".",
                        readyPlanetButtons(use.game(), use.user(), target))
                .withPagedButtons());
    }

    @ButtonHandler(READY_PLANET)
    static void readyPlanet(ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String[] payload = buttonID.substring(READY_PLANET.length()).split("\\|", 2);
        Player owner = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        String planetName = payload.length == 2 ? payload[1] : null;
        if (owner == null || !isReadyablePlanet(target, planetName)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        target.refreshPlanet(planetName);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                target.getRepresentationNoPing() + " readied " + Helper.getPlanetRepresentation(planetName, game)
                        + " with Oo'ka, the Bloom Agent.");
        offerCoexistencePlacement(game, owner, target);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE_INFANTRY)
    static void placeInfantry(ButtonInteractionEvent event, Game game, Player owner, String buttonID) {
        String[] payload = buttonID.substring(PLACE_INFANTRY.length()).split("\\|", 2);
        Player target = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        String planetName = payload.length == 2 ? payload[1] : null;
        if (!isPlacementPlanet(game, target, planetName) || !hasInfantryReinforcement(game, owner)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile tile = game.getTileFromPlanet(planetName);
        String coexistenceFlag = game.getStoredValue("coexistFlag");
        game.setStoredValue("coexistFlag", "yes");
        try {
            AddUnitService.addUnits(event, tile, game, owner.getColor(), "1 infantry " + planetName);
        } finally {
            if (coexistenceFlag.isEmpty()) {
                game.removeStoredValue("coexistFlag");
            } else {
                game.setStoredValue("coexistFlag", coexistenceFlag);
            }
        }

        ButtonHelperAbilities.oceanBoundCheck(game);
        ThurvialiAbilityHandler.checkRadiantGrafting(game);
        BloomAbilityHandler.syncCoexistingAbilities(game, owner);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                owner.getRepresentationNoPing() + " placed 1 infantry into coexistence on "
                        + Helper.getPlanetRepresentation(planetName, game) + " with Oo'ka, the Bloom Agent.");
        ButtonHelper.deleteMessage(event);
    }

    private static void offerCoexistencePlacement(Game game, Player owner, Player target) {
        List<Button> buttons = placementButtons(game, owner, target);
        if (buttons.isEmpty()) {
            return;
        }
        String targetName = game.isFowMode() ? "another player" : target.getRepresentationNoPing();
        MessageHelper.sendMessageToChannelWithButtons(
                owner.getCorrectChannel(),
                owner.getRepresentationNoPing() + ", " + targetName
                        + " readied a planet with Oo'ka, the Bloom Agent. You may place 1 infantry into coexistence on a planet they control.",
                buttons);
    }

    private static List<Button> readyPlanetButtons(Game game, Player owner, Player target) {
        return target.getExhaustedPlanets().stream()
                .filter(planetName -> target.getPlanets().contains(planetName))
                .filter(planetName -> game.getUnitHolderFromPlanet(planetName) != null)
                .sorted()
                .map(planetName -> Buttons.green(
                        target.factionButtonChecker() + READY_PLANET + owner.getFaction() + "|" + planetName,
                        Helper.getPlanetRepresentation(planetName, game)))
                .toList();
    }

    private static List<Button> placementButtons(Game game, Player owner, Player target) {
        if (!hasInfantryReinforcement(game, owner)) {
            return List.of();
        }
        return target.getPlanets().stream()
                .filter(planetName -> isPlacementPlanet(game, target, planetName))
                .sorted()
                .map(planetName -> Buttons.green(
                        owner.factionButtonChecker() + PLACE_INFANTRY + target.getFaction() + "|" + planetName,
                        Helper.getPlanetRepresentation(planetName, game)))
                .toList();
    }

    private static boolean isReadyablePlanet(Player player, String planetName) {
        return planetName != null
                && player.getPlanets().contains(planetName)
                && player.getExhaustedPlanets().contains(planetName);
    }

    private static boolean isPlacementPlanet(Game game, Player target, String planetName) {
        Tile tile = planetName == null ? null : game.getTileFromPlanet(planetName);
        return target != null
                && planetName != null
                && tile != null
                && !tile.isHomeSystem(game)
                && target.getPlanets().contains(planetName)
                && game.getUnitHolderFromPlanet(planetName) != null;
    }

    private static boolean hasInfantryReinforcement(Game game, Player player) {
        return ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, UnitType.Infantry.value, true)
                < player.getUnitCap(UnitType.Infantry.value);
    }
}
