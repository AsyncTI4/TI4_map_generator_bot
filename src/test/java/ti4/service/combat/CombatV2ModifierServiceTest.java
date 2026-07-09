package ti4.service.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.BombardmentAssignment;
import ti4.helpers.BombardmentAssignmentType;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.json.JsonMapperManager;
import ti4.model.CombatModifierModel;
import ti4.model.CombatModifierRelatedModel;
import ti4.model.FactionModel;
import ti4.model.TemporaryCombatModifierModel;
import ti4.model.UnitModel;
import ti4.service.combat.CombatV2RollData.Context;
import ti4.service.combat.CombatV2RollData.Modifiers;
import ti4.service.combat.CombatV2RollData.Request;
import ti4.service.combat.CombatV2RollData.UnitModifiers;
import ti4.service.player.PlayerColorService;
import ti4.testUtils.BaseTi4Test;

class CombatV2ModifierServiceTest extends BaseTi4Test {

    @Test
    void selectingTemporaryModifiersDoesNotConsumeGameState() {
        Harness harness = new Harness();
        Player player = harness.player("sol");
        String ability = player.getAbilities().iterator().next();
        CombatModifierModel combat = modifier("v2_temp_combat", Constants.COMBAT_MODIFIERS, 1, ability);
        combat.setPersistenceType(Constants.MOD_TEMP_ONE_ROUND.toString());
        CombatModifierModel barrage = modifier("v2_temp_afb", Constants.COMBAT_MODIFIERS, 1, ability);
        barrage.setPersistenceType(Constants.MOD_TEMP_ONE_ROUND.toString());
        barrage.setForCombatAbility(CombatRollType.AFB);
        player.addTempCombatMod(new TemporaryCombatModifierModel(Constants.ABILITY, ability, combat, 0));
        player.addTempCombatMod(new TemporaryCombatModifierModel(Constants.ABILITY, ability, barrage, 0));

        List<?> selected = CombatV2ModifierService.currentTemporaryModifiers(player, false, CombatRollType.combatround);

        assertEquals(1, selected.size());
        assertEquals(2, player.getTempCombatModifiers().size());
    }

    @Test
    void indexesAllDefinitionsForTheSameSourceAndCalculatesTheUnitOnce() {
        Harness harness = new Harness();
        Player player = harness.player("sol");
        Player opponent = harness.player("mentak");
        Tile tile = harness.tile("19");
        UnitHolder holder = tile.getSpaceUnitHolder();
        UnitModel cruiser = player.getPriorityUnitByAsyncID("ca", null);
        String ability = player.getAbilities().iterator().next();

        CombatModifierModel first = modifier("v2_test_a", Constants.COMBAT_MODIFIERS, 1, ability);
        CombatModifierModel second = modifier("v2_test_b", Constants.COMBAT_MODIFIERS, 2, ability);
        CombatModifierModel extra = modifier("v2_test_c", Constants.COMBAT_EXTRA_ROLLS, 1, ability);
        extra.setApplyEachForQuantity(true);
        Map<String, CombatModifierModel> definitions = new HashMap<>(Mapper.getCombatModifiers());
        definitions.put(first.getAlias(), first);
        definitions.put(second.getAlias(), second);
        definitions.put(extra.getAlias(), extra);

        try (MockedStatic<Mapper> mapper = mockStatic(Mapper.class, CALLS_REAL_METHODS)) {
            mapper.when(Mapper::getCombatModifiers).thenReturn(definitions);
            Map<UnitModel, Integer> units = Map.of(cruiser, 2);
            Request request = harness.request(player, tile, Constants.SPACE);
            Modifiers modifiers = CombatV2ModifierService.resolve(
                    new ModifierInputs(request, CombatRollType.combatround, holder, opponent, units, Map.of()));

            List<String> aliases = modifiers.applied().stream()
                    .filter(applied -> applied.ruleId().startsWith("v2_test_"))
                    .map(CombatV2RollData.AppliedModifier::ruleId)
                    .toList();
            assertIterableEquals(List.of("v2_test_a", "v2_test_b", "v2_test_c"), aliases);

            Context context = new Context(
                    request,
                    CombatRollType.combatround,
                    false,
                    holder,
                    opponent,
                    Map.of(Pair.of(cruiser, holder), 2),
                    units,
                    Map.of(),
                    modifiers,
                    List.of());
            UnitModifiers unitModifiers = CombatV2ModifierService.forUnit(context, cruiser, 2, holder);
            assertEquals(3, unitModifiers.toHit());
            assertEquals(2, unitModifiers.extraDice());
        }
    }

