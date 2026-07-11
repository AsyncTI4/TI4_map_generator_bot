package ti4.service.planet;

import java.util.List;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.PlanetLocation;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;

/** Common effects which act on a filtered set of planets controlled by one player. */
@UtilityClass
public class PlanetEffectService {

    public static List<PlanetLocation> exhaustControlledPlanets(
            Player player, Game game, Predicate<PlanetLocation> eligible) {
        return game.streamControlledPlanetLocations(player)
                .filter(eligible)
                .peek(location -> player.exhaustPlanet(location.planet().getName()))
                .toList();
    }

    /** Sends a grammatically correct exhaustion summary to the player's cards-info thread. */
    public static void sendExhaustionSummary(
            Player player,
            Game game,
            List<PlanetLocation> exhausted,
            String exhaustedIntro,
            String noEligiblePlanetsMessage) {
        if (exhausted.isEmpty()) {
            if (noEligiblePlanetsMessage != null) {
                MessageHelper.sendMessageToChannel(
                        player.getCardsInfoThread(), player.getRepresentationUnfogged() + noEligiblePlanetsMessage);
            }
            return;
        }

        List<String> planetRepresentations = exhausted.stream()
                .map(location ->
                        Helper.getPlanetRepresentation(location.planet().getName(), game))
                .toList();
        String planets = planetRepresentations.size() == 1
                ? planetRepresentations.getFirst()
                : String.join(", ", planetRepresentations.subList(0, planetRepresentations.size() - 1))
                        + " and "
                        + planetRepresentations.getLast();
        String verb = planetRepresentations.size() == 1 ? " has" : " have";
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + exhaustedIntro + planets + verb + " been exhausted.");
    }
}
