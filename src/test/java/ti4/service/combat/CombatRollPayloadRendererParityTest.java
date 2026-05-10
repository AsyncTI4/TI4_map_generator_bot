package ti4.service.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.StringUtils;
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
import ti4.game.UnitHolder;
import ti4.helpers.CombatMessageHelper;
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
import ti4.model.TileModel;
import ti4.model.UnitModel;
import ti4.service.emoji.MiscEmojis;
import ti4.service.fow.FOWCombatThreadMirroring;
import ti4.service.player.PlayerColorService;
import ti4.service.unit.DestroyUnitService;
import ti4.spring.context.SpringContext;
import ti4.testUtils.BaseTi4Test;

class CombatRollPayloadRendererParityTest extends BaseTi4Test {

    @Test
    void rendersBasicSpaceCombatRoundWithMixedUnitGroups() {
        Harness harness = new Harness();
        Player sol = harness.player("sol");
        Player mentak = harness.player("mentak");
        Tile tile = harness.tile("19");
        harness.add(tile, sol, UnitType.Carrier, 1);
        harness.add(tile, sol, UnitType.Cruiser, 2);
        harness.add(tile, mentak, UnitType.Destroyer, 1);

        assertRollBodyParity(harness, sol, mentak, tile, CombatRollType.combatround, 8, 2, 10);
    }

    @Test
    void rendersRickarRickaniWinnuCommanderModifierOnMecatol() {
        Harness harness = new Harness();
        Player winnu = harness.player("winnu");
        Player sol = harness.player("sol");
        Tile mecatol = harness.tile("112");
        harness.add(mecatol, winnu, UnitType.Flagship, 1);
        harness.add(mecatol, sol, UnitType.Carrier, 1);

        RenderedRoll roll = assertRollBodyParity(harness, winnu, sol, mecatol, CombatRollType.combatround, 3, 6, 9);

        assertTrue(roll.productionMessage().contains("Rickar Rickani"));
        assertTrue(roll.productionMessage().contains("hits on **5** (+2 mods)"));
    }

    @Test
    void rendersSuperchargeSelectedUnitSplit() {
        Harness harness = new Harness();
        Player bluetf = harness.player("bluetf");
        Player sol = harness.player("sol");
        Tile tile = harness.tile("112");
        bluetf.addOwnedUnitByID("tf-echoofascension");
        bluetf.addTech("tf-supercharge");
        harness.add(tile, bluetf, UnitType.Flagship, 1);
        harness.add(tile, bluetf, UnitType.Mech, 4);
        harness.add(tile, sol, UnitType.Carrier, 1);

        RenderedRoll roll =
                assertRollBodyParity(harness, bluetf, sol, tile, CombatRollType.combatround, 2, 3, 4, 5, 6, 7, 8, 9);

        assertTrue(roll.productionMessage().contains("Applied +2 to the rolls of 1 unit with _Supercharge_."));
        assertTrue(roll.productionMessage().contains("always hits (+2 mods)"));
    }

    @Test
    void rendersMunitionsReservesRerollSegment() {
        Harness harness = new Harness();
        Player jolnar = harness.player("jolnar");
        Player sol = harness.player("sol");
        Tile tile = harness.tile("19");
        harness.add(tile, jolnar, UnitType.Cruiser, 1);
        harness.add(tile, sol, UnitType.Carrier, 1);
        harness.game.setStoredValue("munitionsReserves", jolnar.getFaction());

        RenderedRoll roll = assertRollBodyParity(harness, jolnar, sol, tile, CombatRollType.combatround, 1, 10);

        assertTrue(roll.productionMessage().contains("**Munitions Reserve** rerolling 1 miss"));
    }

    @Test
    void rendersJolNarCommanderAfbRerollMisses() {
        Harness harness = new Harness();
        Player jolnar = harness.player("jolnar");
        Player sol = harness.player("sol");
        Tile tile = harness.tile("19");
        harness.add(tile, jolnar, UnitType.Destroyer, 1);
        harness.add(tile, sol, UnitType.Fighter, 2);

        RenderedRoll roll = assertRollBodyParity(harness, jolnar, sol, tile, CombatRollType.AFB, 1, 2, 10, 9);

        assertTrue(roll.productionMessage().contains("Rerolling 2 misses due to Ta Zern, the Jol-Nar Commander:"));
    }

    @Test
    void rendersArgentStrikeWingAlphaTwoInfantryDestructionNote() {
        Harness harness = new Harness();
        Player argent = harness.player("argent");
        Player sol = harness.player("sol");
        Tile tile = harness.tile("19");
        argent.addOwnedUnitByID("argent_destroyer2");
        argent.addTech("swa2");
        harness.add(tile, argent, UnitType.Destroyer, 1);
        harness.add(tile, sol, UnitType.Fighter, 2);
        harness.add(tile, sol, UnitType.Infantry, 3);

        try (MockedStatic<DestroyUnitService> ignored = mockStatic(DestroyUnitService.class)) {
            RenderedRoll roll = assertRollBodyParity(harness, argent, sol, tile, CombatRollType.AFB, 9, 10, 2, 3);

            assertTrue(roll.productionMessage().contains("Due to the Strike Wing Alpha II destroyer ability, 2 of"));
            assertTrue(roll.productionMessage().contains("infantry were destroyed"));
        }
    }

