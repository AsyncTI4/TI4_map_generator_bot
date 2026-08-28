package ti4.helpers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;
import software.amazon.awssdk.utils.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Oblivion.OblivionUnitHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.game.persistence.GameManager;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.BorderAnomalyHolder;
import ti4.model.PromissoryNoteModel;
import ti4.model.WormholeModel;
import ti4.service.combat.StartCombatService;
import ti4.service.fow.FOWPlusService;
import ti4.service.game.GameNameService;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.service.unit.CheckUnitContainmentService;

public final class FoWHelper {
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

    public static boolean isPrivateGame(
            Game game, @Nullable GenericInteractionCreateEvent event, @Nullable Channel channel2) {
        Channel eventChannel = event == null ? null : event.getChannel();
        Channel channel = channel2 != null ? channel2 : eventChannel;
        if (channel == null) {
            return game.isFowMode();
        }
        if (channel instanceof ThreadChannel) {
            channel = ((ThreadChannel) channel).getParentChannel();
        }

        if (game == null) {
            String gameName = GameNameService.getGameNameFromChannel(channel);
            if (!GameManager.isValid(gameName)) {
                return false;
            }
            game = GameManager.getManagedGame(gameName).getGame();
        }
        if (game.isFowMode() && channel2 != null || event != null) {
            return channel.getName().endsWith(Constants.PRIVATE_CHANNEL);
        }
        return false;
    }

    /**
     * Shows who did something in a normal game, but hides it under fog. Returns the player's plain
     * (non-pinging) name when the game isn't fogged, or {@code fogPhrase} when it is — you pass the
     * placeholder to show instead: {@code "someone"}, {@code "another player"}, or {@code ""} to drop
     * the actor entirely. Replaces the hand-written
     * {@code game.isFowMode() ? "someone" : player.getRepresentationNoPing()} so the hide-the-actor
     * rule lives in one place.
     *
     * <p>Only use this where the visible (non-fog) text is the player's normal representation. It does
     * not fit sites that show something else when unfogged — a Discord username (e.g.
     * {@code ActionCardHelper.showAll}) or a bare faction emoji (the explore-discovery lines); handle
     * those directly.
     */
    public static String actorOrAnon(Game game, Player player, String fogPhrase) {
        return game.isFowMode() ? fogPhrase : player.getRepresentationNoPing();
    }

    /**
     * Like {@link #actorOrAnon}, but the visible (non-fog) form is the player's compact faction
     * emoji/color ({@code getFactionEmojiOrColor()}) instead of their full name. Under fog it returns
     * {@code fogPhrase} — usually a generic word, or the player's raw color for sites that
     * deliberately reveal color in fog. Replaces
     * {@code game.isFowMode() ? "<phrase>" : player.getFactionEmojiOrColor()}.
     */
    public static String factionEmojiOrAnon(Game game, Player player, String fogPhrase) {
        return game.isFowMode() ? fogPhrase : player.getFactionEmojiOrColor();
    }

    /**
     * Hides identity per-viewer — finer-grained than the game-wide fog flag. Under fog it returns what
     * {@code viewer} is actually allowed to see about {@code target}: the target's color, or
     * {@code "???"} if the viewer can't see the target's stats (this respects alliances and promissory
     * notes, via {@code target.getColorIfCanSeeStats(viewer)}). When not fogged it returns
     * {@code unfoggedRendering}, which you supply because it varies by site (full representation,
     * faction name, etc.).
     *
     * <p>Use this instead of a plain {@code isFowMode()} check when a message should reveal different
     * things to different players.
     */
    public static String identityOrColorIfCanSeeStats(
            Game game, Player target, Player viewer, String unfoggedRendering) {
        return game.isFowMode() ? target.getColorIfCanSeeStats(viewer) : unfoggedRendering;
    }

    /**
     * Picks where a public "X happened" announcement should go. In a normal game, if the player isn't
     * already acting in the shared Actions channel, returns that Actions channel so everyone sees it.
     * Under fog (or when they're already in the Actions channel) returns the channel the interaction
     * came from, keeping the message local. Send one message to whatever this returns.
     *
     * <p>If the public and local versions need different wording, use {@link #announcePublicOrLocal}
     * instead. Extracted from the repeated
     * {@code !game.isFowMode() && event.getChannel() != game.getActionsChannel()} shape in
     * {@code ExploreService}.
     */
    public static MessageChannel actionsChannelOrLocal(Game game, GenericInteractionCreateEvent event) {
        return shouldAnnouncePublicly(game, event) ? game.getActionsChannel() : event.getMessageChannel();
    }

    /**
     * The shared decision behind {@link #actionsChannelOrLocal} and {@link #announcePublicOrLocal}:
     * announce in the public Actions channel only in a non-fog game where the interaction didn't
     * already happen there. Always false under fog — the player pressed the button from their own
     * private channel, so the announcement stays with them.
     */
    private static boolean shouldAnnouncePublicly(Game game, GenericInteractionCreateEvent event) {
        return !game.isFowMode() && event.getChannel() != game.getActionsChannel();
    }

