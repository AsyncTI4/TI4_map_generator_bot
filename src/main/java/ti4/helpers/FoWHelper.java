package ti4.helpers;

import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;

import java.util.*;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import ti4.commands.cards.CardsInfo;
import ti4.generator.Mapper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

public class FoWHelper {

	public static Boolean isPrivateGame(GenericInteractionCreateEvent event) {
		return isPrivateGame(null, null, event.getChannel());
	}

	public static Boolean isPrivateGame(GenericCommandInteractionEvent event) {
		return isPrivateGame(null, event);
	}

	public static Boolean isPrivateGame(@Nullable Map map, GenericCommandInteractionEvent event) {
		return isPrivateGame(map, event, null);
	}
	
	/** Method to determine of a viewing player should be able to see the stats of a particular faction */
	public static boolean canSeeStatsOfFaction(Map map, String faction, Player viewingPlayer) {
		for (Player player : map.getPlayers().values()) {
			if (faction.equals(player.getFaction())) {
				return canSeeStatsOfPlayer(map, player, viewingPlayer);
			}
		}
		return false;
	}

	/** Method to determine of a viewing player should be able to see the stats of a particular player */
	public static boolean canSeeStatsOfPlayer(Map map, Player player, Player viewingPlayer) {
		if (player == viewingPlayer) {
			return true;
		}

		return viewingPlayer != null && player != null && (hasHomeSystemInView(map, player, viewingPlayer)
				|| hasPlayersPromInPlayArea(player, viewingPlayer) || hasMahactCCInFleet(player, viewingPlayer));
	}

	/** Check if the fog filter needs to be updated, then return the list of tiles that the player can see */
	public static Set<String> fowFilter(Map map, Player player) {
		if (player != null) {
			initializeFog(map, player);

			Set<String> systems = new HashSet<>();
			for (java.util.Map.Entry<String, Tile> tileEntry : new HashMap<>(map.getTileMap()).entrySet()) {
				if (!tileEntry.getValue().hasFog(player)) {
					systems.add(tileEntry.getKey());
				}
			}
			return systems;
		}
		return Collections.emptySet();
	}

	private static void initializeFog(Map map, @NotNull Player player) {
		// if (player.hasFogInitialized()) {
		// 	return;
		// }

		// Get all tiles with the player in it
		Set<String> tilesWithPlayerUnitsPlanets = new HashSet<>();
		for (java.util.Map.Entry<String, Tile> tileEntry : new HashMap<>(map.getTileMap()).entrySet()) {
			if (FoWHelper.playerIsInSystem(map, tileEntry.getValue(), player)) {
				tilesWithPlayerUnitsPlanets.add(tileEntry.getKey());
			}
		}

		Set<String> tilePositionsToShow = new HashSet<>(tilesWithPlayerUnitsPlanets);
		for (String tilePos : tilesWithPlayerUnitsPlanets) {
			Set<String> adjacentTiles = FoWHelper.getAdjacentTiles(map, tilePos, player);
			tilePositionsToShow.addAll(adjacentTiles);
		}

		for (Tile tile : map.getTileMap().values()) {
			boolean tileHasFog = !tilePositionsToShow.contains(tile.getPosition());
			tile.setTileFog(player, tileHasFog);
		}

		updatePlayerFogTiles(map, player, tilePositionsToShow);
		player.setFogInitialized(true);
	}

	public static void updateFog(Map map, Player player) {
		player.setFogInitialized(false);
		initializeFog(map, player);
	}

    private static void updatePlayerFogTiles(Map map, Player player, Set<String> tileKeys) {
        for (String key_ : tileKeys) {
            Tile tileToUpdate = map.getTileByPosition(key_);

            if (tileToUpdate != null) {
                player.updateFogTile(tileToUpdate, "Round " + map.getRound());
            }
        }
    }

