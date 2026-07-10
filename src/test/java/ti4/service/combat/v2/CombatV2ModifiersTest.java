package ti4.service.combat.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.FactionModel;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollType;
import ti4.service.combat.v2.CombatV2DiceData.AdditionalRollRule;
import ti4.service.combat.v2.CombatV2DiceData.ModifierEffect;
import ti4.service.combat.v2.CombatV2DiceData.RerollRule;
import ti4.service.combat.v2.CombatV2DiceData.RollModifier;
import ti4.service.combat.v2.CombatV2DiceData.RollSource;
import ti4.service.combat.v2.CombatV2DiceData.UnitRollPlan;
import ti4.service.combat.v2.CombatV2DiceData.ValueModifier;
import ti4.service.combat.v2.CombatV2RollData.BombardmentModifiers;
import ti4.service.combat.v2.CombatV2RollData.Context;
import ti4.service.combat.v2.CombatV2RollData.Request;
import ti4.service.combat.v2.CombatV2RollData.ResolvedModifier;
import ti4.service.player.PlayerColorService;
import ti4.testUtils.BaseTi4Test;

class CombatV2ModifiersTest extends BaseTi4Test {

    @Test
    void resolvesNumericalRulesWithoutReadingModifierJson() {
        Harness harness = new Harness();
        Player player = harness.player("sol");
        Player opponent = harness.player("mentak");
        player.addAbility("fragile");
        UnitModel cruiser = player.getPriorityUnitByAsyncID("ca", null);

        UnitRollPlan plan = harness.plan(player, opponent, cruiser, CombatRollType.combatround);

        assertEquals(-1, valueOf(plan.modifiers(), ModifierEffect.TO_HIT));
    }

    @Test
    void resolvesStatRulesFromOwnedUnits() {
        Harness harness = new Harness();
        Player player = harness.player("sol");
        Player opponent = harness.player("mentak");
        player.getUnitsOwned().add("tf-eidolonlandwaster");
        player.getUnitsOwned().add("tf-eidolonterminus");
        UnitModel mech = player.getPriorityUnitByAsyncID("mf", null);

        UnitRollPlan plan = harness.plan(player, opponent, mech, CombatRollType.combatround);

        assertEquals(mech.getCombatDieCountForAbility(CombatRollType.combatround, player) + 1, plan.dicePerUnit());
        assertEquals(
                Math.max(1, mech.getCombatDieHitsOnForAbility(CombatRollType.combatround, player) - 1), plan.hitsOn());
    }

    @Test
    void resolvesAdditionalDiceAndStoredStateRerolls() {
        Harness harness = new Harness();
        Player player = harness.player("sol");
        Player opponent = harness.player("mentak");
        UnitModel sigmaFlagship = Mapper.getUnit("sigma_jolnar_flagship_1");

        UnitRollPlan flagshipPlan = harness.plan(player, opponent, sigmaFlagship, CombatRollType.combatround);
        AdditionalRollRule additional = flagshipPlan.additionalRolls().getFirst();
        assertEquals(RollSource.SIGMA_JOL_NAR_FLAGSHIP, additional.source());
        assertTrue(additional.repeat());

        harness.game.setStoredValue("munitionsReserves", player.getFaction());
        UnitModel cruiser = player.getPriorityUnitByAsyncID("ca", null);
        UnitRollPlan cruiserPlan = harness.plan(player, opponent, cruiser, CombatRollType.combatround);
        assertTrue(
                cruiserPlan.rerolls().stream().map(RerollRule::source).anyMatch(RollSource.MUNITIONS_RESERVES::equals));
    }

    @Test
    void selectsProximaHitRerollsInsideTheInlineRule() {
        Harness harness = new Harness();
        Player player = harness.player("sol");
        player.addTech("tf-tacticalbrilliance");
        player.addTech("proxima");
        UnitModel dreadnought = player.getPriorityUnitByAsyncID("dn", null);

        UnitRollPlan plan = harness.plan(player, player, dreadnought, CombatRollType.bombardment);

        assertTrue(plan.rerolls().stream().map(RerollRule::source).anyMatch(RollSource.JOL_NAR_COMMANDER_HITS::equals));
    }

    @Test
    void replacesScopeExclusionsWithTypedUnitMatching() {
        Harness harness = new Harness();
        Player player = harness.player("sol");
        Player opponent = harness.player("mentak");
        UnitModel flagship = Mapper.getUnit("sardakk_flagship");
        UnitModel cruiser = player.getPriorityUnitByAsyncID("ca", null);

        Map<UnitModel, UnitRollPlan> plans =
                harness.plans(player, opponent, List.of(flagship, cruiser), CombatRollType.combatround);

        String rule = "plus_1_always_other_units_with_flagship";
        assertFalse(plans.get(flagship).modifiers().stream().anyMatch(modifier -> rule.equals(modifier.id())));
        assertTrue(plans.get(cruiser).modifiers().stream().anyMatch(modifier -> rule.equals(modifier.id())));
    }

    @Test
    void resolvesControlNetworkEntirelyFromRegisteredStateEligibility() {
        Harness harness = new Harness();
        Player player = harness.player("sol");
        Player opponent = harness.player("mentak");
        player.addAbility("control_network");
        UnitModel pds = player.getPriorityUnitByAsyncID("pd", null);
        String suffix = player.getFaction();
        harness.game.setStoredValue("controlNetworkSpaceCannonTile" + suffix, harness.tile.getTileID());
        harness.game.setStoredValue("controlNetworkSpaceCannonHolder" + suffix, Constants.SPACE);
        harness.game.setStoredValue(
                "controlNetworkSpaceCannonRoll" + suffix, CombatRollType.SpaceCannonOffence.toString());

        UnitRollPlan plan = harness.plan(player, opponent, pds, CombatRollType.SpaceCannonOffence);

        assertTrue(plan.modifiers().stream().anyMatch(modifier -> "netrunners_control_network".equals(modifier.id())));
        assertEquals(-1, valueOf(plan.modifiers(), ModifierEffect.TO_HIT));
    }

