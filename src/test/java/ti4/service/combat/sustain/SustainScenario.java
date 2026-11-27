package ti4.service.combat.sustain;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;

/**
 * A one-tile board (Wellon, position 101) to put units on and ask every sustain path the same question: which of the
 * owner's units may use SUSTAIN DAMAGE here? Each path answers with the unit types it would sustain.
 */
final class SustainScenario {

    static final String POSITION = "101";
    static final String PLANET = "wellon";
    static final String SPACE = Constants.SPACE;

    private static final int SAFETY_LIMIT = 30;

    final Game game = new Game();
    final Tile tile = new Tile("19", POSITION);

    SustainScenario() {
        game.setTile(tile);
    }

    Player addPlayer(String color, String faction, String... ownedUnits) {
        Player player = game.addPlayer(color + "Id", color + "User");
        player.setFaction(faction);
        player.setColor(color);
        player.setFactionEmoji("a");
        for (String unit : ownedUnits) {
            player.addOwnedUnitByID(unit);
        }
        return player;
    }

    void place(Player player, String holder, UnitType type, int count) {
        tile.addUnit(holder, Units.getUnitKey(type, player.getColorID()), count);
    }

    // An opponent with ships here and no action cards, so auto-assign does not hold back for Direct Hit.
    Player addOpponent(String color, String faction, String... ownedUnits) {
        Player opponent = addPlayer(color, faction, ownedUnits);
        opponent.addOwnedUnitByID("destroyer");
        place(opponent, SPACE, UnitType.Destroyer, 1);
        return opponent;
    }

    Set<UnitType> manual(Player owner, String assignHitsType, String holder) {
        Set<UnitType> sustainable = EnumSet.noneOf(UnitType.class);
        List<Button> buttons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(owner, game, tile, assignHitsType);
        for (Button button : buttons) {
            String id = button.getCustomId();
            int start = id.indexOf("assignDamage_");
            if (start < 0) continue;
            List<String> parts =
                    List.of(id.substring(start + "assignDamage_".length()).split("_"));
            boolean onPlanet = parts.contains(PLANET);
            if (onPlanet == PLANET.equals(holder)) {
                sustainable.add(Units.findUnitType(parts.get(2)));
            }
        }
        return sustainable;
    }

    Set<UnitType> autoSpace(Player owner) {
        return repeatSingleHits(owner, SPACE, () -> {
            ButtonHelperModifyUnits.autoAssignSpaceCombatHits(owner, game, tile, 1, null, false);
        });
    }

    Set<UnitType> autoGround(Player owner) {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        try (MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            return repeatSingleHits(owner, PLANET, () -> {
                ButtonHelperModifyUnits.autoAssignGroundCombatHits(owner, game, PLANET, 1, event);
            });
        }
    }

    // Raid Formation is the only way auto-assigned anti-fighter barrage hits damage ships, so the Argent player
    // must be here. Without fighters, every hit goes to a ship with SUSTAIN DAMAGE in one pass.
    Set<UnitType> autoAntiFighterBarrage(Player owner) {
        Player argent = addPlayer("green", "argent", "destroyer");
        argent.addAbility("raid_formation");
        place(argent, SPACE, UnitType.Destroyer, 1);
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        try (MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            ButtonHelperModifyUnits.autoAssignAntiFighterBarrageHits(owner, game, POSITION, 20, event);
        }
        return damagedTypes(owner, SPACE);
    }

    boolean canSustain(Player owner, String holder, HitSource hitSource, UnitType type) {
        UnitHolder unitHolder = tile.getUnitHolders().get(holder);
        SustainContext context = SustainContext.of(game, owner, tile, unitHolder, hitSource);
        return SustainEligibility.canSustain(context, owner.getUnitFromUnitKey(key(owner, type)));
    }

    UnitKey key(Player owner, UnitType type) {
        return Units.getUnitKey(type, owner.getColorID());
    }

    // Assigns one hit at a time and records every unit type that became damaged. The first hit that sustains nothing
    // ends the run; it may destroy an already damaged unit, which is why the record is kept per hit.
    private Set<UnitType> repeatSingleHits(Player owner, String holder, Runnable assignOneHit) {
        Set<UnitType> sustained = EnumSet.noneOf(UnitType.class);
        for (int round = 0; round < SAFETY_LIMIT; round++) {
            Map<UnitType, Integer> before = damagedCounts(owner, holder);
            assignOneHit.run();
            Map<UnitType, Integer> after = damagedCounts(owner, holder);
            Set<UnitType> newlyDamaged = EnumSet.noneOf(UnitType.class);
            after.forEach((type, count) -> {
                if (count > before.getOrDefault(type, 0)) newlyDamaged.add(type);
            });
            if (newlyDamaged.isEmpty()) {
                break;
            }
            sustained.addAll(newlyDamaged);
        }
        return sustained;
    }

    private Map<UnitType, Integer> damagedCounts(Player owner, String holder) {
        UnitHolder unitHolder = tile.getUnitHolders().get(holder);
        Map<UnitType, Integer> counts = new EnumMap<>(UnitType.class);
        for (UnitKey unitKey : unitHolder.getUnitKeys()) {
            if (owner.unitBelongsToPlayer(unitKey)) {
                counts.merge(unitKey.unitType(), unitHolder.getDamagedUnitCount(unitKey), Integer::sum);
            }
        }
        return counts;
    }

    private Set<UnitType> damagedTypes(Player owner, String holder) {
        UnitHolder unitHolder = tile.getUnitHolders().get(holder);
        Set<UnitType> damaged = EnumSet.noneOf(UnitType.class);
        for (UnitKey unitKey : unitHolder.getUnitKeys()) {
            if (owner.unitBelongsToPlayer(unitKey) && unitHolder.getDamagedUnitCount(unitKey) > 0) {
                damaged.add(unitKey.unitType());
            }
        }
        return damaged;
    }
}