	private static boolean hasHomeSystemInView(@NotNull Map map, @NotNull Player player, @NotNull Player viewingPlayer) {
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
			String playerSetup = Mapper.getPlayerSetup(faction);
			if (playerSetup != null) {
				String[] setupInfo = playerSetup.split(";");
				hsIDs.add(setupInfo[1]);
			}
		}

		for (Tile tile : map.getTileMap().values()) {
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

	public static Boolean isPrivateGame(Map map, @Nullable GenericCommandInteractionEvent event,
			@Nullable Channel channel_) {
		Boolean isFoWPrivate = null;
		Channel eventChannel = event == null ? null : event.getChannel();
		Channel channel = channel_ != null ? channel_ : eventChannel;
		if (channel == null) {
			return null;
		}
		if (map == null) {
			String gameName = channel.getName();
			gameName = gameName.replace(CardsInfo.CARDS_INFO, "");
			gameName = gameName.substring(0, gameName.indexOf("-"));
			map = MapManager.getInstance().getMap(gameName);
			if (map == null) {
				return isFoWPrivate;
			}
		}
		if (map.isFoWMode() && channel_ != null || event != null) {
			isFoWPrivate = channel.getName().endsWith(Constants.PRIVATE_CHANNEL);
		}
		return isFoWPrivate;
	}

	/** Return a list of tile positions that are adjacent to a source position.
	 *  Includes custom adjacent tiles defined on the map level, hyperlanes, and
	 *  wormholes
	 */
	public static Set<String> getAdjacentTiles(Map map, String position, Player player) {
		Set<String> adjacentPositions = traverseHyperlaneAdjacencies(map, position, -1, new HashSet<>(), null);

		List<String> adjacentCustomTiles = map.getCustomAdjacentTiles().get(position);
		if (adjacentCustomTiles != null) {
			adjacentPositions.addAll(adjacentCustomTiles);
		}

		Set<String> wormholeAdjacencies = getWormholeAdjacencies(map, position, player);
		if (wormholeAdjacencies != null) {
			adjacentPositions.addAll(wormholeAdjacencies);
		}

		return adjacentPositions;
	}

	/** Return a list of tile positions that are adjacent to a source position either directly or via hyperlanes
	 * <p>
	 * Does not traverse wormholes
	 */
	private static Set<String> traverseHyperlaneAdjacencies(Map map, String position, Integer sourceDirection,
			Set<String> exploredSet, String prevTile) {
		Set<String> tiles = new HashSet<>();
		if (exploredSet.contains(position + sourceDirection)) {
			// We already explored this tile from this direction!
			return tiles;
		}
		// mark the tile as explored
		exploredSet.add(position + sourceDirection);

		Tile currentTile = map.getTileByPosition(position);
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

		if (hyperlaneData == null && sourceDirection != -1) {
			// do not explore non-hyperlanes except for your starting space
			return tiles;
		}

		List<String> directlyAdjacentTiles = Mapper.getAdjacentTilePositions(position);
		if (directlyAdjacentTiles == null || directlyAdjacentTiles.size() != 6) {
			// adjacency file for this tile is not filled in
			return tiles;
		}

		// for each adjacent tile...
		for (int i = 0; i < 6; i++) {
			String position_ = directlyAdjacentTiles.get(i);
			
			String override = map.getAdjacentTileOverride(position, i);
			if (override != null) {
				position_ = override;
			}
			
			if (position_.equals("x") || (hyperlaneData != null && !hyperlaneData.get(i))) {
				// the hyperlane doesn't exist & doesn't go that direction, skip.
				continue;
			}

			// explore that tile now!
			Set<String> newTiles = traverseHyperlaneAdjacencies(map, position_, (i + 3) % 6, exploredSet,
					position + sourceDirection);
			tiles.addAll(newTiles);
		}
		return tiles;
	}

	/** Check the map for other tiles that have wormholes connecting to the source system.
	 *  <p>
	 *  Also takes into account player abilities and agendas
	 */
	private static Set<String> getWormholeAdjacencies(Map map, String position, Player player) {
		Set<String> adjacentPositions = new HashSet<>();
		Set<Tile> allTiles = new HashSet<Tile>(map.getTileMap().values());
		Tile tile = map.getTileByPosition(position);

		String ghostFlagship = null;
		for (Player p : map.getPlayers().values()) {
			if ("ghost".equals(p.getFaction())) {
				ghostFlagship = Mapper.getUnitID("fs", p.getColor());
				break;
			}
		}

		Set<String> wormholeIDs = Mapper.getWormholes(tile.getTileID());
		for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
			HashSet<String> tokenList = unitHolder.getTokenList();
			for (String token : tokenList) {
				if (token.contains(Constants.ALPHA)) {
					wormholeIDs.add(Constants.ALPHA);
					if ((player != null && "ghost".equals(player.getFaction()))
							|| map.getLaws().keySet().contains("wormhole_recon")
							|| map.getLaws().keySet().contains("absol_recon")) {
						wormholeIDs.add(Constants.BETA);
					}
				} else if (token.contains(Constants.BETA)) {
					wormholeIDs.add(Constants.BETA);
					if ((player != null && "ghost".equals(player.getFaction()))
							|| map.getLaws().keySet().contains("wormhole_recon")
							|| map.getLaws().keySet().contains("absol_recon")) {
						wormholeIDs.add(Constants.ALPHA);
					}
				} else if (token.contains(Constants.GAMMA)) {
					wormholeIDs.add(Constants.GAMMA);
				} else if (token.contains(Constants.DELTA)) {
					wormholeIDs.add(Constants.DELTA);
				}
			}
			if (ghostFlagship != null && unitHolder.getUnits().getOrDefault(ghostFlagship, 0) > 0) {
				wormholeIDs.add(Constants.DELTA);
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
	public static List<Player> getAdjacentPlayers(Map map, String position) {
		List<Player> players = new ArrayList<>();
		Set<String> tilesToCheck = getAdjacentTiles(map, position, null);

		for (Player player_ : map.getPlayers().values()) {
			Set<String> tiles = new HashSet<>(tilesToCheck);
			if ("ghost".equals(player_.getFaction())) {
				tiles.addAll(getWormholeAdjacencies(map, position, player_));
			}

			for (String position_ : tiles) {
				Tile tile = map.getTileByPosition(position_);
				if (playerIsInSystem(map, tile, player_)) {
					players.add(player_);
					break;
				}
			}
		}

		return players;
	}

	/** Check if the specified player should have vision on the system */
	public static boolean playerIsInSystem(Map map, Tile tile, Player player) {
		Set<String> unitHolderNames = tile.getUnitHolders().keySet();
		List<String> playerPlanets = player.getPlanets();
		if (playerPlanets.stream().anyMatch(unitHolderNames::contains)) {
			return true;
		} else if ("18".equals(tile.getTileID()) && player.getTechs().contains("iihq")) {
			return true;
		} else if ("s11".equals(tile.getTileID()) && "cabal".equals(player.getFaction())) {
			return true;
		} else if ("s12".equals(tile.getTileID()) && "nekro".equals(player.getFaction())) {
			return true;
		} else if ("s13".equals(tile.getTileID()) && "yssaril".equals(player.getFaction())) {
			return true;
		}

		return playerHasUnitsInSystem(player, tile);
	}

	/** Check if the player has units in the system */
	public static boolean playerHasUnitsInSystem(Player player, Tile tile) {
		if (!player.isActivePlayer())
			return false;

		String colorID = Mapper.getColorID(player.getColor());
		HashMap<String, Integer> units = new HashMap<>();
		for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
			units.putAll(unitHolder.getUnits());
		}
		for (String key : units.keySet()) {
			if (key.startsWith(colorID)) {
				return true;
			}
		}
		return false;
	}

	/** Ping the players adjacent to a given system */
	public static void pingSystem(Map activeMap, GenericInteractionCreateEvent event, String position, String message) {
		// get players adjacent
		List<Player> players = getAdjacentPlayers(activeMap, position);
		int successfulCount = 0;
		for (Player player_ : players) {
			String playerMessage = Helper.getPlayerRepresentation(event, player_, true) + " - System " + position + " has been pinged:\n>>> " + message;
			boolean success = MessageHelper.sendPrivateMessageToPlayer(player_, activeMap, playerMessage);
			successfulCount += success ? 1 : 0;
		}

		if (successfulCount < players.size()) {
			MessageHelper.replyToMessage(event, "One or more pings failed to send. Please follow up with game's GM.");
		} else {
			MessageHelper.replyToMessage(event, "Successfully sent all pings.");
		}
	}

	/** This will ping all players */
	public static void pingAllPlayers(Map activeMap, GenericInteractionCreateEvent event, String message) {
		int succesfulCount = 0;

		for (Player player_ : activeMap.getPlayers().values()) {
			String playerMessage = Helper.getPlayerRepresentation(event, player_, true) + " all player ping\n>>> " + message;
			boolean success = MessageHelper.sendPrivateMessageToPlayer(player_, activeMap, playerMessage);
			succesfulCount += success ? 1 : 0;
		}

		if (succesfulCount < activeMap.getPlayers().size()) {
			MessageHelper.replyToMessage(event, "One more more pings failed to send.  Please follow up with game's GM.");
		} else {
			MessageHelper.replyToMessage(event, "Succesfully sent all pings.");
		}
	}

	public static void pingAllPlayersWithFullStats(Map activeMap, GenericInteractionCreateEvent event,
			Player playerWithChange, String message) {
		var playersToPing = activeMap.getPlayers().values().stream()
				.filter(viewer -> FoWHelper.canSeeStatsOfPlayer(activeMap, playerWithChange, viewer))
				.collect(Collectors.toSet());
		int succesfulCount = 0;

		for (Player player_ : playersToPing) {
			String playerMessage = Helper.getPlayerRepresentation(event, player_, true) + " stats change\n> " + message;
			boolean success = MessageHelper.sendPrivateMessageToPlayer(player_, activeMap, playerMessage);
			succesfulCount += success ? 1 : 0;
		}

		if (succesfulCount < playersToPing.size()) {
			MessageHelper.replyToMessage(event, "One more more pings failed to send.  Please follow up with game's GM.");
		} else {
			MessageHelper.replyToMessage(event, "Succesfully sent all pings.");
		}
	}

	public static void pingPlayersDifferentMessages(
		Map activeMap, 
		GenericInteractionCreateEvent event,
		Player playerWithChange, 
		String messageForFullInfo, 
		String messageForAll
	) {
		Set<Player> playersWithVisiblity = activeMap.getPlayers().values().stream()
				.filter(viewer -> FoWHelper.canSeeStatsOfPlayer(activeMap, playerWithChange, viewer))
				.collect(Collectors.toSet());
		Set<Player> playersWithoutVisiblity = activeMap.getPlayers().values().stream()
				.filter(player -> !playersWithVisiblity.contains(player)).collect(Collectors.toSet());

		int succesfulCount = 0;

		for (Player player_ : playersWithVisiblity) {
			boolean success = MessageHelper.sendPrivateMessageToPlayer(player_, activeMap, messageForFullInfo);
			succesfulCount += success ? 1 : 0;
		}
		for (Player player_ : playersWithoutVisiblity) {
			boolean success = MessageHelper.sendPrivateMessageToPlayer(player_, activeMap, messageForAll);
			succesfulCount += success ? 1 : 0;
		}

		if (succesfulCount < activeMap.getPlayers().values().size()) {
			MessageHelper.replyToMessage(event, "One more more pings failed to send.  Please follow up with game's GM.");
		} else {
			MessageHelper.replyToMessage(event, "Succesfully sent all pings.");
		}

	}

}