    @Test
    void rendersThalnosRerollAvailableNote() {
        Harness harness = new Harness();
        Player naalu = harness.player("naalu");
        Player sol = harness.player("sol");
        Tile tile = harness.tile("19");
        naalu.addRelic("thalnos");
        harness.add(tile, naalu, UnitType.Cruiser, 1);
        harness.add(tile, sol, UnitType.Carrier, 1);

        RenderedRoll roll = assertRollBodyParity(harness, naalu, sol, tile, CombatRollType.combatround, 1);

        assertTrue(roll.productionMessage().contains("You have _The Crown of Thalnos_ and may reroll the miss"));
    }

    @Test
    void rendersThalnosSelfDestructionNote() {
        Harness harness = new Harness();
        Player naalu = harness.player("naalu");
        Player sol = harness.player("sol");
        Tile tile = harness.tile("19");
        harness.add(tile, naalu, UnitType.Cruiser, 1);
        harness.add(tile, sol, UnitType.Carrier, 1);
        harness.game.setStoredValue("thalnosPlusOne", "true");
        harness.game.setSpecificThalnosUnit(tile.getPosition() + "_space_cruiser", 1);

        try (MockedStatic<DestroyUnitService> ignored = mockStatic(DestroyUnitService.class)) {
            RenderedRoll roll = assertRollBodyParity(harness, naalu, sol, tile, CombatRollType.combatround, 1);

            assertTrue(roll.productionMessage().contains("destroyed 1 of their own"));
            assertTrue(roll.productionMessage().contains("due to a Thalnos miss"));
        }
    }

    @Test
    void rendersLetnevFlagshipSelfRepairNote() {
        Harness harness = new Harness();
        Player letnev = harness.player("letnev");
        Player sol = harness.player("sol");
        Tile tile = harness.tile("19");
        harness.add(tile, letnev, UnitType.Flagship, 1);
        harness.add(tile, sol, UnitType.Carrier, 1);
        tile.addUnitDamage(Constants.SPACE, Units.getUnitKey(UnitType.Flagship, letnev.getColorID()), 1);

        RenderedRoll roll = assertRollBodyParity(harness, letnev, sol, tile, CombatRollType.combatround, 8, 2);

        assertTrue(roll.productionMessage().contains("Repaired the Arc Secundus at start of this combat round"));
    }

    @Test
    void rendersJolNarFlagshipBlueDiceAndBonusHits() {
        Harness harness = new Harness();
        Player jolnar = harness.player("jolnar");
        Player sol = harness.player("sol");
        Tile tile = harness.tile("19");
        harness.add(tile, jolnar, UnitType.Flagship, 1);
        harness.add(tile, sol, UnitType.Carrier, 1);

        RenderedRoll roll = assertRollBodyParity(harness, jolnar, sol, tile, CombatRollType.combatround, 9, 10);

        assertTrue(roll.productionMessage().contains(" - 6 hits"));
    }

    @Test
    void secondHalfOfCombatRollPassesRenderablePayloadToReplayMirror() {
        Harness harness = new Harness();
        Player sol = harness.player("sol");
        Player mentak = harness.player("mentak");
        Tile tile = harness.tile("19");
        harness.add(tile, sol, UnitType.Cruiser, 1);
        harness.add(tile, mentak, UnitType.Carrier, 1);
        GenericInteractionCreateEvent event = mock(GenericInteractionCreateEvent.class);
        when(event.getMessageChannel()).thenReturn(mock(MessageChannel.class));
        CombatReplayService replayService = mock(CombatReplayService.class);

        try (MockedStatic<DiceHelper> dice = mockDice(10);
                MockedStatic<MessageHelper> ignoredMessages = mockStatic(MessageHelper.class);
                MockedStatic<FOWCombatThreadMirroring> ignoredFow = mockStatic(FOWCombatThreadMirroring.class);
                MockedStatic<SpringContext> spring = mockStatic(SpringContext.class)) {
            spring.when(() -> SpringContext.getBean(CombatReplayService.class)).thenReturn(replayService);

            CombatRollService.secondHalfOfCombatRoll(
                    sol, harness.game, event, tile, Constants.SPACE, CombatRollType.combatround, false);
        }

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<CombatRollPayload> payloadCaptor = ArgumentCaptor.forClass(CombatRollPayload.class);
        verify(replayService)
                .mirrorCombatRoll(
                        eq(harness.game),
                        eq(sol),
                        eq(mentak),
                        eq(tile),
                        messageCaptor.capture(),
                        eq(CombatRollType.combatround),
                        anyBoolean(),
                        anyBoolean(),
                        payloadCaptor.capture());

        assertEquals(messageCaptor.getValue(), CombatRollPayloadRenderer.render(payloadCaptor.getValue()));
    }

