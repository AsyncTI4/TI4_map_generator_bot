package ti4.service.combat.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.BombardmentAssignment;
import ti4.helpers.BombardmentAssignmentType;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.json.JsonMapperManager;
import ti4.model.FactionModel;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollType;
import ti4.service.combat.CombatService;
import ti4.service.combat.v2.CombatV2RollData.BombardmentModifiers;
import ti4.service.combat.v2.CombatV2RollData.Context;
import ti4.service.combat.v2.CombatV2RollData.Request;
import ti4.service.combat.v2.CombatV2RollData.ResolvedModifier;
import ti4.service.player.PlayerColorService;
import ti4.testUtils.BaseTi4Test;

class CombatV2ModifierLifecycleTest extends BaseTi4Test {

    @Test
    void modifierActivationRouterLeavesDisabledGamesOnTheExistingPath() {
        Harness harness = new Harness();
        Player player = harness.player("sol");

        assertTrue(CombatService.activateModifier(harness.game, player, Constants.AC, "mb1"));

        assertEquals(1, player.getNewTempCombatModifiers().size());
        assertTrue(player.getCombatModifierActivations().isEmpty());
    }

    @Test
    void changingTheToggleDoesNotTranslateOrDiscardPendingModifierState() {
        Harness harness = new Harness();
        Player player = harness.player("sol");
        assertTrue(CombatService.activateModifier(harness.game, player, Constants.AC, "mb1"));

        CombatV2Config.setEnabled(harness.game, true);
        assertEquals(1, player.getNewTempCombatModifiers().size());
        assertTrue(player.getCombatModifierActivations().isEmpty());
        assertTrue(CombatService.activateModifier(harness.game, player, Constants.AC, "mb1"));
        assertEquals(1, player.getCombatModifierActivations().size());

        CombatV2Config.setEnabled(harness.game, false);
        assertEquals(1, player.getCombatModifierActivations().size());
        assertEquals(1, player.getNewTempCombatModifiers().size());
    }

    @Test
    void registryActivationIsSelectedThenConsumedAfterTheRoll() {
        Harness harness = new Harness();
        Player player = harness.player("sol");
        Player opponent = harness.player("mentak");
        Tile tile = harness.tile("19");
        UnitModel cruiser = player.getPriorityUnitByAsyncID("ca", null);
        CombatV2Config.setEnabled(harness.game, true);
        assertTrue(CombatService.canActivateModifier(harness.game, Constants.TECH, "sc"));
        assertTrue(CombatService.activateModifier(harness.game, player, Constants.TECH, "sc"));
        assertTrue(player.getTempCombatModifiers().isEmpty());

        ModifierInputs inputs = new ModifierInputs(
                player,
                harness.game,
                tile,
                CombatRollType.combatround,
                tile.getSpaceUnitHolder(),
                opponent,
                Map.of(cruiser, 1),
                Map.of());
        BombardmentModifiers bombardment = CombatV2Modifiers.bombardmentModifiers(inputs);
        List<ResolvedModifier> selected = CombatV2Modifiers.resolve(inputs, bombardment);

        assertTrue(selected.stream().anyMatch(modifier -> "plus1_1round_all".equals(modifier.ruleId())));
        assertEquals(1, player.getCombatModifierActivations().size());
        CombatModifierActivation activation =
                player.getCombatModifierActivations().getFirst();
        assertEquals(activation, CombatModifierActivation.fromSaveString(activation.getSaveString()));

        Request request = harness.request(player, tile, Constants.SPACE);
        Context context = new Context(
                request.player(),
                request.game(),
                request.event(),
                request.tile(),
                request.unitHolderName(),
                CombatRollType.combatround,
                false,
                tile.getSpaceUnitHolder(),
                opponent,
                Map.of(Pair.of(cruiser, tile.getSpaceUnitHolder()), 1),
                Map.of(),
                selected,
                bombardment,
                List.of());
        CombatV2Modifiers.consumeActivations(context);

        assertTrue(player.getCombatModifierActivations().isEmpty());
    }

    @Test
    void combatDurationComesFromTheRegistryAndBindsTheActivation() {
        Harness harness = new Harness();
        Player player = harness.player("sol");
        Player opponent = harness.player("mentak");
        Tile tile = harness.tile("19");
        var holder = tile.getPlanetUnitHolders().getFirst();
        UnitModel infantry = player.getPriorityUnitByAsyncID("gf", null);
        CombatV2Config.setEnabled(harness.game, true);
        assertTrue(CombatService.activateModifier(harness.game, player, Constants.PROMISSORY_NOTES, "tekklar"));

        Request request = harness.request(player, tile, holder.getName());
        ModifierInputs inputs = new ModifierInputs(
                request.player(),
                request.game(),
                request.tile(),
                CombatRollType.combatround,
                holder,
                opponent,
                Map.of(infantry, 1),
                Map.of());
        BombardmentModifiers bombardment = CombatV2Modifiers.bombardmentModifiers(inputs);
        List<ResolvedModifier> modifiers = CombatV2Modifiers.resolve(inputs, bombardment);
        Context context = new Context(
                request.player(),
                request.game(),
                request.event(),
                request.tile(),
                request.unitHolderName(),
                CombatRollType.combatround,
                false,
                holder,
                opponent,
                Map.of(Pair.of(infantry, holder), 1),
                Map.of(),
                modifiers,
                bombardment,
                List.of());
        CombatV2Modifiers.consumeActivations(context);

        CombatModifierActivation activation =
                player.getCombatModifierActivations().getFirst();
        assertEquals(tile.getTileID(), activation.systemId());
        assertEquals(holder.getName(), activation.holderName());
    }

    @Test
    void compilesBombardmentAssignmentsForFilteringAndUnitValues() {
        Harness harness = new Harness();
        Player player = harness.player("sol");
        Player opponent = harness.player("mentak");
        player.addTech("ps");
        Tile tile = harness.tile("19");
        UnitModel dreadnought = player.getPriorityUnitByAsyncID("dn", null);
        String target = tile.getPlanetUnitHolders().getFirst().getName();

        harness.game.setStoredValue("bombardmentTarget" + player.getFaction(), target);
        harness.game.setStoredValue(
                "assignedBombardment" + player.getFaction(),
                JsonMapperManager.basic()
                        .writeValueAsString(List.of(new BombardmentAssignment(
                                "plasmascoring", target, false, BombardmentAssignmentType.TECH))));

        ModifierInputs inputs = new ModifierInputs(
                player,
                harness.game,
                tile,
                CombatRollType.bombardment,
                tile.getSpaceUnitHolder(),
                opponent,
                Map.of(dreadnought, 1),
                Map.of());
        BombardmentModifiers bombardment = CombatV2Modifiers.bombardmentModifiers(inputs);
        List<ResolvedModifier> modifiers = CombatV2Modifiers.resolve(inputs, bombardment);

        assertTrue(bombardment.assignmentsPresent());
        assertTrue(bombardment.hasSource("plasmascoring"));
        assertTrue(modifiers.stream().anyMatch(modifier -> "plus1_roll_plasmascoring".equals(modifier.ruleId())));
    }

    private static final class Harness {
        private final Game game = new Game();
        private final GenericInteractionCreateEvent event = mock(GenericInteractionCreateEvent.class);

        private Harness() {
            game.newGameSetup();
            game.setName("Combat V2 Modifier Lifecycle Test");
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
