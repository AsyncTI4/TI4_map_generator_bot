package ti4.service.unit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

class CaptureUnitService {

    public static List<Player> listCapturingMechPlayers(
            Game game, List<RemovedUnit> allUnits, RemovedUnit removedUnitType) {
        if (removedUnitType.unitKey().getUnitType() != UnitType.Infantry) return List.of();
        if (!(removedUnitType.uh() instanceof Planet planet)) return List.of();
        if (ButtonHelper.isLawInPlay(game, "articles_war")) return List.of();
        Player destroyedPlayer = removedUnitType.getPlayer(game);
        if (destroyedPlayer == null) return List.of();

        List<Player> capturing = new ArrayList<>();
        for (Player player : game.getRealPlayers()) {
            if (!player.hasUnit("cabal_mech")) continue;
            if (planet.getUnitCount(UnitType.Mech, player) == 0) continue;
            capturing.add(player);
        }
        return capturing;
    }

    public static List<Player> listCapturingFlagshipPlayers(
            Game game, List<RemovedUnit> allUnits, RemovedUnit removed) {
        Tile tile = removed.tile();

        // "sigma_vuilraith_flagship_1" does not capture your own units
        List<Player> cabals = game.getRealPlayers().stream()
                .filter(p -> p.hasUnit("cabal_flagship") || p.hasUnit("sigma_vuilraith_flagship_2"))
                .toList();
        List<Player> cabalsWithFs = new ArrayList<>();
        for (Player p : cabals) {
            // Flagship cannot capture itself
            if (p.unitBelongsToPlayer(removed.unitKey()) && removed.unitKey().getUnitType() == UnitType.Flagship)
                continue;

            // If the flagship was not destroyed
            if (tile.getSpaceUnitHolder().getUnitCount(UnitType.Flagship, p) > 0) {
                cabalsWithFs.add(p);
                continue;
            }

            // Or if the flagship was destroyed
            for (RemovedUnit rm : allUnits) {
                if (!p.unitBelongsToPlayer(rm.unitKey())) continue;
                if (rm.unitKey().getUnitType() != UnitType.Flagship) continue;
                cabalsWithFs.add(p);
                break;
            }
        }

        return cabalsWithFs;
    }

    public static List<Player> listCapturingCombatPlayers(Game game, RemovedUnit removed) {
        UnitHolder combatOnHolder = removed.uh();
        Set<String> counted = new HashSet<>();
        List<Player> playersWithDevour = new ArrayList<>();
        for (UnitKey key : combatOnHolder.getUnitKeys()) {
            if (!counted.add(key.getColorID())) continue;

            Player p2 = game.getPlayerByUnitKey(key).orElse(null);
            if (p2 != null && p2 != removed.getPlayer(game) && p2.hasAbility("devour")) {
                playersWithDevour.add(p2);
            }
        }
        return playersWithDevour;
    }

    public static List<Player> listProbableKiller(Game game, RemovedUnit removed) {
        UnitHolder combatOnHolder = removed.uh();
        Set<String> counted = new HashSet<>();
        List<Player> playerOpponents = new ArrayList<>();
        Player owner = removed.getPlayer(game);
        for (UnitKey key : combatOnHolder.getUnitKeys()) {
            if (!counted.add(key.getColorID())) continue;

            Player p2 = game.getPlayerByUnitKey(key).orElse(null);
            if (p2 != null && p2 != owner && !p2.getAllianceMembers().contains(owner.getFaction())) {
                playerOpponents.add(p2);
            }
        }
        if (owner != game.getActivePlayer()
                && game.getActivePlayer() != null
                && !game.getActivePlayer().getAllianceMembers().contains(owner.getFaction())) {
            playerOpponents.add(game.getActivePlayer());
        }
        return playerOpponents;
    }

    public static void executeCapture(GenericInteractionCreateEvent event, Game game, Player cabal, RemovedUnit unit) {
        Player player = unit.getPlayer(game);
        String name = unit.unitKey().unitName();
        int amt = unit.getTotalRemoved();
        ButtonHelperFactionSpecific.cabalEatsUnit(player, game, cabal, amt, name, event);
    }
}