    @Test
    void resolvesAdvancedTargetingSystemsEntirelyFromRegisteredStateEligibility() {
        Harness harness = new Harness();
        Player player = harness.player("sol");
        Player opponent = harness.player("mentak");
        player.addTech("beironats");
        UnitHolder planet = harness.tile.getPlanetUnitHolders().getFirst();
        UnitModel mech = player.getPriorityUnitByAsyncID("mf", null);
        String suffix = player.getFaction();
        harness.game.setStoredValue("ironATSActiveTile_" + suffix, harness.tile.getPosition());
        harness.game.setStoredValue("ironATSActiveOpponent_" + suffix, opponent.getFaction());
        harness.game.setStoredValue("ironATSBoundHolder_" + suffix, planet.getName());

        UnitRollPlan plan = harness.plan(player, opponent, mech, CombatRollType.combatround, planet);

        assertTrue(plan.modifiers().stream()
                .anyMatch(modifier -> "iron_advanced_targeting_systems".equals(modifier.id())));
        assertEquals(1, valueOf(plan.modifiers(), ModifierEffect.EXTRA_DICE));
    }

    private static int valueOf(List<RollModifier> modifiers, ModifierEffect effect) {
        return modifiers.stream()
                .filter(ValueModifier.class::isInstance)
                .map(ValueModifier.class::cast)
                .filter(modifier -> modifier.effect() == effect)
                .mapToInt(ValueModifier::value)
                .sum();
    }

    private static final class Harness {
        private final Game game = new Game();
        private final GenericInteractionCreateEvent event = mock(GenericInteractionCreateEvent.class);
        private final Tile tile = new Tile("19", "101");

        private Harness() {
            game.newGameSetup();
            game.setName("Combat V2 Modifier Registry Test");
            game.setCcNPlasticLimit(false);
            game.setTile(tile);
            game.setActiveSystem(tile.getPosition());
            when(event.getMessageChannel()).thenReturn(mock(MessageChannel.class));
        }

        private Player player(String faction) {
            FactionModel model = Mapper.getFaction(faction);
            Player player = game.addPlayer(model.getAlias(), model.getFactionName());
            player.setFaction(game, faction);
            player.setFactionEmoji("<" + faction + ">");
            player.setColor(PlayerColorService.getPreferredColor(player));
            player.setUnitsOwned(new HashSet<>(model.getUnits()));
            player.setAbilities(new HashSet<>(model.getAbilities()));
            player.setTechs(model.getStartingTech());
            return player;
        }

        private UnitRollPlan plan(Player player, Player opponent, UnitModel unit, CombatRollType rollType) {
            return plan(player, opponent, unit, rollType, tile.getSpaceUnitHolder());
        }

        private UnitRollPlan plan(
                Player player, Player opponent, UnitModel unit, CombatRollType rollType, UnitHolder holder) {
            return plans(player, opponent, List.of(unit), rollType, holder).get(unit);
        }

        private Map<UnitModel, UnitRollPlan> plans(
                Player player, Player opponent, List<UnitModel> unitModels, CombatRollType rollType) {
            return plans(player, opponent, unitModels, rollType, tile.getSpaceUnitHolder());
        }

        private Map<UnitModel, UnitRollPlan> plans(
                Player player,
                Player opponent,
                List<UnitModel> unitModels,
                CombatRollType rollType,
                UnitHolder holder) {
            Request request = new Request(player, game, event, tile, holder.getName());
            Map<UnitModel, Integer> units = new LinkedHashMap<>();
            Map<Pair<UnitModel, UnitHolder>, Integer> rollingUnits = new LinkedHashMap<>();
            for (UnitModel unit : unitModels) {
                units.put(unit, 1);
                rollingUnits.put(Pair.of(unit, holder), 1);
            }
            ModifierInputs inputs = new ModifierInputs(player, game, tile, rollType, holder, opponent, units, Map.of());
            BombardmentModifiers bombardment = CombatV2Modifiers.bombardmentModifiers(inputs);
            List<ResolvedModifier> modifiers = CombatV2Modifiers.resolve(inputs, bombardment);
            Context context = new Context(
                    request.player(),
                    request.game(),
                    request.event(),
                    request.tile(),
                    request.unitHolderName(),
                    rollType,
                    false,
                    holder,
                    opponent,
                    rollingUnits,
                    Map.of(),
                    modifiers,
                    bombardment,
                    List.of());
            Map<UnitModel, UnitRollPlan> plans = new LinkedHashMap<>();
            for (var rollingUnit : rollingUnits.entrySet()) {
                UnitModel unit = rollingUnit.getKey().getLeft();
                plans.put(
                        unit,
                        new UnitRollPlan(
                                unit,
                                rollingUnit.getKey().getRight(),
                                1,
                                unit.getCombatDieCountForAbility(rollType, player),
                                unit.getCombatDieHitsOnForAbility(rollType, player),
                                RollSource.PRIMARY,
                                CombatV2Modifiers.forUnit(context, rollingUnit)));
            }
            return plans;
        }
    }
}
