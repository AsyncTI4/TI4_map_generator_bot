package ti4.service.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.helpers.DiceHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.testUtils.BaseTi4Test;

class CombatUnitCatalogSmokeTest extends BaseTi4Test {

    @Test
    void everyRegisteredUnitExecutesEveryPrintedRollAbility() {
        int executedProfiles = 0;
        try (MockedStatic<DiceHelper> dice = mockStatic(DiceHelper.class, CALLS_REAL_METHODS);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            dice.when(() -> DiceHelper.rollDice(anyInt(), anyInt())).thenAnswer(invocation -> {
                int threshold = invocation.getArgument(0);
                int count = invocation.getArgument(1);
                return java.util.stream.IntStream.range(0, count)
                        .mapToObj(index -> DiceHelper.spoof(threshold, 10))
                        .toList();
            });

            for (UnitModel model : Mapper.getUnits().values()) {
                validateIdentity(model);
                for (CombatRollType rollType : CombatRollType.values()) {
                    try {
                        executedProfiles += executePrintedAbility(model, rollType);
                    } catch (Throwable failure) {
                        throw new AssertionError(
                                "Catalog combat execution failed for " + profile(model, rollType), failure);
                    }
                }
            }
        }
        assertTrue(executedProfiles > Mapper.getUnits().size(), "the matrix must exercise multi-ability unit models");
    }

    private static int executePrintedAbility(UnitModel model, CombatRollType rollType) {
        CombatRollTestSupport.Harness harness = CombatRollTestSupport.harness();
        Player player = harness.player("sol");
        Player opponent = harness.player("mentak");
        player.setUnitsOwned(new HashSet<>(List.of(model.getId(), "destroyer", "cruiser", "dreadnought")));
        Tile tile = harness.tile("19");
        UnitHolder holder = selectHolder(tile, model, rollType);
        int dice = model.getCombatDieCountForAbility(rollType, player);
        int threshold = model.getCombatDieHitsOnForAbility(rollType, player);
        if (dice <= 0) return 0;

        assertTrue(dice < 100, () -> profile(model, rollType) + " has an implausible printed die count: " + dice);
        assertTrue(
                threshold >= 0 && threshold <= 10,
                () -> profile(model, rollType) + " has an invalid printed threshold: " + threshold);
        Map<Pair<UnitModel, UnitHolder>, Integer> units = new LinkedHashMap<>();
        units.put(Pair.of(model, holder), 1);
        CombatContext combat =
                new CombatContext(player, harness.game, harness.event, tile, holder.getName(), rollType, false);
        combat.setCombatOnHolder(holder);
        combat.setPlayerUnits(units);
        combat.setOpponent(opponent);
        combat.setModifiers(new CombatRollModifiers(List.of(), List.of(), List.of()));

        CombatRollResult result = UnitRollExecution.rollForUnitsWithResult(combat);

        CombatRollTestSupport.assertThat(result).completed();
        assertFalse(result.payload().unitRolls().isEmpty(), () -> profile(model, rollType) + " selected no roll");
        assertTrue(
                result.payload().unitRolls().stream()
                        .anyMatch(roll -> model.getId().equals(roll.unitId())),
                () -> profile(model, rollType) + " disappeared from its payload");
        return 1;
    }

    private static UnitHolder selectHolder(Tile tile, UnitModel model, CombatRollType rollType) {
        if (rollType == CombatRollType.combatround
                && Boolean.TRUE.equals(model.getIsGroundForce())
                && !Boolean.TRUE.equals(model.getIsShip())) {
            return tile.getPlanetUnitHolders().getFirst();
        }
        return tile.getUnitHolders().get(Constants.SPACE);
    }

    private static void validateIdentity(UnitModel model) {
        assertNotNull(model, "the unit registry cannot contain null models");
        assertFalse(model.getId().isBlank(), "registered units require an id");
        assertFalse(model.getBaseType().isBlank(), () -> model.getId() + " requires a base type");
        assertFalse(model.getAsyncId().isBlank(), () -> model.getId() + " requires an async id");
    }

    private static String profile(UnitModel model, CombatRollType rollType) {
        return model.getId() + "/" + rollType;
    }
}
