package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.Iron;

import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollType;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.leader.PurgeHeroService;
import ti4.service.leader.UnlockLeaderService;

@UtilityClass
public class IronLeadersHandler {

    public static final String AGENT_ID = "ironagent";
    public static final String COMMANDER_ID = "ironcommander";
    public static final String HERO_ID = "ironhero";
    public static final String MASTER_OF_DEFENSE_BUTTON_ID = "ironUseMasterOfDefense";
    public static final String IRON_HERO_BUTTON_ID = "purgeIronHero";
    private static final String IRON_HERO_ELIGIBLE_PREFIX = "ironHeroEligible_";

    public static Button getMasterOfDefenseCardsInfoButton() {
        return Buttons.gray(MASTER_OF_DEFENSE_BUTTON_ID, "Use Master of Defense", FactionEmojis.iron);
    }

    @ButtonHandler(MASTER_OF_DEFENSE_BUTTON_ID)
    public static void useMasterOfDefense(Game game, Player player, ButtonInteractionEvent event) {
        Leader agent = player.getLeader(AGENT_ID).orElse(null);
        if (agent == null || !player.hasUnexhaustedLeader(AGENT_ID)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "_Master of Defense_ is no longer available.");
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        ExhaustLeaderService.exhaustLeader(game, player, agent);

        MessageHelper.sendMessageToChannel(
                game.getActionsChannel(),
                player.getRepresentation()
                        + " exhausted **Shipwright Kastel**, the Iron Tide agent, to allow 1 unit to not be destroyed or removed.");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    public static boolean shouldAutoRerollCommanderMechMisses(
            Game game, Player player, UnitModel unitModel, CombatRollType rollType) {
        return game.playerHasLeaderUnlockedOrAlliance(player, COMMANDER_ID)
                && rollType == CombatRollType.combatround
                && unitModel != null
                && unitModel.getUnitType() == UnitType.Mech;
    }

    public static void checkCommanderUnlockAfterCombat(Game game, Tile tile, UnitHolder holder, String assignHitsType) {
        if (game == null || tile == null || assignHitsType == null || !assignHitsType.contains("combat")) {
            return;
        }

        if ("groundcombat".equalsIgnoreCase(assignHitsType)) {
            if (holder == null) {
                for (UnitHolder unitHolder : tile.getPlanetUnitHolders()) {
                    tryUnlockCommanderOnPlanet(game, unitHolder);
                }
                return;
            }
            if ("space".equalsIgnoreCase(holder.getName())) {
                return;
            }
            tryUnlockCommanderOnPlanet(game, holder);
            return;
        }

        List<Player> remainingPlayers = ButtonHelper.getPlayersWithShipsInTheSystem(game, tile);
        if (remainingPlayers.size() != 1) {
            return;
        }
        Player winner = remainingPlayers.getFirst();
        if (winner.hasLeader(COMMANDER_ID)
                && !winner.hasLeaderUnlocked(COMMANDER_ID)
                && tile.getSpaceUnitHolder().getUnitCount(UnitType.Mech, winner) > 0) {
            UnlockLeaderService.unlockLeader(COMMANDER_ID, game, winner);
        }
    }

    public static void updateIronHeroEligibility(Game game, Player player, Tile activeSystem) {
        String key = getIronHeroEligibilityKey(player);
        if (game == null || player == null || activeSystem == null || !player.hasLeaderUnlocked(HERO_ID)) {
            if (game != null && player != null) {
                game.setStoredValue(key, "");
            }
            return;
        }

        int movedMechs = 0;
        boolean movedOtherShips = false;
        for (Map.Entry<String, Map<UnitKey, List<Integer>>> displaced :
                game.getTacticalActionDisplacement().entrySet()) {
            for (Map.Entry<UnitKey, List<Integer>> movedUnit :
                    displaced.getValue().entrySet()) {
                int movedCount = movedUnit.getValue().stream()
                        .filter(java.util.Objects::nonNull)
                        .mapToInt(Integer::intValue)
                        .sum();
                if (movedCount <= 0) {
                    continue;
                }

                if (player.unitBelongsToPlayer(movedUnit.getKey())
                        && movedUnit.getKey().unitType() == UnitType.Mech) {
                    movedMechs += movedCount;
                    continue;
                }

                Player owner =
                        game.getPlayerFromColorOrFaction(movedUnit.getKey().colorID());
                UnitModel unitModel = owner == null ? null : owner.getUnitFromUnitKey(movedUnit.getKey());
                if (unitModel != null && unitModel.getIsShip()) {
                    movedOtherShips = true;
                    break;
                }
            }
            if (movedOtherShips) {
                break;
            }
        }

        game.setStoredValue(key, movedMechs > 0 && !movedOtherShips ? activeSystem.getPosition() : "");
    }

    public static boolean canUseIronHero(Game game, Player player, Tile activeSystem) {
        return game != null
                && player != null
                && activeSystem != null
                && player.hasLeaderUnlocked(HERO_ID)
                && activeSystem.getSpaceUnitHolder().getUnitCount(UnitType.Mech, player) > 0
                && activeSystem.getPosition().equals(game.getStoredValue(getIronHeroEligibilityKey(player)));
    }

    @ButtonHandler(IRON_HERO_BUTTON_ID)
    public static void useIronHero(ButtonInteractionEvent event, Player player, Game game) {
        if (!canUseIronHero(game, player, game.getTileByPosition(game.getActiveSystem()))) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "_Apex Frame - Mecha Prime_ is no longer available in this system.");
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        PurgeHeroService.purgeHeroPreamble(event, player, game, HERO_ID, "Admiral Thalen, the Iron Tide hero");
        game.setStoredValue(getIronHeroEligibilityKey(player), "");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", use the landing buttons to commit each of your mechs in the active system to planets in this system and then resolve ground combat, if able.");
    }

    private static String getIronHeroEligibilityKey(Player player) {
        return IRON_HERO_ELIGIBLE_PREFIX + player.getFaction();
    }

    private static void tryUnlockCommanderOnPlanet(Game game, UnitHolder holder) {
        List<Player> remainingPlayers = ButtonHelper.getPlayersWithUnitsOnAPlanet(game, holder);
        if (remainingPlayers.size() != 1) {
            return;
        }
        Player winner = remainingPlayers.getFirst();
        if (winner.hasLeader(COMMANDER_ID)
                && !winner.hasLeaderUnlocked(COMMANDER_ID)
                && holder.getUnitCount(UnitType.Mech, winner) > 0) {
            UnlockLeaderService.unlockLeader(COMMANDER_ID, game, winner);
        }
    }
}
