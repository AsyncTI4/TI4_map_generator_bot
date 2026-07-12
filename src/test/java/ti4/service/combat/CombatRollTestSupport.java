package ti4.service.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.tuple.Pair;
import org.mockito.MockedStatic;
import ti4.contest.replay.core.CombatRollPayload;
import ti4.contest.replay.core.CombatRollPayload.DieRollSource;
import ti4.contest.replay.core.CombatRollPayload.UnitRollType;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.CombatModHelper;
import ti4.helpers.Constants;
import ti4.helpers.DiceHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.NamedCombatModifierModel;
import ti4.model.UnitModel;
import ti4.service.player.PlayerColorService;

final class CombatRollTestSupport {
    private CombatRollTestSupport() {}

    static void assertCompletedContract(CombatRollResult result) {
        assertEquals(CombatRollStatus.COMPLETED, result.status());
        assertTrue(result.totalHits() >= 0, "completed rolls cannot have negative hits");
        assertNotNull(result.payload(), "completed rolls must expose their payload");
        assertNotNull(result.payload().total(), "completed payloads must expose roll totals");
        assertTrue(result.payload().total().misses() >= 0, "completed rolls cannot record negative misses");
        assertTrue(
                result.payload().total().maximumHits()
                        >= result.payload().total().displayedTotalHits(),
                "maximum hits must cover the raw displayed total");
        int payloadDice = result.payload().unitRolls().stream()
                .mapToInt(roll -> roll.dice().size())
                .sum();
        assertEquals(result.payload().total().diceRolled(), payloadDice, "each die must occur once in the payload");
        result.payload().unitRolls().forEach(CombatRollTestSupport::assertRollSource);
    }

    static CombatResultAssert assertThat(CombatRollResult result) {
        return new CombatResultAssert(result);
    }

    static void assertStoppedContract(CombatRollResult result, CombatRollStatus expectedStatus) {
        assertEquals(expectedStatus, result.status());
        CombatRollTestSupport.assertThat(result).hits(0);
        assertNull(result.payload());
        assertTrue(result.message().isEmpty());
    }

    static Harness harness() {
        return new Harness();
    }

    static ScriptedDice dice(int... results) {
        return new ScriptedDice(results);
    }

    static StoppedRollSnapshot stoppedSnapshot(Player player, Tile tile) {
        return new StoppedRollSnapshot(player, tile);
    }

    static void assertNoButtonsSent(MockedStatic<MessageHelper> messages) {
        messages.verify(
                () -> MessageHelper.sendMessageToChannelWithButtons(any(MessageChannel.class), anyString(), anyList()),
                never());
        messages.verify(
                () -> MessageHelper.sendMessageToChannel(any(MessageChannel.class), anyString(), anyList()), never());
    }

    static final class CombatResultAssert {
        private final CombatRollResult actual;

        private CombatResultAssert(CombatRollResult actual) {
            this.actual = actual;
        }

        CombatResultAssert completed() {
            assertCompletedContract(actual);
            return this;
        }

        CombatResultAssert stopped(CombatRollStatus status) {
            assertStoppedContract(actual, status);
            return this;
        }

        CombatResultAssert hits(int expected) {
            assertEquals(expected, actual.totalHits(), "unexpected total hits");
            return this;
        }

        CombatResultAssert maximumHits(int expected) {
            completedPayload();
            assertEquals(expected, actual.payload().total().maximumHits(), "unexpected maximum hits");
            return this;
        }

        CombatResultAssert diceRolled(int expected) {
            completedPayload();
            assertEquals(expected, actual.payload().total().diceRolled(), "unexpected number of rolled dice");
            return this;
        }

        CombatResultAssert unitRolls(int expected) {
            completedPayload();
            assertEquals(expected, actual.payload().unitRolls().size(), "unexpected number of unit-roll segments");
            return this;
        }

        CombatResultAssert includesUnit(String unitId) {
            completedPayload();
            assertTrue(
                    actual.payload().unitRolls().stream().anyMatch(roll -> unitId.equals(roll.unitId())),
                    () -> "payload does not include unit " + unitId);
            return this;
        }

        CombatResultAssert hasRollType(UnitRollType rollType) {
            completedPayload();
            assertTrue(
                    actual.payload().unitRolls().stream().anyMatch(roll -> roll.rollType() == rollType),
                    () -> "payload does not contain a " + rollType + " rollType");
            return this;
        }

        CombatResultAssert lacksRollType(UnitRollType rollType) {
            return rollTypeCount(rollType, 0);
        }

        CombatResultAssert rollTypeCount(UnitRollType rollType, long expected) {
            completedPayload();
            long actualCount = actual.payload().unitRolls().stream()
                    .filter(roll -> roll.rollType() == rollType)
                    .count();
            assertEquals(expected, actualCount, "unexpected number of " + rollType + " rolls");
            return this;
        }

