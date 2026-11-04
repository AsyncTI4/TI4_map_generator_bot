package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.bloom;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Helper;
import ti4.helpers.adjacency.Adjacency;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class BloomTechHandler {
    private static final String HYPHAL_MIGRATION_TECH = "bebloomg";

    public static boolean canZygomyceteIIMoveThroughShips(Game game, Player player) {
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        return activeSystem != null
                && activeSystem.getPlanetUnitHolders().stream()
                        .map(Planet::getName)
                        .anyMatch(game.getPlanetsPlayerIsCoexistingOn(player)::contains);
    }

    public static void offerHyphalMigration(Game game, Player committer, Tile tile, String planetName) {
        for (Player bloomPlayer : game.getRealPlayers()) {
            if (!canResolveHyphalMigration(game, bloomPlayer, committer, tile, planetName)) {
                continue;
            }

            String committerName = game.isFowMode() ? "another player" : committer.getRepresentationNoPing();
            MessageHelper.sendMessageToChannelWithButtons(
                    bloomPlayer.getCardsInfoThread(),
                    bloomPlayer.getRepresentationNoPing() + ", " + committerName + " committed units to "
                            + Helper.getPlanetRepresentation(planetName, game)
                            + ". You may exhaust _Hyphal Migration_ to place 1 infantry into coexistence there.",
                    List.of(
                            Buttons.green(
                                    bloomPlayer.factionButtonChecker() + "exhaustTech_" + HYPHAL_MIGRATION_TECH
                                            + "_" + committer.getFaction() + "|" + tile.getPosition() + "|"
                                            + planetName,
                                    "Exhaust Hyphal Migration"),
                            Buttons.DECLINE_DELETE_BUTTONS));
        }
    }

    public static boolean canResolveHyphalMigration(
            Game game, Player bloomPlayer, Player committer, Tile destination, String planetName) {
        if (bloomPlayer == null
                || committer == null
                || bloomPlayer == committer
                || destination == null
                || !bloomPlayer.hasTechReady(HYPHAL_MIGRATION_TECH)) {
            return false;
        }
        return isHyphalMigrationTarget(game, bloomPlayer, committer, destination, planetName);
    }

    public static void resolveHyphalMigration(
            ButtonInteractionEvent event, Game game, Player bloomPlayer, String hyphalMigrationTarget) {
        String[] payload = hyphalMigrationTarget.split("\\|", 3);
        Player committer = payload.length == 3 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        Tile destination = payload.length == 3 ? game.getTileByPosition(payload[1]) : null;
        String planetName = payload.length == 3 ? payload[2] : null;
        if (!isHyphalMigrationTarget(game, bloomPlayer, committer, destination, planetName)) {
            return;
        }

        String coexistenceFlag = game.getStoredValue("coexistFlag");
        game.setStoredValue("coexistFlag", "yes");
        try {
            AddUnitService.addUnits(event, destination, game, bloomPlayer.getColor(), "1 infantry " + planetName);
        } finally {
            if (coexistenceFlag.isEmpty()) {
                game.removeStoredValue("coexistFlag");
            } else {
                game.setStoredValue("coexistFlag", coexistenceFlag);
            }
        }

        BloomAbilityHandler.syncCoexistingAbilities(game, bloomPlayer);
        MessageHelper.sendMessageToChannel(
                bloomPlayer.getCorrectChannel(),
                bloomPlayer.getRepresentationNoPing() + " placed 1 infantry into coexistence on "
                        + Helper.getPlanetRepresentation(planetName, game)
                        + " with _Hyphal Migration_ after another player committed units there.");
    }

    private static boolean isHyphalMigrationTarget(
            Game game, Player bloomPlayer, Player committer, Tile destination, String planetName) {
        if (bloomPlayer == null
                || committer == null
                || bloomPlayer == committer
                || destination == null
                || destination.getUnitHolderFromPlanet(planetName) == null) {
            return false;
        }
        return game.getTileMap().values().stream()
                .filter(tile -> !tile.isHomeSystem(game))
                .filter(tile ->
                        tile.getPlanetUnitHolders().stream().anyMatch(planet -> planet.hasGroundForces(bloomPlayer)))
                .anyMatch(tile -> tile.getPosition().equals(destination.getPosition())
                        || Adjacency.of(game, bloomPlayer)
                                .forEffects()
                                .from(tile.getPosition())
                                .contains(destination.getPosition()));
    }
}
