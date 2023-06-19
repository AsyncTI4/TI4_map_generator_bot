package ti4.helpers;

import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;

import java.util.*;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import ti4.commands.cardsac.ACInfo_Legacy;
import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;

public class FoWHelper {

	public static Boolean isPrivateGame(GenericInteractionCreateEvent event) {
		if (event == null) {
			return null;
		}
		return isPrivateGame(null, null, event.getChannel());
	}

	public static Boolean isPrivateGame(GenericCommandInteractionEvent event) {
		return isPrivateGame(null, event);
	}

	public static Boolean isPrivateGame(@Nullable Map activeMap, GenericInteractionCreateEvent event) {
		return isPrivateGame(activeMap, event, null);
	}

	public static Boolean isPrivateGame(Map activeMap) {
		return isPrivateGame(activeMap, null, null);
	}

	/** Method to determine of a viewing player should be able to see the stats of a particular faction */
	public static boolean canSeeStatsOfFaction(Map activeMap, String faction, Player viewingPlayer) {
		for (Player player : activeMap.getPlayers().values()) {
			if (faction.equals(player.getFaction())) {
				return canSeeStatsOfPlayer(activeMap, player, viewingPlayer);
			}
		}
		return false;
	}

	/** Method to determine of a viewing player should be able to see the stats of a particular player */
	public static boolean canSeeStatsOfPlayer(Map activeMap, Player player, Player viewingPlayer) {
		if (player == viewingPlayer) {
			return true;
		}

		return viewingPlayer != null && player != null && activeMap != null &&
			( hasHomeSystemInView(activeMap, player, viewingPlayer)
				|| hasPlayersPromInPlayArea(player, viewingPlayer)
				|| hasMahactCCInFleet(player, viewingPlayer)
			);
	}

	/** Check if the fog filter needs to be updated, then return the list of tiles that the player can see */
	public static Set<String> fowFilter(Map activeMap, Player player) {
		if (player != null) {
			updateFog(activeMap, player);

			Set<String> systems = new HashSet<>();
			for (java.util.Map.Entry<String, Tile> tileEntry : new HashMap<>(activeMap.getTileMap()).entrySet()) {
				if (!tileEntry.getValue().hasFog(player)) {
					systems.add(tileEntry.getKey());
				}
			}
			return systems;
		}
		return Collections.emptySet();
	}

	private static void initializeFog(Map activeMap, @NotNull Player player, boolean forceRecalculate) {
		if (player.hasFogInitialized() && !forceRecalculate) {
			return;
		}

		// Get all tiles with the player in it
		Set<String> tilesWithPlayerUnitsPlanets = new HashSet<>();
		for (java.util.Map.Entry<String, Tile> tileEntry : new HashMap<>(activeMap.getTileMap()).entrySet()) {
			if (FoWHelper.playerIsInSystem(activeMap, tileEntry.getValue(), player)) {
				tilesWithPlayerUnitsPlanets.add(tileEntry.getKey());
			}
		}

		Set<String> tilePositionsToShow = new HashSet<>(tilesWithPlayerUnitsPlanets);
		for (String tilePos : tilesWithPlayerUnitsPlanets) {
			Set<String> adjacentTiles = FoWHelper.getAdjacentTiles(activeMap, tilePos, player, true);
			tilePositionsToShow.addAll(adjacentTiles);
		}

		String playerSweep = Mapper.getSweepID(player.getColor());
		for (Tile tile : activeMap.getTileMap().values()) {
			if (tile.hasCC(playerSweep)) {
				tilePositionsToShow.add(tile.getPosition());
			}
			boolean tileHasFog = !tilePositionsToShow.contains(tile.getPosition());
			tile.setTileFog(player, tileHasFog);
		}

		updatePlayerFogTiles(activeMap, player);
		player.setFogInitialized(true);
	}