        CombatResultAssert messageContains(String text) {
            assertTrue(actual.message().contains(text), () -> "combat message does not contain: " + text);
            return this;
        }

        CombatResultAssert hasNoteFrom(String sourceId) {
            completedPayload();
            assertTrue(
                    actual.payload().notes().stream().anyMatch(note -> sourceId.equals(note.sourceId())),
                    () -> "payload does not contain a note from " + sourceId);
            return this;
        }

        private void completedPayload() {
            assertNotNull(actual.payload(), "result must have a payload before inspecting roll details");
        }
    }

    static final class ScriptedDice implements AutoCloseable {
        private final ArrayDeque<Integer> results = new ArrayDeque<>();
        private final MockedStatic<DiceHelper> mock = mockStatic(DiceHelper.class, CALLS_REAL_METHODS);
        private int rolledDice;

        private ScriptedDice(int... scriptedResults) {
            for (int result : scriptedResults) results.add(result);
            mock.when(() -> DiceHelper.rollDice(anyInt(), anyInt())).thenAnswer(invocation -> {
                int threshold = invocation.getArgument(0);
                int count = invocation.getArgument(1);
                List<DiceHelper.Die> dice = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    Integer result = results.poll();
                    if (result == null) throw new AssertionError("Combat rolled more dice than the scenario supplied");
                    dice.add(DiceHelper.spoof(threshold, result));
                    rolledDice++;
                }
                return dice;
            });
        }

        int rolledDice() {
            return rolledDice;
        }

        void assertExhausted() {
            assertTrue(results.isEmpty(), "Scenario supplied dice that production combat never rolled: " + results);
        }

