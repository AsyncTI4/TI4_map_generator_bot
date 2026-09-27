package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Xytheris;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.Mapper;
import ti4.model.CombatModifierModel;
import ti4.model.NamedCombatModifierModel;
import ti4.service.combat.CombatRollType;
import ti4.service.transaction.SendPromissoryService;

@UtilityClass
public class XytherisPromissoryHandler {
    private static final String SWARM_SPAWN = "thpnxytheris";
    private static final String SWARM_SPAWN_PENDING = "swarmSpawnUnitAbilityRoll_";

    public static void activateSwarmSpawn(Game game, Player player) {
        if (game == null
                || player == null
                || !player.getPromissoryNotesInPlayArea().contains(SWARM_SPAWN)) {
            return;
        }
        game.setStoredValue(SWARM_SPAWN_PENDING + player.getFaction(), "pending");
    }

    public static void addSwarmSpawnModifier(
            List<NamedCombatModifierModel> modifiers, Game game, Player player, CombatRollType rollType) {
        if (!hasPendingSwarmSpawn(game, player, rollType)) {
            return;
        }
        CombatModifierModel modifier = Mapper.getCombatModifiers().get("swarm_spawn_" + rollType.name());
        if (modifier != null) {
            modifiers.add(new NamedCombatModifierModel(modifier, "_Swarm Spawn_"));
        }
    }

    public static void resolveSwarmSpawnAfterRoll(
            GenericInteractionCreateEvent event, Game game, Player player, CombatRollType rollType) {
        if (!hasPendingSwarmSpawn(game, player, rollType)) {
            return;
        }
        game.removeStoredValue(SWARM_SPAWN_PENDING + player.getFaction());
        Player owner = game.getPNOwner(SWARM_SPAWN);
        if (owner != null) {
            SendPromissoryService.returnPromissoryFromPlayAreaToOwner(event, game, player, owner, SWARM_SPAWN);
        }
    }

    private static boolean hasPendingSwarmSpawn(Game game, Player player, CombatRollType rollType) {
        return game != null
                && player != null
                && isUnitAbilityRoll(rollType)
                && "pending".equals(game.getStoredValue(SWARM_SPAWN_PENDING + player.getFaction()))
                && player.getPromissoryNotesInPlayArea().contains(SWARM_SPAWN);
    }

    private static boolean isUnitAbilityRoll(CombatRollType rollType) {
        return rollType == CombatRollType.SpaceCannonOffence
                || rollType == CombatRollType.SpaceCannonDefence
                || rollType == CombatRollType.AFB
                || rollType == CombatRollType.bombardment;
    }
}
