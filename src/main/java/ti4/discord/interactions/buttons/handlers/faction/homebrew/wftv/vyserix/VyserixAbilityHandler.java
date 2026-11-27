package ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.vyserix;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class VyserixAbilityHandler {

    private static final String TRIANGULATION_PLANET = "vyserixTriangulationPlanet";

    public static void onGainPlanetWithTechSpec(
            Player player, Game game, GenericInteractionCreateEvent event, Tile tile, UnitHolder unitHolder) {
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " placed 1 PDS on "
                        + Helper.getPlanetRepresentation(unitHolder.getName(), game)
                        + " due to the Veiled Ember Forge ability. This is optional but was done automatically.");
        AddUnitService.addUnits(event, tile, game, player.getColor(), "pds " + unitHolder.getName());
    }

    public static void ignoreScatteredLegionsOn(Game game, String planetId) {
        game.setStoredValue(TRIANGULATION_PLANET, planetId);
    }

    public static void checkScatteredLegions(Game game, Player player) {
        Tile home = player.getHomeSystemTile();
        String triangulationPlanet = game.getStoredValue(TRIANGULATION_PLANET);
        List<String> overLimit = new ArrayList<>();
        for (String planetId : player.getPlanets()) {
            Tile tile = game.getTileFromPlanet(planetId);
            if (tile == null || tile.equals(home) || planetId.equals(triangulationPlanet)) {
                continue;
            }
            Planet planet = game.getPlanetsInfo().get(planetId);
            if (planet == null) {
                continue;
            }
            int limit = Math.max(planet.getResources(), planet.getInfluence());
            int groundForces =
                    planet.getUnitCount(UnitType.Infantry, player) + planet.getUnitCount(UnitType.Mech, player);
            if (groundForces > limit) {
                overLimit.add(Helper.getPlanetRepresentation(planetId, game) + " (" + groundForces + "/" + limit + ")");
            }
        }
        if (overLimit.isEmpty()) {
            return;
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", a reminder that _Scattered Legions_ limits your ground"
                        + " forces on a non-home planet to the greater of its resources or influence. Move the"
                        + " excess on these planets to your home system, or return them to reinforcements: "
                        + String.join(", ", overLimit) + ".");
    }
}