	public static Set<String> getTilePositionsToShow(Map activeMap, @NotNull Player player) {
		// Get all tiles with the player in it
		Set<String> tilesWithPlayerUnitsPlanets = new HashSet<>();
		for (java.util.Map.Entry<String, Tile> tileEntry : new HashMap<>(activeMap.getTileMap()).entrySet()) {
			if (FoWHelper.playerIsInSystem(activeMap, tileEntry.getValue(), player)) {
				tilesWithPlayerUnitsPlanets.add(tileEntry.getKey());
			}
		}

		Set<String> tilePositionsToShow = new HashSet<>(tilesWithPlayerUnitsPlanets);
		for (String tilePos : tilesWithPlayerUnitsPlanets) {
			Set<String> adjacentTiles = FoWHelper.getAdjacentTiles(activeMap, tilePos, player, true);
			tilePositionsToShow.addAll(adjacentTiles);
		}

		String playerSweep = Mapper.getSweepID(player.getColor());
		for (Tile tile : activeMap.getTileMap().values()) {
			if (tile.hasCC(playerSweep)) {
				tilePositionsToShow.add(tile.getPosition());
			}
		}
		return tilePositionsToShow;
	}

	public static void updateFog(Map activeMap, Player player) {
		if (player != null) initializeFog(activeMap, player, true);
	}

    private static void updatePlayerFogTiles(Map activeMap, Player player) {
        for (Tile tileToUpdate : activeMap.getTileMap().values()) {
            if (!tileToUpdate.hasFog(player)) {
                player.updateFogTile(tileToUpdate, "Round " + activeMap.getRound());
            }
        }
    }

	private static boolean hasHomeSystemInView(@NotNull Map activeMap, @NotNull Player player, @NotNull Player viewingPlayer) {
		String faction = player.getFaction();
		if (faction == null) {
			return false;
		}

		List<String> hsIDs = new ArrayList<>();
		if ("keleres".equals(faction)) {
			hsIDs.add("92");
			hsIDs.add("93");
			hsIDs.add("94");
		} else if ("ghost".equals(faction)) {
			hsIDs.add("51");
		} else {
			FactionModel playerSetup = Mapper.getFactionSetup(faction);
			if (playerSetup != null) {
				hsIDs.add(playerSetup.homeSystemTile);
			}
		}

		for (Tile tile : activeMap.getTileMap().values()) {
			if (hsIDs.contains(tile.getTileID()) && !tile.hasFog(viewingPlayer)) {
				return true;
			}
		}

		return false;
	}

	private static boolean hasPlayersPromInPlayArea(@NotNull Player player, @NotNull Player viewingPlayer) {
		boolean hasPromInPA = false;
		String playerColor = player.getColor();
		String playerFaction = player.getFaction();
		List<String> promissoriesInPlayArea = viewingPlayer.getPromissoryNotesInPlayArea();
		for (String prom_ : promissoriesInPlayArea) {
			String promissoryNoteOwner = Mapper.getPromissoryNoteOwner(prom_);
			if (playerColor != null && playerColor.equals(promissoryNoteOwner)
					|| playerFaction != null && playerFaction.equals(promissoryNoteOwner)) {
				hasPromInPA = true;
				break;
			}
		}
		return hasPromInPA;
	}

	private static boolean hasMahactCCInFleet(@NotNull Player player, @NotNull Player viewingPlayer) {
		List<String> mahactCCs = viewingPlayer.getMahactCC();
		return mahactCCs.contains(player.getColor());
	}

	public static Boolean isPrivateGame(Map activeMap, @Nullable GenericInteractionCreateEvent event, @Nullable Channel channel_) {
		Boolean isFoWPrivate = null;
		Channel eventChannel = event == null ? null : event.getChannel();
		Channel channel = channel_ != null ? channel_ : eventChannel;
		if (channel == null) {
			return activeMap.isFoWMode();
		}
		if (activeMap == null) {
			String gameName = channel.getName();
			gameName = gameName.replace(ACInfo_Legacy.CARDS_INFO, "");
			gameName = gameName.substring(0, gameName.indexOf("-"));
			activeMap = MapManager.getInstance().getMap(gameName);
			if (activeMap == null) {
				return isFoWPrivate;
			}
		}
		if (activeMap.isFoWMode() && channel_ != null || event != null) {
			isFoWPrivate = channel.getName().endsWith(Constants.PRIVATE_CHANNEL);
		}
		return isFoWPrivate;
	}

