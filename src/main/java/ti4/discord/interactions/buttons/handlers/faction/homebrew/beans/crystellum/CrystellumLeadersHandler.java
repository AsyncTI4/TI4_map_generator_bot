package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.crystellum;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.leader.PlayHeroService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class CrystellumLeadersHandler {
    private static final String AGENT = "crystellumagent";
    private static final String HERO = "crystellumhero";
    private static final String USE_AGENT = "crystellumUseAgent";
    private static final String AGENT_TARGET = "crystellumAgentTarget_";
    private static final String PURGE_CRYST_HERO = "purgeCrystellumHero_";

    public static Button getCrystellumAgentButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + USE_AGENT, "Use Shardwright Veyla", FactionEmojis.crystellum);
    }

    @ButtonHandler(USE_AGENT)
    public static void useShardwrightVeyla(ButtonInteractionEvent event, Player player, Game game) {
        if (event == null || player == null || game == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!player.hasLeader(AGENT)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!player.hasUnexhaustedLeader(AGENT)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (game.getActiveSystem() == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile activeTile = game.getTileByPosition(game.getActiveSystem());
        if (activeTile == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayersNNeutral()) {
            if (target == null) {
                continue;
            }
            if (!activeTile.hasPlayerNonFighterShips(target)) {
                continue;
            }

            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + AGENT_TARGET + target.getFaction(),
                    target.getFaction(),
                    target.getFactionEmoji()));
        }

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    "There are no eligible players in the active system for _Shardwright Veyla_.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose the player that will place fighters in the active system.",
                buttons);
    }

    @ButtonHandler(AGENT_TARGET)
    public static void resolveShardwrightVeyla(
            ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        if (event == null || player == null || game == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!player.hasLeader(AGENT)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!player.hasUnexhaustedLeader(AGENT)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (game.getActiveSystem() == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile activeTile = game.getTileByPosition(game.getActiveSystem());
        if (activeTile == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Cannot find the active system or there is no active system.");
            return;
        }

        String targetFaction = buttonID.substring(AGENT_TARGET.length());
        Player activePlayer = game.getPlayerFromColorOrFaction(targetFaction);
        if (activePlayer == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        int numberOfNFShips = ButtonHelper.checkNumberNonFighterShips(activePlayer, activeTile);
        if (numberOfNFShips < 1) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    activePlayer.getRepresentationNoPing()
                            + " does not have any non-fighter ships in the active system.");
            return;
        }

        Leader agent = player.getLeader(AGENT).orElse(null);
        if (agent == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        ExhaustLeaderService.exhaustLeader(game, player, agent);
        AddUnitService.addUnits(event, activeTile, game, activePlayer.getColor(), numberOfNFShips + " fighter");

        if (activePlayer != player) {
            player.gainTG(numberOfNFShips);
        }

        String nonCrystellum = (activePlayer != player
                ? (" " + player.toString() + " also gained " + numberOfNFShips + " trade goods.")
                : "");

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", used _Shardwright Veyla_ to allow "
                        + activePlayer.getRepresentationUnfogged()
                        + " to place " + numberOfNFShips + " fighters"
                        + " in " + activeTile.toString() + "."
                        + nonCrystellum);

        ButtonHelper.deleteMessage(event);
    }

    public static int getCrystellumCommanderCapacitySystemCount(Game game, Player player) {
        if (game == null || player == null) {
            return 0;
        }

        Tile homeTile = player.getHomeSystemTile();
        int count = 0;

        for (Tile tile : game.getTiles()) {
            if (tile == null) {
                continue;
            }
            if (homeTile != null && tile.getPosition().equals(homeTile.getPosition())) {
                continue;
            }

            UnitHolder space = tile.getSpaceUnitHolder();
            if (space == null) {
                continue;
            }

            boolean qualifies = false;
            for (UnitKey key : space.getUnitKeys()) {
                if (!player.unitBelongsToPlayer(key)) {
                    continue;
                }

                UnitModel model = player.getUnitFromUnitKey(key);
                if (model == null || !model.getIsShip()) {
                    continue;
                }

                if (model.getCapacityValue() > 0) {
                    qualifies = true;
                    break;
                }
            }

            if (qualifies) {
                count++;
            }
        }

        return count;
    }

    public static void giveCommanderReminder(Player player, Game game) {
        if (player == null || game == null) {
            return;
        }
        Tile activeTile = game.getTileByPosition(game.getActiveSystem());
        if (activeTile == null) {
            return;
        }
        if (!FoWHelper.otherPlayersHaveShipsInSystem(player, activeTile, game)) {
            return;
        }

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", this is a reminder that you have _Highbearer Lumina_ unlocked, and have activated a system that contains another player's ships. As such, you may choose to add +1 to the capacity value of each ship being moved for the remainder of this tactical action. This is not automated.");
    }

    public static boolean canUseCrystellumHero(Player player) {
        return player != null && player.hasLeader(HERO) && player.hasLeaderUnlocked(HERO);
    }

    public static Button getCrystellumHeroButton(Player player, Tile tile) {
        return Buttons.gray(
                player.factionButtonChecker() + PURGE_CRYST_HERO + tile.getPosition(),
                "Purge Crystellum Hero",
                FactionEmojis.crystellum);
    }

    @ButtonHandler(PURGE_CRYST_HERO)
    public static void resolveCrystellumHero(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        if (event == null || player == null || game == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!player.hasLeader(HERO)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!player.hasLeaderUnlocked(HERO)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String tilePos = buttonID.substring(PURGE_CRYST_HERO.length());
        Tile tile = game.getTileByPosition(tilePos);
        if (tile == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (!canUseCrystellumHero(player)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Leader hero = player.getLeader(HERO).orElse(null);
        if (hero == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        PlayHeroService.removeLeader(game, player, hero);

        Player opponent = ButtonHelper.getPlayersWithShipsInTheSystem(game, tile).stream()
                .filter(p -> p != player && !player.isPlayerMemberOfAlliance(p))
                .findFirst()
                .orElse(null);

        if (opponent == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not find an opposing player for _Facet_ in this combat.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        UnitHolder space = tile.getSpaceUnitHolder();
        if (space == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not read ships in the combat system for _Facet_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        Map<UnitType, Integer> mirroredShips = new LinkedHashMap<>();
        for (UnitKey opponentShips : space.getUnitKeys()) {
            if (!opponent.unitBelongsToPlayer(opponentShips)) {
                continue;
            }
            UnitModel model = opponent.getUnitFromUnitKey(opponentShips);
            if (model == null) {
                continue;
            }
            if (!model.getIsShip()) {
                continue;
            }
            if (model.getUnitType() == UnitType.Fighter) {
                continue;
            }

            int count = space.getUnitCount(opponentShips);
            if (count < 1) {
                continue;
            }

            mirroredShips.merge(model.getUnitType(), count, Integer::sum);
        }

        List<String> summary = new ArrayList<>();
        for (Map.Entry<UnitType, Integer> entry : mirroredShips.entrySet()) {
            String label = entry.getKey().humanReadableName().toLowerCase();
            summary.add(entry.getValue() + "x " + label);
        }

        if (mirroredShips.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + " purged _Facet_. "
                            + "There were no opposing non-fighter ships to mirror in the active system.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.setStoredValue(getFacetBypassKey(player, tilePos), "true");
        game.setStoredValue(getFacetBypassTrackerKey(player), tilePos);

        for (Map.Entry<UnitType, Integer> entry : mirroredShips.entrySet()) {
            String unitName = getFacetPlacementName(entry.getKey());
            if (unitName == null) {
                continue;
            }

            AddUnitService.addUnits(event, tile, game, player.getColor(), entry.getValue() + " " + unitName);
        }

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + " purged _Facet_. "
                        + "It will mirror the following ships from "
                        + opponent.getRepresentationUnfogged()
                        + ": "
                        + String.join(", ", summary)
                        + ".");
        ButtonHelper.deleteMessage(event);
    }

    private static String getFacetPlacementName(UnitType type) {
        return switch (type) {
            case Carrier -> "carrier";
            case Cruiser -> "cruiser";
            case Destroyer -> "destroyer";
            case Dreadnought -> "dreadnought";
            case Flagship -> "flagship";
            case Warsun -> "warsun";
            default -> null;
        };
    }

    public static String getFacetBypassKey(Player player, String tilePos) {
        return "crystellumFacetBypass_" + player.getFaction() + "_" + tilePos;
    }

    private static String getFacetBypassTrackerKey(Player player) {
        return "crystellumFacetBypassTile_" + player.getFaction();
    }

    public static boolean hasFacetBypass(Game game, Player player, Tile tile) {
        if (game == null || player == null || tile == null) {
            return false;
        }
        return "true".equals(game.getStoredValue(getFacetBypassKey(player, tile.getPosition())));
    }

    public static void clearFacetBypass(Game game, Player player) {
        if (game == null || player == null) {
            return;
        }

        String trackedTilePos = game.getStoredValue(getFacetBypassTrackerKey(player));
        if (trackedTilePos.isEmpty()) {
            return;
        }

        game.removeStoredValue(getFacetBypassKey(player, trackedTilePos));
        game.removeStoredValue(getFacetBypassTrackerKey(player));
    }
}
