package ti4.helpers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.combat.StartCombat;
import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.BorderAnomalyHolder;
import ti4.model.WormholeModel;

public class FoWHelper {

	public static boolean isPrivateGame(GenericInteractionCreateEvent event) {
		if (event == null) {
			return false;
		}
		return isPrivateGame(null, null, event.getChannel());
	}

	public static boolean isPrivateGame(GenericCommandInteractionEvent event) {
		return isPrivateGame(null, event);
	}

	public static boolean isPrivateGame(@Nullable Game game, GenericInteractionCreateEvent event) {
		return isPrivateGame(game, event, null);
	}

	public static boolean isPrivateGame(Game game) {
		return isPrivateGame(game, null, null);
	}

	public static boolean isPrivateGame(Game game, @Nullable GenericInteractionCreateEvent event,
		@Nullable Channel channel_) {
		Channel eventChannel = event == null ? null : event.getChannel();
		Channel channel = channel_ != null ? channel_ : eventChannel;
		if (channel == null) {
			return game.isFowMode();
		}
		if (game == null) {
			String gameName = channel.getName();
			gameName = gameName.replace(Constants.CARDS_INFO_THREAD_PREFIX, "");
			gameName = gameName.substring(0, gameName.indexOf("-"));
			game = GameManager.getInstance().getGame(gameName);
			if (game == null) {
				return false;
			}
		}
		if (game.isFowMode() && channel_ != null || event != null) {
			return channel.getName().endsWith(Constants.PRIVATE_CHANNEL);
		}
		return false;
	}

	/**
	 * Method to determine of a viewing player should be able to see the stats of a
	 * particular faction
	 */
	public static boolean canSeeStatsOfFaction(Game game, String faction, Player viewingPlayer) {
		for (Player player : game.getPlayers().values()) {
			if (faction.equals(player.getFaction())) {
				return canSeeStatsOfPlayer(game, player, viewingPlayer);
			}
		}
		return false;
	}

	/**
	 * Method to determine of a viewing player should be able to see the stats of a
	 * particular player
	 */
	public static boolean canSeeStatsOfPlayer(Game game, Player player, Player viewingPlayer) {
		if (!player.isRealPlayer() || !viewingPlayer.isRealPlayer()) {
			return false;
		}
		if (player == viewingPlayer) {
			return true;
		}

		return viewingPlayer != null && player != null && game != null &&
			(hasHomeSystemInView(game, player, viewingPlayer)
				|| hasPlayersPromInPlayArea(player, viewingPlayer)
				|| hasMahactCCInFleet(player, viewingPlayer) || viewingPlayer.getAllianceMembers().contains(player.getFaction()));
	}

	/**
	 * Check if the fog filter needs to be updated, then return the list of tiles
	 * that the player can see
	 */
	public static Set<String> fowFilter(Game game, Player player) {
		if (player != null) {
			updateFog(game, player);

			Set<String> systems = new HashSet<>();
			for (Map.Entry<String, Tile> tileEntry : new HashMap<>(game.getTileMap()).entrySet()) {
				if (!tileEntry.getValue().hasFog(player)) {
					systems.add(tileEntry.getKey());
				}
			}
			return systems;
		}
		return Collections.emptySet();
	}

	private static void initializeFog(Game game, @NotNull Player player, boolean forceRecalculate) {
		if (player.hasFogInitialized() && !forceRecalculate) {
			return;
		}

		// Get all tiles with the player in it
		Set<String> tilesWithPlayerUnitsPlanets = new HashSet<>();
		for (Map.Entry<String, Tile> tileEntry : new HashMap<>(game.getTileMap()).entrySet()) {
			if (playerIsInSystem(game, tileEntry.getValue(), player, false)) {
				tilesWithPlayerUnitsPlanets.add(tileEntry.getKey());
			}
		}

		Set<String> tilePositionsToShow = new HashSet<>(tilesWithPlayerUnitsPlanets);
		for (String tilePos : tilesWithPlayerUnitsPlanets) {
			Set<String> adjacentTiles = getAdjacentTiles(game, tilePos, player, true);
			tilePositionsToShow.addAll(adjacentTiles);
		}

		String playerSweep = Mapper.getSweepID(player.getColor());
		for (Tile tile : game.getTileMap().values()) {
			if (tile.hasCC(playerSweep)) {
				tilePositionsToShow.add(tile.getPosition());
			}
			boolean tileHasFog = !tilePositionsToShow.contains(tile.getPosition());
			tile.setTileFog(player, tileHasFog);
		}

		updatePlayerFogTiles(game, player);
		player.setFogInitialized(true);
	}

