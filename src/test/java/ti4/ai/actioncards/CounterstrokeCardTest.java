package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.ai.AiTestGame;
import ti4.ai.actioncards.CardPlay.Stage;
import ti4.ai.brain.AiDecision;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class CounterstrokeCardTest extends BaseTi4Test {

    private static final long SETTLED = NOW + 11_000L;
    private static final int HAND_INDEX = 412;

    private CardTable table;
    private MockedStatic<GameMessageManager> messages;
    private Tile stranded;

    @BeforeEach
    void setUp() {
        table = new CardTable();
        table.nekro.setTacticalCC(1);
        table.nekro.setFleetCC(3);
        messages = Mockito.mockStatic(GameMessageManager.class);
        messages.when(() -> GameMessageManager.getOne(anyString(), anyString()))
                .thenAnswer(call -> Optional.of(new GameMessage(
                        call.getArgument(1),
                        GameMessageType.ACTION_CARD,
                        new LinkedHashSet<>(List.of("nekro", "sol")),
                        0L)));
        table.test.isActive(table.sol, "action");
    }

    @AfterEach
    void tearDown() {
        messages.close();
    }

    private String ccId() {
        return Mapper.getCCID(table.nekro.getColor());
    }

    // A loaded carrier sits one system short of a free planet, held there by its own command token.
    private void aStrandedFleetBesideAFreePlanet() {
        stranded = table.system("40", AiTestGame.neighbourOf(AiTestGame.HOME));
        table.ships(table.nekro, stranded, UnitType.Carrier, 1);
        table.test.units(stranded, "space", table.nekro, UnitType.Infantry, 2);
        stranded.addCC(ccId());
        String beyond = ti4.model.MapPositionMapper.getAdjacentTilePositions(stranded.getPosition()).stream()
                .filter(candidate -> !"x".equals(candidate))
                .filter(candidate -> !AiTestGame.HOME.equals(candidate))
                .filter(candidate -> !ti4.model.MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME)
                        .contains(candidate))
                .findFirst()
                .orElseThrow();
        table.system("26", beyond);
        table.game.setActiveSystem(stranded.getPosition());
        table.nekro.setActionCard("counterstroke", HAND_INDEX);
    }

    private AiPrompt hand() {
        return prompt(
                "hand",
                PromptSource.AI_THREAD,
                NOW,
                "ac_play_from_hand_" + HAND_INDEX,
                "ac_play_from_hand_" + HAND_INDEX + "_counterstroke_" + table.game.getActiveSystem(),
                "cardsInfo");
    }

    private Optional<Plan> plan() {
        return AiActionCards.of("counterstroke").orElseThrow().planFor(table.game, table.nekro);
    }

    @Test
    void counterstrokeIsAtLeastATacticTokenWhenTheSystemHoldsItsToken() {
        Tile outpost = table.system("40", "302");
        outpost.addCC(ccId());
        table.game.setActiveSystem("302");

        assertThat(plan().orElseThrow().value()).isGreaterThanOrEqualTo(0.75);
    }

    @Test
    void counterstrokeNeedsTheSeatsTokenInTheActivatedSystem() {
        table.system("40", "302");
        table.game.setActiveSystem("302");

        assertThat(plan()).isEmpty();
    }

    @Test
    void counterstrokeIsWorthMoreWhenTheTokenHoldsAFleetBackFromAFreePlanet() {
        aStrandedFleetBesideAFreePlanet();
        double unlocked = plan().orElseThrow().value();
        stranded.removeCC(ccId());

        assertThat(unlocked).isGreaterThan(1.0);
    }

    // A command token is worth about two trade goods (2r = 3i = 1 token = 2 trade goods), more than an average action
    // card, so Counterstroke is worth playing just to take the token back.
    @Test
    void counterstrokeIsPlayedEvenWhenItOnlyReturnsAToken() {
        Tile outpost = table.system("40", "302");
        outpost.addCC(ccId());
        table.game.setActiveSystem("302");
        table.nekro.setActionCard("counterstroke", HAND_INDEX);

        assertThat(ActionCardRules.afterActivation(table.test.context(hand()))).isPresent();
    }

    @Test
    void counterstrokeIsPlayedOnItsOwnButtonWhenTheTokenFreesAPlan() {
        aStrandedFleetBesideAFreePlanet();

        assertThat(pressedId(ActionCardRules.afterActivation(table.test.context(hand()))
                        .orElseThrow()))
                .isEqualTo("ac_play_from_hand_" + HAND_INDEX + "_counterstroke_" + stranded.getPosition());
    }

    @Test
    void counterstrokeResolvesWithTheButtonTheBotPostsOnceTheWindowHasClosed() {
        aStrandedFleetBesideAFreePlanet();
        ActionCardRules.afterActivation(table.test.context(hand()));
        table.nekro.getActionCards().remove("counterstroke");
        table.game.getGameStats().recordAcPlay("Counterstroke", table.nekro);
        AiPrompt announcement = prompt(
                "announce",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_moveAlongAfterAllHaveReactedToAC_Counterstroke",
                "no_sabotage",
                "sabotage_ac_Counterstroke_nekro");
        AiPrompt resolve = AiTestGame.withContent(
                prompt(
                        "resolve",
                        PromptSource.PUBLIC,
                        NOW,
                        "resolveCounterStroke_" + stranded.getPosition(),
                        "resolveCounterStroke_999"),
                table.nekro.getRepresentation()
                        + ", after checking for Sabos, use the button to resolve _Counterstroke_.");

        Optional<AiDecision> pressed = Optional.empty();
        for (int step = 0; step < 6 && !(pressed.orElse(null) instanceof AiDecision.Press); step++) {
            pressed =
                    ActionCardRules.continuePlay(table.test.contextAt(SETTLED + step * 20_000L, announcement, resolve));
        }

        assertThat(pressedId(pressed.orElseThrow())).isEqualTo("resolveCounterStroke_" + stranded.getPosition());
        String key = "acPlay|msg:" + table.test.context().turnKey() + "@" + stranded.getPosition();
        assertThat(table.test.memory.get(key).flatMap(CardPlay::decode).map(CardPlay::stage))
                .contains(Stage.DONE);
    }
}