	/** Return a list of tile positions that are adjacent to a source position.
	 *  Includes custom adjacent tiles defined on the map level, hyperlanes, and
	 *  wormholes
	 */
	public static Set<String> getAdjacentTiles(Map activeMap, String position, Player player, boolean toShow) {
		Set<String> adjacentPositions = traverseAdjacencies(activeMap, false, position);
		
		List<String> adjacentCustomTiles = activeMap.getCustomAdjacentTiles().get(position);
		
		List<String> adjacentCustomTiles2 = new ArrayList<String>();
		if (adjacentCustomTiles != null) {
			if (!toShow) {
				for (String t : adjacentCustomTiles) {
					if (activeMap.getCustomAdjacentTiles().get(t) != null && activeMap.getCustomAdjacentTiles().get(t).contains(position)) {
						adjacentCustomTiles2.add(t);
					}
				}
				adjacentPositions.addAll(adjacentCustomTiles2);
			} else {
				adjacentPositions.addAll(adjacentCustomTiles);
			}
		}
		if (!toShow) {
			for (String primaryTile : activeMap.getCustomAdjacentTiles().keySet()) {
				System.out.println("Primary tile" + primaryTile);
				System.out.println("Position" + position);
				if (activeMap.getCustomAdjacentTiles().get(primaryTile).contains(position)) {
					if (!adjacentPositions.contains(primaryTile)) {
						adjacentPositions.add(primaryTile);
					}
				}
			}
		}
		
		Set<String> wormholeAdjacencies = getWormholeAdjacencies(activeMap, position, player);
		if (wormholeAdjacencies != null) {
			adjacentPositions.addAll(wormholeAdjacencies);
		}

		return adjacentPositions;
	}

	/** Return a list of tile positions that are adjacent to a source position either directly or via hyperlanes
	 * <p>
	 * Does not traverse wormholes
	 */
	public static Set<String> traverseAdjacencies(Map activeMap, boolean naturalMapOnly, String position) {
		Set<String> adjacentPositions = traverseAdjacencies(activeMap, naturalMapOnly, position, -1, new HashSet<>(), null);
		return adjacentPositions;
	}

	/** Return a list of tile positions that are adjacent to a source position either directly or via hyperlanes
	 * <p>
	 * Does not traverse wormholes
	 */
	private static Set<String> traverseAdjacencies(Map activeMap, boolean naturalMapOnly, String position, Integer sourceDirection, Set<String> exploredSet, String prevTile) {
		Set<String> tiles = new HashSet<>();
		if (exploredSet.contains(position + sourceDirection)) {
			// We already explored this tile from this direction!
			return tiles;
		}
		// mark the tile as explored
		exploredSet.add(position + sourceDirection);

		Tile currentTile = activeMap.getTileByPosition(position);
		if (currentTile == null) {
			// could not load the requested tile
			return tiles;
		}

		List<Boolean> hyperlaneData = currentTile.getHyperlaneData(sourceDirection);
		if (hyperlaneData != null && hyperlaneData.size() == 0) {
			// We could not load the hyperlane data correctly, quit
			return tiles;
		}

		// we are allowed to see this tile
		tiles.add(position);

		if ((hyperlaneData == null || naturalMapOnly) && sourceDirection != -1) {
			// do not explore non-hyperlanes except for your starting space
			return tiles;
		}

		List<String> directlyAdjacentTiles = PositionMapper.getAdjacentTilePositions(activeMap, position);
		if (directlyAdjacentTiles == null || directlyAdjacentTiles.size() != 6) {
			// adjacency file for this tile is not filled in
			return tiles;
		}

		// for each adjacent tile...
		for (int i = 0; i < 6; i++) {
			String position_ = directlyAdjacentTiles.get(i);

			String override = activeMap.getAdjacentTileOverride(position, i);
			if (override != null) {
				if (naturalMapOnly) continue;
				position_ = override;
			}

			if (position_.equals("x") || (hyperlaneData != null && !hyperlaneData.get(i))) {
				// the hyperlane doesn't exist & doesn't go that direction, skip.
				continue;
			}

			// explore that tile now!
			Set<String> newTiles = traverseAdjacencies(activeMap, naturalMapOnly, position_, (i + 3) % 6, exploredSet, position + sourceDirection);
			tiles.addAll(newTiles);
		}
		return tiles;
	}