	public static Set<String> getTilePositionsToShow(Game game, @NotNull Player player) {
		// Get all tiles with the player in it
		Set<String> tilesWithPlayerUnitsPlanets = new HashSet<>();
		for (Map.Entry<String, Tile> tileEntry : new HashMap<>(game.getTileMap()).entrySet()) {
			if (playerIsInSystem(game, tileEntry.getValue(), player, false)) {
				tilesWithPlayerUnitsPlanets.add(tileEntry.getKey());
			}
		}

		Set<String> tilePositionsToShow = new HashSet<>(tilesWithPlayerUnitsPlanets);
		for (String tilePos : tilesWithPlayerUnitsPlanets) {
			Set<String> adjacentTiles = getAdjacentTiles(game, tilePos, player, true);
			tilePositionsToShow.addAll(adjacentTiles);
		}

		String playerSweep = Mapper.getSweepID(player.getColor());
		for (Tile tile : game.getTileMap().values()) {
			if (tile.hasCC(playerSweep)) {
				tilePositionsToShow.add(tile.getPosition());
			}
		}
		return tilePositionsToShow;
	}

	public static void updateFog(Game game, Player player) {
		if (player != null)
			initializeFog(game, player, true);
	}

	private static void updatePlayerFogTiles(Game game, Player player) {
		for (Tile tileToUpdate : game.getTileMap().values()) {
			if (!tileToUpdate.hasFog(player)) {
				player.updateFogTile(tileToUpdate, "Rnd " + game.getRound());
			}
		}
	}

	public static boolean hasHomeSystemInView(@NotNull Game game, @NotNull Player player,
		@NotNull Player viewingPlayer) {
		Tile tile = player.getHomeSystemTile();
		if (tile != null && !tile.hasFog(viewingPlayer)) {
			return true;
		}
		return false;
	}

