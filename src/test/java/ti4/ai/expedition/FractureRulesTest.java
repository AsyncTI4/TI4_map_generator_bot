package ti4.ai.expedition;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.HOME;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.helpers.Constants;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// Unlocking a breakthrough offers a roll for The Fracture, and a Fracture that enters offers ingress tokens to place:
// the AI rolls at once and places them near itself and away from the rivals that fight.
class FractureRulesTest extends BaseTi4Test {

    private static final String DONE = "Done Resolving";

    private AiTestGame test;
    private String near;
    private String far;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        test.aiIsActive("action");
        near = AiTestGame.neighbourOf(HOME);
        far = farFrom(HOME, 4);
        test.place("22", near);
        test.place("19", far);
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

    private AiPrompt ingress(String messageId, int allowed, String... positions) {
        List<String> ids = new java.util.ArrayList<>();
        List<String> labels = new java.util.ArrayList<>();
        for (String position : positions) {
            ids.add("FFCC_nekro_addIngressToken_" + position + "_" + allowed);
            labels.add("Add Ingress To " + position);
        }
        ids.add("deleteButtons");
        labels.add(DONE);
        return prompt(messageId, PromptSource.PUBLIC, NOW, ids, labels);
    }

    @Test
    void rollsForTheFractureAtOnce() {
        AiPrompt roll = prompt("roll", PromptSource.PUBLIC, NOW, "FFCC_nekro_rollFracture_nekrobt");

        assertThat(pressedId(FractureRules.roll(test.context(roll)).orElseThrow()))
                .isEqualTo("FFCC_nekro_rollFracture_nekrobt");
    }

    @Test
    void doesNotRollForAnotherSeatsBreakthrough() {
        AiPrompt roll = prompt("roll", PromptSource.PUBLIC, NOW, "FFCC_sol_rollFracture_solbt");

        assertThat(FractureRules.roll(test.context(roll))).isEmpty();
    }

    @Test
    void placesTheIngressTokenInTheSystemNearestItself() {
        AiPrompt prompt = ingress("ingress", 1, far, near);

        assertThat(pressedId(FractureRules.placeIngress(test.context(prompt)).orElseThrow()))
                .isEqualTo("FFCC_nekro_addIngressToken_" + near + "_1");
    }

    // Three tokens a colour are allowed: the best three are pressed one by one, never a fourth, then the prompt is
    // dismissed.
    @Test
    void placesNoMoreThanThePromptAllowsThenDismissesIt() {
        String second = AiTestGame.neighbourOf(near);
        test.place("27", second);
        AiPrompt all = ingress("ingress", 2, far, near, second);

        String first = pressedId(FractureRules.placeIngress(test.context(all)).orElseThrow());
        assertThat(first).contains("addIngressToken_");
        Set<String> pressed = Set.of(key(all, first));

        AiPrompt remaining = ingress("ingress", 2, far, second);
        String next = pressedId(FractureRules.placeIngress(test.contextFor(test.nekro, pressed, NOW, remaining))
                .orElseThrow());
        assertThat(next).contains("addIngressToken_");
        Set<String> twice = Set.of(key(all, first), key(all, next));

        AiPrompt left = ingress("ingress", 2, far);
        assertThat(pressedId(FractureRules.placeIngress(test.contextFor(test.nekro, twice, NOW, left))
                        .orElseThrow()))
                .isEqualTo("deleteButtons");
    }

    @Test
    void skipsASystemThatAlreadyHoldsAnIngressToken() {
        test.game.getTileByPosition(near).addToken(Constants.TOKEN_INGRESS, "space");
        AiPrompt prompt = ingress("ingress", 1, far, near);

        assertThat(pressedId(FractureRules.placeIngress(test.context(prompt)).orElseThrow()))
                .isEqualTo("FFCC_nekro_addIngressToken_" + far + "_1");
    }

    // Two systems equally close: the one beside a rival's home is a way in for that rival's fleets.
    @Test
    void staysAwayFromTheSystemBesideAnotherHome() {
        String borderPosition = near;
        test.solHomeBeside(borderPosition);
        String quiet = quietNeighbour(borderPosition);
        test.place("27", quiet);
        AiPrompt prompt = ingress("ingress", 1, borderPosition, quiet);

        assertThat(pressedId(FractureRules.placeIngress(test.context(prompt)).orElseThrow()))
                .isEqualTo("FFCC_nekro_addIngressToken_" + quiet + "_1");
    }

    private String quietNeighbour(String borderPosition) {
        List<String> nearSol = MapPositionMapper.getAdjacentTilePositions(
                test.sol.getHomeSystemTile() == null
                        ? borderPosition
                        : test.sol.getHomeSystemTile().getPosition());
        return MapPositionMapper.getAdjacentTilePositions(HOME).stream()
                .filter(position -> !"x".equals(position) && !position.equals(borderPosition))
                .filter(position -> test.game.getTileByPosition(position) == null)
                .filter(position -> !nearSol.contains(position))
                .filter(position -> !MapPositionMapper.getAdjacentTilePositions(borderPosition)
                        .contains(position))
                .findFirst()
                .orElseThrow();
    }

    private static String key(AiPrompt prompt, String customId) {
        return prompt.messageId() + "|" + customId;
    }
}
