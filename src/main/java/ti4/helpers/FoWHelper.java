package ti4.helpers;

import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;

import java.util.*;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import ti4.commands.cardsac.ACInfo_Legacy;
import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.WormholeModel;

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

	public static Boolean isPrivateGame(@Nullable Game activeGame, GenericInteractionCreateEvent event) {
		return isPrivateGame(activeGame, event, null);
	}

	public static Boolean isPrivateGame(Game activeGame) {
		return isPrivateGame(activeGame, null, null);
	}

	/** Method to determine of a viewing player should be able to see the stats of a particular faction */
	public static boolean canSeeStatsOfFaction(Game activeGame, String faction, Player viewingPlayer) {
		for (Player player : activeGame.getPlayers().values()) {
			if (faction.equals(player.getFaction())) {
				return canSeeStatsOfPlayer(activeGame, player, viewingPlayer);
			}
		}
		return false;
	}

	/** Method to determine of a viewing player should be able to see the stats of a particular player */
	public static boolean canSeeStatsOfPlayer(Game activeGame, Player player, Player viewingPlayer) {
		if (player == viewingPlayer) {
			return true;
		}

		return viewingPlayer != null && player != null && activeGame != null &&
			( hasHomeSystemInView(activeGame, player, viewingPlayer)
				|| hasPlayersPromInPlayArea(player, viewingPlayer)
				|| hasMahactCCInFleet(player, viewingPlayer)
			);
	}

	/** Check if the fog filter needs to be updated, then return the list of tiles that the player can see */
	public static Set<String> fowFilter(Game activeGame, Player player) {
		if (player != null) {
			updateFog(activeGame, player);

			Set<String> systems = new HashSet<>();
			for (Map.Entry<String, Tile> tileEntry : new HashMap<>(activeGame.getTileMap()).entrySet()) {
				if (!tileEntry.getValue().hasFog(player)) {
					systems.add(tileEntry.getKey());
				}
			}
			return systems;
		}
		return Collections.emptySet();
	}

	private static void initializeFog(Game activeGame, @NotNull Player player, boolean forceRecalculate) {
		if (player.hasFogInitialized() && !forceRecalculate) {
			return;
		}

		// Get all tiles with the player in it
		Set<String> tilesWithPlayerUnitsPlanets = new HashSet<>();
		for (Map.Entry<String, Tile> tileEntry : new HashMap<>(activeGame.getTileMap()).entrySet()) {
			if (FoWHelper.playerIsInSystem(activeGame, tileEntry.getValue(), player)) {
				tilesWithPlayerUnitsPlanets.add(tileEntry.getKey());
			}
		}

		Set<String> tilePositionsToShow = new HashSet<>(tilesWithPlayerUnitsPlanets);
		for (String tilePos : tilesWithPlayerUnitsPlanets) {
			Set<String> adjacentTiles = FoWHelper.getAdjacentTiles(activeGame, tilePos, player, true);
			tilePositionsToShow.addAll(adjacentTiles);
		}

		String playerSweep = Mapper.getSweepID(player.getColor());
		for (Tile tile : activeGame.getTileMap().values()) {
			if (tile.hasCC(playerSweep)) {
				tilePositionsToShow.add(tile.getPosition());
			}
			boolean tileHasFog = !tilePositionsToShow.contains(tile.getPosition());
			tile.setTileFog(player, tileHasFog);
		}

		updatePlayerFogTiles(activeGame, player);
		player.setFogInitialized(true);
	}

	public static Set<String> getTilePositionsToShow(Game activeGame, @NotNull Player player) {
		// Get all tiles with the player in it
		Set<String> tilesWithPlayerUnitsPlanets = new HashSet<>();
		for (Map.Entry<String, Tile> tileEntry : new HashMap<>(activeGame.getTileMap()).entrySet()) {
			if (FoWHelper.playerIsInSystem(activeGame, tileEntry.getValue(), player)) {
				tilesWithPlayerUnitsPlanets.add(tileEntry.getKey());
			}
		}

		Set<String> tilePositionsToShow = new HashSet<>(tilesWithPlayerUnitsPlanets);
		for (String tilePos : tilesWithPlayerUnitsPlanets) {
			Set<String> adjacentTiles = FoWHelper.getAdjacentTiles(activeGame, tilePos, player, true);
			tilePositionsToShow.addAll(adjacentTiles);
		}

		String playerSweep = Mapper.getSweepID(player.getColor());
		for (Tile tile : activeGame.getTileMap().values()) {
			if (tile.hasCC(playerSweep)) {
				tilePositionsToShow.add(tile.getPosition());
			}
		}
		return tilePositionsToShow;
	}

	public static void updateFog(Game activeGame, Player player) {
		if (player != null) initializeFog(activeGame, player, true);
	}

    private static void updatePlayerFogTiles(Game activeGame, Player player) {
        for (Tile tileToUpdate : activeGame.getTileMap().values()) {
            if (!tileToUpdate.hasFog(player)) {
                player.updateFogTile(tileToUpdate, "Round " + activeGame.getRound());
            }
        }
    }

	private static boolean hasHomeSystemInView(@NotNull Game activeGame, @NotNull Player player, @NotNull Player viewingPlayer) {
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
				hsIDs.add(playerSetup.getHomeSystem());
			}
		}

		for (Tile tile : activeGame.getTileMap().values()) {
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

	public static Boolean isPrivateGame(Game activeGame, @Nullable GenericInteractionCreateEvent event, @Nullable Channel channel_) {
		Boolean isFoWPrivate = null;
		Channel eventChannel = event == null ? null : event.getChannel();
		Channel channel = channel_ != null ? channel_ : eventChannel;
		if (channel == null) {
			return activeGame.isFoWMode();
		}
		if (activeGame == null) {
			String gameName = channel.getName();
			gameName = gameName.replace(ACInfo_Legacy.CARDS_INFO, "");
			gameName = gameName.substring(0, gameName.indexOf("-"));
			activeGame = GameManager.getInstance().getGame(gameName);
			if (activeGame == null) {
				return isFoWPrivate;
			}
		}
		if (activeGame.isFoWMode() && channel_ != null || event != null) {
			isFoWPrivate = channel.getName().endsWith(Constants.PRIVATE_CHANNEL);
		}
		return isFoWPrivate;
	}

	/** Return a list of tile positions that are adjacent to a source position.
	 *  Includes custom adjacent tiles defined on the map level, hyperlanes, and
	 *  wormholes
	 */
	public static Set<String> getAdjacentTiles(Game activeGame, String position, Player player, boolean toShow) {
		Set<String> adjacentPositions = traverseAdjacencies(activeGame, false, position);
		
		List<String> adjacentCustomTiles = activeGame.getCustomAdjacentTiles().get(position);
		
		List<String> adjacentCustomTiles2 = new ArrayList<>();
		if (adjacentCustomTiles != null) {
			if (!toShow) {
				for (String t : adjacentCustomTiles) {
					if (activeGame.getCustomAdjacentTiles().get(t) != null && activeGame.getCustomAdjacentTiles().get(t).contains(position)) {
						adjacentCustomTiles2.add(t);
					}
				}
				adjacentPositions.addAll(adjacentCustomTiles2);
			} else {
				adjacentPositions.addAll(adjacentCustomTiles);
			}
		}
		if (!toShow) {
			for (String primaryTile : activeGame.getCustomAdjacentTiles().keySet()) {
				System.out.println("Primary tile" + primaryTile);
				System.out.println("Position" + position);
				if (activeGame.getCustomAdjacentTiles().get(primaryTile).contains(position)) {
					adjacentPositions.add(primaryTile);
				}
			}
		}
		
		Set<String> wormholeAdjacencies = getWormholeAdjacencies(activeGame, position, player);
		adjacentPositions.addAll(wormholeAdjacencies);

		return adjacentPositions;
	}

	/** Return a list of tile positions that are adjacent to a source position either directly or via hyperlanes
	 * <p>
	 * Does not traverse wormholes
	 */
	public static Set<String> traverseAdjacencies(Game activeGame, boolean naturalMapOnly, String position) {
		return traverseAdjacencies(activeGame, naturalMapOnly, position, -1, new HashSet<>(), null);
	}

	/** Return a list of tile positions that are adjacent to a source position either directly or via hyperlanes
	 * <p>
	 * Does not traverse wormholes
	 */
	private static Set<String> traverseAdjacencies(Game activeGame, boolean naturalMapOnly, String position, Integer sourceDirection, Set<String> exploredSet, String prevTile) {
		Set<String> tiles = new HashSet<>();
		if (exploredSet.contains(position + sourceDirection)) {
			// We already explored this tile from this direction!
			return tiles;
		}
		// mark the tile as explored
		exploredSet.add(position + sourceDirection);

		Tile currentTile = activeGame.getTileByPosition(position);
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

		List<String> directlyAdjacentTiles = PositionMapper.getAdjacentTilePositions(position);
		if (directlyAdjacentTiles == null || directlyAdjacentTiles.size() != 6) {
			// adjacency file for this tile is not filled in
			return tiles;
		}

		// for each adjacent tile...
		for (int i = 0; i < 6; i++) {
			String position_ = directlyAdjacentTiles.get(i);

			String override = activeGame.getAdjacentTileOverride(position, i);
			if (override != null) {
				if (naturalMapOnly) continue;
				position_ = override;
			}

			if ("x".equals(position_) || (hyperlaneData != null && !hyperlaneData.get(i))) {
				// the hyperlane doesn't exist & doesn't go that direction, skip.
				continue;
			}

			// explore that tile now!
			Set<String> newTiles = traverseAdjacencies(activeGame, naturalMapOnly, position_, (i + 3) % 6, exploredSet, position + sourceDirection);
			tiles.addAll(newTiles);
		}
		return tiles;
	}



	public static boolean doesTileHaveWHs(Game activeGame, String position, Player player) {
		Set<String> adjacentPositions = new HashSet<>();
		Set<Tile> allTiles = new HashSet<>(activeGame.getTileMap().values());
		Tile tile = activeGame.getTileByPosition(position);

		String ghostFlagship = null;
		for (Player p : activeGame.getPlayers().values()) {
			if (p.ownsUnit("ghost_flagship")) {
				ghostFlagship = Mapper.getUnitID("fs", p.getColor());
				break;
			}
		}

		boolean wh_recon = activeGame.getLaws().containsKey("wormhole_recon");
		boolean absol_recon = activeGame.getLaws().containsKey("absol_recon");

		Set<String> wormholeIDs = Mapper.getWormholes(tile.getTileID());
		if(wormholeIDs == null){
			wormholeIDs = new HashSet<>();
		}
		for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
			HashSet<String> tokenList = unitHolder.getTokenList();
			for (String token : tokenList) {
				for(WormholeModel.Wormhole wh : WormholeModel.Wormhole.values()) {
					if(token.contains(wh.getWhString())) {
						wormholeIDs.add(wh.getWhString());
						wormholeIDs.add(wh.toString());
					}
				}
				
			}
			if (ghostFlagship != null && unitHolder.getUnits().getOrDefault(ghostFlagship, 0) > 0) {
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

		return !wormholeIDs.isEmpty();
	}



	public static boolean doesTileHaveAlphaOrBeta(Game activeGame, String position, Player player) {
		Set<String> adjacentPositions = new HashSet<>();
		Set<Tile> allTiles = new HashSet<>(activeGame.getTileMap().values());
		Tile tile = activeGame.getTileByPosition(position);

		String ghostFlagship = null;
		for (Player p : activeGame.getPlayers().values()) {
			if (p.ownsUnit("ghost_flagship")) {
				ghostFlagship = Mapper.getUnitID("fs", p.getColor());
				break;
			}
		}

		boolean wh_recon = activeGame.getLaws().containsKey("wormhole_recon");
		boolean absol_recon = activeGame.getLaws().containsKey("absol_recon");

		Set<String> wormholeIDs = Mapper.getWormholes(tile.getTileID());
		if(wormholeIDs == null){
			wormholeIDs = new HashSet<>();
		}
		for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
			HashSet<String> tokenList = unitHolder.getTokenList();
			for (String token : tokenList) {
				String tokenName = "wh" + token.replace("token_", "").replace(".png", "").replace("creuss", "");
				if(!tokenName.contains("champion")){
					tokenName=tokenName.replace("ion", "");
				}
				for(WormholeModel.Wormhole wh : WormholeModel.Wormhole.values())
					if(tokenName.contains(wh.getWhString())) {
						wormholeIDs.add(wh.getWhString());
						if(!wh.toString().contains("eta") || wh.toString().contains("beta")){
							wormholeIDs.add(wh.toString());
						}
						break;
					}
				/*if (token.contains(Constants.ALPHA)) {
					wormholeIDs.add(Constants.ALPHA);
				} else if (token.contains(Constants.BETA)) {
					wormholeIDs.add(Constants.BETA);
				} else if (token.contains(Constants.GAMMA)) {
					wormholeIDs.add(Constants.GAMMA);
				} else if (token.contains(Constants.DELTA)) {
					wormholeIDs.add(Constants.DELTA);
				} else if (token.contains(Constants.EPSILON)) {
					wormholeIDs.add(Constants.EPSILON);
				} else if (token.contains(Constants.VOYAGE)) {
					wormholeIDs.add(Constants.VOYAGE);
				} else if (token.contains(Constants.CHAMPION)) {
					wormholeIDs.add(Constants.CHAMPION);
				} else if (token.contains(Constants.NARROWS)) {
					wormholeIDs.add(Constants.NARROWS);
				} else if (token.contains(Constants.ZETA)) {
					wormholeIDs.add(Constants.ZETA);
				} else if (token.contains(Constants.ETA)) {
					wormholeIDs.add(Constants.ETA);
				} else if (token.contains(Constants.CUSTOM_ERONOUS_WHEPSILON)) {
					wormholeIDs.add(Constants.CUSTOM_ERONOUS_WHEPSILON);
				} else if (token.contains(Constants.CUSTOM_ERONOUS_WHIOTA)) {
					wormholeIDs.add(Constants.CUSTOM_ERONOUS_WHIOTA);
				} else if (token.contains(Constants.CUSTOM_ERONOUS_WHTHETA)) {
					wormholeIDs.add(Constants.CUSTOM_ERONOUS_WHTHETA);
				} else if (token.contains(Constants.CUSTOM_ERONOUS_WHZETA)) {
					wormholeIDs.add(Constants.CUSTOM_ERONOUS_WHZETA);
				} else if (token.contains(Constants.CUSTOM_ERONOUS_WHETA)) {
					wormholeIDs.add(Constants.CUSTOM_ERONOUS_WHETA);
				}*/
			}
			if (ghostFlagship != null && unitHolder.getUnits().getOrDefault(ghostFlagship, 0) > 0) {
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

		return (wormholeIDs.contains(Constants.ALPHA) || wormholeIDs.contains(Constants.BETA));
	}

	/** Check the map for other tiles that have wormholes connecting to the source system.
	 *  <p>
	 *  Also takes into account player abilities and agendas
	 */
	private static Set<String> getWormholeAdjacencies(Game activeGame, String position, Player player) {
		Set<String> adjacentPositions = new HashSet<>();
		Set<Tile> allTiles = new HashSet<>(activeGame.getTileMap().values());
		Tile tile = activeGame.getTileByPosition(position);

		String ghostFlagship = null;
		for (Player p : activeGame.getPlayers().values()) {
			if (p.ownsUnit("ghost_flagship")) {
				ghostFlagship = Mapper.getUnitID("fs", p.getColor());
				break;
			}
		}

		boolean wh_recon = activeGame.getLaws().containsKey("wormhole_recon");
		boolean absol_recon = activeGame.getLaws().containsKey("absol_recon");

		Set<String> wormholeIDs = Mapper.getWormholes(tile.getTileID());
		if(wormholeIDs == null){
			wormholeIDs = new HashSet<>();
		}
		for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
			HashSet<String> tokenList = unitHolder.getTokenList();
			for (String token : tokenList) {
				String tokenName = "wh" + token.replace("token_", "").replace(".png", "").replace("creuss", "");
				if(!tokenName.contains("champion")){
					tokenName=tokenName.replace("ion", "");
				}
				for(WormholeModel.Wormhole wh : WormholeModel.Wormhole.values())
					if(tokenName.contains(wh.getWhString())) {
						wormholeIDs.add(wh.getWhString());
						if(!wh.toString().contains("eta") || wh.toString().contains("beta")){
							wormholeIDs.add(wh.toString());
						}
						break;
					}
				/*if (token.contains(Constants.ALPHA)) {
					wormholeIDs.add(Constants.ALPHA);
				} else if (token.contains(Constants.BETA)) {
					wormholeIDs.add(Constants.BETA);
				} else if (token.contains(Constants.GAMMA)) {
					wormholeIDs.add(Constants.GAMMA);
				} else if (token.contains(Constants.DELTA)) {
					wormholeIDs.add(Constants.DELTA);
				} else if (token.contains(Constants.EPSILON)) {
					wormholeIDs.add(Constants.EPSILON);
				} else if (token.contains(Constants.VOYAGE)) {
					wormholeIDs.add(Constants.VOYAGE);
				} else if (token.contains(Constants.CHAMPION)) {
					wormholeIDs.add(Constants.CHAMPION);
				} else if (token.contains(Constants.NARROWS)) {
					wormholeIDs.add(Constants.NARROWS);
				} else if (token.contains(Constants.ZETA)) {
					wormholeIDs.add(Constants.ZETA);
				} else if (token.contains(Constants.ETA)) {
					wormholeIDs.add(Constants.ETA);
				} else if (token.contains(Constants.CUSTOM_ERONOUS_WHEPSILON)) {
					wormholeIDs.add(Constants.CUSTOM_ERONOUS_WHEPSILON);
				} else if (token.contains(Constants.CUSTOM_ERONOUS_WHIOTA)) {
					wormholeIDs.add(Constants.CUSTOM_ERONOUS_WHIOTA);
				} else if (token.contains(Constants.CUSTOM_ERONOUS_WHTHETA)) {
					wormholeIDs.add(Constants.CUSTOM_ERONOUS_WHTHETA);
				} else if (token.contains(Constants.CUSTOM_ERONOUS_WHZETA)) {
					wormholeIDs.add(Constants.CUSTOM_ERONOUS_WHZETA);
				} else if (token.contains(Constants.CUSTOM_ERONOUS_WHETA)) {
					wormholeIDs.add(Constants.CUSTOM_ERONOUS_WHETA);
				}*/
			}
			if (ghostFlagship != null && unitHolder.getUnits().getOrDefault(ghostFlagship, 0) > 0) {
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
	public static List<Player> getAdjacentPlayers(Game activeGame, String position, boolean includeSweep) {
		List<Player> players = new ArrayList<>();
		Set<String> tilesToCheck = getAdjacentTiles(activeGame, position, null, false);
		Tile startingTile = activeGame.getTileByPosition(position);

		for (Player player_ : activeGame.getPlayers().values()) {
			Set<String> tiles = new HashSet<>(tilesToCheck);
			if ("ghost".equals(player_.getFaction())) {
				tiles.addAll(getWormholeAdjacencies(activeGame, position, player_));
			}

			if (includeSweep && startingTile.hasCC(Mapper.getSweepID(player_.getColor()))) {
				players.add(player_);
				continue;
			}

			for (String position_ : tiles) {
				Tile tile = activeGame.getTileByPosition(position_);
				if(tile != null)
				{
					if (playerIsInSystem(activeGame, tile, player_)) {
						players.add(player_);
						break;
					}
				}
				
			}
		}

		return players;
	}

	/** Check if the specified player should have vision on the system */
	public static boolean playerIsInSystem(Game activeGame, Tile tile, Player player) {
		Set<String> unitHolderNames = tile.getUnitHolders().keySet();
		List<String> playerPlanets = player.getPlanets();
		if (playerPlanets.stream().anyMatch(unitHolderNames::contains)) {
			return true;
		} else if ("18".equals(tile.getTileID()) && player.hasTech("iihq")) {
			return true;
		} else if ("s11".equals(tile.getTileID()) && canSeeStatsOfFaction(activeGame, "cabal", player)) {
			return true;
		} else if ("s12".equals(tile.getTileID()) && canSeeStatsOfFaction(activeGame, "nekro", player)) {
			return true;
		} else if ("s13".equals(tile.getTileID()) && canSeeStatsOfFaction(activeGame, "yssaril", player)) {
			return true;
		}

		return playerHasUnitsInSystem(player, tile);
	}

	/** Check if the player has units in the system */
	public static boolean playerHasUnitsInSystem(Player player, Tile tile) {
		String colorID = Mapper.getColorID(player.getColor());
		if (colorID == null) return false; // player doesn't have a color

		Map<String, Integer> units = new HashMap<>();
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

		UnitHolder unitHolder =tile.getUnitHolders().get(Constants.SPACE);
		Map<String, Integer> units = new HashMap<>(unitHolder.getUnits());
		
		for (String key : units.keySet()) {
			if (key != null) {
				if (key.startsWith(colorID)) {
					return true;
				}
			}
		}
		return false;
	}
	public static boolean playerHasFightersInSystem(Player player, Tile tile) {
		String colorID = Mapper.getColorID(player.getColor());
		if (colorID == null) return false; // player doesn't have a color

		UnitHolder unitHolder =tile.getUnitHolders().get(Constants.SPACE);
		Map<String, Integer> units = new HashMap<>(unitHolder.getUnits());
		
		for (String key : units.keySet()) {
			if (key != null) {
				if (key.startsWith(colorID) && key.contains("ff")) {
					return true;
				}
			}
		}
		return false;
	}
	public static boolean playerHasUnitsOnPlanet(Player player, Tile tile, String planet) {
		String colorID = Mapper.getColorID(player.getColor());
		if (colorID == null) return false; // player doesn't have a color

		UnitHolder unitHolder =tile.getUnitHolders().get(planet);
		Map<String, Integer> units = new HashMap<>(unitHolder.getUnits());
		
		for (String key : units.keySet()) {
			if (key != null) {
				if (key.startsWith(colorID)) {
					return true;
				}
			}
		}
		return false;
	}
	public static boolean playerHasInfantryOnPlanet(Player player, Tile tile, String planet) {
		String colorID = Mapper.getColorID(player.getColor());
		if (colorID == null) return false; // player doesn't have a color

		UnitHolder unitHolder =tile.getUnitHolders().get(planet);
		Map<String, Integer> units = new HashMap<>(unitHolder.getUnits());
		
		for (String key : units.keySet()) {
			if (key != null) {
				if (key.startsWith(colorID) && key.contains("gf")) {
					return true;
				}
			}
		}
		return false;
	}

	/** Ping the players adjacent to a given system */
	public static void pingSystem(Game activeGame, GenericInteractionCreateEvent event, String position, String message) {
		if(activeGame.getTileByPosition(position) == null){
			return;
		}
		// get players adjacent
		List<Player> players = getAdjacentPlayers(activeGame, position, true);
		int successfulCount = 0;
		for (Player player_ : players) {
			String playerMessage = Helper.getPlayerRepresentation(player_, activeGame) + " - System " + position + " has been pinged:\n>>> " + message;
			boolean success = MessageHelper.sendPrivateMessageToPlayer(player_, activeGame, playerMessage);
			MessageChannel channel = player_.getPrivateChannel();
			MessageHelper.sendMessageToChannelWithButtons(channel, "Use Button to refresh view of system", ButtonHelper.getButtonsForPictureCombats(activeGame, position, player_, player_, "justPicture"));
			successfulCount += success ? 1 : 0;
		}
		feedbackMessage(event, successfulCount, players.size());
	}

	/** This will ping all players */
	public static void pingAllPlayers(Game activeGame, GenericInteractionCreateEvent event, String message) {
		int succesfulCount = 0;

		for (Player player_ : activeGame.getPlayers().values()) {
			String playerMessage = Helper.getPlayerRepresentation(player_, activeGame) + " all player ping\n>>> " + message;
			boolean success = MessageHelper.sendPrivateMessageToPlayer(player_, activeGame, playerMessage);
			succesfulCount += success ? 1 : 0;
		}
		feedbackMessage(event, succesfulCount, activeGame.getPlayers().size());
	}

	public static void pingAllPlayersWithFullStats(Game activeGame, GenericInteractionCreateEvent event, Player playerWithChange, String message) {
		var playersToPing = activeGame.getPlayers().values().stream()
				.filter(viewer -> initializeAndCheckStatVisibility(activeGame, playerWithChange, viewer))
				.collect(Collectors.toSet());
		int succesfulCount = 0;

		String playerMessage = Helper.getPlayerRepresentation(playerWithChange, activeGame) + " stats changed:\n" + message;
		for (Player player_ : playersToPing) {
			boolean success = MessageHelper.sendPrivateMessageToPlayer(player_, activeGame, playerMessage);
			succesfulCount += success ? 1 : 0;
		}
		feedbackMessage(event, succesfulCount, playersToPing.size());
	}

	public static void pingPlayersDifferentMessages(
		Game activeGame,
		GenericInteractionCreateEvent event,
		Player playerWithChange,
		String messageForFullInfo,
		String messageForAll
	) {
		Set<Player> playersWithVisiblity = activeGame.getPlayers().values().stream()
				.filter(viewer -> initializeAndCheckStatVisibility(activeGame, playerWithChange, viewer))
				.collect(Collectors.toSet());
		Set<Player> playersWithoutVisiblity = activeGame.getPlayers().values().stream()
				.filter(player -> !playersWithVisiblity.contains(player) && player != playerWithChange)
				.collect(Collectors.toSet());
		int succesfulCount = 0;
		int totalPings = playersWithVisiblity.size() + playersWithoutVisiblity.size();

		for (Player player_ : playersWithVisiblity) {
			boolean success = MessageHelper.sendPrivateMessageToPlayer(player_, activeGame, messageForFullInfo);
			succesfulCount += success ? 1 : 0;
		}
		for (Player player_ : playersWithoutVisiblity) {
			if (!player_.isRealPlayer()) continue;
			boolean success = MessageHelper.sendPrivateMessageToPlayer(player_, activeGame, messageForAll);
			succesfulCount += success ? 1 : 0;
		}
		feedbackMessage(event, succesfulCount, totalPings);
	}

	public static void pingPlayersTransaction(
		Game activeGame,
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
		for (Player player_ : activeGame.getPlayers().values()) {
			if ("null".equals(player_.getColor())) continue;
			if (player_ == sendingPlayer || player_ == receivingPlayer) continue;
			attemptCount++;

			// let's figure out what they can see!
			initializeFog(activeGame, player_, false);
			boolean senderVisible = FoWHelper.canSeeStatsOfPlayer(activeGame, sendingPlayer, player_);
			boolean receiverVisible = FoWHelper.canSeeStatsOfPlayer(activeGame, receivingPlayer, player_);

			StringBuilder sb = new StringBuilder();
			// first off let's give full info for someone that can see both sides
			if (senderVisible) {
				sb.append(Helper.getPlayerRepresentation(sendingPlayer, activeGame));
			} else {
				sb.append("???");
			}
			sb.append(" sent ").append(transactedObject).append(" to ");
			if (receiverVisible) {
				sb.append(Helper.getPlayerRepresentation(receivingPlayer, activeGame));
			} else {
				sb.append("???");
			}

			String message = sb.toString();
			if (!senderVisible && !receiverVisible) {
				message = noVisibilityMessage;
			}
			boolean success = MessageHelper.sendPrivateMessageToPlayer(player_, activeGame, message);
			successCount += success ? 1 : 0;
		}
		feedbackMessage(event, successCount, attemptCount);
	}

	private static void feedbackMessage(GenericInteractionCreateEvent event, int success, int total) {
		if (success < total) {
			MessageHelper.replyToMessage(event, "One more more pings failed to send.  Please follow up with game's GM.");
		} else {
			MessageHelper.replyToMessage(event, "Successfully sent all pings.");
		}
	}

	private static boolean initializeAndCheckStatVisibility(Game activeGame, Player player, Player viewer) {
		if (viewer == player) return false;
		if ("null".equals(viewer.getColor())) return false;
		initializeFog(activeGame, viewer, false);
		return FoWHelper.canSeeStatsOfPlayer(activeGame, player, viewer);
	}
}