	private static boolean hasPlayersPromInPlayArea(@NotNull Player player, @NotNull Player viewingPlayer) {
		boolean hasPromInPA = false;
		Game game = player.getGame();
		List<String> promissoriesInPlayArea = viewingPlayer.getPromissoryNotesInPlayArea();
		for (String prom_ : promissoriesInPlayArea) {
			if (game.getPNOwner(prom_) == player) {
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

	/**
	 * Return a list of tile positions that are adjacent to a source position.
	 * Includes custom adjacent tiles defined on the map level, hyperlanes, and
	 * wormholes
	 */
	public static Set<String> getAdjacentTiles(Game game, String position, Player player, boolean toShow) {
		return getAdjacentTiles(game, position, player, toShow, true);
	}

	public static Set<String> getAdjacentTiles(Game game, String position, Player player, boolean toShow, boolean includeTile) {
		Set<String> adjacentPositions = traverseAdjacencies(game, false, position);

		List<String> adjacentCustomTiles = game.getCustomAdjacentTiles().get(position);

		List<String> adjacentCustomTiles2 = new ArrayList<>();
		if (adjacentCustomTiles != null) {
			if (!toShow) {
				for (String t : adjacentCustomTiles) {
					if (game.getCustomAdjacentTiles().get(t) != null
						&& game.getCustomAdjacentTiles().get(t).contains(position)) {
						adjacentCustomTiles2.add(t);
					}
				}
				adjacentPositions.addAll(adjacentCustomTiles2);
			} else {
				adjacentPositions.addAll(adjacentCustomTiles);
			}
		}
		if (!toShow) {
			for (String primaryTile : game.getCustomAdjacentTiles().keySet()) {
				if (game.getCustomAdjacentTiles().get(primaryTile).contains(position)) {
					adjacentPositions.add(primaryTile);
				}
			}
		}

		Set<String> wormholeAdjacencies = getWormholeAdjacencies(game, position, player);
		adjacentPositions.addAll(wormholeAdjacencies);

		if (player != null && game.playerHasLeaderUnlockedOrAlliance(player, "ghoticommander")
			&& player == game.getActivePlayer() && !game.getActiveSystem().isEmpty()
			&& game.getTileByPosition(game.getActiveSystem()).getPlanetUnitHolders().size() == 0) {
			Collection<Tile> tileList = game.getTileMap().values();
			List<String> frontierTileList = Mapper.getFrontierTileIds();
			for (Tile tile : tileList) {
				if (tile.getPlanetUnitHolders().size() == 0 && (tile.getUnitHolders().size() == 2
					|| frontierTileList.contains(tile.getTileID()))) {
					adjacentPositions.add(tile.getPosition());
				}
			}
		}
		if (includeTile) {
			adjacentPositions.add(position);
		} else {
			adjacentPositions.remove(position);
		}
		return adjacentPositions;
	}

	public static Set<String> getAdjacentTilesAndNotThisTile(Game game, String position, Player player, boolean toShow) {

		return getAdjacentTiles(game, position, player, toShow, false);
	}

	/**
	 * Return a list of tile positions that are adjacent to a source position either
	 * directly or via hyperlanes
	 * <p>
	 * Does not traverse wormholes
	 */
	public static Set<String> traverseAdjacencies(Game game, boolean naturalMapOnly, String position) {
		return traverseAdjacencies(game, naturalMapOnly, position, -1, new HashSet<>(), null);
	}

	/**
	 * Return a list of tile positions that are adjacent to a source position either
	 * directly or via hyperlanes
	 * <p>
	 * Does not traverse wormholes
	 */
	private static Set<String> traverseAdjacencies(Game game, boolean naturalMapOnly, String position, Integer sourceDirection, Set<String> exploredSet, String prevTile) {
		Set<String> tiles = new HashSet<>();
		if (exploredSet.contains(position + sourceDirection)) {
			// We already explored this tile from this direction!
			return tiles;
		}
		// mark the tile as explored
		exploredSet.add(position + sourceDirection);

		Tile currentTile = game.getTileByPosition(position);
		if (currentTile == null) {
			// could not load the requested tile
			return tiles;
		}

		List<Boolean> hyperlaneData = currentTile.getHyperlaneData(sourceDirection);
		if (hyperlaneData != null && hyperlaneData.isEmpty() && !naturalMapOnly) {
			// We could not load the hyperlane data correctly, quit
			return tiles;
		}

		// we are allowed to see this tile
		tiles.add(position);

		if ((hyperlaneData == null || naturalMapOnly) && sourceDirection != -1) {
			// do not explore non-hyperlanes except for your starting space
			return tiles;
		}

		List<String> directlyAdjacentTiles = PositionMapper.getAdjacentTilePositions(position);
		if (directlyAdjacentTiles == null || directlyAdjacentTiles.size() != 6) {
			// adjacency file for this tile is not filled in
			return tiles;
		}

		// for each adjacent tile...
		for (int i = 0; i < 6; i++) {
			int dirFrom = (i + 3) % 6;
			String position_ = directlyAdjacentTiles.get(i);
			boolean borderBlocked = false;
			for (BorderAnomalyHolder b : game.getBorderAnomalies()) {
				if (b == null || b.getTile() == null) continue;
				if (b.getTile().equals(position) && b.getDirection() == i && b.blocksAdjacency())
					borderBlocked = true;
				if (b.getTile().equals(position_) && b.getDirection() == dirFrom && b.blocksAdjacency())
					borderBlocked = true;
			}
			if (borderBlocked && !naturalMapOnly)
				continue;

			String override = game.getAdjacentTileOverride(position, i);
			if (override != null) {
				if (naturalMapOnly)
					continue;
				position_ = override;
			}

			if ("x".equals(position_) || (hyperlaneData != null && !hyperlaneData.isEmpty() && !hyperlaneData.get(i))) {
				// the hyperlane doesn't exist & doesn't go that direction, skip.
				continue;
			}

			// explore that tile now!
			int direcetionFrom = naturalMapOnly ? -2 : dirFrom;
			Set<String> newTiles = traverseAdjacencies(game, naturalMapOnly, position_, direcetionFrom, exploredSet,
				position + sourceDirection);
			tiles.addAll(newTiles);
		}
		return tiles;
	}

	public static boolean isTileAdjacentToAnAnomaly(Game game, String position, Player player) {
		for (String adjPos : getAdjacentTilesAndNotThisTile(game, position, player, false)) {
			if (game.getTileByPosition(adjPos).isAnomaly(game)) {
				return true;
			}
		}
		return false;

	}

	public static boolean doesTileHaveWHs(Game game, String position) {
		return !getTileWHs(game, position).isEmpty();
	}

	public static Set<String> getTileWHs(Game game, String position) {
		Tile tile = game.getTileByPosition(position);

		String ghostFlagshipColor = null;
		for (Player p : game.getPlayers().values()) {
			if (p.ownsUnit("ghost_flagship")) {
				ghostFlagshipColor = p.getColor();
				break;
			}
		}

		Set<String> wormholeIDs = Mapper.getWormholes(tile.getTileID());
		for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
			Set<String> tokenList = unitHolder.getTokenList();
			for (String token : tokenList) {
				String tokenName = "wh" + token.replace("token_", "").replace(".png", "").replace("creuss", "");
				if (!tokenName.contains("champion")) {
					tokenName = tokenName.replace("ion", "");
				}
				for (WormholeModel.Wormhole wh : WormholeModel.Wormhole.values()) {
					if (tokenName.contains(wh.getWhString())) {
						//wormholeIDs.add(wh.getWhString());
						wormholeIDs.add(wh.toString());
						break;
					}
				}

			}
			if (unitHolder.getUnitCount(UnitType.Flagship, ghostFlagshipColor) > 0) {
				wormholeIDs.add(Constants.DELTA);
			}
		}

		return wormholeIDs;
	}

	public static boolean doesTileHaveAlphaOrBeta(Game game, String position) {
		Tile tile = game.getTileByPosition(position);

		Set<String> wormholeIDs = Mapper.getWormholes(tile.getTileID());
		for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
			Set<String> tokenList = unitHolder.getTokenList();
			for (String token : tokenList) {
				String tokenName = "wh" + token.replace("token_", "").replace(".png", "").replace("creuss", "");
				if (!tokenName.contains("champion")) {
					tokenName = tokenName.replace("ion", "");
				}
				for (WormholeModel.Wormhole wh : WormholeModel.Wormhole.values()) {
					if (tokenName.contains(wh.getWhString())) {
						wormholeIDs.add(wh.getWhString());
						if (!wh.toString().contains("eta") || wh.toString().contains("beta")) {
							wormholeIDs.add(wh.toString());
						}
						break;
					}
				}
			}
		}

		return (wormholeIDs.contains(Constants.ALPHA) || wormholeIDs.contains(Constants.BETA));
	}

	public static boolean doesTileHaveBeta(Game game, String position) {
		Tile tile = game.getTileByPosition(position);

		Set<String> wormholeIDs = Mapper.getWormholes(tile.getTileID());
		for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
			Set<String> tokenList = unitHolder.getTokenList();
			for (String token : tokenList) {
				String tokenName = "wh" + token.replace("token_", "").replace(".png", "").replace("creuss", "");
				if (!tokenName.contains("champion")) {
					tokenName = tokenName.replace("ion", "");
				}
				for (WormholeModel.Wormhole wh : WormholeModel.Wormhole.values()) {
					if (tokenName.contains(wh.getWhString())) {
						wormholeIDs.add(wh.getWhString());
						if (!wh.toString().contains("eta") || wh.toString().contains("beta")) {
							wormholeIDs.add(wh.toString());
						}
						break;
					}
				}
			}
		}

		return wormholeIDs.contains(Constants.BETA);
	}