    /**
     * Sends an announcement to the right place with the right wording. In a non-fog game not already
     * in the Actions channel, posts {@code publicMessage} to the public Actions channel; otherwise
     * posts {@code localMessage} to the interaction's own channel.
     *
     * <p>Use this when the public and local versions read differently — e.g. the public one is
     * prefixed with the finder's faction emoji and the local one is a plain sentence. When both
     * messages are identical, use {@link #actionsChannelOrLocal} with a single send instead.
     */
    public static void announcePublicOrLocal(
            Game game, GenericInteractionCreateEvent event, String publicMessage, String localMessage) {
        if (shouldAnnouncePublicly(game, event)) {
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), publicMessage);
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), localMessage);
        }
    }

    /**
     * Builds a "pick a player" button whose label and icon never reveal who the target is under fog.
     * Normal game: the target's faction short-name plus faction emoji. Fog game: the capitalized color
     * name (e.g. "Red", via {@link Player#getFactionNameOrColor()}) plus a neutral color-chip icon —
     * color only, never the faction. {@code style} sets the button color: "gray" (default), "green",
     * "red", or "blue".
     *
     * <p>This replaces several hand-rolled button blocks that had drifted apart (some showed a raw
     * lowercase color, some varied the non-fog label). The icon comes from
     * {@link Player#fogSafeEmoji()} — faction emoji when clear, color chip when fogged — matching the
     * Twilight's Fall action-card buttons.
     */
    public static Button fogSafeTargetButton(String buttonId, String style, Player target) {
        boolean fogged = target.getGame().isFowMode();
        String label = fogged
                ? target.getFactionNameOrColor()
                : target.getFactionModel().getShortName();
        return styledButton(style, buttonId, label, target.fogSafeEmoji());
    }

    /**
     * Per-viewer version of {@link #fogSafeTargetButton(String, String, Player)}, for buttons that
     * must respect what one specific {@code viewer} is allowed to see (e.g. Psionic Hammer) rather
     * than just the game-wide fog flag. Under fog the label is
     * {@code target.getColorIfCanSeeStats(viewer)} — the target's color, or {@code "???"} if this
     * viewer isn't allowed to see them — with no icon, so it can't leak color to someone who shouldn't
     * see it. In a normal game it's the faction short-name plus faction emoji, as usual.
     */
    public static Button fogSafeTargetButton(String buttonId, String style, Player target, Player viewer) {
        boolean fogged = target.getGame().isFowMode();
        String label = fogged
                ? target.getColorIfCanSeeStats(viewer)
                : target.getFactionModel().getShortName();
        String emoji = fogged ? null : target.getFactionEmoji();
        return styledButton(style, buttonId, label, emoji);
    }

    /** Dispatch a {@link Buttons} factory by {@code style} ("gray" default, "green", "red", "blue"). */
    private static Button styledButton(String style, String buttonId, String label, String emoji) {
        if ("green".equals(style)) return Buttons.green(buttonId, label, emoji);
        if ("red".equals(style)) return Buttons.red(buttonId, label, emoji);
        if ("blue".equals(style)) return Buttons.blue(buttonId, label, emoji);
        return Buttons.gray(buttonId, label, emoji);
    }

    /**
     * Tells both the acting player and an affected player about something, with fog handled. Always
     * sends {@code message} to {@code primary}'s channel. Under fog — where each player is in their
     * own private channel — it also sends the same message to {@code affected}'s channel (unless
     * {@code affected} is {@code primary}) so they aren't left out. In a normal game that second send
     * is skipped, matching the original {@code send(primary…); if (fowMode && affected != primary)
     * send(affected…);} pattern.
     */
    public static void notifyPlayerAndAffectedInFog(Game game, Player primary, Player affected, String message) {
        notifyPlayerAndAffectedInFog(game, primary, message, affected, message);
    }

    /**
     * Same as {@link #notifyPlayerAndAffectedInFog(Game, Player, Player, String)}, but lets the
     * affected player get a different (usually anonymized) message than the acting player.
     */
    public static void notifyPlayerAndAffectedInFog(
            Game game, Player primary, String primaryMessage, Player affected, String affectedMessage) {
        MessageHelper.sendMessageToChannel(primary.getCorrectChannel(), primaryMessage);
        if (game.isFowMode() && affected != primary) {
            MessageHelper.sendMessageToChannel(affected.getCorrectChannel(), affectedMessage);
        }
    }

    /**
     * Announces a two-player interaction, with fog handled. Under fog, sends a private "you did X"
     * message to the {@code actor} and a private "X happened to you" message to the {@code affected}
     * player. In a normal game, sends a single third-person {@code publicMessage} to the actor's
     * channel. Replaces
     * {@code if (fowMode) { send(actor, …); send(affected, …); } else { send(actor, publicMsg); }}.
     *
     * <p><b>Only use this when the original non-fog message went to the actor's channel.</b> The
     * non-fog send goes to {@code actor.getCorrectChannel()}, which is not always the public main
     * channel — in a normal game where players have private channels,
     * {@link Player#getCorrectChannel()} returns the actor's private channel. If the non-fog message
     * instead needs to reach the {@code affected} player or the public main channel, keep explicit
     * routing — this helper would send it to the wrong place.
     */
    public static void notifyActorAndAffectedElsePublic(
            Game game,
            Player actor,
            String actorFogMessage,
            Player affected,
            String affectedFogMessage,
            String publicMessage) {
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(actor.getCorrectChannel(), actorFogMessage);
            MessageHelper.sendMessageToChannel(affected.getCorrectChannel(), affectedFogMessage);
        } else {
            MessageHelper.sendMessageToChannel(actor.getCorrectChannel(), publicMessage);
        }
    }

    public static boolean canSeeStatsOfFaction(Game game, String faction, Player viewingPlayer) {
        for (Player player : game.getRealPlayers()) {
            if (faction.equalsIgnoreCase(player.getFaction())) {
                return canSeeStatsOfPlayer(game, player, viewingPlayer);
            }
        }
        return false;
    }

    public static boolean canSeeStatsOfPlayer(Game game, Player player, Player viewingPlayer) {
        if (game == null || !player.isRealPlayer() || !viewingPlayer.isRealPlayer()) {
            return false;
        }
        if (player == viewingPlayer) {
            return true;
        }
        if (viewingPlayer.getAllianceMembers().contains(player.getFaction())) {
            return true;
        }
        if (!FOWPlusService.isActive(game)
                && !game.getFowOption(FOWOption.STATS_FROM_HS_ONLY)
                && (hasPlayersPromInPlayArea(game, player, viewingPlayer)
                        || hasMahactCCInFleet(game, player, viewingPlayer))) {
            return true;
        }
        initializeFog(game, viewingPlayer, false);
        return hasHomeSystemInView(player, viewingPlayer);
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
        if (player.isFogInitialized() && !forceRecalculate) {
            return;
        }

        player.setFogInitialized(true);

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

    /**
     * Whether this tile has ever been revealed to the player, per their persisted fog memory.
     * <p>
     * Deliberately keys on position only. It does <b>not</b> compare the remembered tileID against the tile
     * currently at that position, because tiles get rewritten in place (FlipTileService turns 82a into 82b and
     * similar), which would turn a legitimately remembered system into a false negative. This matches the
     * existing precedent in {@link Tile#hasFog(Player)} for Light Fog mode.
     */
    public static boolean hasEverSeenTile(@NotNull Player player, String position) {
        return position != null && player.getFogTiles().containsKey(position);
    }

    /**
     * Positions the player can see right now, unioned with every position they have ever seen. Outside fog
     * everything is known, so this returns the whole map.
     */
    public static Set<String> getKnownTilePositions(Game game, @NotNull Player player) {
        if (!game.isFowMode()) {
            return new HashSet<>(game.getTileMap().keySet());
        }
        Set<String> known = new HashSet<>(getTilePositionsToShow(game, player));
        known.addAll(player.getFogTiles().keySet());
        return known;
    }

    /** Whether the player could know that the system at this position exists. */
    public static boolean knowsTile(Game game, @NotNull Player player, String position) {
        if (!game.isFowMode()) return true;
        return hasEverSeenTile(player, position)
                || getTilePositionsToShow(game, player).contains(position);
    }

    /**
     * Whether the player could know that this planet exists: either they can see (or have seen) the system it
     * sits in, or they can see the stats of whoever controls it, which discloses that player's planets.
     * <p>
     * Callers building a whole list should prefer the batched path in {@code PlanetTargetService}, which
     * computes the visible-position set once instead of once per planet.
     */
    public static boolean knowsPlanetExists(Game game, @NotNull Player player, String planetId) {
        if (!game.isFowMode()) return true;
        Tile tile = game.getTileFromPlanet(planetId);
        if (tile == null) return false;
        if (knowsTile(game, player, tile.getPosition())) return true;
        Player owner = game.getPlayerThatControlsPlanet(planetId, true);
        return owner != null && canSeeStatsOfPlayer(game, owner, player);
    }

    public static void updateFog(Game game, Player player) {
        if (player != null) initializeFog(game, player, true);
    }

    private static void updatePlayerFogTiles(Game game, Player player) {
        for (Tile tileToUpdate : game.getTileMap().values()) {
            if (!tileToUpdate.isValid()) {
                BotLogger.warning(
                        String.format("Tile %s is not valid in game %s", tileToUpdate.getTileID(), game.getName()));
                continue;
            }
            if (!tileToUpdate.hasFog(player)
                    || tileToUpdate.isSupernova() && game.getFowOption(FOWOption.BRIGHT_NOVAS)) {
                player.updateFogTile(tileToUpdate, "Rnd " + game.getRound());
            }
        }
    }

    public static boolean hasHomeSystemInView(@NotNull Player player, @NotNull Player viewingPlayer) {
        Tile tile = player.getHomeSystemTile();
        return tile != null && !tile.hasFog(viewingPlayer);
    }

    private static boolean hasPlayersPromInPlayArea(
            @NotNull Game game, @NotNull Player player, @NotNull Player viewingPlayer) {
        for (String prom_ : viewingPlayer.getPromissoryNotesInPlayArea()) {
            if (game.getPNOwner(prom_) != player) {
                continue;
            }
            if (!game.getFowOption(revealGateFor(Mapper.getPromissoryNote(prom_)))) {
                return true;
            }
        }
        return false;
    }

    private static FOWOption revealGateFor(PromissoryNoteModel pn) {
        // Faction-specific homebrew replacements (e.g. Black Spectrum's per-faction Alliance/SftT
        // cards) keep the alias of the card they replace here, so classify by that when present.
        String classificationAlias = pn.getHomebrewReplacesID().orElse(pn.getAlias());
        if (classificationAlias.endsWith("_an")) return FOWOption.HIDE_STATS_VIA_ALLIANCE;
        if (classificationAlias.endsWith("_sftt")) return FOWOption.HIDE_STATS_VIA_SFTT;
        return FOWOption.HIDE_STATS_VIA_FACTION_PN;
    }

    private static boolean hasMahactCCInFleet(
            @NotNull Game game, @NotNull Player player, @NotNull Player viewingPlayer) {
        if (game.getFowOption(FOWOption.HIDE_STATS_VIA_MAHACT_CC)) {
            return false;
        }
        return viewingPlayer.getMahactCC().contains(player.getColor());
    }

    /**
     * Return a list of tile positions that are adjacent to a source position.
     * Includes custom adjacent tiles defined on the map level, hyperlanes, and
     * wormholes
     */
    public static Set<String> getAdjacentTiles(Game game, String position, Player player, boolean toShow) {
        return getAdjacentTiles(game, position, player, toShow, true);
    }

    public static Set<String> getAdjacentTiles(
            Game game, String position, Player player, boolean toShow, boolean includeTile) {
        return getAdjacentTiles(game, position, player, toShow, includeTile, false);
    }

    public static Set<String> getAdjacentTiles(
            Game game, String position, Player player, boolean toShow, boolean includeTile, boolean forDistance) {
        if (FOWPlusService.isVoid(game, position)) return new HashSet<>();

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

        Set<String> wormholeAdjacencies = getWormholeAdjacencies(game, position, player, false, forDistance);
        adjacentPositions.addAll(wormholeAdjacencies);

        Set<String> otherAdjacencies = getNonWormholeAdjacencies(game, position);
        adjacentPositions.addAll(otherAdjacencies);

        if (player != null
                && (game.playerHasLeaderUnlockedOrAlliance(player, "celdauricommander")
                        || player.hasTech("tf-starbasewebway"))
                && player == game.getActivePlayer()
                && forDistance
                && !game.getCurrentActiveSystem().isEmpty()
                && ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Spacedock)
                        .contains(game.getTileByPosition(position))) {

            for (Tile tile : ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Spacedock)) {
                if (tile.getPosition().equalsIgnoreCase(position)) {
                    continue;
                }
                adjacentPositions.add(tile.getPosition());
            }
        }

        // If player has ghoti commander, is active player and has activated a system
        if (player != null
                && game.playerHasLeaderUnlockedOrAlliance(player, "ghoticommander")
                && player == game.getActivePlayer()
                && !game.getCurrentActiveSystem().isEmpty()) {
            Set<Player> playersToCheck = new HashSet<>();
            playersToCheck.add(player);
            if (game.isAllianceMode()) {
                playersToCheck.addAll(game.getRealPlayers().stream()
                        .filter(alliancePlayer -> player.getAllianceMembers().contains(alliancePlayer.getFaction()))
                        .collect(Collectors.toSet()));
            }

            // Check that they or their alliance have units in any empty system to be able to see the other empties as
            // adjacencies
            Set<Tile> emptyTiles = getEmptyTiles(game);
            boolean containsUnits =
                    emptyTiles.stream().anyMatch(tile -> playersToCheck.stream().anyMatch(tile::containsPlayersUnits));
            if (containsUnits
                    && game.getTileByPosition(position).getPlanetUnitHolders().isEmpty()) {
                adjacentPositions.addAll(
                        emptyTiles.stream().map(Tile::getPosition).collect(Collectors.toSet()));
            }
        }

        OblivionUnitHandler.addObsidianMirrorAdjacencies(game, player, position, adjacentPositions);

        if (includeTile) {
            adjacentPositions.add(position);
        } else {
            adjacentPositions.remove(position);
        }
        return adjacentPositions;
    }

    private static Set<String> getNonWormholeAdjacencies(Game game, String position) {
        Set<String> adjacentPositions = new HashSet<>();
        Set<Tile> allTiles = new HashSet<>(game.getTileMap().values());
        Tile tile = game.getTileByPosition(position);
        if (tile == null) {
            return adjacentPositions;
        }

        Set<Feature> adjToFeatures = EnumSet.noneOf(Feature.class);
        if (tile.hasEgress()) adjToFeatures.add(Feature.ingress);

        if (tile.hasIngress()) adjToFeatures.add(Feature.egress);

        if (game.isCosmicPhenomenaeMode()) {
            if (tile.isScar(game)) {
                adjToFeatures.add(Feature.scar);
            }
        }

        if (game.getActivePlayer() != null
                && game.getActivePlayer().hasUnlockedBreakthrough("nivynbt")
                && tile.isScar(game)) {
            adjToFeatures.add(Feature.egress);
        }

        if (game.getActivePlayer() != null
                && game.getActivePlayer().hasTech("tf-fraactalspikedrives")
                && !tile.getWormholes(game).isEmpty()) {
            adjToFeatures.add(Feature.egress);
        }

        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            for (String token : unitHolder.getTokenList()) {
                switch (token) {
                    case Constants.TOKEN_BREACH_ACTIVE -> adjToFeatures.add(Feature.breach);
                    case Constants.TOKEN_INGRESS -> adjToFeatures.add(Feature.egress);
                    case Constants.TOKEN_EGRESS -> adjToFeatures.add(Feature.ingress);
                }
            }
        }

        for (Tile t : allTiles) {
            if (adjToFeatures.contains(Feature.egress) && t.hasEgress()) {
                adjacentPositions.add(t.getPosition());
                continue;
            }
            if (adjToFeatures.contains(Feature.ingress) && t.hasIngress()) {
                adjacentPositions.add(t.getPosition());
                continue;
            }
            if (game.isCosmicPhenomenaeMode()) {
                if (adjToFeatures.contains(Feature.scar) && t.isScar(game)) {
                    adjacentPositions.add(t.getPosition());
                    continue;
                }
            }
            for (UnitHolder unitHolder : t.getUnitHolders().values()) {
                for (String token : unitHolder.getTokenList()) {
                    if (adjToFeatures.contains(Feature.breach) && Constants.TOKEN_BREACH_ACTIVE.equals(token)) {
                        adjacentPositions.add(t.getPosition());
                        break;
                    }
                    if (adjToFeatures.contains(Feature.ingress) && Constants.TOKEN_INGRESS.equals(token)) {
                        adjacentPositions.add(t.getPosition());
                        break;
                    }
                    if (adjToFeatures.contains(Feature.egress) && Constants.TOKEN_EGRESS.equals(token)) {
                        adjacentPositions.add(t.getPosition());
                        break;
                    }
                }
            }
        }
        return adjacentPositions;
    }

    public static Set<Tile> getEmptyTiles(Game game) {
        Set<Tile> emptyTiles = new HashSet<>();
        Collection<Tile> tileList = game.getTileMap().values();
        List<String> frontierTileList = Mapper.getFrontierTileIds();
        for (Tile tile : tileList) {
            if (tile.getPlanetUnitHolders().isEmpty()
                    && (tile.getUnitHolders().size() == 2 || frontierTileList.contains(tile.getTileID()))) {
                emptyTiles.add(tile);
            }
        }
        return emptyTiles;
    }

    public static Set<String> getAdjacentTilesAndNotThisTile(
            Game game, String position, Player player, boolean toShow) {
        return getAdjacentTiles(game, position, player, toShow, false);
    }

    /**
     * Return a list of tile positions that are adjacent to a source position either
     * directly or via hyperlanes
     * <p>
     * Does not traverse wormholes
     */
    public static Set<String> traverseAdjacencies(Game game, boolean naturalMapOnly, String position) {
        return traverseAdjacencies(game, naturalMapOnly, position, -1, new HashSet<>());
    }

    /**
     * Return a list of tile positions that are adjacent to a source position either
     * directly or via hyperlanes
     * <p>
     * Does not traverse wormholes
     */
    private static Set<String> traverseAdjacencies(
            Game game, boolean naturalMapOnly, String position, Integer sourceDirection, Set<String> exploredSet) {
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

        List<Boolean> hyperlaneData = currentTile.getHyperlaneData(sourceDirection, game);
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
                if (b.getTile().equals(position) && b.getDirection() == i && b.blocksAdjacencyOut()
                        || b.getTile().equals(position_) && b.getDirection() == dirFrom && b.blocksAdjacencyIn()) {
                    borderBlocked = true;
                }
            }
            if (borderBlocked && !naturalMapOnly) continue;

            String override = game.getAdjacentTileOverride(position, i);
            if (override != null) {
                if (naturalMapOnly) continue;
                position_ = override;
            }

            if ("x".equals(position_) || (hyperlaneData != null && !hyperlaneData.isEmpty() && !hyperlaneData.get(i))) {
                // the hyperlane doesn't exist & doesn't go that direction, skip.
                continue;
            }

            if (!FOWPlusService.shouldTraverseAdjacency(game, position_, dirFrom)) {
                continue;
            }

            // explore that tile now!
            int direcetionFrom = naturalMapOnly ? -2 : dirFrom;
            Set<String> newTiles = traverseAdjacencies(game, naturalMapOnly, position_, direcetionFrom, exploredSet);
            tiles.addAll(newTiles);
        }
        return tiles;
    }

    public static boolean isTileInExileRange(Game game, Tile tile, Player player) {
        if (player.hasUnit("crimson_destroyer")) {
            List<Tile> destroyers = ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Destroyer);
            for (Tile tile2 : destroyers) {
                if (getAdjacentTiles(game, tile.getPosition(), player, false, true)
                        .contains(tile2.getPosition())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isTileInUpgradedExileRange(Game game, Tile tile, Player player) {
        if (player.hasUnit("crimson_destroyer2")) {
            List<Tile> destroyers = ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Destroyer);
            for (String adjPos : getAdjacentTiles(game, tile.getPosition(), player, false, true)) {
                for (Tile tile2 : destroyers) {
                    if (getAdjacentTiles(game, adjPos, player, false, true).contains(tile2.getPosition())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isTileAdjacentToAnAnomaly(Game game, String position, Player player) {
        for (String adjPos : getAdjacentTilesAndNotThisTile(game, position, player, false)) {
            if (game.getTileByPosition(adjPos).isAnomaly(game, player)) {
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
            if (p.ownsUnit("ghost_flagship")
                    || p.ownsUnit("sigma_creuss_flagship_1")
                    || p.ownsUnit("sigma_creuss_flagship_2")) {
                ghostFlagshipColor = p.getColor();
                break;
            }
        }

        Set<String> wormholeIDs = Mapper.getWormholes(tile.getTileID());
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            Set<String> tokenList = unitHolder.getTokenList();
            for (String token : tokenList) {
                String tokenName =
                        "wh" + token.replace("token_", "").replace(".png", "").replace("creuss", "");
                if (!tokenName.contains("champion")) {
                    tokenName = tokenName.replace("ion", "");
                }
                for (WormholeModel.Wormhole wh : WormholeModel.Wormhole.values()) {
                    if (tokenName.contains(wh.getWhString())) {
                        // wormholeIDs.add(wh.getWhString());
                        wormholeIDs.add(wh.toString());
                        break;
                    }
                }
                if (tokenName.contains("sigma_weirdway")) {
                    wormholeIDs.add(Constants.ALPHA);
                    wormholeIDs.add(Constants.BETA);
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
                String tokenName =
                        "wh" + token.replace("token_", "").replace(".png", "").replace("creuss", "");
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
                if (tokenName.contains("sigma_weirdway")) {
                    wormholeIDs.add(Constants.ALPHA);
                    wormholeIDs.add(Constants.BETA);
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
                String tokenName =
                        "wh" + token.replace("token_", "").replace(".png", "").replace("creuss", "");
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
                if (tokenName.contains("sigma_weirdway")) {
                    wormholeIDs.add(Constants.ALPHA);
                    wormholeIDs.add(Constants.BETA);
                }
            }
        }

        return wormholeIDs.stream().anyMatch(id -> id.contains(Constants.BETA));
    }

    public static boolean doesTileHaveAlpha(Game game, String position) {
        Tile tile = game.getTileByPosition(position);

        Set<String> wormholeIDs = Mapper.getWormholes(tile.getTileID());
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            Set<String> tokenList = unitHolder.getTokenList();
            for (String token : tokenList) {
                String tokenName =
                        "wh" + token.replace("token_", "").replace(".png", "").replace("creuss", "");
                if (!tokenName.contains("champion")) {
                    tokenName = tokenName.replace("ion", "");
                }
                for (WormholeModel.Wormhole wh : WormholeModel.Wormhole.values()) {
                    if (tokenName.contains(wh.getWhString())) {
                        wormholeIDs.add(wh.getWhString());
                        break;
                    }
                }
                if (tokenName.contains("sigma_weirdway")) {
                    wormholeIDs.add(Constants.ALPHA);
                    wormholeIDs.add(Constants.BETA);
                }
            }
        }

        return wormholeIDs.stream().anyMatch(id -> id.contains(Constants.ALPHA));
    }

    /**
     * Check the map for other tiles that have wormholes connecting to the source
     * system.
     * <p>
     * Also takes into account player abilities and agendas
     */
    private static Set<String> getWormholeAdjacencies(Game game, String position, Player player, boolean neighbors) {
        return getWormholeAdjacencies(game, position, player, neighbors, false);
    }

    private static Set<String> getWormholeAdjacencies(
            Game game, String position, Player player, boolean neighbors, boolean forDistance) {
        Set<String> adjacentPositions = new HashSet<>();
        Set<Tile> allTiles = new HashSet<>(game.getTileMap().values());
        Tile tile = game.getTileByPosition(position);

        String ghostFlagshipColor = null;
        for (Player p : game.getPlayers().values()) {
            if (p.ownsUnit("ghost_flagship")
                    || p.ownsUnit("sigma_creuss_flagship_1")
                    || p.ownsUnit("sigma_creuss_flagship_2")) {
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
                String tokenName =
                        "wh" + token.replace("token_", "").replace(".png", "").replace("creuss", "");
                if (!tokenName.contains("champion")) {
                    tokenName = tokenName.replace("ion", "");
                }
                for (WormholeModel.Wormhole wh : WormholeModel.Wormhole.values()) {
                    if (tokenName.contains(wh.getWhString())) {
                        wormholeIDs.add(wh.toString());
                        break;
                    }
                }
                if (tokenName.contains("sigma_weirdway")) {
                    wormholeIDs.add(Constants.ALPHA);
                    wormholeIDs.add(Constants.BETA);
                }
            }
            if (unitHolder.getUnitCount(UnitType.Flagship, ghostFlagshipColor) > 0) {
                wormholeIDs.add(Constants.DELTA);
            }
        }

        boolean hasQuantumEntanglement = player != null && player.hasAbility("quantum_entanglement");

        if (player != null
                && player.hasAbility("sundered")
                && player == game.getActivePlayer()
                && forDistance
                && !game.getCurrentActiveSystem().isEmpty()) {
            Set<String> keepers = new HashSet<>(Set.of("epsilon"));
            if (hasQuantumEntanglement || wh_recon || absol_recon) {
                keepers.addAll(Set.of("alpha", "beta"));
            }
            wormholeIDs.removeIf(wh -> !keepers.contains(wh.toLowerCase()));
        }

        if (tile.getSpaceUnitHolder().getTokenList().contains(Constants.TOKEN_SEVERED)) {
            Set<String> keepers = new HashSet<>();
            if (hasQuantumEntanglement || wh_recon || absol_recon) {
                keepers.addAll(Set.of("alpha", "beta"));
            }
            wormholeIDs.removeIf(wh -> !keepers.contains(wh.toLowerCase()));
        }

        if (player != null
                && "ghost".equals(player.getFaction())
                && game.getPlayerFromColorOrFaction("crimson") != null) {
            wormholeIDs.removeIf("epsilon"::equalsIgnoreCase);
        }

        if (hasQuantumEntanglement || wh_recon || absol_recon) {
            if (wormholeIDs.contains(Constants.ALPHA)) {
                wormholeIDs.add(Constants.BETA);
            } else if (wormholeIDs.contains(Constants.BETA)) {
                wormholeIDs.add(Constants.ALPHA);
            }
        }

        if (player != null
                && player == game.getActivePlayer()
                && !game.getCurrentActiveSystem().isEmpty()
                && player.hasTech("lgf")
                && !player.getPlanets().contains("mrte")
                && !player.getPlanets().contains("mr")
                && (tile.getUnitHolders().containsKey("mrte")
                        || tile.getUnitHolders().containsKey("mr"))) {
            wormholeIDs.add(Constants.BETA);
            wormholeIDs.add(Constants.ALPHA);
        }

        if (player != null
                && player == game.getActivePlayer()
                && !game.getCurrentActiveSystem().isEmpty()
                && player.hasTech("tf-lazaxgatefolding")) {
            boolean hasUncontrolledLeg = false;
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (planet.isLegendary() && !player.getPlanets().contains(planet.getName())) {
                    hasUncontrolledLeg = true;
                }
            }
            if (hasUncontrolledLeg) {
                wormholeIDs.add(Constants.BETA);
                wormholeIDs.add(Constants.ALPHA);
            }
        }
        if (!hasQuantumEntanglement
                && !wh_recon
                && !absol_recon
                && forDistance
                && ButtonHelper.isLawInPlay(game, "travel_ban")
                && !neighbors) {
            wormholeIDs.remove(Constants.ALPHA);
            wormholeIDs.remove(Constants.BETA);
        }

        if (wormholeIDs.isEmpty()) {
            return adjacentPositions;
        }

        Set<String> wormholeTiles = new HashSet<>();
        for (String wormholeID : wormholeIDs) {
            wormholeTiles.addAll(Mapper.getWormholesTiles(wormholeID));
        }

        boolean ghostAgent = player != null
                && player.isActivePlayer()
                && !StringUtils.isEmpty(game.getStoredValue("ghostagent_active"))
                && game.getActiveSystem().equals(game.getStoredValue("ghostagent_active"));
        for (Tile tile_ : allTiles) {
            String position_ = tile_.getPosition();

            if (wormholeTiles.contains(tile_.getTileID()) || ghostAgent && doesTileHaveWHs(game, position_)) {
                adjacentPositions.add(position_);
                continue;
            }
            for (UnitHolder unitHolder : tile_.getUnitHolders().values()) {
                Set<String> tokenList = unitHolder.getTokenList();
                for (String token : tokenList) {
                    for (String wormholeID : wormholeIDs) {
                        if (token.contains(wormholeID)
                                && !("eta".equals(wormholeID)
                                        && (token.contains("beta")
                                                || token.contains("theta")
                                                || token.contains("zeta")))) {
                            adjacentPositions.add(position_);
                        }
                    }
                    if (token.contains("sigma_weirdway")
                            && (wormholeIDs.contains(Constants.ALPHA) || wormholeIDs.contains(Constants.BETA))) {
                        adjacentPositions.add(position_);
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
        if (FOWPlusService.isVoid(game, position)) return players;

        Set<String> tilesToCheck = getAdjacentTiles(game, position, null, false);
        Tile startingTile = game.getTileByPosition(position);

        for (Player player_ : game.getRealPlayers()) {
            Set<String> tiles = new HashSet<>(tilesToCheck);
            if (player_.hasAbility("quantum_entanglement") || ButtonHelper.isLawInPlay(game, "travel_ban")) {
                tiles.addAll(getWormholeAdjacencies(game, position, player_, true));
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
        if (tile == null) return false;

        Set<String> unitHolderNames = tile.getUnitHolders().keySet();
        List<String> playerPlanets = player.getPlanetsAllianceMode();
        if (forNeighbors) {
            playerPlanets = player.getPlanets();
        }
        if (playerPlanets.stream().anyMatch(unitHolderNames::contains)) {
            return true;
        } else if (tile.isMecatol(game) && player.hasIIHQ()) {
            return true;
        } else if ("s11".equals(tile.getTileID()) && canSeeStatsOfFaction(game, "cabal", player)) {
            return true;
        } else if ("s12".equals(tile.getTileID()) && canSeeStatsOfFaction(game, "nekro", player)) {
            return true;
        } else if ("s13".equals(tile.getTileID()) && canSeeStatsOfFaction(game, "yssaril", player)) {
            return true;
        }

        if (game.isAllianceMode() && !forNeighbors) {
            boolean allianceHasUnits = game.getRealPlayers().stream()
                    .filter(alliancePlayer -> alliancePlayer != player)
                    .filter(alliancePlayer -> player.getAllianceMembers().contains(alliancePlayer.getFaction()))
                    .anyMatch(alliancePlayer -> playerHasUnitsInSystem(alliancePlayer, tile));

            if (allianceHasUnits) {
                return true;
            }
        }
        return playerHasUnitsInSystem(player, tile);
    }

    /** Check if the player has units in the system */
    public static boolean playerHasUnitsInSystem(Player player, Tile tile) {
        return tile != null && tile.containsPlayersUnits(player);
    }

    public static boolean playerHasPlanetsInSystem(Player player, Tile tile) {
        if (tile == null || player == null) return false;

        for (UnitHolder uH : tile.getPlanetUnitHolders()) {
            if (player.getPlanetsAllianceMode().contains(uH.getName())) {
                return true;
            }
        }
        return false;
    }

    public static boolean playerHasShipsInSystem(Player player, Tile tile) {
        String colorID = Mapper.getColorID(player.getColor());
        if (tile == null || colorID == null) return false;

        UnitHolder unitHolder = tile.getUnitHolders().get(Constants.SPACE);
        Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());

        for (UnitKey unitKey : units.keySet()) {
            if (unitKey != null && unitKey.colorID().equals(colorID)) {
                return true;
            }
        }
        return false;
    }

    public static boolean playerHasActualShipsInSystem(Player player, Tile tile) {
        String colorID = Mapper.getColorID(player.getColor());
        if (tile == null || colorID == null) return false;

        UnitHolder unitHolder = tile.getUnitHolders().get(Constants.SPACE);
        Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());

        for (UnitKey unitKey : units.keySet()) {
            if (unitKey != null
                    && unitKey.colorID().equals(colorID)
                    && player.getUnitFromAsyncID(unitKey.asyncID()) != null
                    && player.getUnitFromAsyncID(unitKey.asyncID()).getIsShip()) {
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
            if (playerHasActualShipsInSystem(p2, tile)) {
                return true;
            }
        }
        return false;
    }

    public static boolean otherPlayersHaveMovementBlockersInSystem(Player player, Tile tile, Game game) {
        if (player.hasTech("dsrhody")) {
            return false;
        }
        for (Player p2 : game.getRealPlayersNDummies()) {
            if (p2 == player || player.getAllianceMembers().contains(p2.getFaction())) {
                continue;
            }
            if (p2.hasTech("ah") && ButtonHelperAgents.doesTileHaveAStructureInIt(p2, tile)) {
                return true;
            }
            if ((p2.hasAbility("decree") || p2.hasTech("tf-radiantsigils")) && tile.isAnomaly(game, p2)) {
                List<Tile> tiles = new ArrayList<>();
                tiles.addAll(CheckUnitContainmentService.getTilesContainingPlayersUnits(game, p2, UnitType.Infantry));
                tiles.addAll(CheckUnitContainmentService.getTilesContainingPlayersUnits(game, p2, UnitType.Mech));
                if (tiles.contains(tile)) {
                    return true;
                }
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
        if (tile == null || colorID == null) return false;

        UnitHolder unitHolder = tile.getUnitHolders().get(Constants.SPACE);
        Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());

        for (UnitKey unitKey : units.keySet()) {
            if (unitKey != null && unitKey.colorID().equals(colorID) && unitKey.unitType() == UnitType.Fighter) {
                return true;
            }
        }
        return false;
    }

    public static boolean playerHasFightersInAdjacentSystems(Player player, Tile tile, Game game) {
        String colorID = Mapper.getColorID(player.getColor());
        if (tile == null || colorID == null) return false;

        for (String pos : getAdjacentTilesAndNotThisTile(game, tile.getPosition(), player, false)) {
            Tile tile2 = game.getTileByPosition(pos);
            UnitHolder unitHolder = tile2.getUnitHolders().get(Constants.SPACE);
            if (unitHolder.getUnitCount(UnitType.Fighter, player.getColor()) > 0) {
                return true;
            }
        }
        return false;
    }

    public static boolean playerHasShipsInAdjacentSystems(Player player, Tile tile, Game game) {
        String colorID = Mapper.getColorID(player.getColor());
        if (tile == null || colorID == null) return false;

        for (String pos : getAdjacentTilesAndNotThisTile(game, tile.getPosition(), player, false)) {
            Tile tile2 = game.getTileByPosition(pos);
            if (playerHasActualShipsInSystem(player, tile2)) {
                return true;
            }
        }
        return false;
    }

    public static boolean playerHasUnitsOnPlanet(Player player, Tile tile, String planet) {
        return playerHasUnitsOnPlanet(player, tile.getUnitHolders().get(planet));
    }

    public static boolean playerHasUnitsOnPlanet(Player player, UnitHolder unitHolder) {
        String colorID = Mapper.getColorID(player.getColor());
        if (colorID == null) return false;

        Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());

        for (UnitKey unitKey : units.keySet()) {
            if (unitKey != null && unitKey.colorID().equals(colorID)) {
                return true;
            }
        }
        return false;
    }

    public static boolean otherPlayersHaveUnitsOnPlanet(Player player, UnitHolder unitHolder) {

        for (Player p2 : player.getGame().getRealPlayersExcludingThis(player)) {
            String colorID = Mapper.getColorID(p2.getColor());
            if (colorID == null) return false;

            Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());

            for (UnitKey unitKey : units.keySet()) {
                if (unitKey != null && unitKey.colorID().equals(colorID)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean playerHasInfantryOnPlanet(Player player, Tile tile, String planet) {
        String colorID = Mapper.getColorID(player.getColor());
        if (tile == null || colorID == null) return false;

        UnitHolder unitHolder = tile.getUnitHolders().get(planet);
        Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());

        for (UnitKey unitKey : units.keySet()) {
            if (unitKey != null && unitKey.colorID().equals(colorID) && unitKey.unitType() == UnitType.Infantry) {
                return true;
            }
        }
        return false;
    }

    /** Ping the players adjacent to a given system */
    public static void pingSystem(Game game, String position, String message) {
        pingSystem(game, position, message, true);
    }

    public static void pingSystem(Game game, String position, String message, boolean viewSystemButton) {
        Tile tile = game.getTileByPosition(position);
        if (tile == null) {
            return;
        }
        // get players adjacent
        for (Player player_ : game.getRealPlayers()) {

            if (message.toLowerCase().contains(player_.getColor().toLowerCase())
                    && !message.toLowerCase()
                            .contains("split" + player_.getColor().toLowerCase())) {
                continue; // skip pinging players if their color is mentioned in the message
            }
            if (getTilePositionsToShow(game, player_).contains(position)) {
                String playerMessage = player_.getRepresentationUnfogged() + " - System "
                        + tile.getRepresentationForButtons() + " has been pinged:\n>>> " + message;
                List<Button> refreshButton = viewSystemButton
                        ? StartCombatService.getGeneralCombatButtons(game, position, player_, player_, "justPicture")
                        : new ArrayList<>();
                MessageHelper.sendMessageToChannelWithButtons(
                        player_.getPrivateChannel(), playerMessage, refreshButton);
            }
        }
    }

    public static void pingAllPlayersWithFullStats(
            Game game, GenericInteractionCreateEvent event, Player playerWithChange, String message) {
        var playersToPing = game.getRealPlayers().stream()
                .filter(viewer -> initializeAndCheckStatVisibility(game, playerWithChange, viewer))
                .collect(Collectors.toSet());

        String playerMessage = playerWithChange.getRepresentation() + " stats changed: " + message;
        for (Player player_ : playersToPing) {
            MessageHelper.sendPrivateMessageToPlayer(player_, game, playerMessage);
        }
    }

    public static void pingPlayersDifferentMessages(
            Game game,
            GenericInteractionCreateEvent event,
            Player playerWithChange,
            String messageForFullInfo,
            String messageForAll) {
        Set<Player> playersWithVisiblity = game.getRealPlayers().stream()
                .filter(viewer -> initializeAndCheckStatVisibility(game, playerWithChange, viewer))
                .collect(Collectors.toSet());
        Set<Player> playersWithoutVisiblity = game.getRealPlayers().stream()
                .filter(player -> !playersWithVisiblity.contains(player) && player != playerWithChange)
                .collect(Collectors.toSet());

        for (Player player_ : playersWithVisiblity) {
            MessageHelper.sendPrivateMessageToPlayer(player_, game, messageForFullInfo);
        }
        for (Player player_ : playersWithoutVisiblity) {
            MessageHelper.sendPrivateMessageToPlayer(player_, game, messageForAll);
        }
    }

    public static void pingPlayersTransaction(
            Game game,
            GenericInteractionCreateEvent event,
            Player sendingPlayer,
            Player receivingPlayer,
            String transactedObject,
            String noVisibilityMessage // for stuff like SFTT
            ) {
        // iterate through the player list. this may result in some extra pings, we'll
        // sort that out later
        for (Player player_ : game.getRealPlayers()) {
            if ("null".equals(player_.getColor())) continue;
            if (player_ == sendingPlayer || player_ == receivingPlayer) continue;

            // let's figure out what they can see!
            initializeFog(game, player_, false);
            boolean senderVisible = canSeeStatsOfPlayer(game, sendingPlayer, player_);
            boolean receiverVisible = canSeeStatsOfPlayer(game, receivingPlayer, player_);

            StringBuilder sb = new StringBuilder();
            // first off let's give full info for someone that can see both sides
            if (senderVisible) {
                sb.append(sendingPlayer.getRepresentation());
            } else {
                sb.append("Someone");
            }
            sb.append(" sent ").append(transactedObject).append(" to ");
            if (receiverVisible) {
                sb.append(receivingPlayer.getRepresentation());
            } else {
                sb.append("someone");
            }

            String message = sb.toString();
            if (!senderVisible && !receiverVisible) {
                message = noVisibilityMessage;
            }
            MessageHelper.sendPrivateMessageToPlayer(player_, game, message);
        }
    }

    private static boolean initializeAndCheckStatVisibility(Game game, Player player, Player viewer) {
        if (viewer == player) return false;
        if ("null".equals(viewer.getColor())) return false;
        initializeFog(game, viewer, false);
        return canSeeStatsOfPlayer(game, player, viewer);
    }

    public static boolean isGameMaster(String userId, Game game) {
        return game.getPlayersWithGMRole().stream()
                .anyMatch(player -> player.getUserID().equals(userId));
    }

    private enum Feature {
        ingress,
        egress,
        breach,
        scar
    }
}
