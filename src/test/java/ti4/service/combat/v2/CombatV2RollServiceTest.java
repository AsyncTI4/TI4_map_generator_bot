package ti4.service.combat.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import ti4.helpers.BombardmentAssignment;
import ti4.helpers.BombardmentAssignmentType;
import ti4.helpers.Constants;
import ti4.helpers.DiceHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.json.JsonMapperManager;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.service.combat.CombatRollType;
import ti4.service.combat.v2.CombatV2DiceData.RollSource;
import ti4.service.combat.v2.CombatV2RollData.Request;
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

    @Test
    void superchargeSplitsOnePhysicalUnitFromTheRest() {
        Harness harness = new Harness();
        Player sol = harness.player("sol");
        Player mentak = harness.player("mentak");
        sol.addTech("tf-supercharge");
        Tile tile = harness.tile("19");
        Harness.add(tile, sol, UnitType.Cruiser, 2);
        Harness.add(tile, mentak, UnitType.Carrier, 1);
        harness.game.setStoredValue("highestValueSingleUnit" + sol.getFaction(), "ca");

        GenericInteractionCreateEvent event = mock(GenericInteractionCreateEvent.class);
        when(event.getMessageChannel()).thenReturn(mock(MessageChannel.class));
        CombatReplayService replay = mock(CombatReplayService.class);

        try (MockedStatic<DiceHelper> ignoredDice = mockDice(7, 7);
                MockedStatic<MessageHelper> ignoredMessages = mockStatic(MessageHelper.class);
                MockedStatic<FOWCombatThreadMirroring> ignoredFow = mockStatic(FOWCombatThreadMirroring.class);
                MockedStatic<SpringContext> spring = mockStatic(SpringContext.class)) {
            spring.when(() -> SpringContext.getBean(CombatReplayService.class)).thenReturn(replay);
            assertEquals(
                    2, CombatV2RollService.combatRound(new Request(sol, harness.game, event, tile, Constants.SPACE)));
        }

        ArgumentCaptor<CombatRollPayload> payload = ArgumentCaptor.forClass(CombatRollPayload.class);
        verify(replay)
                .mirrorCombatRoll(
                        any(), any(), any(), any(), anyString(), any(), anyBoolean(), anyBoolean(), payload.capture());
        assertEquals(
                List.of(RollSource.SUPERCHARGE_SELECTED_UNIT, RollSource.SUPERCHARGE_REST),
                payload.getValue().unitRolls().stream()
                        .map(CombatRollPayload.UnitRoll::segmentType)
                        .toList());
        assertEquals("", harness.game.getStoredValue("highestValueSingleUnit" + sol.getFaction()));
    }

    @Test
    void munitionsReservesUsesOriginalMissesAndIsConsumed() {
        Harness harness = new Harness();
        Player sol = harness.player("sol");
        Player mentak = harness.player("mentak");
        Tile tile = harness.tile("19");
        Harness.add(tile, sol, UnitType.Cruiser, 1);
        Harness.add(tile, mentak, UnitType.Carrier, 1);
        harness.game.setStoredValue("munitionsReserves", sol.getFaction());

        GenericInteractionCreateEvent event = mock(GenericInteractionCreateEvent.class);
        when(event.getMessageChannel()).thenReturn(mock(MessageChannel.class));
        CombatReplayService replay = mock(CombatReplayService.class);

        try (MockedStatic<DiceHelper> ignoredDice = mockDice(1, 10);
                MockedStatic<MessageHelper> ignoredMessages = mockStatic(MessageHelper.class);
                MockedStatic<FOWCombatThreadMirroring> ignoredFow = mockStatic(FOWCombatThreadMirroring.class);
                MockedStatic<SpringContext> spring = mockStatic(SpringContext.class)) {
            spring.when(() -> SpringContext.getBean(CombatReplayService.class)).thenReturn(replay);
            assertEquals(
                    1, CombatV2RollService.combatRound(new Request(sol, harness.game, event, tile, Constants.SPACE)));
        }

        assertEquals("", harness.game.getStoredValue("munitionsReserves"));
        ArgumentCaptor<CombatRollPayload> payload = ArgumentCaptor.forClass(CombatRollPayload.class);
        verify(replay)
                .mirrorCombatRoll(
                        any(), any(), any(), any(), anyString(), any(), anyBoolean(), anyBoolean(), payload.capture());
        assertEquals(1, payload.getValue().total().misses());
        assertEquals(
                RollSource.MUNITIONS_RESERVES,
                payload.getValue().unitRolls().getLast().segmentType());
    }

    @Test
    void thalnosUsesSelectedQuantitiesAndClearsItsRollState() {
        Harness harness = new Harness();
        Player sol = harness.player("sol");
        Player mentak = harness.player("mentak");
        Tile tile = harness.tile("19");
        Harness.add(tile, sol, UnitType.Cruiser, 2);
        Harness.add(tile, mentak, UnitType.Carrier, 1);
        harness.game.setSpecificThalnosUnit(tile.getPosition() + "_space_cruiser", 1);
        harness.game.setStoredValue("thalnosPlusOne", "true");

        GenericInteractionCreateEvent event = mock(GenericInteractionCreateEvent.class);
        when(event.getMessageChannel()).thenReturn(mock(MessageChannel.class));
        CombatReplayService replay = mock(CombatReplayService.class);

        try (MockedStatic<DiceHelper> ignoredDice = mockDice(10);
                MockedStatic<MessageHelper> ignoredMessages = mockStatic(MessageHelper.class);
                MockedStatic<FOWCombatThreadMirroring> ignoredFow = mockStatic(FOWCombatThreadMirroring.class);
                MockedStatic<SpringContext> spring = mockStatic(SpringContext.class)) {
            spring.when(() -> SpringContext.getBean(CombatReplayService.class)).thenReturn(replay);
            assertEquals(
                    1,
                    CombatV2RollService.thalnosCombatRound(
                            new Request(sol, harness.game, event, tile, Constants.SPACE)));
        }

        assertEquals("false", harness.game.getStoredValue("thalnosPlusOne"));
        ArgumentCaptor<CombatRollPayload> payload = ArgumentCaptor.forClass(CombatRollPayload.class);
        verify(replay)
                .mirrorCombatRoll(
                        any(), any(), any(), any(), anyString(), any(), anyBoolean(), anyBoolean(), payload.capture());
        assertEquals(1, payload.getValue().total().diceRolled());
        assertEquals(1, payload.getValue().unitRolls().getFirst().quantity());
    }

    @Test
    void dragonBombardmentPublishesAdjacentPlanetAssignment() {
        Harness harness = new Harness();
        Player obsidian = harness.player("obsidian");
        Player mentak = harness.player("mentak");
        obsidian.getUnitsOwned().remove("ws");
        obsidian.getUnitsOwned().add("tf-dragonfreed");
        Tile origin = harness.tile("19");
        Tile adjacent = harness.tile("20");
        String target = origin.getPlanetUnitHolders().getFirst().getName();
        String adjacentPlanet = adjacent.getPlanetUnitHolders().getFirst().getName();
        origin.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Warsun, obsidian.getColorID()), 1);
        origin.addUnit(target, Units.getUnitKey(UnitType.Infantry, mentak.getColorID()), 1);
        adjacent.addUnit(adjacentPlanet, Units.getUnitKey(UnitType.Infantry, mentak.getColorID()), 1);
        mentak.addPlanet(target);
        harness.game.setStoredValue("bombardmentTarget" + obsidian.getFaction(), target);
        harness.game.setStoredValue(
                "assignedBombardment" + obsidian.getFaction(),
                JsonMapperManager.basic()
                        .writeValueAsString(List.of(
                                new BombardmentAssignment("ws", target, false, BombardmentAssignmentType.UNIT))));

        GenericInteractionCreateEvent event = mock(GenericInteractionCreateEvent.class);
        MessageChannel channel = mock(MessageChannel.class);
        when(event.getMessageChannel()).thenReturn(channel);
        CombatReplayService replay = mock(CombatReplayService.class);

        try (MockedStatic<DiceHelper> ignoredDice = mockDice(10, 10, 10);
                MockedStatic<MessageHelper> messages = mockStatic(MessageHelper.class);
                MockedStatic<FOWCombatThreadMirroring> ignoredFow = mockStatic(FOWCombatThreadMirroring.class);
                MockedStatic<FoWHelper> fow = mockStatic(FoWHelper.class, CALLS_REAL_METHODS);
                MockedStatic<SpringContext> spring = mockStatic(SpringContext.class)) {
            spring.when(() -> SpringContext.getBean(CombatReplayService.class)).thenReturn(replay);
            fow.when(() -> FoWHelper.getAdjacentTiles(harness.game, origin.getPosition(), obsidian, false, true))
                    .thenReturn(Set.of(adjacent.getPosition()));

            assertEquals(
                    3,
                    CombatV2RollService.bombardmentTarget(
                            new Request(obsidian, harness.game, event, origin, Constants.SPACE)));
            messages.verify(() -> MessageHelper.sendMessageToChannelWithButtons(
                    eq(channel), contains("Dragon BOMBARDMENT"), anyList()));
        }
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