	/** Check the map for other tiles that have wormholes connecting to the source system.
	 *  <p>
	 *  Also takes into account player abilities and agendas
	 */
	private static Set<String> getWormholeAdjacencies(Map activeMap, String position, Player player) {
		Set<String> adjacentPositions = new HashSet<>();
		Set<Tile> allTiles = new HashSet<Tile>(activeMap.getTileMap().values());
		Tile tile = activeMap.getTileByPosition(position);

		String ghostFlagship = null;
		for (Player p : activeMap.getPlayers().values()) {
			if ("ghost".equals(p.getFaction())) {
				ghostFlagship = Mapper.getUnitID("fs", p.getColor());
				break;
			}
		}

		boolean wh_recon = activeMap.getLaws().keySet().contains("wormhole_recon");
		boolean absol_recon = activeMap.getLaws().keySet().contains("absol_recon");

		Set<String> wormholeIDs = Mapper.getWormholes(tile.getTileID());
		for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
			HashSet<String> tokenList = unitHolder.getTokenList();
			for (String token : tokenList) {
				if (token.contains(Constants.ALPHA)) {
					wormholeIDs.add(Constants.ALPHA);
				} else if (token.contains(Constants.BETA)) {
					wormholeIDs.add(Constants.BETA);
				} else if (token.contains(Constants.GAMMA)) {
					wormholeIDs.add(Constants.GAMMA);
				} else if (token.contains(Constants.DELTA)) {
					wormholeIDs.add(Constants.DELTA);
				} else if (token.contains(Constants.EPSILON)) {
					wormholeIDs.add(Constants.EPSILON);
				} else if (token.contains(Constants.ZETA)) {
					wormholeIDs.add(Constants.ZETA);
				} else if (token.contains(Constants.ETA)) {
					wormholeIDs.add(Constants.ETA);
				}
			}
			if (ghostFlagship != null && unitHolder.getUnits().getOrDefault(ghostFlagship, 0) > 0) {
				wormholeIDs.add(Constants.DELTA);
			}
		}

		if ((player != null && "ghost".equals(player.getFaction())) || wh_recon || absol_recon) {
			if (wormholeIDs.contains(Constants.ALPHA)) {
				wormholeIDs.add(Constants.BETA);
			} else if (wormholeIDs.contains(Constants.BETA)) {
				wormholeIDs.add(Constants.ALPHA);
			}
		}

		if (wormholeIDs.isEmpty()) {
			return adjacentPositions;
		}

		Set<String> wormholeTiles = new HashSet<>();
		for (String wormholeID : wormholeIDs) {
			wormholeTiles.addAll(Mapper.getWormholesTiles(wormholeID));
		}