    @Test
    void unknownConditionsFailClosed() {
        Harness harness = new Harness();
        Player player = harness.player("sol");
        Player opponent = harness.player("mentak");
        Tile tile = harness.tile("19");
        UnitModel cruiser = player.getPriorityUnitByAsyncID("ca", null);
        String ability = player.getAbilities().iterator().next();

        CombatModifierModel typo = modifier("v2_unknown_condition", Constants.COMBAT_MODIFIERS, 9, ability);
        typo.setCondition("definitely_not_a_real_condition");
        Map<String, CombatModifierModel> definitions = new HashMap<>(Mapper.getCombatModifiers());
        definitions.put(typo.getAlias(), typo);

        try (MockedStatic<Mapper> mapper = mockStatic(Mapper.class, CALLS_REAL_METHODS)) {
            mapper.when(Mapper::getCombatModifiers).thenReturn(definitions);
            Modifiers modifiers = CombatV2ModifierService.resolve(new ModifierInputs(
                    harness.request(player, tile, Constants.SPACE),
                    CombatRollType.combatround,
                    tile.getSpaceUnitHolder(),
                    opponent,
                    Map.of(cruiser, 1),
                    Map.of()));

            assertFalse(
                    modifiers.applied().stream().anyMatch(applied -> "v2_unknown_condition".equals(applied.ruleId())));
        }
    }

    @Test
    void compilesBombardmentAssignmentsForFilteringAndUnitValues() {
        Harness harness = new Harness();
        Player player = harness.player("sol");
        Player opponent = harness.player("mentak");
        Tile tile = harness.tile("19");
        UnitModel dreadnought = player.getPriorityUnitByAsyncID("dn", null);
        String target = tile.getPlanetUnitHolders().getFirst().getName();

        CombatModifierModel plasma =
                customModifier("plus1_roll_plasmascoring", Constants.COMBAT_EXTRA_ROLLS, 1, CombatRollType.bombardment);
        Map<String, CombatModifierModel> definitions = new HashMap<>(Mapper.getCombatModifiers());
        definitions.put(plasma.getAlias(), plasma);
        harness.game.setStoredValue("bombardmentTarget" + player.getFaction(), target);
        harness.game.setStoredValue(
                "assignedBombardment" + player.getFaction(),
                JsonMapperManager.basic()
                        .writeValueAsString(List.of(new BombardmentAssignment(
                                "plasmascoring", target, false, BombardmentAssignmentType.TECH))));

        try (MockedStatic<Mapper> mapper = mockStatic(Mapper.class, CALLS_REAL_METHODS)) {
            mapper.when(Mapper::getCombatModifiers).thenReturn(definitions);
            Modifiers modifiers = CombatV2ModifierService.resolve(new ModifierInputs(
                    harness.request(player, tile, Constants.SPACE),
                    CombatRollType.bombardment,
                    tile.getSpaceUnitHolder(),
                    opponent,
                    Map.of(dreadnought, 1),
                    Map.of()));

            assertTrue(modifiers.bombardment().assignmentsPresent());
            assertTrue(modifiers.bombardment().hasSource("plasmascoring"));
            assertTrue(modifiers.applied().stream()
                    .anyMatch(applied -> "plus1_roll_plasmascoring".equals(applied.ruleId())));
        }
    }

    private static CombatModifierModel modifier(String alias, String type, int value, String ability) {
        CombatModifierRelatedModel related = new CombatModifierRelatedModel();
        related.setType(Constants.ABILITY);
        related.setAlias(ability);

        CombatModifierModel modifier = new CombatModifierModel();
        modifier.setAlias(alias);
        modifier.setType(type);
        modifier.setValue(value);
        modifier.setPersistenceType("ALWAYS");
        modifier.setRelated(List.of(related));
        modifier.setForCombatAbility(CombatRollType.combatround);
        modifier.setScope("");
        return modifier;
    }

    private static CombatModifierModel customModifier(String alias, String type, int value, CombatRollType rollType) {
        CombatModifierRelatedModel related = new CombatModifierRelatedModel();
        related.setType(Constants.CUSTOM);
        related.setAlias(Constants.CUSTOM);
        related.setMessage(alias);

        CombatModifierModel modifier = new CombatModifierModel();
        modifier.setAlias(alias);
        modifier.setType(type);
        modifier.setValue(value);
        modifier.setPersistenceType("ALWAYS");
        modifier.setRelated(List.of(related));
        modifier.setForCombatAbility(rollType);
        modifier.setScope("");
        return modifier;
    }

    private static final class Harness {
        private final Game game = new Game();
        private final GenericInteractionCreateEvent event = mock(GenericInteractionCreateEvent.class);

        private Harness() {
            game.newGameSetup();
            game.setName("Combat V2 Modifier Service Test");
            game.setCcNPlasticLimit(false);
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

        private Tile tile(String tileId) {
            Tile tile = new Tile(tileId, "101");
            game.setTile(tile);
            game.setActiveSystem(tile.getPosition());
            return tile;
        }

        private Request request(Player player, Tile tile, String holder) {
            return new Request(player, game, event, tile, holder);
        }
    }
}