        @Override
        public void close() {
            mock.close();
        }
    }

    static final class StoppedRollSnapshot {
        private final Player player;
        private final int tradeGoods;
        private final int commodities;
        private final int actualHits;
        private final int expectedHits;
        private final List<?> temporaryModifiers;
        private final Map<String, String> units;
        private final Map<String, String> roundTrackers;

        private StoppedRollSnapshot(Player player, Tile tile) {
            this.player = player;
            tradeGoods = player.getTg();
            commodities = player.getCommodities();
            actualHits = player.getActualHits();
            expectedHits = player.getExpectedHitsTimes10();
            temporaryModifiers = List.copyOf(player.getTempCombatModifiers());
            units = new LinkedHashMap<>();
            roundTrackers = new LinkedHashMap<>();
            tile.getUnitHolders().forEach((name, holder) -> {
                units.put(name, holder.getUnits().toString());
                String key = "combatRoundTracker" + player.getFaction() + tile.getPosition() + name;
                roundTrackers.put(key, player.getGame().getStoredValue(key));
            });
        }

        void assertUnchanged(Tile tile) {
            assertEquals(tradeGoods, player.getTg(), "stopped roll changed trade goods");
            assertEquals(commodities, player.getCommodities(), "stopped roll changed commodities");
            assertEquals(actualHits, player.getActualHits(), "stopped roll changed actual-hit statistics");
            assertEquals(expectedHits, player.getExpectedHitsTimes10(), "stopped roll changed expected hits");
            assertEquals(temporaryModifiers, player.getTempCombatModifiers(), "stopped roll consumed modifiers");
            tile.getUnitHolders()
                    .forEach((name, holder) -> assertEquals(
                            units.get(name), holder.getUnits().toString(), "stopped roll changed units on " + name));
            roundTrackers.forEach((key, value) -> assertEquals(
                    value, player.getGame().getStoredValue(key), "stopped roll changed combat-round tracking"));
        }
    }

    static final class Harness {
        final Game game = new Game();
        final GenericInteractionCreateEvent event = mock(GenericInteractionCreateEvent.class);
        final MessageChannel channel = mock(MessageChannel.class);

        private Harness() {
            game.newGameSetup();
            game.setName("Combat Roll Test");
            game.setCcNPlasticLimit(false);
            when(event.getMessageChannel()).thenReturn(channel);
        }

        Player player(String faction) {
            FactionModel model = Mapper.getFaction(faction);
            Player player = game.addPlayer(model.getAlias(), model.getFactionName());
            player.setFaction(game, faction);
            player.setFactionEmoji("<" + faction + ">");
            player.setColor(PlayerColorService.getPreferredColor(player));
            player.setUnitsOwned(new HashSet<>(model.getUnits()));
            player.addBreakthrough(model.getBreakthrough());
            player.setCommoditiesBase(model.getCommodities());
            player.setPlanets(model.getHomePlanets());
            player.setFactionTechs(model.getFactionTech());
            if (model.getStartingTech() != null) player.setTechs(model.getStartingTech());
            return player;
        }

        Tile tile(String tileId) {
            Tile tile = new Tile(tileId, nextPosition());
            game.setTile(tile);
            game.setActiveSystem(tile.getPosition());
            return tile;
        }

        Tile tileAt(String tileId, String position) {
            Tile tile = new Tile(tileId, position);
            game.setTile(tile);
            return tile;
        }

        void add(Tile tile, UnitHolder holder, Player player, UnitType type, int count) {
            tile.addUnit(holder.getName(), Units.getUnitKey(type, player.getColorID()), count);
        }

        void addToSpace(Tile tile, Player player, UnitType type, int count) {
            add(tile, tile.getUnitHolders().get(Constants.SPACE), player, type, count);
        }

        void ownUnit(Player player, String unitId) {
            player.addOwnedUnitByID(unitId);
        }

        void unlockLeader(Player player, String leaderId) {
            player.getLeader(leaderId).orElseThrow().setLocked(false);
        }

        CombatRollPipelineState preparedState(
                Player player, Player opponent, Tile tile, UnitHolder holder, CombatRollType rollType) {
            Map<Pair<UnitModel, UnitHolder>, Integer> units =
                    CombatUnitResolver.getUnitsInCombatByHolder(tile, holder, player, event, rollType, game);
            Map<UnitModel, Integer> flatUnits = CombatUnitResolver.flattenUnitMap(units);
            Map<UnitModel, Integer> opponentUnits =
                    CombatUnitResolver.getUnitsInCombat(tile, holder, opponent, event, rollType, game);
            List<NamedCombatModifierModel> modifiers = CombatModHelper.getModifiers(
                    player,
                    opponent,
                    flatUnits,
                    opponentUnits,
                    tile.getTileModel(),
                    game,
                    rollType,
                    holder,
                    Constants.COMBAT_MODIFIERS);
            List<NamedCombatModifierModel> extraRolls = CombatModHelper.getModifiers(
                    player,
                    opponent,
                    flatUnits,
                    opponentUnits,
                    tile.getTileModel(),
                    game,
                    rollType,
                    holder,
                    Constants.COMBAT_EXTRA_ROLLS);
            CombatRollPipelineState state =
                    new CombatRollPipelineState(player, game, event, tile, holder.getName(), rollType, false);
            state.setCombatOnHolder(holder);
            state.setPlayerUnits(units);
            state.setOpponent(opponent);
            state.setModifiers(new CombatRollModifiers(modifiers, extraRolls, List.of()));
            return state;
        }

        CombatRollResult execute(Player player, Player opponent, Tile tile, CombatRollType rollType) {
            return execute(player, opponent, tile, tile.getUnitHolders().get(Constants.SPACE), rollType);
        }

        CombatRollResult execute(
                Player player, Player opponent, Tile tile, UnitHolder holder, CombatRollType rollType) {
            int expectedHitsBefore = player.getExpectedHitsTimes10();
            CombatRollResult result =
                    UnitRollExecution.rollForUnitsWithResult(preparedState(player, opponent, tile, holder, rollType));
            assertCompletedContract(result);
            int expectedIncrease = result.payload().unitRolls().stream()
                    .flatMap(roll -> roll.dice().stream())
                    .mapToInt(die -> 11 - die.threshold())
                    .sum();
            assertEquals(
                    expectedHitsBefore + expectedIncrease,
                    player.getExpectedHitsTimes10(),
                    "expected-hit accounting must include every rolled die exactly once");
            return result;
        }

        private String nextPosition() {
            for (String position : PositionMapper.getTilePositions()) {
                if (game.getTileByPosition(position) == null) return position;
            }
            throw new IllegalStateException("No open tile positions remain");
        }
    }

    private static void assertRollSource(CombatRollPayload.UnitRoll roll) {
        DieRollSource expectedSource =
                switch (roll.rollType()) {
                    case PRIMARY -> DieRollSource.PRIMARY;
                    case JOL_NAR_COMMANDER_REROLL_MISSES, IRON_COMMANDER_REROLL_MISSES -> DieRollSource.REROLL_MISS;
                    case JOL_NAR_COMMANDER_REROLL_HITS -> DieRollSource.REROLL_HIT;
                    case KALTRIM_COMMANDER_REROLL_ONES -> DieRollSource.REROLL_ONE;
                    case MUNITIONS_RESERVES_REROLL -> DieRollSource.MUNITIONS_RESERVES;
                };
        assertTrue(
                roll.dice().stream().allMatch(die -> die.source() == expectedSource),
                () -> "wrong die source for " + roll.rollType() + ": " + roll.dice());
    }
}