		for (Tile tile_ : allTiles) {
			String position_ = tile_.getPosition();

			if (wormholeTiles.contains(tile_.getTileID())) {
				adjacentPositions.add(position_);
				continue;
			}
			for (UnitHolder unitHolder : tile_.getUnitHolders().values()) {
				HashSet<String> tokenList = unitHolder.getTokenList();
				for (String token : tokenList) {
					for (String wormholeID : wormholeIDs) {
						if (token.contains(wormholeID)) {
							adjacentPositions.add(position_);
						}
					}
				}
				if (wormholeIDs.contains(Constants.DELTA) && unitHolder.getUnits().getOrDefault(ghostFlagship, 0) > 0) {
					adjacentPositions.add(position_);
				}
			}
		}
		return adjacentPositions;
	}

	/** Return the list of players that are adjacent to a particular position
	 *  <p>
	 *  WARNING: This function returns information that certain players may not be privy to
	 */
	public static List<Player> getAdjacentPlayers(Map activeMap, String position, boolean includeSweep) {
		List<Player> players = new ArrayList<>();
		Set<String> tilesToCheck = getAdjacentTiles(activeMap, position, null, false);
		Tile startingTile = activeMap.getTileByPosition(position);

		for (Player player_ : activeMap.getPlayers().values()) {
			Set<String> tiles = new HashSet<>(tilesToCheck);
			if ("ghost".equals(player_.getFaction())) {
				tiles.addAll(getWormholeAdjacencies(activeMap, position, player_));
			}

			if (includeSweep && startingTile.hasCC(Mapper.getSweepID(player_.getColor()))) {
				players.add(player_);
				continue;
			}

			for (String position_ : tiles) {
				Tile tile = activeMap.getTileByPosition(position_);
				if(tile != null)
				{
					if (playerIsInSystem(activeMap, tile, player_)) {
						players.add(player_);
						break;
					}
				}
				
			}
		}

		return players;
	}

	/** Check if the specified player should have vision on the system */
	public static boolean playerIsInSystem(Map activeMap, Tile tile, Player player) {
		Set<String> unitHolderNames = tile.getUnitHolders().keySet();
		List<String> playerPlanets = player.getPlanets();
		if (playerPlanets.stream().anyMatch(unitHolderNames::contains)) {
			return true;
		} else if ("18".equals(tile.getTileID()) && player.hasTech("iihq")) {
			return true;
		} else if ("s11".equals(tile.getTileID()) && canSeeStatsOfFaction(activeMap, "cabal", player)) {
			return true;
		} else if ("s12".equals(tile.getTileID()) && canSeeStatsOfFaction(activeMap, "nekro", player)) {
			return true;
		} else if ("s13".equals(tile.getTileID()) && canSeeStatsOfFaction(activeMap, "yssaril", player)) {
			return true;
		}

		return playerHasUnitsInSystem(player, tile);
	}

	/** Check if the player has units in the system */
	public static boolean playerHasUnitsInSystem(Player player, Tile tile) {
		String colorID = Mapper.getColorID(player.getColor());
		if (colorID == null) return false; // player doesn't have a color

		HashMap<String, Integer> units = new HashMap<>();
		for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
			units.putAll(unitHolder.getUnits());
		}
		for (String key : units.keySet()) {
			if (key != null) {
				if (key.startsWith(colorID)) {
					return true;
				}
			}
		}
		return false;
	}
	public static boolean playerHasShipsInSystem(Player player, Tile tile) {
		String colorID = Mapper.getColorID(player.getColor());
		if (colorID == null) return false; // player doesn't have a color

		HashMap<String, Integer> units = new HashMap<>();
		UnitHolder unitHolder =tile.getUnitHolders().get(Constants.SPACE);
		units.putAll(unitHolder.getUnits());
		
		for (String key : units.keySet()) {
			if (key != null) {
				if (key.startsWith(colorID)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Ping the players adjacent to a given system */
	public static void pingSystem(Map activeMap, GenericInteractionCreateEvent event, String position, String message) {
		if(activeMap.getTileByPosition(position) == null){
			return;
		}
		// get players adjacent
		List<Player> players = getAdjacentPlayers(activeMap, position, true);
		int successfulCount = 0;
		for (Player player_ : players) {
			String playerMessage = Helper.getPlayerRepresentation(player_, activeMap) + " - System " + position + " has been pinged:\n>>> " + message;
			boolean success = MessageHelper.sendPrivateMessageToPlayer(player_, activeMap, playerMessage);
			successfulCount += success ? 1 : 0;
		}
		feedbackMessage(event, successfulCount, players.size());
	}

	/** This will ping all players */
	public static void pingAllPlayers(Map activeMap, GenericInteractionCreateEvent event, String message) {
		int succesfulCount = 0;

		for (Player player_ : activeMap.getPlayers().values()) {
			String playerMessage = Helper.getPlayerRepresentation(player_, activeMap) + " all player ping\n>>> " + message;
			boolean success = MessageHelper.sendPrivateMessageToPlayer(player_, activeMap, playerMessage);
			succesfulCount += success ? 1 : 0;
		}
		feedbackMessage(event, succesfulCount, activeMap.getPlayers().size());
	}

	public static void pingAllPlayersWithFullStats(Map activeMap, GenericInteractionCreateEvent event, Player playerWithChange, String message) {
		var playersToPing = activeMap.getPlayers().values().stream()
				.filter(viewer -> initializeAndCheckStatVisibility(activeMap, playerWithChange, viewer))
				.collect(Collectors.toSet());
		int succesfulCount = 0;

		String playerMessage = Helper.getPlayerRepresentation(playerWithChange, activeMap) + " stats changed:\n" + message;
		for (Player player_ : playersToPing) {
			boolean success = MessageHelper.sendPrivateMessageToPlayer(player_, activeMap, playerMessage);
			succesfulCount += success ? 1 : 0;
		}
		feedbackMessage(event, succesfulCount, playersToPing.size());
	}

	public static void pingPlayersDifferentMessages(
		Map activeMap,
		GenericInteractionCreateEvent event,
		Player playerWithChange,
		String messageForFullInfo,
		String messageForAll
	) {
		Set<Player> playersWithVisiblity = activeMap.getPlayers().values().stream()
				.filter(viewer -> initializeAndCheckStatVisibility(activeMap, playerWithChange, viewer))
				.collect(Collectors.toSet());
		Set<Player> playersWithoutVisiblity = activeMap.getPlayers().values().stream()
				.filter(player -> !playersWithVisiblity.contains(player) && player != playerWithChange)
				.collect(Collectors.toSet());
		int succesfulCount = 0;
		int totalPings = playersWithVisiblity.size() + playersWithoutVisiblity.size();

		for (Player player_ : playersWithVisiblity) {
			boolean success = MessageHelper.sendPrivateMessageToPlayer(player_, activeMap, messageForFullInfo);
			succesfulCount += success ? 1 : 0;
		}
		for (Player player_ : playersWithoutVisiblity) {
			if (!player_.isRealPlayer()) continue;
			boolean success = MessageHelper.sendPrivateMessageToPlayer(player_, activeMap, messageForAll);
			succesfulCount += success ? 1 : 0;
		}
		feedbackMessage(event, succesfulCount, totalPings);
	}

	public static void pingPlayersTransaction(
		Map activeMap,
		GenericInteractionCreateEvent event,
		Player sendingPlayer,
		Player receivingPlayer,
		String transactedObject,
		String noVisibilityMessage // for stuff like SFTT
	) {
		int successCount = 0;
		int attemptCount = 0;
		// iterate through the player list. this may result in some extra pings, we'll
		// sort that out later
		for (Player player_ : activeMap.getPlayers().values()) {
			if (player_.getColor().equals("null")) continue;
			if (player_ == sendingPlayer || player_ == receivingPlayer) continue;
			attemptCount++;

			// let's figure out what they can see!
			initializeFog(activeMap, player_, false);
			boolean senderVisible = FoWHelper.canSeeStatsOfPlayer(activeMap, sendingPlayer, player_);
			boolean receiverVisible = FoWHelper.canSeeStatsOfPlayer(activeMap, receivingPlayer, player_);

			StringBuilder sb = new StringBuilder();
			// first off let's give full info for someone that can see both sides
			if (senderVisible) {
				sb.append(Helper.getPlayerRepresentation(sendingPlayer, activeMap));
			} else {
				sb.append("???");
			}
			sb.append(" sent " + transactedObject + " to ");
			if (receiverVisible) {
				sb.append(Helper.getPlayerRepresentation(receivingPlayer, activeMap));
			} else {
				sb.append("???");
			}

			String message = sb.toString();
			if (!senderVisible && !receiverVisible) {
				message = noVisibilityMessage;
			}
			boolean success = MessageHelper.sendPrivateMessageToPlayer(player_, activeMap, message);
			successCount += success ? 1 : 0;
		}
		feedbackMessage(event, successCount, attemptCount);
	}

	private static void feedbackMessage(GenericInteractionCreateEvent event, int success, int total) {
		if (success < total) {
			MessageHelper.replyToMessage(event, "One more more pings failed to send.  Please follow up with game's GM.");
		} else {
			MessageHelper.replyToMessage(event, "Succesfully sent all pings.");
		}
	}

	private static boolean initializeAndCheckStatVisibility(Map activeMap, Player player, Player viewer) {
		if (viewer == player) return false;
		if (viewer.getColor().equals("null")) return false;
		initializeFog(activeMap, viewer, false);
		return FoWHelper.canSeeStatsOfPlayer(activeMap, player, viewer);
	}
}
