package ti4.ai.naalu;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

// Z'eu (Thunder's Edge) is offered in the seat's private thread after any player's command token is placed. The seat
// (the test game's "nekro" seat, given the agent) uses it only on its own token, when freeing that system lets its
// next tactical action do clearly more, or at once on its own activation; every other offer is declined so it does not
// linger.
class ZeuAgentRulesTest extends BaseTi4Test {

    private static final long STALE = NOW - 3_600_000L;
    private static final String FAR = "304";

    private AiTestGame test;
    private Tile home;
    private String lodor;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(2);
        home = test.nekroHome();
        lodor = test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME)).getPosition();
        test.nekro.addLeader(ZeuAgentRules.AGENT);
        test.nekro.setTacticalCC(3);
        test.nekro.setStrategicCC(2);
        test.nekro.setFleetCC(3);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Infantry, 2);
        test.units(home, "space", test.nekro, UnitType.Destroyer, 1);
        test.isActive(test.sol, "action");
    }

    private static String use(String position, String owner) {
        return "FFCC_nekro_exhaustAgent_" + ZeuAgentRules.AGENT + "_" + position + "_" + owner;
    }

    private static AiPrompt offer(long created, String position, String owner) {
        return prompt(
                "zeu-" + position + owner + created,
                PromptSource.AI_THREAD,
                created,
                use(position, owner),
                "deleteButtons");
    }

    private void tokenAtHome() {
        home.addCC(Mapper.getCCID(test.nekro.getColor()));
    }

    private String next(AiPrompt... prompts) {
        return ZeuAgentRules.next(test.context(prompts))
                .map(AiTestGame::pressedId)
                .orElse("");
    }

    // Another player's Diplomacy locked the seat's home system, where its whole fleet waits: Z'eu frees it.
    @Test
    void freesItsHomeSystemLockedByAnotherPlayer() {
        tokenAtHome();

        assertThat(next(offer(NOW, AiTestGame.HOME, "nekro"))).isEqualTo(use(AiTestGame.HOME, "nekro"));
    }

    @Test
    void declinesAnotherPlayersToken() {
        assertThat(next(offer(NOW, lodor, "sol"))).isEqualTo("deleteButtons");
    }

    // Lodor is the planet its carrier would take next: a token there blocks that expansion, so Z'eu frees it.
    @Test
    void freesTheSystemItWantsToExpandInto() {
        test.game.getTileByPosition(lodor).addCC(Mapper.getCCID(test.nekro.getColor()));

        assertThat(next(offer(NOW, lodor, "nekro"))).isEqualTo(use(lodor, "nekro"));
    }

    // A token in a system three steps away, out of reach of every ship, is not worth the once-per-round agent.
    @Test
    void keepsTheAgentForAnUnimportantToken() {
        Tile far = test.place("25", FAR);
        far.addCC(Mapper.getCCID(test.nekro.getColor()));

        assertThat(next(offer(NOW, FAR, "nekro"))).isEqualTo("deleteButtons");
    }

    @Test
    void declinesWithoutATacticToken() {
        tokenAtHome();
        test.nekro.setTacticalCC(0);

        assertThat(next(offer(NOW, AiTestGame.HOME, "nekro"))).isEqualTo("deleteButtons");
    }

    // An offer from an earlier turn is past its window ("after a token is placed").
    @Test
    void declinesAStaleOffer() {
        tokenAtHome();

        assertThat(next(offer(STALE, AiTestGame.HOME, "nekro"))).isEqualTo("deleteButtons");
    }

    @Test
    void nothingOnceTheAgentIsExhausted() {
        tokenAtHome();
        test.nekro.getLeader(ZeuAgentRules.AGENT).orElseThrow().setExhausted(true);

        assertThat(next(offer(NOW, AiTestGame.HOME, "nekro"))).isEmpty();
    }

    private AiPrompt ownActivation() {
        test.aiIsActive("action");
        test.game.setActiveSystem(lodor);
        test.game.setStoredValue("currentActionSummarynekro", " Activated " + lodor + " (Lodor).");
        test.game.getTileByPosition(lodor).addCC(Mapper.getCCID(test.nekro.getColor()));
        return offer(NOW, lodor, "nekro");
    }

    // The card's window is right after the token is placed, so its own activation is answered at once, before any
    // ship moves: the token goes back, so the system can be activated again and the fleet that ends there can move on.
    @Test
    void returnsItsOwnActivationTokenAtOnce() {
        assertThat(next(ownActivation())).isEqualTo(use(lodor, "nekro"));
    }

    // Without a tactic token there is no second activation this round, so the agent is kept.
    @Test
    void keepsItOnItsOwnActivationWithoutATacticTokenLeft() {
        AiPrompt own = ownActivation();
        test.nekro.setTacticalCC(0);

        assertThat(next(own)).isEqualTo("deleteButtons");
    }
}
