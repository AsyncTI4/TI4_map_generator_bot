package ti4.ai.expedition;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.HOME;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;
import static ti4.ai.AiTestGame.withContent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Player;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// The seat that claims the last slice puts Thunder's Edge on the board and, when several seats tie for the most
// slices, chooses which of them places the infantry: itself if it can, close to home and away from the aggressive.
class ThundersEdgeRulesTest extends BaseTi4Test {

    private AiTestGame test;
    private String near;
    private String far;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        test.aiIsActive("action");
        near = AiTestGame.neighbourOf(HOME);
        test.place("41", near);
        far = farFrom(HOME, 4);
        test.place("44", far);
    }

    private static String farFrom(String origin, int steps) {
        Map<String, Integer> distance = new HashMap<>(Map.of(origin, 0));
        ArrayDeque<String> queue = new ArrayDeque<>(List.of(origin));
        while (!queue.isEmpty()) {
            String position = queue.removeFirst();
            for (String next : MapPositionMapper.getAdjacentTilePositions(position)) {
                if ("x".equals(next) || distance.containsKey(next)) continue;
                distance.put(next, distance.get(position) + 1);
                if (distance.get(next) == steps) return next;
                queue.addLast(next);
            }
        }
        throw new AssertionError("no tile " + steps + " steps from " + origin);
    }

    private AiPrompt tiles(String... positions) {
        List<String> ids = new ArrayList<>();
        for (String position : positions) ids.add("placeThundersEdge_" + position);
        AiPrompt prompt = prompt("tiles", PromptSource.PUBLIC, NOW, ids.toArray(String[]::new));
        return withContent(prompt, test.nekro.getRepresentationNoPing() + ", please choose which system to place.");
    }

    @Test
    void startsPlacingWhenItClaimedTheLastSlice() {
        AiPrompt start = prompt("start", PromptSource.PUBLIC, NOW, "FFCC_nekro_placeThundersEdge");

        assertThat(pressedId(ThundersEdgeRules.next(test.context(start)).orElseThrow()))
                .isEqualTo("FFCC_nekro_placeThundersEdge");
    }

    @Test
    void doesNotStartAnotherSeatsPlacement() {
        AiPrompt start = prompt("start", PromptSource.PUBLIC, NOW, "FFCC_sol_placeThundersEdge");

        assertThat(ThundersEdgeRules.next(test.context(start))).isEmpty();
    }

    @Test
    void placesItBesideItsHomeRatherThanFarAway() {
        assertThat(pressedId(
                        ThundersEdgeRules.next(test.context(tiles(far, near))).orElseThrow()))
                .isEqualTo("placeThundersEdge_" + near);
    }

    // A tile next to a rival's home is where that rival's fleets arrive first, so a quieter tile of the same
    // distance is taken instead.
    @Test
    void keepsAwayFromTheRivalsHome() {
        test.solHomeBeside(near);
        String quiet = AiTestGame.neighbourOf(HOME);
        for (String position : MapPositionMapper.getAdjacentTilePositions(HOME)) {
            if (!"x".equals(position) && !position.equals(near) && test.game.getTileByPosition(position) == null) {
                quiet = position;
                break;
            }
        }
        test.place("46", quiet);

        assertThat(pressedId(
                        ThundersEdgeRules.next(test.context(tiles(near, quiet))).orElseThrow()))
                .isEqualTo("placeThundersEdge_" + quiet);
    }

    @Test
    void leavesAnotherSeatsTileChoiceAlone() {
        AiPrompt others = withContent(
                prompt("tiles", PromptSource.PUBLIC, NOW, "placeThundersEdge_" + near),
                test.sol.getRepresentationNoPing() + ", please choose which system to place.");

        assertThat(ThundersEdgeRules.next(test.context(others))).isEmpty();
    }

    // Tied for the most slices, the seat that claimed the last one picks itself for the infantry.
    @Test
    void picksItselfAmongTheTiedSeats() {
        AiPrompt recipients = prompt(
                "recipients",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_placeThundersEdge_" + near + "_sol",
                "FFCC_nekro_placeThundersEdge_" + near + "_nekro");

        assertThat(pressedId(ThundersEdgeRules.next(test.context(recipients)).orElseThrow()))
                .isEqualTo("FFCC_nekro_placeThundersEdge_" + near + "_nekro");
    }

    // Not among the tied seats, it gives the infantry to the one that threatens it least: not the points leader.
    @Test
    void picksTheLeastThreateningRivalWhenItIsNotTied() {
        Player mahact = test.addSeat("200000000000000009", "mahact", "green");
        test.points(test.sol, 5);
        test.points(mahact, 1);
        AiPrompt recipients = prompt(
                "recipients",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_placeThundersEdge_" + near + "_sol",
                "FFCC_nekro_placeThundersEdge_" + near + "_mahact");

        assertThat(pressedId(ThundersEdgeRules.next(test.context(recipients)).orElseThrow()))
                .isEqualTo("FFCC_nekro_placeThundersEdge_" + near + "_mahact");
    }
}
