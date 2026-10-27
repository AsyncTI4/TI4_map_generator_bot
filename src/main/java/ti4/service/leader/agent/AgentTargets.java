package ti4.service.leader.agent;

import java.util.Optional;
import java.util.function.Function;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
public class AgentTargets {

    public record PlanetInSystem(Tile system, String planet) {}

    public static Optional<Player> player(Game game, String payload) {
        if (StringUtils.isEmpty(payload)) {
            return Optional.empty();
        }
        Player wholePayloadMatch = game.getPlayerFromColorOrFaction(payload);
        if (wholePayloadMatch != null) {
            return Optional.of(wholePayloadMatch);
        }
        return Optional.ofNullable(game.getPlayerFromColorOrFaction(StringUtils.substringBefore(payload, "_")));
    }

    public static Optional<Player> playerOrUser(Game game, Player user, String payload) {
        if (StringUtils.isEmpty(payload)) {
            return Optional.of(user);
        }
        return player(game, payload);
    }

    public static Optional<PlanetInSystem> planetInSystem(Game game, String payload) {
        Tile system = game.getTileByPosition(StringUtils.substringBefore(payload, "_"));
        String planet = StringUtils.substringAfter(payload, "_");
        if (system == null || planet.isEmpty() || system.getUnitHolderFromPlanet(planet) == null) {
            return Optional.empty();
        }
        return Optional.of(new PlanetInSystem(system, planet));
    }

    public static <T> Optional<T> playerOrPlanetInSystem(
            Game game,
            Player user,
            String payload,
            Function<Player, T> forPlayer,
            Function<PlanetInSystem, T> forPlanet) {
        if (StringUtils.isEmpty(payload)) {
            return Optional.of(forPlayer.apply(user));
        }
        Player wholePayloadMatch = game.getPlayerFromColorOrFaction(payload);
        if (wholePayloadMatch != null) {
            return Optional.of(forPlayer.apply(wholePayloadMatch));
        }
        Optional<PlanetInSystem> planet = planetInSystem(game, payload);
        if (planet.isPresent()) {
            return planet.map(forPlanet);
        }
        return player(game, payload).map(forPlayer);
    }
}
