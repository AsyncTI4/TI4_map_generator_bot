package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.tile.SystemContextImage;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.service.unit.DestroyUnitService;
import ti4.spring.context.SpringContext;
import ti4.testUtils.BaseTi4Test;

/**
 * An infinite stack stores 999 infantry and never shrinks, so any loop that waits for infantry to run out would spin
 * forever. These cases pin the loops that touch an infinite stack to a bounded number of rounds and dice.
 */
class InfiniteStackLoopGuardTest extends BaseTi4Test {

    private static final Duration FAST = Duration.ofSeconds(10);

    private Game game;
    private Player letnev;
    private Player hacan;
    private Tile tile;
    private UnitHolder mecatol;
    private final List<String> messages = new ArrayList<>();

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("infinite-stack-loop-guard-test");
        tile = new Tile("18", "000");
        game.setTile(tile);
        letnev = seat("111", "letnev", "black");
        hacan = seat("222", "hacan", "yellow");
        mecatol = tile.getUnitHolders().get("mr");
    }

    private Player seat(String userId, String faction, String color) {
        Player player = game.addPlayer(userId, faction);
        player.setFaction(game, faction);
        player.setColor(color);
        player.setUnitsOwned(new HashSet<>(Mapper.getFaction(faction).getUnits()));
        return player;
    }

    private UnitKey infantry(Player player) {
        return Units.getUnitKey(UnitType.Infantry, player.getColorID());
    }

    private int automateGroundCombatCountingDice() {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        try (MockedStatic<MessageHelper> messageHelper = mockStatic(MessageHelper.class);
                MockedStatic<SystemContextImage> ignored = mockStatic(SystemContextImage.class);
                MockedStatic<SpringContext> spring = mockStatic(SpringContext.class);
                MockedConstruction<Die> dice = mockConstruction(Die.class)) {
            messageHelper
                    .when(() -> MessageHelper.sendMessageToChannel(any(), anyString()))
                    .thenAnswer(call -> messages.add(call.getArgument(1)));
            messageHelper
                    .when(() -> MessageHelper.sendMessageToEventChannel(any(), anyString()))
                    .thenAnswer(call -> messages.add(call.getArgument(1)));
            messageHelper
                    .when(() -> MessageHelper.sendMessageToChannelWithButtons(any(), anyString(), anyList()))
                    .thenAnswer(call -> messages.add(call.getArgument(1)));
            spring.when(() -> SpringContext.getBean(any()))
                    .thenAnswer(call -> mock(call.<Class<?>>getArgument(0), RETURNS_DEEP_STUBS));
            ButtonHelperModifyUnits.automateGroundCombat(letnev, hacan, "mr", game, event);
            return dice.constructed().size();
        }
    }

    @Test
    void twoInfiniteStacksStopAtOnceInsteadOfFightingForever() {
        mecatol.makeInfinite(infantry(letnev));
        mecatol.makeInfinite(infantry(hacan));

        int dice = assertTimeoutPreemptively(FAST, this::automateGroundCombatCountingDice);

        assertThat(dice).isZero();
        assertThat(messages).anyMatch(message -> message.contains("can never end"));
    }

    @Test
    void anInfiniteStackEndsAnAutomatedGroundCombatInOneRound() {
        mecatol.makeInfinite(infantry(letnev));
        mecatol.addUnit(infantry(hacan), 50);

        int dice = assertTimeoutPreemptively(FAST, this::automateGroundCombatCountingDice);

        // Only the 50 ordinary infantry roll, once; the infinite stack rolls nothing.
        assertThat(dice).isLessThanOrEqualTo(50);
        assertThat(mecatol.getUnitCount(infantry(hacan))).isZero();
        assertThat(mecatol.isInfinite(infantry(letnev))).isTrue();
        // Automation assigns the hits itself, so no stale manual hit-assignment prompt is posted.
        assertThat(messages).noneMatch(message -> message.contains("please assign"));
    }

    @Test
    void destroyingFromAnInfiniteStackTriggersNoResurrectionRolls() {
        letnev.addTech("inf2");
        mecatol.makeInfinite(infantry(letnev));

        try (MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class);
                MockedConstruction<Die> dice = mockConstruction(Die.class)) {
            DestroyUnitService.destroyUnit(null, tile, game, infantry(letnev), 40, mecatol, true);

            assertThat(dice.constructed()).isEmpty();
        }
        assertThat(mecatol.isInfinite(infantry(letnev))).isTrue();
    }
}
