package ti4.service.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import ti4.contest.replay.core.CombatRollPayload;
import ti4.contest.replay.core.renderers.CombatRollPayloadRenderer;
import ti4.contest.replay.service.CombatReplayService;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.DiceHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.service.combat.CombatV2RollData.Request;
import ti4.service.fow.FOWCombatThreadMirroring;
import ti4.service.player.PlayerColorService;
import ti4.spring.context.SpringContext;
import ti4.testUtils.BaseTi4Test;

class CombatV2RollServiceTest extends BaseTi4Test {

    @Test
    void combatRoundUsesStructuredStagesAndProducesRenderablePayload() {
        Harness harness = new Harness();
        Player sol = harness.player("sol");
        Player mentak = harness.player("mentak");
        Tile tile = harness.tile("19");
        Harness.add(tile, sol, UnitType.Cruiser, 1);
        Harness.add(tile, mentak, UnitType.Carrier, 1);

        GenericInteractionCreateEvent event = mock(GenericInteractionCreateEvent.class);
        when(event.getMessageChannel()).thenReturn(mock(MessageChannel.class));
        CombatReplayService replay = mock(CombatReplayService.class);

        try (MockedStatic<DiceHelper> ignoredDice = mockDice(10);
                MockedStatic<MessageHelper> ignoredMessages = mockStatic(MessageHelper.class);
                MockedStatic<FOWCombatThreadMirroring> ignoredFow = mockStatic(FOWCombatThreadMirroring.class);
                MockedStatic<SpringContext> spring = mockStatic(SpringContext.class)) {
            spring.when(() -> SpringContext.getBean(CombatReplayService.class)).thenReturn(replay);

            int hits = CombatV2RollService.combatRound(new Request(sol, harness.game, event, tile, Constants.SPACE));
            assertEquals(1, hits);
        }

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<CombatRollPayload> payload = ArgumentCaptor.forClass(CombatRollPayload.class);
        verify(replay)
                .mirrorCombatRoll(
                        eq(harness.game),
                        eq(sol),
                        eq(mentak),
                        eq(tile),
                        message.capture(),
                        eq(CombatRollType.combatround),
                        anyBoolean(),
                        anyBoolean(),
                        payload.capture());

        assertEquals(message.getValue(), CombatRollPayloadRenderer.render(payload.getValue()));
        assertEquals(
                "1",
                harness.game.getStoredValue(
                        "combatRoundTracker" + sol.getFaction() + tile.getPosition() + Constants.SPACE));
    }

    @Test
    void antiFighterBarrageUsesTheSameStructuredStages() {
        Harness harness = new Harness();
        Player sol = harness.player("sol");
        Player mentak = harness.player("mentak");
        Tile tile = harness.tile("19");
        Harness.add(tile, sol, UnitType.Destroyer, 1);
        Harness.add(tile, mentak, UnitType.Fighter, 1);

        GenericInteractionCreateEvent event = mock(GenericInteractionCreateEvent.class);
        when(event.getMessageChannel()).thenReturn(mock(MessageChannel.class));
        CombatReplayService replay = mock(CombatReplayService.class);

        try (MockedStatic<DiceHelper> ignoredDice = mockDice(10, 10);
                MockedStatic<MessageHelper> ignoredMessages = mockStatic(MessageHelper.class);
                MockedStatic<FOWCombatThreadMirroring> ignoredFow = mockStatic(FOWCombatThreadMirroring.class);
                MockedStatic<SpringContext> spring = mockStatic(SpringContext.class)) {
            spring.when(() -> SpringContext.getBean(CombatReplayService.class)).thenReturn(replay);

            int hits = CombatV2RollService.antiFighterBarrage(
                    new Request(sol, harness.game, event, tile, Constants.SPACE));
            assertEquals(2, hits);
        }

        ArgumentCaptor<CombatRollPayload> payload = ArgumentCaptor.forClass(CombatRollPayload.class);
        verify(replay)
                .mirrorCombatRoll(
                        eq(harness.game),
                        eq(sol),
                        eq(mentak),
                        eq(tile),
                        anyString(),
                        eq(CombatRollType.AFB),
                        anyBoolean(),
                        anyBoolean(),
                        payload.capture());
        assertEquals(CombatRollType.AFB, payload.getValue().header().rollType());
    }

    private static MockedStatic<DiceHelper> mockDice(int... results) {
        ArrayDeque<Integer> queuedResults = new ArrayDeque<>();
        for (int result : results) queuedResults.add(result);
        MockedStatic<DiceHelper> dice = mockStatic(DiceHelper.class, CALLS_REAL_METHODS);
        dice.when(() -> DiceHelper.rollDice(anyInt(), anyInt())).thenAnswer(invocation -> {
            int threshold = invocation.getArgument(0);
            int count = invocation.getArgument(1);
            List<DiceHelper.Die> rolledDice = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Integer result = queuedResults.poll();
                if (result == null) throw new AssertionError("Not enough queued dice for Combat V2 test.");
                rolledDice.add(DiceHelper.spoof(threshold, result));
            }
            return rolledDice;
        });
        return dice;
    }

    private static final class Harness {
        private final Game game = new Game();

        private Harness() {
            game.newGameSetup();
            game.setName("Combat V2 Service Test");
            game.setCcNPlasticLimit(false);
        }

        private Player player(String faction) {
            FactionModel model = Mapper.getFaction(faction);
            Player player = game.addPlayer(model.getAlias(), model.getFactionName());
            player.setFaction(game, faction);
            player.setFactionEmoji("<" + faction + ">");
            player.setColor(PlayerColorService.getPreferredColor(player));
            player.setUnitsOwned(new HashSet<>(model.getUnits()));
            player.addBreakthrough(model.getBreakthrough());
            player.setBreakthroughUnlocked(model.getBreakthrough(), true);
            player.setCommoditiesBase(model.getCommodities());
            player.setPlanets(model.getHomePlanets());
            player.setFactionTechs(model.getFactionTech());
            if (model.getStartingTech() != null) player.setTechs(model.getStartingTech());
            for (String tech : player.getFactionTechs()) player.addTech(tech);
            for (Leader leader : player.getLeaders()) leader.setLocked(false);
            return player;
        }

        private Tile tile(String tileId) {
            Tile tile = new Tile(tileId, nextPosition());
            game.setTile(tile);
            game.setActiveSystem(tile.getPosition());
            return tile;
        }

        private static void add(Tile tile, Player player, UnitType unitType, int count) {
            tile.addUnit(Constants.SPACE, Units.getUnitKey(unitType, player.getColorID()), count);
        }

        private String nextPosition() {
            for (String position : PositionMapper.getTilePositions()) {
                if (game.getTileByPosition(position) == null) return position;
            }
            return null;
        }
    }
}
