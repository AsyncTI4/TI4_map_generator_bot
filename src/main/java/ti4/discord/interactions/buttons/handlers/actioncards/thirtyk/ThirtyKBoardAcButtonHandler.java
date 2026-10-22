package ti4.discord.interactions.buttons.handlers.actioncards.thirtyk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.CheckDistanceHelper;
import ti4.helpers.DiceHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitType;
import ti4.helpers.thirtyk.ThirtyKAcState;
import ti4.helpers.thirtyk.ThirtyKCards;
import ti4.helpers.thirtyk.ThirtyKDistance;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.FactionModel;
import ti4.model.Mapper;
import ti4.model.UnitModel;
import ti4.service.fow.BlindSelectionService;
import ti4.service.fow.PlanetTargetService;
import ti4.service.fow.PlanetTargetService.PlanetTargetSpec;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.RemoveUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class ThirtyKBoardAcButtonHandler {

    public static final String CONFLAGRATION = "thkAcConflagration";
    public static final String BLEW_UP = "thkAcBlewUpStart";
    public static final String CUMULATIVE = "thkAcCumulativeStart";
    public static final String DISTANT_MAPS = "thkAcDistantMapsStart";
    public static final String FOLDING = "thkAcFoldingStart";
    public static final String CORRUPT = "thkAcCorruptStart";
    public static final String RENEWAL = "thkAcRenewal_";
    public static final String FINAL_STAND = "thkAcFinalStandStart";
    private static final String BLEW_UP_TILE = "thkAcBlewUpTile_";
    private static final String BLEW_UP_FIGHTER = "thkAcBlewUpFighter_";
    private static final String CUMULATIVE_PLACE = "thkAcCumulativePlace_";
    private static final String DISTANT_MAPS_PLANET = "thkAcDistantMapsPlanet_";
    private static final String DISTANT_MAPS_PAY = "thkAcDmPay_";
    private static final String DISTANT_MAPS_TRAIT = "thkAcDmTrait_";
    private static final String FOLDING_TILE = "thkAcFoldingTile_";
    private static final String FOLDING_MOVE = "thkAcFoldingMove_";
    private static final String CORRUPT_PLANETS = "thkAcCorruptPlanets";
    private static final String CORRUPT_PLACE = "thkAcCorruptPlace_";
    private static final String RENEWAL_ACCEPT = "thkAcRenewalAccept_";
    private static final String FINAL_STAND_ROLL = "thkAcFinalStandRoll_";
    private static final String FOLDING_STAY = "thkAcFoldingStay";
    private static final List<String> EXPLORE_TRAITS = List.of("cultural", "industrial", "hazardous");

    @ButtonHandler(CONFLAGRATION)
    public static void conflagration(ButtonInteractionEvent event, Game game, Player player) {
        ThirtyKAcState.PlanetLoss loss = ThirtyKAcState.planetLoss(game, player);
        Player newOwner = loss == null ? null : game.getPlayerFromColorOrFaction(loss.newOwnerFaction());
        Tile tile = loss == null ? null : game.getTileFromPlanet(loss.planet());
        if (newOwner == null || tile == null || !newOwner.getPlanets().contains(loss.planet())) {
            ThirtyKAcState.clearPlanetLoss(game, player);
            ThirtyKAcTargets.noTarget(event, player, "Conflagration");
            return;
        }
        ThirtyKAcState.clearPlanetLoss(game, player);
        ButtonHelper.deleteMessage(event);
        UnitHolder planet = tile.getUnitHolders().get(loss.planet());
        int infantry = planet == null ? 0 : planet.getUnitCount(UnitType.Infantry, newOwner.getColor());
        int destroyed = Math.max(0, infantry - 1);
        if (destroyed > 0) {
            DestroyUnitService.destroyUnits(
                    event, tile, game, newOwner.getColor(), destroyed + " inf " + loss.planet(), false);
        }
        String planetName = Helper.getPlanetRepresentation(loss.planet(), game);
        FoWHelper.notifyActorAndAffectedElsePublic(
                game,
                player,
                player.getRepresentationUnfogged() + ", the fires you left behind consume " + destroyed
                        + " infantry on " + planetName + " (_Conflagration_).",
                newOwner,
                newOwner.getRepresentationUnfogged() + ", firestorms sweep " + planetName + " and destroy " + destroyed
                        + " of your infantry (_Conflagration_).",
                player.getRepresentationNoPing() + " set " + planetName + " ablaze with _Conflagration_, destroying "
                        + destroyed + " of " + newOwner.getRepresentationNoPing() + "'s infantry.");
    }

    @ButtonHandler(BLEW_UP)
    public static void blewUp(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = blewUpTileButtons(game, player);
        if (buttons.isEmpty()) {
            ThirtyKAcTargets.noTarget(event, player, "And Then They Blew Up!");
            return;
        }
        ThirtyKAcState.clearFightersBlownUp(game, player);
        ButtonHelper.deleteMessage(event);
        NewStuffHelper.sendPaginated(
                player.getCorrectChannel(),
                blewUpMessage(player),
                buttons,
                List.of(),
                player.factionButtonChecker() + BLEW_UP_TILE);
    }

    private static List<Button> blewUpTileButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ThirtyKCards.visibleTiles(game, player)) {
            if (fighterOwners(game, tile).isEmpty()) continue;
            if (!ThirtyKDistance.isMoreThanFiveFromHome(game, player, tile.getPosition())) continue;
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + BLEW_UP_TILE + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
        }
        return buttons;
    }

    private static String blewUpMessage(Player player) {
        return player.getRepresentationUnfogged()
                + ", choose a system more than 5 hexes from your home system to destroy up to 2 fighters in.";
    }

    private static List<Player> fighterOwners(Game game, Tile tile) {
        List<Player> owners = new ArrayList<>();
        for (Player owner : game.getRealPlayersNNeutral()) {
            if (tile.getSpaceUnitHolder().getUnitCount(UnitType.Fighter, owner.getColor()) > 0) owners.add(owner);
        }
        return owners;
    }

    @ButtonHandler(BLEW_UP_TILE)
    public static void blewUpTile(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (NewStuffHelper.turnPage(
                event,
                buttonID,
                blewUpMessage(player),
                () -> blewUpTileButtons(game, player),
                List.of(),
                player.factionButtonChecker() + BLEW_UP_TILE)) {
            return;
        }
        String position = buttonID.substring(BLEW_UP_TILE.length());
        Tile tile = game.getTileByPosition(position);
        if (tile == null || !ThirtyKDistance.isMoreThanFiveFromHome(game, player, position)) {
            PlanetTargetService.fizzle(event, player);
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (Player owner : fighterOwners(game, tile)) {
            String label = game.isFowMode()
                    ? owner.getFactionNameOrColor()
                    : owner.getFactionModel().getShortName();
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + BLEW_UP_FIGHTER + position + "_" + owner.getColor(),
                    "Destroy 1 Fighter: " + label,
                    owner.fogSafeEmoji()));
        }
        buttons.add(Buttons.gray(player.factionButtonChecker() + "deleteButtons", "Done"));
        ButtonHelper.deleteMessage(event);
        ThirtyKAcTargets.send(
                player, player.getRepresentationUnfogged() + ", destroy up to 2 fighters in that system.", buttons);
    }

    @ButtonHandler(BLEW_UP_FIGHTER)
    public static void blewUpFighter(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(BLEW_UP_FIGHTER.length()).split("_", 2);
        if (parts.length != 2) return;
        Tile tile = game.getTileByPosition(parts[0]);
        Player owner = game.getPlayerFromColorOrFaction(parts[1]);
        int destroyed = ThirtyKAcState.fightersBlownUp(game, player, parts[0]);
        if (tile == null || owner == null || destroyed >= 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (tile.getSpaceUnitHolder().getUnitCount(UnitType.Fighter, owner.getColor()) < 1) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        DestroyUnitService.destroyUnits(event, tile, game, owner.getColor(), "1 ff", false);
        ThirtyKAcState.setFightersBlownUp(game, player, parts[0], destroyed + 1);
        String where = tile.getRepresentationForButtons(game, player);
        FoWHelper.notifyActorAndAffectedElsePublic(
                game,
                player,
                player.getRepresentationUnfogged() + ", a hangar bay detonates in " + where
                        + " and 1 fighter is lost (_And Then They Blew Up!_).",
                owner,
                owner.getRepresentationUnfogged() + ", 1 of your fighters in "
                        + tile.getRepresentationForButtons(game, owner)
                        + " explodes without warning (_And Then They Blew Up!_).",
                player.getRepresentationNoPing() + " watched 1 of " + owner.getRepresentationNoPing()
                        + "'s fighters in " + where + " blow up (_And Then They Blew Up!_).");
        if (destroyed + 1 >= 2) {
            ThirtyKAcState.clearFightersBlownUp(game, player);
            ButtonHelper.deleteMessage(event);
        }
    }

    @ButtonHandler(CUMULATIVE)
    public static void cumulativeSupport(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = cumulativeButtons(game, player);
        if (buttons.isEmpty()) {
            ThirtyKAcTargets.noTarget(event, player, "Cumulative Support");
            return;
        }
        ButtonHelper.deleteMessage(event);
        NewStuffHelper.sendPaginated(
                player.getCorrectChannel(),
                cumulativeMessage(player),
                buttons,
                List.of(),
                player.factionButtonChecker() + CUMULATIVE_PLACE);
    }

    private static List<Button> cumulativeButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ThirtyKCards.visibleTiles(game, player)) {
            for (Planet holder : tile.getPlanetUnitHolders()) {
                String planet = holder.getName();
                int infantry = cumulativeInfantry(game, player, planet);
                if (infantry < 1) continue;
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + CUMULATIVE_PLACE + planet,
                        Helper.getPlanetRepresentation(planet, game) + " (" + infantry + " Infantry)"));
            }
        }
        return buttons;
    }

    private static String cumulativeMessage(Player player) {
        return player.getRepresentationUnfogged() + ", choose a planet. You place infantry on it equal"
                + " to its distance from your home system, up to 4.";
    }

    private static int cumulativeInfantry(Game game, Player player, String planet) {
        Tile home = player.getHomeSystemTile();
        Tile tile = game.getTileFromPlanet(planet);
        if (home == null || tile == null) return 0;
        Map<String, Integer> distances = CheckDistanceHelper.getTileDistances(game, null, home.getPosition(), 4, true);
        return Math.min(4, distances.getOrDefault(tile.getPosition(), 4));
    }

    @ButtonHandler(CUMULATIVE_PLACE)
    public static void cumulativePlace(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (NewStuffHelper.turnPage(
                event,
                buttonID,
                cumulativeMessage(player),
                () -> cumulativeButtons(game, player),
                List.of(),
                player.factionButtonChecker() + CUMULATIVE_PLACE)) {
            return;
        }
        String planet = buttonID.substring(CUMULATIVE_PLACE.length());
        int infantry = cumulativeInfantry(game, player, planet);
        Tile tile = game.getTileFromPlanet(planet);
        if (tile == null || infantry < 1) return;
        AddUnitService.addUnits(event, tile, game, player.getColor(), infantry + " inf " + planet);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", volunteers gathered along the way rally to "
                        + Helper.getPlanetRepresentation(planet, game) + ": " + infantry
                        + " infantry placed (_Cumulative Support_).");
    }

    private static PlanetTargetSpec distantMapsSpec(Game game, Player player) {
        return PlanetTargetSpec.of(DISTANT_MAPS_PLANET + BlindSelectionService.TBD_FACTION)
                .excludingSelf()
                .where(planet -> isFarPlanet(game, player, planet.getName()));
    }

    private static boolean isFarPlanet(Game game, Player player, String planet) {
        Tile tile = game.getTileFromPlanet(planet);
        return tile != null && ThirtyKDistance.isMoreThanFiveFromHome(game, player, tile.getPosition());
    }

    @ButtonHandler(DISTANT_MAPS)
    public static void distantMaps(ButtonInteractionEvent event, Game game, Player player) {
        if (game.isFowMode()) {
            List<Button> buttons =
                    PlanetTargetService.targetButtons(game, player, distantMapsSpec(game, player), List.of());
            if (buttons.isEmpty()) {
                ThirtyKAcTargets.noTarget(event, player, "Distant Maps");
                return;
            }
            ButtonHelper.deleteMessage(event);
            ThirtyKAcTargets.send(player, distantMapsMessage(player), buttons);
            return;
        }
        List<Button> buttons = distantMapsButtons(game, player);
        if (buttons.isEmpty()) {
            ThirtyKAcTargets.noTarget(event, player, "Distant Maps");
            return;
        }
        ButtonHelper.deleteMessage(event);
        NewStuffHelper.sendPaginated(
                player.getCorrectChannel(), distantMapsMessage(player), buttons, List.of(), distantMapsNav(player));
    }

    private static List<Button> distantMapsButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (!isDistantMapsTarget(game, player, planet.getName())) continue;
                buttons.add(Buttons.gray(
                        distantMapsNav(player) + planet.getName(),
                        Helper.getPlanetRepresentation(planet.getName(), game)));
            }
        }
        return buttons;
    }

    private static String distantMapsNav(Player player) {
        return player.factionButtonChecker() + DISTANT_MAPS_PLANET + BlindSelectionService.TBD_FACTION + "_";
    }

    private static String distantMapsMessage(Player player) {
        return player.getRepresentationUnfogged()
                + ", choose a planet more than 5 hexes from your home system for _Distant Maps_.";
    }

    private static boolean isDistantMapsTarget(Game game, Player player, String planet) {
        Player owner = game.getPlayerThatControlsPlanet(planet, true);
        return owner != null
                && owner != player
                && isFarPlanet(game, player, planet)
                && !explorableTraits(game, planet).isEmpty();
    }

    private static List<String> explorableTraits(Game game, String planet) {
        Planet holder = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        if (holder == null) return List.of();
        return EXPLORE_TRAITS.stream()
                .filter(trait -> holder.getPlanetTypes().contains(trait))
                .toList();
    }

    @ButtonHandler(DISTANT_MAPS_PLANET)
    public static void distantMapsPlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!game.isFowMode()
                && NewStuffHelper.turnPage(
                        event,
                        buttonID,
                        distantMapsMessage(player),
                        () -> distantMapsButtons(game, player),
                        List.of(),
                        distantMapsNav(player))) {
            return;
        }
        PlanetTargetSpec spec = distantMapsSpec(game, player);
        if (PlanetTargetService.handlePlanetPage(event, game, player, buttonID, spec)) return;
        var target = PlanetTargetService.resolve(
                game, player, buttonID, spec, resolved -> isFarPlanet(game, player, resolved.planetId()));
        List<String> traits = target == null ? List.of() : explorableTraits(game, target.planetId());
        if (target == null || target.owner() == player || traits.isEmpty()) {
            PlanetTargetService.fizzle(event, player);
            return;
        }
        ButtonHelper.deleteMessage(event);
        if (traits.size() == 1) {
            revealDistantMap(game, player, target.owner(), target.planetId(), traits.getFirst());
            return;
        }
        List<Button> buttons = traits.stream()
                .map(trait -> Buttons.green(
                        player.factionButtonChecker() + DISTANT_MAPS_TRAIT + traitCode(trait) + "_" + target.planetId(),
                        "Reveal " + StringUtils.capitalize(trait)))
                .toList();
        ThirtyKAcTargets.send(
                player, player.getRepresentationUnfogged() + ", choose which exploration deck to reveal.", buttons);
    }

    @ButtonHandler(DISTANT_MAPS_TRAIT)
    public static void distantMapsTrait(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String rest = buttonID.substring(DISTANT_MAPS_TRAIT.length());
        String trait = traitFromCode(StringUtils.substringBefore(rest, "_"));
        String planet = StringUtils.substringAfter(rest, "_");
        Player owner = game.getPlayerThatControlsPlanet(planet, true);
        if (owner == null
                || owner == player
                || !isFarPlanet(game, player, planet)
                || !explorableTraits(game, planet).contains(trait)) {
            PlanetTargetService.fizzle(event, player);
            return;
        }
        ButtonHelper.deleteMessage(event);
        revealDistantMap(game, player, owner, planet, trait);
    }

    private static String traitCode(String trait) {
        return trait.substring(0, 1);
    }

    private static String traitFromCode(String code) {
        if (code.length() != 1) return "";
        return EXPLORE_TRAITS.stream()
                .filter(trait -> trait.startsWith(code))
                .findFirst()
                .orElse("");
    }

    private static void revealDistantMap(Game game, Player player, Player owner, String planetId, String trait) {
        List<String> deck = game.getExploreDeck(trait);
        ExploreModel top = deck.isEmpty() ? null : Mapper.getExplore(deck.getFirst());
        String planetName = Helper.getPlanetRepresentation(planetId, game);
        if (top == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", the " + trait + " exploration deck is empty.");
            return;
        }
        String reveal = "old star charts show what waits on " + planetName + ", the top " + trait
                + " exploration card (_Distant Maps_):";
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannelWithEmbed(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", " + reveal,
                    top.getRepresentationEmbed());
            MessageHelper.sendMessageToChannelWithEmbed(
                    owner.getCorrectChannel(),
                    owner.getRepresentationUnfogged() + ", " + reveal,
                    top.getRepresentationEmbed());
        } else {
            MessageHelper.sendMessageToChannelWithEmbed(
                    game.getMainGameChannel(),
                    player.getRepresentationNoPing() + ", " + reveal,
                    top.getRepresentationEmbed());
        }
        List<Button> buttons = new ArrayList<>();
        if (owner.getTg() >= 2) {
            buttons.add(Buttons.green(
                    owner.factionButtonChecker() + DISTANT_MAPS_PAY + player.getColor() + "_" + traitCode(trait) + "_"
                            + planetId,
                    "Pay 2 Trade Goods To Explore"));
        }
        buttons.add(Buttons.red(owner.factionButtonChecker() + "deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                owner.getCorrectChannel(),
                owner.getRepresentationUnfogged() + ", you may pay "
                        + FoWHelper.factionEmojiOrAnon(game, player, "the player who revealed it")
                        + " 2 trade goods to explore " + planetName + ".",
                buttons);
    }

    @ButtonHandler(DISTANT_MAPS_PAY)
    public static void distantMapsPay(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player actor = ThirtyKAcTargets.target(game, buttonID, DISTANT_MAPS_PAY);
        String rest = ThirtyKAcTargets.afterTarget(buttonID, DISTANT_MAPS_PAY);
        String trait = traitFromCode(StringUtils.substringBefore(rest, "_"));
        String planet = StringUtils.substringAfter(rest, "_");
        Planet holder = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        if (actor == null
                || holder == null
                || player.getTg() < 2
                || !player.getPlanets().contains(planet)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You cannot pay for _Distant Maps_ now.");
            return;
        }
        ButtonHelper.deleteMessage(event);
        player.setTg(player.getTg() - 2);
        ButtonHelperStats.gainTGs(event, game, actor, 2, false);
        List<Button> explore = ButtonHelper.getPlanetExplorationButtons(game, holder, player);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", you paid 2 trade goods for the charts. Explore "
                        + Helper.getPlanetRepresentation(planet, game) + ".",
                explore == null
                        ? List.of()
                        : explore.stream()
                                .filter(button -> button.getCustomId() != null
                                        && button.getCustomId().endsWith("_" + trait))
                                .toList());
    }

    @ButtonHandler(FOLDING)
    public static void folding(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = foldingTileButtons(game, player);
        if (buttons.isEmpty()) {
            ThirtyKAcTargets.noTarget(event, player, "Folding of Spacetime");
            return;
        }
        ButtonHelper.deleteMessage(event);
        NewStuffHelper.sendPaginated(
                player.getCorrectChannel(),
                foldingMessage(player),
                buttons,
                List.of(),
                player.factionButtonChecker() + FOLDING_TILE);
    }

    private static List<Button> foldingTileButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ThirtyKCards.visibleTiles(game, player)) {
            if (!tile.isEdgeOfBoard(game)
                    || ButtonHelper.getPlayersWithShipsInTheSystem(game, tile).isEmpty()) {
                continue;
            }
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + FOLDING_TILE + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
        }
        return buttons;
    }

    private static String foldingMessage(Player player) {
        return player.getRepresentationUnfogged() + ", choose a system on the edge of the game board.";
    }

    @ButtonHandler(FOLDING_TILE)
    public static void foldingTile(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (NewStuffHelper.turnPage(
                event,
                buttonID,
                foldingMessage(player),
                () -> foldingTileButtons(game, player),
                List.of(),
                player.factionButtonChecker() + FOLDING_TILE)) {
            return;
        }
        Tile tile = game.getTileByPosition(buttonID.substring(FOLDING_TILE.length()));
        if (tile == null || !tile.isEdgeOfBoard(game)) {
            PlanetTargetService.fizzle(event, player);
            return;
        }
        ButtonHelper.deleteMessage(event);
        List<Player> movers = ButtonHelper.getPlayersWithShipsInTheSystem(game, tile);
        for (Player mover : movers) {
            offerFolding(game, mover, tile);
        }
        if (!movers.contains(player)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", spacetime folds around that system; the players with"
                            + " ships there were offered the move (_Folding of Spacetime_).");
        }
    }

    private static List<Button> foldingDestinations(Game game, Player mover, Tile origin) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ThirtyKCards.visibleTiles(game, mover)) {
            if (tile == origin || !tile.isEdgeOfBoard(game)) continue;
            if (FoWHelper.otherPlayersHaveShipsInSystem(mover, tile, game)) continue;
            buttons.add(Buttons.gray(
                    mover.factionButtonChecker() + FOLDING_MOVE + origin.getPosition() + "_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, mover)));
        }
        return buttons;
    }

    private static void offerFolding(Game game, Player mover, Tile origin) {
        NewStuffHelper.sendPaginated(
                mover.getCorrectChannel(),
                foldingMoveMessage(game, mover, origin),
                foldingDestinations(game, mover, origin),
                stayButton(mover),
                foldingMoveNav(mover, origin));
    }

    private static String foldingMoveMessage(Game game, Player mover, Tile origin) {
        return mover.getRepresentationUnfogged() + ", you may move your ships in "
                + origin.getRepresentationForButtons(game, mover)
                + " to another edge system without other players' ships (_Folding of Spacetime_).";
    }

    private static String foldingMoveNav(Player mover, Tile origin) {
        return mover.factionButtonChecker() + FOLDING_MOVE + origin.getPosition() + "_";
    }

    private static List<Button> stayButton(Player mover) {
        return List.of(Buttons.red(mover.factionButtonChecker() + FOLDING_STAY, "Stay"));
    }

    @ButtonHandler(FOLDING_MOVE)
    public static void foldingMove(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(FOLDING_MOVE.length()).split("_");
        if (parts.length != 2) return;
        Tile from = game.getTileByPosition(parts[0]);
        if (from == null || !FoWHelper.playerHasShipsInSystem(player, from)) {
            PlanetTargetService.fizzle(event, player);
            return;
        }
        if (NewStuffHelper.turnPage(
                event,
                buttonID,
                foldingMoveMessage(game, player, from),
                () -> foldingDestinations(game, player, from),
                stayButton(player),
                foldingMoveNav(player, from))) {
            return;
        }
        Tile to = game.getTileByPosition(parts[1]);
        if (to == null || !to.isEdgeOfBoard(game) || FoWHelper.otherPlayersHaveShipsInSystem(player, to, game)) {
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", that system cannot be chosen now; pick again.");
            offerFolding(game, player, from);
            return;
        }
        List<RemovedUnit> moved = RemoveUnitService.removeAllPlayerNonStructureUnits(
                event, game, player, from, from.getSpaceUnitHolder());
        AddUnitService.addUnits(
                event,
                game,
                moved.stream().map(unit -> unit.onUnitHolder(to, "space")).toList());
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", space folds and your ships slip from "
                        + from.getRepresentationForButtons(game, player) + " to "
                        + to.getRepresentationForButtons(game, player) + " (_Folding of Spacetime_).");
    }

    @ButtonHandler(FOLDING_STAY)
    public static void foldingStay(ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(CORRUPT)
    public static void corruptManifestation(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>(ButtonHelper.getExhaustButtonsWithTG(game, player, "inf"));
        buttons.add(Buttons.green(player.factionButtonChecker() + CORRUPT_PLANETS, "Done Paying 2 Influence"));
        ButtonHelper.deleteMessage(event);
        ThirtyKAcTargets.send(
                player,
                player.getRepresentationUnfogged() + ", pay 2 influence for _Corrupt Manifestation_, then press done.",
                buttons);
    }

    @ButtonHandler(CORRUPT_PLANETS)
    public static void corruptPlanets(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (!tile.isFracture()) continue;
            for (Planet planet : tile.getPlanetUnitHolders()) {
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + CORRUPT_PLACE + planet.getName(),
                        Helper.getPlanetRepresentation(planet.getName(), game)));
            }
        }
        if (buttons.isEmpty()) {
            ThirtyKAcTargets.noTarget(event, player, "Corrupt Manifestation");
            return;
        }
        ButtonHelper.deleteMessage(event);
        ThirtyKAcTargets.send(
                player,
                player.getRepresentationUnfogged()
                        + ", choose a planet in the Fracture to place 2 neutral infantry on.",
                buttons);
    }

    @ButtonHandler(CORRUPT_PLACE)
    public static void corruptPlace(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planet = buttonID.substring(CORRUPT_PLACE.length());
        Tile tile = game.getTileFromPlanet(planet);
        if (tile == null || !tile.isFracture()) return;
        UnitHolder holder = tile.getUnitHolders().get(planet);
        boolean contested = false;
        for (Player other : game.getRealPlayers()) {
            if (holder != null && FoWHelper.playerHasUnitsOnPlanet(other, holder)) contested = true;
        }
        AddUnitService.addUnits(event, tile, game, game.getNeutral().getColor(), "2 inf " + planet);
        ButtonHelper.deleteMessage(event);
        String planetName = Helper.getPlanetRepresentation(planet, game);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", 2 neutral infantry claw their way out of the Fracture onto "
                        + planetName + " (_Corrupt Manifestation_)."
                        + (contested
                                ? " Another player has units there: the neutral infantry invade and resolve ground"
                                        + " combat. Start that combat manually."
                                : ""));
    }

    public static List<Button> renewalButtons(Game game, Player player) {
        return ThirtyKAcTargets.otherPlayerButtons(game, player, RENEWAL, null);
    }

    @ButtonHandler(RENEWAL)
    public static void renewal(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = ThirtyKAcTargets.target(game, buttonID, RENEWAL);
        if (target == null || target == player) {
            PlanetTargetService.fizzle(event, player);
            return;
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentationUnfogged() + ", " + FoWHelper.factionEmojiOrAnon(game, player, "a player")
                        + " played _Extreme Renewal_ on you. You may remove all of your units from the board and all"
                        + " units from your home system, then place your starting units in your home system, draw 2"
                        + " action cards and replenish your commodities.",
                List.of(
                        Buttons.red(
                                target.factionButtonChecker() + RENEWAL_ACCEPT + player.getColor(), "Reset My Forces"),
                        Buttons.gray(target.factionButtonChecker() + "deleteButtons", "Decline")));
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", waiting for " + ThirtyKAcTargets.name(game, target, player)
                        + " to answer _Extreme Renewal_.");
    }

    @ButtonHandler(RENEWAL_ACCEPT)
    public static void renewalAccept(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player actor = ThirtyKAcTargets.target(game, buttonID, RENEWAL_ACCEPT);
        Tile home = player.getHomeSystemTile();
        FactionModel faction = player.getFactionModel();
        if (home == null || faction == null || faction.getStartingFleet() == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Could not find your home system or starting units.");
            return;
        }
        ButtonHelper.deleteMessage(event);
        for (Tile tile : game.getTileMap().values()) {
            tile.removeAllUnits(player.getColor());
        }
        for (Player other : game.getPlayers().values()) {
            if (other.getColor() != null) home.removeAllUnits(other.getColor());
        }
        AddUnitService.addUnitsToDefaultLocations(event, home, game, player.getColor(), faction.getStartingFleet());
        ActionCardHelper.drawActionCards(player, 2);
        ButtonHelperStats.replenishComms(event, game, player, false);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", your forces are swept from the board and reborn at home;"
                        + " you draw 2 action cards and replenish your commodities (_Extreme Renewal_).");
        if (actor != null) {
            MessageHelper.sendMessageToChannel(
                    actor.getCorrectChannel(),
                    actor.getRepresentationUnfogged()
                            + ", your target accepted _Extreme Renewal_ and is rebuilding from nothing.");
        }
    }

    @ButtonHandler(FINAL_STAND)
    public static void finalStand(ButtonInteractionEvent event, Game game, Player player) {
        ThirtyKAcState.clearFinalStand(game, player);
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            Planet holder = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
            if (holder == null || holder.getUnitCount(UnitType.Infantry, player.getColor()) != 1) continue;
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + FINAL_STAND_ROLL + planet,
                    "Roll Final Stand On " + Helper.getPlanetRepresentation(planet, game)));
        }
        if (buttons.isEmpty()) {
            ThirtyKAcTargets.noTarget(event, player, "Final Stand");
            return;
        }
        buttons.add(Buttons.gray(player.factionButtonChecker() + "deleteButtons", "Done"));
        ButtonHelper.deleteMessage(event);
        ThirtyKAcTargets.send(
                player,
                player.getRepresentationUnfogged() + ", for each round of this ground combat, press the button to"
                        + " roll the extra dice from _Final Stand_ while you have exactly 1 infantry there.",
                buttons);
    }

    @ButtonHandler(FINAL_STAND_ROLL)
    public static void finalStandRoll(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planet = buttonID.substring(FINAL_STAND_ROLL.length());
        Planet holder = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        UnitModel infantry = player.getUnitByBaseType("infantry");
        if (holder == null
                || infantry == null
                || !player.getPlanets().contains(planet)
                || holder.getUnitCount(UnitType.Infantry, player.getColor()) != 1) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "You no longer have exactly 1 infantry on a planet you control there.");
            return;
        }
        if (!ThirtyKAcState.markFinalStandRoll(game, player, planet, groundCombatRound(game, planet))) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "You already rolled _Final Stand_ on that planet this round of ground combat.");
            return;
        }
        ButtonHelper.deleteTheOneButton(event);
        int dice = Math.max(0, holder.getInfluence());
        List<DiceHelper.Die> rolls = DiceHelper.rollDice(infantry.getCombatHitsOn(), dice);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + "'s last infantry on "
                        + Helper.getPlanetRepresentation(planet, game) + " makes a _Final Stand_, rolling " + dice
                        + " dice: " + DiceHelper.formatDiceOutput(rolls)
                        + " (" + DiceHelper.countSuccesses(rolls) + " hit(s)).",
                List.of(Buttons.red(
                        player.factionButtonChecker() + FINAL_STAND_ROLL + planet,
                        "Roll Final Stand Next Round On " + Helper.getPlanetRepresentation(planet, game))));
    }

    private static String groundCombatRound(Game game, String planet) {
        Tile tile = game.getTileFromPlanet(planet);
        if (tile == null) return "0";
        int round = 0;
        for (Player combatant : game.getRealPlayersNNeutral()) {
            round = Math.max(
                    round,
                    NumberUtils.toInt(game.getStoredValue(
                            "combatRoundTracker" + combatant.getFaction() + tile.getPosition() + planet)));
        }
        return String.valueOf(round);
    }
}
