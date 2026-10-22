package ti4.image.map;

import java.util.List;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.VeiledHeartService;

/**
 * Golden-harness mutation for the player-area veiled card fields, which no corpus save reaches: it switches a Twilight's
 * Fall game into Veiled Heart mode and gives the first two real players veiled cards of every type, so the VEILED
 * rectangles of the ability, unit, genome and paradigm groups are drawn (one, two and no cards per type).
 */
final class VeiledHeartVariety {

    private VeiledHeartVariety() {}

    static void apply(Game game) {
        game.setVeiledHeartMode(true);
        List<Player> players = game.getRealPlayers();
        Player first = players.get(0);
        Player second = players.get(1);

        for (String card :
                List.of("tf-mitosis", "tf-stymie", "tf-dawncrusher", "experimentalagent", "brilliancehero")) {
            VeiledHeartService.addVeiledCard(first, card);
        }
        for (String card : List.of("tf-armada", "hyperagent", "researchagent", "devourhero")) {
            VeiledHeartService.addVeiledCard(second, card);
        }
    }
}