	public static boolean doesTileHaveAlpha(Game game, String position) {
		Tile tile = game.getTileByPosition(position);

		Set<String> wormholeIDs = Mapper.getWormholes(tile.getTileID());
		for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
			Set<String> tokenList = unitHolder.getTokenList();
			for (String token : tokenList) {
				String tokenName = "wh" + token.replace("token_", "").replace(".png", "").replace("creuss", "");
				if (!tokenName.contains("champion")) {
					tokenName = tokenName.replace("ion", "");
				}
				for (WormholeModel.Wormhole wh : WormholeModel.Wormhole.values()) {
					if (tokenName.contains(wh.getWhString())) {
						wormholeIDs.add(wh.getWhString());
						break;
					}
				}
			}
		}

		return (wormholeIDs.contains(Constants.ALPHA));
	}

	/**
	 * Check the map for other tiles that have wormholes connecting to the source
	 * system.
	 * <p>
	 * Also takes into account player abilities and agendas
	 */
	private static Set<String> getWormholeAdjacencies(Game game, String position, Player player) {
		Set<String> adjacentPositions = new HashSet<>();
		Set<Tile> allTiles = new HashSet<>(game.getTileMap().values());
		Tile tile = game.getTileByPosition(position);

		String ghostFlagshipColor = null;
		for (Player p : game.getPlayers().values()) {
			if (p.ownsUnit("ghost_flagship")) {
				ghostFlagshipColor = p.getColor();
				break;
			}
		}

		boolean wh_recon = ButtonHelper.isLawInPlay(game, "wormhole_recon");
		boolean absol_recon = ButtonHelper.isLawInPlay(game, "absol_recon");
		if (tile == null || tile.getTileID() == null) {
			return adjacentPositions;
		}
		Set<String> wormholeIDs = Mapper.getWormholes(tile.getTileID());
		for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
			Set<String> tokenList = unitHolder.getTokenList();
			for (String token : tokenList) {
				String tokenName = "wh" + token.replace("token_", "").replace(".png", "").replace("creuss", "");
				if (!tokenName.contains("champion")) {
					tokenName = tokenName.replace("ion", "");
				}
				for (WormholeModel.Wormhole wh : WormholeModel.Wormhole.values()) {
					if (tokenName.contains(wh.getWhString())) {
						wormholeIDs.add(wh.getWhString());
						if (!wh.toString().contains("eta") || wh.toString().contains("beta")) {
							wormholeIDs.add(wh.toString());
						}
						break;
					}
				}
			}
			if (unitHolder.getUnitCount(UnitType.Flagship, ghostFlagshipColor) > 0) {
				wormholeIDs.add(Constants.DELTA);
			}
		}

		if ((player != null && player.hasAbility("quantum_entanglement")) || wh_recon || absol_recon) {
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
				Set<String> tokenList = unitHolder.getTokenList();
				for (String token : tokenList) {
					for (String wormholeID : wormholeIDs) {
						if (token.contains(wormholeID)) {
							adjacentPositions.add(position_);
						}
					}
				}
				if (wormholeIDs.contains(Constants.DELTA)
					&& unitHolder.getUnitCount(UnitType.Flagship, ghostFlagshipColor) > 0) {
					adjacentPositions.add(position_);
				}
			}
		}
		return adjacentPositions;
	}

	/**
	 * Return the list of players that are adjacent to a particular position
	 * <p>
	 * WARNING: This function returns information that certain players may not be
	 * privy to
	 */
	public static List<Player> getAdjacentPlayers(Game game, String position, boolean includeSweep) {
		List<Player> players = new ArrayList<>();
		Set<String> tilesToCheck = getAdjacentTiles(game, position, null, false);
		Tile startingTile = game.getTileByPosition(position);

		for (Player player_ : game.getRealPlayers()) {
			Set<String> tiles = new HashSet<>(tilesToCheck);
			if (player_.hasAbility("quantum_entanglement")) {
				tiles.addAll(getWormholeAdjacencies(game, position, player_));
			}

			if (includeSweep && startingTile.hasCC(Mapper.getSweepID(player_.getColor()))) {
				players.add(player_);
				continue;
			}

			for (String position_ : tiles) {
				Tile tile = game.getTileByPosition(position_);
				if (tile != null) {
					if (playerIsInSystem(game, tile, player_, true)) {
						players.add(player_);
						break;
					}
				}

			}
		}

		return players;
	}

	/** Check if the specified player should have vision on the system */
	public static boolean playerIsInSystem(Game game, Tile tile, Player player, boolean forNeighbors) {
		Set<String> unitHolderNames = tile.getUnitHolders().keySet();
		List<String> playerPlanets = player.getPlanetsAllianceMode();
		if (forNeighbors) {
			playerPlanets = player.getPlanets();
		}
		if (playerPlanets.stream().anyMatch(unitHolderNames::contains)) {
			return true;
		} else if (tile.isMecatol() && player.hasTech("iihq")) {
			return true;
		} else if ("s11".equals(tile.getTileID()) && canSeeStatsOfFaction(game, "cabal", player)) {
			return true;
		} else if ("s12".equals(tile.getTileID()) && canSeeStatsOfFaction(game, "nekro", player)) {
			return true;
		} else if ("s13".equals(tile.getTileID()) && canSeeStatsOfFaction(game, "yssaril", player)) {
			return true;
		}

		return playerHasUnitsInSystem(player, tile);
	}

	/** Check if the player has units in the system */
	public static boolean playerHasUnitsInSystem(Player player, Tile tile) {
		return tile.containsPlayersUnits(player);
	}

	public static boolean playerHasPlanetsInSystem(Player player, Tile tile) {
		if (tile == null || player == null) {
			return false;
		}
		for (UnitHolder uH : tile.getPlanetUnitHolders()) {
			if (player.getPlanetsAllianceMode().contains(uH.getName())) {
				return true;
			}
		}
		return false;

	}

	public static boolean playerHasShipsInSystem(Player player, Tile tile) {
		String colorID = Mapper.getColorID(player.getColor());
		if (colorID == null)
			return false; // player doesn't have a color

		UnitHolder unitHolder = tile.getUnitHolders().get(Constants.SPACE);
		Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());

		for (UnitKey unitKey : units.keySet()) {
			if (unitKey != null && unitKey.getColorID().equals(colorID)) {
				return true;
			}
		}
		return false;
	}

	public static boolean playerHasActualShipsInSystem(Player player, Tile tile) {
		String colorID = Mapper.getColorID(player.getColor());
		if (colorID == null)
			return false; // player doesn't have a color

		UnitHolder unitHolder = tile.getUnitHolders().get(Constants.SPACE);
		Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());

		for (UnitKey unitKey : units.keySet()) {
			if (unitKey != null && unitKey.getColorID().equals(colorID) && player.getUnitFromAsyncID(unitKey.asyncID()) != null && player.getUnitFromAsyncID(unitKey.asyncID()).getIsShip()) {
				return true;
			}
		}
		return false;
	}

	public static boolean otherPlayersHaveShipsInSystem(Player player, Tile tile, Game game) {
		for (Player p2 : game.getRealPlayersNDummies()) {
			if (p2 == player || player.getAllianceMembers().contains(p2.getFaction())) {
				continue;
			}
			if (playerHasShipsInSystem(p2, tile)) {
				return true;
			}
		}
		return false;
	}

	public static boolean otherPlayersHaveUnitsInSystem(Player player, Tile tile, Game game) {
		for (Player p2 : game.getRealPlayersNDummies()) {
			if (p2 == player || player.getAllianceMembers().contains(p2.getFaction())) {
				continue;
			}
			if (playerHasUnitsInSystem(p2, tile)) {
				return true;
			}
		}
		return false;
	}

	public static boolean playerHasFightersInSystem(Player player, Tile tile) {
		String colorID = Mapper.getColorID(player.getColor());
		if (colorID == null)
			return false; // player doesn't have a color

		UnitHolder unitHolder = tile.getUnitHolders().get(Constants.SPACE);
		Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());

		for (UnitKey unitKey : units.keySet()) {
			if (unitKey != null && unitKey.getColorID().equals(colorID) && unitKey.getUnitType() == UnitType.Fighter) {
				return true;
			}
		}
		return false;
	}

	public static boolean playerHasFightersInAdjacentSystems(Player player, Tile tile, Game game) {
		String colorID = Mapper.getColorID(player.getColor());
		if (colorID == null)
			return false; // player doesn't have a color
		for (String pos : getAdjacentTilesAndNotThisTile(game, tile.getPosition(), player, false)) {
			Tile tile2 = game.getTileByPosition(pos);
			UnitHolder unitHolder = tile2.getUnitHolders().get(Constants.SPACE);
			if (unitHolder.getUnitCount(UnitType.Fighter, player.getColor()) > 0) {
				return true;
			}
		}
		return false;
	}

	public static boolean playerHasUnitsOnPlanet(Player player, Tile tile, String planet) {
		String colorID = Mapper.getColorID(player.getColor());
		if (colorID == null)
			return false; // player doesn't have a color

		UnitHolder unitHolder = tile.getUnitHolders().get(planet);
		Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());

		for (UnitKey unitKey : units.keySet()) {
			if (unitKey != null && unitKey.getColorID().equals(colorID)) {
				return true;
			}
		}
		return false;
	}

	public static boolean playerHasInfantryOnPlanet(Player player, Tile tile, String planet) {
		String colorID = Mapper.getColorID(player.getColor());
		if (colorID == null)
			return false; // player doesn't have a color

		UnitHolder unitHolder = tile.getUnitHolders().get(planet);
		Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());

		for (UnitKey unitKey : units.keySet()) {
			if (unitKey != null && unitKey.getColorID().equals(colorID) && unitKey.getUnitType() == UnitType.Infantry) {
				return true;
			}
		}
		return false;
	}

	/** Ping the players adjacent to a given system */
	public static void pingSystem(Game game, GenericInteractionCreateEvent event, String position, String message) {
		if (game.getTileByPosition(position) == null) {
			return;
		}
		// get players adjacent
		List<Player> players = getAdjacentPlayers(game, position, true);
		int successfulCount = 0;
		for (Player player_ : players) {
			boolean success = true;
			if (player_.isRealPlayer()) {
				String playerMessage = player_.getRepresentation() + " - System " + position + " has been pinged:\n>>> "
					+ message;
				success = MessageHelper.sendPrivateMessageToPlayer(player_, game, playerMessage);
				MessageChannel channel = player_.getPrivateChannel();
				MessageHelper.sendMessageToChannelWithButtons(channel, "Use Button to refresh view of system",
					StartCombat.getGeneralCombatButtons(game, position, player_, player_, "justPicture", event));
			}
			successfulCount += success ? 1 : 0;
		}
		feedbackMessage(event, successfulCount, players.size());
	}

	/** This will ping all players */
	public static void pingAllPlayers(Game game, GenericInteractionCreateEvent event, String message) {
		int succesfulCount = 0;

		for (Player player_ : game.getPlayers().values()) {
			String playerMessage = player_.getRepresentation() + " all player ping\n>>> " + message;
			boolean success = MessageHelper.sendPrivateMessageToPlayer(player_, game, playerMessage);
			succesfulCount += success ? 1 : 0;
		}
		feedbackMessage(event, succesfulCount, game.getPlayers().size());
	}

	public static void pingAllPlayersWithFullStats(Game game, GenericInteractionCreateEvent event, Player playerWithChange, String message) {
		var playersToPing = game.getPlayers().values().stream()
			.filter(viewer -> initializeAndCheckStatVisibility(game, playerWithChange, viewer))
			.collect(Collectors.toSet());
		int succesfulCount = 0;

		String playerMessage = playerWithChange.getRepresentation() + " stats changed:\n" + message;
		for (Player player_ : playersToPing) {
			boolean success = MessageHelper.sendPrivateMessageToPlayer(player_, game, playerMessage);
			succesfulCount += success ? 1 : 0;
		}
		feedbackMessage(event, succesfulCount, playersToPing.size());
	}

	public static void pingPlayersDifferentMessages(Game game, GenericInteractionCreateEvent event, Player playerWithChange, String messageForFullInfo, String messageForAll) {
		Set<Player> playersWithVisiblity = game.getPlayers().values().stream()
			.filter(viewer -> initializeAndCheckStatVisibility(game, playerWithChange, viewer))
			.collect(Collectors.toSet());
		Set<Player> playersWithoutVisiblity = game.getPlayers().values().stream()
			.filter(player -> !playersWithVisiblity.contains(player) && player != playerWithChange)
			.collect(Collectors.toSet());
		int succesfulCount = 0;
		int totalPings = playersWithVisiblity.size() + playersWithoutVisiblity.size();

		for (Player player_ : playersWithVisiblity) {
			boolean success = MessageHelper.sendPrivateMessageToPlayer(player_, game, messageForFullInfo);
			succesfulCount += success ? 1 : 0;
		}
		for (Player player_ : playersWithoutVisiblity) {
			if (!player_.isRealPlayer())
				continue;
			boolean success = MessageHelper.sendPrivateMessageToPlayer(player_, game, messageForAll);
			succesfulCount += success ? 1 : 0;
		}
		feedbackMessage(event, succesfulCount, totalPings);
	}

	public static void pingPlayersTransaction(
		Game game,
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
		for (Player player_ : game.getPlayers().values()) {
			if ("null".equals(player_.getColor()))
				continue;
			if (player_ == sendingPlayer || player_ == receivingPlayer)
				continue;
			attemptCount++;

			// let's figure out what they can see!
			initializeFog(game, player_, false);
			boolean senderVisible = canSeeStatsOfPlayer(game, sendingPlayer, player_);
			boolean receiverVisible = canSeeStatsOfPlayer(game, receivingPlayer, player_);

			StringBuilder sb = new StringBuilder();
			// first off let's give full info for someone that can see both sides
			if (senderVisible) {
				sb.append(sendingPlayer.getRepresentation());
			} else {
				sb.append("???");
			}
			sb.append(" sent ").append(transactedObject).append(" to ");
			if (receivingPlayer == null) {
				BotLogger.log(event, "`FoWHelper.pingPlayersTransaction` Warning, receivingPlayer is null");
			}
			if (receiverVisible && receivingPlayer != null) {
				sb.append(receivingPlayer.getRepresentation());
			} else {
				sb.append("???");
			}

			String message = sb.toString();
			if (!senderVisible && !receiverVisible) {
				message = noVisibilityMessage;
			}
			boolean success = MessageHelper.sendPrivateMessageToPlayer(player_, game, message);
			successCount += success ? 1 : 0;
		}
		feedbackMessage(event, successCount, attemptCount);
	}

	private static void feedbackMessage(GenericInteractionCreateEvent event, int success, int total) {
		if (success < total) {
			MessageHelper.replyToMessage(event,
				"One more more pings failed to send.  Please follow up with game's GM.");
		} else {
			MessageHelper.replyToMessage(event, "Successfully sent all pings.");
		}
	}

	private static boolean initializeAndCheckStatVisibility(Game game, Player player, Player viewer) {
		if (viewer == player)
			return false;
		if ("null".equals(viewer.getColor()))
			return false;
		initializeFog(game, viewer, false);
		return canSeeStatsOfPlayer(game, player, viewer);
	}

	public static void sanityCheckFowReacts() {
		List<String> badEmojis = new ArrayList<>(Emojis.symbols).stream()
			.map(emoji -> Emoji.fromFormatted(emoji))
			.map(emoji -> (emoji instanceof CustomEmoji c) ? c : null)
			.filter(e -> e != null)
			.filter(e -> AsyncTI4DiscordBot.jda.getEmojiById(e.getId()) == null)
			.map(emoji -> emoji.getName() + " " + emoji.getId())
			.toList();
		if (badEmojis.size() > 0) {
			StringBuilder sb = new StringBuilder(Constants.jazzPing());
			sb.append(" Bad emojis are being used for FOW reacts:\n");
			for (String err : badEmojis) {
				sb.append("```\n").append(err).append("\n```");
			}
			BotLogger.log(sb.toString());
		}
	}
}