    private RenderedRoll assertRollBodyParity(
            Harness harness, Player player, Player opponent, Tile tile, CombatRollType rollType, int... diceResults) {
        try (MockedStatic<DiceHelper> ignored = mockDice(diceResults)) {
            return assertRollBodyParity(harness, player, opponent, tile, rollType);
        }
    }

    private RenderedRoll assertRollBodyParity(
            Harness harness, Player player, Player opponent, Tile tile, CombatRollType rollType) {
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        Map<UnitModel, Integer> playerUnits =
                CombatRollService.getUnitsInCombat(tile, space, player, null, rollType, harness.game);
        Map<UnitModel, Integer> opponentUnits =
                CombatRollService.getUnitsInCombat(tile, space, opponent, null, rollType, harness.game);
        TileModel tileModel = tile.getTileModel();
        List<NamedCombatModifierModel> modifiers = CombatModHelper.getModifiers(
                player,
                opponent,
                playerUnits,
                opponentUnits,
                tileModel,
                harness.game,
                rollType,
                Constants.COMBAT_MODIFIERS);
        List<NamedCombatModifierModel> extraRolls = CombatModHelper.getModifiers(
                player,
                opponent,
                playerUnits,
                opponentUnits,
                tileModel,
                harness.game,
                rollType,
                Constants.COMBAT_EXTRA_ROLLS);

        CombatRollService.CombatRollResult result = CombatRollService.rollForUnitsWithResult(
                playerUnits,
                extraRolls,
                modifiers,
                List.of(),
                player,
                opponent,
                harness.game,
                rollType,
                null,
                tile,
                space);
        String combatSummary = CombatMessageHelper.displayCombatSummary(player, tile, space, rollType);
        String productionMessage = combatSummary + result.message();
        CombatRollPayload.RollHeader header =
                buildHeader(harness.game, player, opponent, tile, space, rollType, combatSummary);
        CombatRollPayload payload = result.payload().withHeader(header);
        String rendered = CombatRollPayloadRenderer.render(payload);

        assertEquals(productionMessage, rendered);
        return new RenderedRoll(productionMessage, payload);
    }

    private CombatRollPayload.RollHeader buildHeader(
            Game game,
            Player player,
            Player opponent,
            Tile tile,
            UnitHolder combatOnHolder,
            CombatRollType rollType,
            String combatSummary) {
        String combatDisplayName =
                StringUtils.substringBetween(combatSummary, "rolls for ", " " + MiscEmojis.RollDice + " :");
        return new CombatRollPayload.RollHeader(
                player.getFaction(),
                player.getColor(),
                player.getFactionEmoji(),
                opponent.getFaction(),
                opponent.getColor(),
                tile.getPosition(),
                tile.getTileID(),
                combatOnHolder.getName(),
                combatDisplayName,
                rollType,
                null,
                false,
                game.isFowMode());
    }

    private static MockedStatic<DiceHelper> mockDice(int... results) {
        ArrayDeque<Integer> queuedResults = new ArrayDeque<>();
        for (int result : results) {
            queuedResults.add(result);
        }
        MockedStatic<DiceHelper> dice = mockStatic(DiceHelper.class, CALLS_REAL_METHODS);
        dice.when(() -> DiceHelper.rollDice(anyInt(), anyInt())).thenAnswer(invocation -> {
            int threshold = invocation.getArgument(0);
            int count = invocation.getArgument(1);
            List<DiceHelper.Die> rolledDice = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Integer result = queuedResults.poll();
                if (result == null) {
                    throw new AssertionError("Not enough queued dice for combat roll parity test.");
                }
                rolledDice.add(DiceHelper.spoof(threshold, result));
            }
            return rolledDice;
        });
        return dice;
    }

    private record RenderedRoll(String productionMessage, CombatRollPayload payload) {}

    private static final class Harness {
        private final Game game = new Game();

        private Harness() {
            game.newGameSetup();
            game.setName("Combat Roll Payload Renderer Parity");
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
            if (model.getStartingTech() != null) {
                player.setTechs(model.getStartingTech());
            }
            for (String tech : player.getFactionTechs()) {
                player.addTech(tech);
            }
            for (Leader leader : player.getLeaders()) {
                leader.setLocked(false);
            }
            return player;
        }

        private Tile tile(String tileId) {
            Tile tile = new Tile(tileId, getNextPosition());
            game.setTile(tile);
            game.setActiveSystem(tile.getPosition());
            return tile;
        }

        private void add(Tile tile, Player player, UnitType unitType, int count) {
            tile.addUnit(Constants.SPACE, Units.getUnitKey(unitType, player.getColorID()), count);
        }

        private String getNextPosition() {
            for (String position : PositionMapper.getTilePositions()) {
                if (game.getTileByPosition(position) == null) return position;
            }
            return null;
        }
    }
}
