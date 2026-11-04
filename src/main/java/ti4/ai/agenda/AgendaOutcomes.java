package ti4.ai.agenda;

import java.util.List;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.model.AgendaModel;
import ti4.model.Mapper;

@UtilityClass
public class AgendaOutcomes {

    private static final List<String> TRAITS = List.of("cultural", "industrial", "hazardous");
    private static final String COVERT = "covert";

    public static List<String> of(Game game) {
        String agenda = AgendaPolicy.currentAgenda(game);
        AgendaModel model = agenda.isBlank() ? null : Mapper.getAgenda(agenda);
        if (model == null || model.getTarget() == null) return List.of();
        String target = outcomeTarget(game, agenda, model).toLowerCase();
        if (target.contains("for/against")) return List.of("for", "against");
        if (target.contains("elect player")) {
            return game.getRealPlayers().stream().map(Player::getFaction).toList();
        }
        if (target.contains("elect law")) return List.copyOf(game.getLaws().keySet());
        if (target.contains("secret objective")) return scoredSecrets(game);
        if (target.contains("strategy card")) return strategyCards(game);
        if (target.contains("planet")) return electablePlanets(game, agenda, target);
        return List.of();
    }

    private static String outcomeTarget(Game game, String agenda, AgendaModel model) {
        if (!COVERT.equals(agenda)) return model.getTarget();
        String[] parts = StringUtils.split(game.getCurrentAgendaInfo(), "_");
        return parts.length > 1 ? parts[1] : "";
    }

    private static List<String> scoredSecrets(Game game) {
        return game.getRealPlayers().stream()
                .flatMap(player -> player.getSecretsScored().keySet().stream())
                .distinct()
                .toList();
    }

    private static List<String> strategyCards(Game game) {
        return game.getStrategyCardSet().getStrategyCardModels().stream()
                .map(card -> String.valueOf(card.getInitiative()))
                .toList();
    }

    private static List<String> electablePlanets(Game game, String agenda, String target) {
        return game.getRealPlayers().stream()
                .flatMap(player -> player.getPlanets().stream())
                .filter(planet -> electable(game, agenda, target, planet))
                .toList();
    }

    private static boolean electable(Game game, String agenda, String target, String planetName) {
        Planet planet = game.getPlanetsInfo().get(planetName);
        if (planet == null || planet.isSpaceStation(game) || !AgendaPolicy.hasRequiredTrait(agenda, planet)) {
            return false;
        }
        for (String trait : TRAITS) {
            if (target.contains(trait) && !planet.getPlanetTypes().contains(trait)) return false;
        }
        if (target.contains("non-home")) {
            Tile tile = game.getTileFromPlanet(planetName);
            if (tile == null || tile.isHomeSystem(game) || tile.isMecatol(game)) return false;
        }
        return true;
    }
}
