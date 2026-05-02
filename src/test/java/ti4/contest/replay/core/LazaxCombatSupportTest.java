package ti4.contest.replay.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.contest.replay.core.CombatReplayDecoys.Decoy;
import ti4.contest.replay.core.CombatReplayDecoys.DecoyUnit;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.model.FactionModel;
import ti4.testUtils.BaseTi4Test;

class LazaxCombatSupportTest extends BaseTi4Test {

    @Test
    void combatSummaryDoesNotRenderQuietusEffectWhenQuietusIsOnlyOwned() {
        Harness harness = new Harness();
        Player attacker = harness.player("sol", "blue");
        Player defender = harness.player("sardakk", "green");
        harness.player("crimson", "red");

        Tile combatTile = harness.tile("112");
        combatTile.addToken(Constants.TOKEN_BREACH_ACTIVE, Constants.SPACE);

        String summary = LazaxCombatSupport.formatCombatTechSummary(combatTile, attacker, defender);

        assertFalse(summary.contains("Quietus"));
    }

    @Test
    void combatSummaryRendersQuietusEffectWhenQuietusIsOnAnActiveBreach() {
        Harness harness = new Harness();
        Player attacker = harness.player("sol", "blue");
        Player defender = harness.player("sardakk", "green");
        Player crimson = harness.player("crimson", "red");

        Tile combatTile = harness.tile("112");
        combatTile.addToken(Constants.TOKEN_BREACH_ACTIVE, Constants.SPACE);
        Tile quietusTile = harness.tile("94");
        quietusTile.addToken(Constants.TOKEN_BREACH_ACTIVE, Constants.SPACE);
        quietusTile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Flagship, crimson.getColorID()), 1);

        String summary = LazaxCombatSupport.formatCombatTechSummary(combatTile, attacker, defender);

        assertTrue(summary.contains("Quietus: active Breach in play"));
    }

    @Test
    void combatSummaryIncludesWarSunTechForReplayWarSunDecoy() {
        Harness harness = new Harness();
        Player attacker = harness.player("sol", "blue");
        Player defender = harness.player("sardakk", "green");
        Tile combatTile = harness.tile("112");
        CombatReplayDecoys.Abilities abilities = new CombatReplayDecoys.Abilities(new Decoy(List.of(
                new DecoyUnit(attacker.getFaction(), attacker.getFactionEmoji(), "blu", UnitType.Warsun, "space", 1))));

        String summary = LazaxCombatSupport.formatCombatTechSummary(combatTile, attacker, defender, abilities);

        assertTrue(summary.contains("War Sun"));
    }

    private static final class Harness {
        private final Game game = new Game();

        private Harness() {
            game.newGameSetup();
            game.setName("Lazax Combat Support Test");
        }

        private Player player(String faction, String color) {
            FactionModel model = Mapper.getFaction(faction);
            Player player = game.addPlayer(model.getAlias(), model.getFactionName());
            player.setFaction(game, faction);
            player.setFactionEmoji("<" + faction + ">");
            player.setColor(color);
            player.setUnitsOwned(new HashSet<>(model.getUnits()));
            return player;
        }

        private Tile tile(String tileId) {
            Tile tile = new Tile(tileId, getNextPosition());
            game.setTile(tile);
            return tile;
        }

        private String getNextPosition() {
            for (String position : PositionMapper.getTilePositions()) {
                if (game.getTileByPosition(position) == null) return position;
            }
            return null;
        }
    }
}
