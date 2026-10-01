package ti4.discord.interactions.buttons.handlers.relics.theodisi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.UnusedCommanderHelper;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.AbilityModel;
import ti4.model.BreakthroughModel;
import ti4.model.LeaderModel;
import ti4.model.PlanetModel;
import ti4.model.RelicModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.LeaderEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.game.MonumentsService;
import ti4.service.leader.RefreshLeaderService;
import ti4.service.leader.UnlockLeaderService;
import ti4.service.relic.SendRelicService;
import ti4.service.tech.PlayerTechService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;

@UtilityClass
public class BlueReverieRelicHandler {
    private static final String PLACE_GEDU_STATION = "placeGeduStation_";
    private static final String GEDU_STATION_TOKEN = "token_gedustation.png";
    private static final String USE_KADLIN = "useKadlinsStaff";
    private static final String SELECT_KADLIN_TARGET = "kadlinStaffTarget_";
    private static final String ANTIPODE_COMPONENT = "theAntipodeComponent_";
    private static final String PLACE_ENTROPIC_SCAR = "placeEntropicScar_";
    private static final String ANTIPODE_REMAINING = "theAntipodeRemaining_";
    private static final String ENTROPIC_SCAR_TOKEN = "token_entropicscar_async.png";
    private static final String USE_AENDS_TORCH = "useAendsTorch_";
    private static final String PLACE_AENDS_TORCH_SHIP = "placeAendsTorchShip_";
    private static final String SELECT_MATJEK_PLANET = "selectMatjekDragonCagePlanet_";
    private static final String GAIN_MATJEK_TECH = "gainMatjekDragonCageTech_";

    public static void transferGeduStationIfNecessary(
            GenericInteractionCreateEvent event, Game game, Tile tile, Player newOwner) {
        if (game == null
                || tile == null
                || newOwner == null
                || !tile.getSpaceUnitHolder().getTokenList().contains(GEDU_STATION_TOKEN)) return;
        Player previousOwner = game.getRealPlayers().stream()
                .filter(player -> player.hasRelic("gedustation"))
                .findFirst()
                .orElse(null);
        if (previousOwner == null
                || previousOwner == newOwner
                || FoWHelper.playerHasActualShipsInSystem(previousOwner, tile)) return;
        SendRelicService.handleSendRelic(event, game, previousOwner, newOwner, "gedustation");
    }

    public static void offerGeduStationPlacement(Game game, Player player) {
        if (game == null || player == null || !player.hasRelic("gedustation")) return;
        player.addPlanet("gedustation");
        List<Button> buttons = getGeduStationPlacementButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + " has no non-home system containing their ships for _Gedu Station_.");
            return;
        }
        String message = player.getRepresentationNoPing() + ", choose a system for _Gedu Station_.";
        String buttonPrefix = player.factionButtonChecker() + PLACE_GEDU_STATION;
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), message, NewStuffHelper.buttonPagination(buttons, buttonPrefix, 0));
    }

    @ButtonHandler(PLACE_GEDU_STATION)
    public static void placeGeduStation(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasRelic("gedustation")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = getGeduStationPlacementButtons(game, player);
        String message = player.getRepresentationNoPing() + ", choose a system for _Gedu Station_.";
        String buttonPrefix = player.factionButtonChecker() + PLACE_GEDU_STATION;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, buttonPrefix, buttonID)) return;

        Tile tile = game.getTileByPosition(buttonID.substring(PLACE_GEDU_STATION.length()));
        if (tile == null
                || tile.isHomeSystem(game)
                || tile.getTileModel().isHyperlane()
                || !FoWHelper.playerHasActualShipsInSystem(player, tile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        for (Tile existingTile : game.getTileMap().values()) {
            existingTile.removeToken(GEDU_STATION_TOKEN, Constants.SPACE);
        }
        tile.addToken(GEDU_STATION_TOKEN, Constants.SPACE);
        player.addPlanet("gedustation");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed _Gedu Station_ in " + tile.getRepresentation() + ".");
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getGeduStationPlacementButtons(Game game, Player player) {
        return ButtonHelper.getTilesWithShipsInTheSystem(player, game).stream()
                .filter(tile -> !tile.isHomeSystem(game) && !tile.getTileModel().isHyperlane())
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + PLACE_GEDU_STATION + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    public static Button getKadlinButton(Player player) {
        return Buttons.green(player.factionButtonChecker() + USE_KADLIN, "Use Kadlin's Staff");
    }

    public static void offerAendsTorch(Game game, Player player, Tile tile) {
        if (!player.hasRelicReady("aendstorch")
                || getAendsTorchShips(game, player).isEmpty()) return;
        List<Button> buttons = List.of(
                Buttons.green(player.factionButtonChecker() + USE_AENDS_TORCH + tile.getPosition(), "Use Aend's Torch"),
                Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", you may exhaust _Aend's Torch_ to place a neutral ship in "
                        + tile.getRepresentationForButtons(game, player) + ".",
                buttons);
    }

    @ButtonHandler(USE_AENDS_TORCH)
    public static void useAendsTorch(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(USE_AENDS_TORCH.length()));
        if (tile == null || !tile.getPosition().equals(game.getActiveSystem()) || !player.hasRelicReady("aendstorch")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = getAendsTorchShips(game, player).stream()
                .sorted(Comparator.comparing(UnitModel::getCost).thenComparing(UnitModel::getName))
                .map(ship -> Buttons.green(
                        player.factionButtonChecker() + PLACE_AENDS_TORCH_SHIP + tile.getPosition() + "|"
                                + ship.getAsyncId(),
                        "Place 1 " + ship.getName(),
                        ship.getUnitEmoji()))
                .toList();
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.addExhaustedRelic("aendstorch");
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose the neutral ship to place with _Aend's Torch_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE_AENDS_TORCH_SHIP)
    public static void placeAendsTorchShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(PLACE_AENDS_TORCH_SHIP.length()).split("\\|", 2);
        Tile tile = parts.length == 2 ? game.getTileByPosition(parts[0]) : null;
        UnitModel ship = parts.length == 2 ? game.getNeutral().getUnitFromAsyncID(parts[1]) : null;
        if (tile == null
                || ship == null
                || !player.getExhaustedRelics().contains("aendstorch")
                || !tile.getPosition().equals(game.getActiveSystem())
                || !getAendsTorchShips(game, player).stream()
                        .map(UnitModel::getAsyncId)
                        .anyMatch(ship.getAsyncId()::equals)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        AddUnitService.addUnits(event, tile, game, game.getNeutralColor(), "1 " + ship.getAsyncId());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " exhausted _Aend's Torch_ and placed 1 neutral " + ship.getName()
                        + " in " + tile.getRepresentationForButtons(game, player) + ".");
        StartCombatService.startSpaceCombat(game, player, game.getNeutral(), tile, event, "-aends-torch");
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveMatjeksDragonCage(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ButtonHelper.getTilesWithShipsInTheSystem(player, game)) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                buttons.add(Buttons.red(
                        player.factionButtonChecker() + SELECT_MATJEK_PLANET + planet.getName(),
                        Helper.getPlanetRepresentation(planet.getName(), game)));
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + " has no planet in a system containing their ships for _Matjek's Dragon Cage_.");
            return;
        }
        String prefix = player.factionButtonChecker() + SELECT_MATJEK_PLANET;
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose a planet for _Matjek's Dragon Cage_.",
                NewStuffHelper.buttonPagination(buttons, prefix, 0));
    }

    @ButtonHandler(SELECT_MATJEK_PLANET)
    public static void resolveMatjeksDragonCagePlanet(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ButtonHelper.getTilesWithShipsInTheSystem(player, game)) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                buttons.add(Buttons.red(
                        player.factionButtonChecker() + SELECT_MATJEK_PLANET + planet.getName(),
                        Helper.getPlanetRepresentation(planet.getName(), game)));
            }
        }
        String message = player.getRepresentationNoPing() + ", choose a planet for _Matjek's Dragon Cage_.";
        String prefix = player.factionButtonChecker() + SELECT_MATJEK_PLANET;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, prefix, buttonID)) return;
        String planetName = buttonID.substring(SELECT_MATJEK_PLANET.length());
        Tile tile = game.getTileFromPlanet(planetName);
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(planetName);
        if (holder == null || !FoWHelper.playerHasActualShipsInSystem(player, tile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Map<UnitKey, Integer> capturedUnits = new HashMap<>();
        Set<Player> capturedPlayers = new LinkedHashSet<>();
        for (UnitKey unitKey : holder.getUnitKeys()) {
            int count = holder.getUnitCount(unitKey);
            if (count < 1) continue;
            capturedUnits.put(unitKey, count);
            Player owner = game.getPlayerByUnitKey(unitKey).orElse(null);
            if (owner != null) capturedPlayers.add(owner);
        }
        DestroyUnitService.destroyAllUnits(event, tile, game, holder, false);
        for (Map.Entry<UnitKey, Integer> unit : capturedUnits.entrySet()) {
            AddUnitService.addUnits(
                    event,
                    player.getNomboxTile(),
                    game,
                    unit.getKey().colorID(),
                    unit.getValue() + " " + unit.getKey().unitName());
        }
        List<Button> techButtons = capturedPlayers.stream()
                .flatMap(captured -> captured.getTechs().stream())
                .distinct()
                .map(Mapper::getTech)
                .filter(tech -> tech != null && !tech.isFactionTech() && !player.hasTech(tech.getAlias()))
                .sorted(Comparator.comparing(TechnologyModel::getName))
                .map(tech -> Buttons.green(
                        player.factionButtonChecker() + GAIN_MATJEK_TECH + tech.getAlias(),
                        "Gain " + tech.getName(),
                        tech.getCondensedReqsEmojis(true)))
                .toList();
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " destroyed and captured all units on "
                        + Helper.getPlanetRepresentation(planetName, game) + " with _Matjek's Dragon Cage_.");
        ButtonHelper.deleteMessage(event);
        if (techButtons.isEmpty()) return;
        techButtons = new ArrayList<>(techButtons);
        techButtons.add(Buttons.red(player.factionButtonChecker() + GAIN_MATJEK_TECH + "done", "Done"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + ", you may gain 1 non-faction technology owned by a player whose units you captured.",
                NewStuffHelper.paginateWithPinnedButtons(
                        techButtons.subList(0, techButtons.size() - 1),
                        List.of(techButtons.getLast()),
                        player.factionButtonChecker() + GAIN_MATJEK_TECH,
                        25,
                        0));
    }

    @ButtonHandler(GAIN_MATJEK_TECH)
    public static void gainMatjeksDragonCageTech(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String techID = buttonID.substring(GAIN_MATJEK_TECH.length());
        if ("done".equals(techID)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        TechnologyModel tech = Mapper.getTech(techID);
        if (tech == null || tech.isFactionTech() || player.hasTech(techID)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        PlayerTechService.addTech(event, game, player, techID);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " gained " + tech.getName() + " with _Matjek's Dragon Cage_.");
        ButtonHelper.deleteMessage(event);
    }

    private static List<UnitModel> getAendsTorchShips(Game game, Player player) {
        return game.getNeutral().getUnitModels().stream()
                .filter(UnitModel::getIsShip)
                .filter(ship -> ship.getCost() > 0 && ship.getCost() < player.getTotalVictoryPoints())
                .toList();
    }

    @ButtonHandler(USE_KADLIN)
    public static void getKadlinsStaffPlayerButtons(ButtonInteractionEvent event, Player player, Game game) {
        if (game == null || player == null || !player.hasRelicReady("kadlinsstaff")) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        List<Button> targets = new ArrayList<>();
        for (Player target : game.getRealPlayers()) {
            if (target.getStrategicCC() < 1) {
                continue;
            }
            targets.add(Buttons.gray(
                    player.factionButtonChecker() + SELECT_KADLIN_TARGET + target.getFaction(),
                    target.getFactionNameOrColor(),
                    target.getFactionEmojiOrColor()));
        }

        if (targets.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", please choose who you want to use _Kadlin's Staff_ on.",
                targets);

        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(SELECT_KADLIN_TARGET)
    private static void resolveKadlinAbility(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasRelicReady("kadlinsstaff")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String targetFaction = buttonID.substring(SELECT_KADLIN_TARGET.length());
        Player target = game.getPlayerFromColorOrFaction(targetFaction);
        if (target == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Unable to find selected player.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (target.getStrategicCC() < 1) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    target.getRepresentationNoPing() + " no longer has a strategy token to spend.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        String leaderID = UnusedCommanderHelper.getUnusedCommander(game);
        if (leaderID == null || leaderID.isBlank()) {
            MessageHelper.sendMessageToChannel(
                    target.getCorrectChannel(),
                    target.getRepresentation()
                            + " cannot gain a new commander, as all commanders are already in play.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.addExhaustedRelic("kadlinsstaff");
        target.setStrategicCC(target.getStrategicCC() - 1);
        target.addLeader(leaderID);
        game.addFakeCommander(leaderID);
        UnlockLeaderService.unlockLeader(
                leaderID,
                game,
                target,
                target.getRepresentation() + " has used _Kadlin's Staff_ to acquire a new commander, "
                        + Mapper.getLeader(leaderID).getName() + "!");
        MessageHelper.sendMessageToChannel(
                target.getCorrectChannel(),
                target.getRepresentation()
                        + ", 1 strategy token has been automatically deducted from your strategy pool.");

        ButtonHelper.deleteMessage(event);
    }

    public static void resolveTheAntipode(ButtonInteractionEvent event, Game game, Player player) {
        game.setStoredValue(ANTIPODE_REMAINING + player.getFaction(), "3");
        sendTheAntipodeComponentButtons(event.getMessageChannel(), game, player, 0);
    }

    @ButtonHandler(ANTIPODE_COMPONENT)
    public static void resolveTheAntipodeComponent(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String payload = buttonID.substring(ANTIPODE_COMPONENT.length());
        if (payload.startsWith("page")) {
            try {
                sendTheAntipodeComponentButtons(event, game, player, Integer.parseInt(payload.substring(4)));
            } catch (NumberFormatException e) {
                ButtonHelper.deleteMessage(event);
            }
            return;
        }
        if ("done".equals(payload)) {
            game.removeStoredValue(ANTIPODE_REMAINING + player.getFaction());
            ButtonHelper.deleteMessage(event);
            sendEntropicScarButtons(event.getMessageChannel(), game, player, 0);
            return;
        }
        int remaining;
        try {
            remaining = Integer.parseInt(game.getStoredValue(ANTIPODE_REMAINING + player.getFaction()));
        } catch (NumberFormatException e) {
            remaining = 0;
        }
        if (remaining < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String[] choice = payload.split("\\|", 2);
        if (choice.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String component = resolveTheAntipodeComponent(game, player, choice[0], choice[1]);
        if (component == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " cannot ready or unlock that component.");
            ButtonHelper.deleteMessage(event);
            sendTheAntipodeComponentButtons(event.getMessageChannel(), game, player, 0);
            return;
        }
        remaining--;
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " readied or unlocked " + component + " using _The Antipode_.");
        ButtonHelper.deleteMessage(event);
        if (remaining == 0) {
            game.removeStoredValue(ANTIPODE_REMAINING + player.getFaction());
            sendEntropicScarButtons(event.getMessageChannel(), game, player, 0);
            return;
        }
        game.setStoredValue(ANTIPODE_REMAINING + player.getFaction(), Integer.toString(remaining));
        sendTheAntipodeComponentButtons(event.getMessageChannel(), game, player, 0);
    }

    @ButtonHandler(PLACE_ENTROPIC_SCAR)
    public static void placeEntropicScar(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String payload = buttonID.substring(PLACE_ENTROPIC_SCAR.length());
        if (payload.startsWith("page")) {
            try {
                sendEntropicScarButtons(event, game, player, Integer.parseInt(payload.substring(4)));
            } catch (NumberFormatException e) {
                ButtonHelper.deleteMessage(event);
            }
            return;
        }
        Tile tile = game.getTileByPosition(payload);
        if (tile == null || tile.getTileModel().isHyperlane() || !canPlaceEntropicScar(game, player, tile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        for (Tile existingTile : game.getTileMap().values()) {
            existingTile.removeToken(ENTROPIC_SCAR_TOKEN, Constants.SPACE);
        }
        tile.addToken(ENTROPIC_SCAR_TOKEN, Constants.SPACE);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed the Entropic Scar token in " + tile.getRepresentation()
                        + ".");
        ButtonHelper.deleteMessage(event);
    }

    private static void sendTheAntipodeComponentButtons(MessageChannel channel, Game game, Player player, int page) {
        List<Button> buttons = getTheAntipodeComponentButtons(game, player);
        if (buttons.isEmpty()) {
            game.removeStoredValue(ANTIPODE_REMAINING + player.getFaction());
            sendEntropicScarButtons(channel, game, player, 0);
            return;
        }
        int remaining = Integer.parseInt(game.getStoredValue(ANTIPODE_REMAINING + player.getFaction()));
        String message = player.getRepresentationNoPing() + ", choose up to " + remaining + " component"
                + (remaining == 1 ? "" : "s") + " to ready or unlock with _The Antipode_.";
        List<Button> pinnedButtons =
                List.of(Buttons.red(player.factionButtonChecker() + ANTIPODE_COMPONENT + "done", "Done"));
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                message,
                NewStuffHelper.paginateWithPinnedButtons(
                        buttons, pinnedButtons, player.factionButtonChecker() + ANTIPODE_COMPONENT, 25, page));
    }

    private static void sendTheAntipodeComponentButtons(
            ButtonInteractionEvent event, Game game, Player player, int page) {
        List<Button> buttons = getTheAntipodeComponentButtons(game, player);
        if (buttons.isEmpty()) {
            game.removeStoredValue(ANTIPODE_REMAINING + player.getFaction());
            ButtonHelper.deleteMessage(event);
            sendEntropicScarButtons(event.getMessageChannel(), game, player, 0);
            return;
        }
        int remaining = Integer.parseInt(game.getStoredValue(ANTIPODE_REMAINING + player.getFaction()));
        String message = player.getRepresentationNoPing() + ", choose up to " + remaining + " component"
                + (remaining == 1 ? "" : "s") + " to ready or unlock with _The Antipode_.";
        List<Button> pinnedButtons =
                List.of(Buttons.red(player.factionButtonChecker() + ANTIPODE_COMPONENT + "done", "Done"));
        MessageHelper.editMessageWithButtons(
                event,
                message,
                NewStuffHelper.paginateWithPinnedButtons(
                        buttons, pinnedButtons, player.factionButtonChecker() + ANTIPODE_COMPONENT, 25, page));
    }

    private static List<Button> getTheAntipodeComponentButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        String prefix = player.factionButtonChecker() + ANTIPODE_COMPONENT;
        for (String planet : player.getExhaustedPlanets()) {
            buttons.add(Buttons.green(prefix + "planet|" + planet, Helper.getPlanetRepresentation(planet, game)));
        }
        for (String breakthrough : player.getBreakthroughIDs()) {
            BreakthroughModel model = Mapper.getBreakthrough(breakthrough);
            if (model == null) continue;
            if (player.isBreakthroughUnlocked(breakthrough) && player.isBreakthroughExhausted(breakthrough)) {
                buttons.add(Buttons.blue(
                        prefix + "breakthrough|" + breakthrough,
                        "Ready " + model.getName() + " Breakthrough",
                        player.getFactionEmoji()));
            } else if (!player.isBreakthroughUnlocked(breakthrough)) {
                buttons.add(Buttons.green(
                        prefix + "unlockBreakthrough|" + breakthrough,
                        "Unlock " + model.getName() + " Breakthrough",
                        player.getFactionEmoji()));
            }
        }
        for (var leader : player.getLeaders()) {
            LeaderModel model = leader.getLeaderModel().orElse(null);
            if (model == null) continue;
            if (leader.isExhausted()) {
                buttons.add(Buttons.gray(
                        prefix + "leader|" + leader.getId(),
                        "Ready " + model.getName(),
                        LeaderEmojis.getLeaderTypeEmoji(leader.getType())));
            } else if (leader.isLocked()) {
                buttons.add(Buttons.green(
                        prefix + "unlockLeader|" + leader.getId(),
                        "Unlock " + model.getName(),
                        LeaderEmojis.getLeaderTypeEmoji(leader.getType())));
            }
        }
        for (String relic : player.getExhaustedRelics()) {
            RelicModel model = Mapper.getRelic(relic);
            if (model != null)
                buttons.add(Buttons.red(prefix + "relic|" + relic, "Ready " + model.getName() + " Relic"));
        }
        for (String tech : player.getExhaustedTechs()) {
            TechnologyModel model = Mapper.getTech(tech);
            if (model != null)
                buttons.add(Buttons.green(
                        prefix + "tech|" + tech,
                        "Ready " + model.getName() + " Technology",
                        model.getCondensedReqsEmojis(true)));
        }
        for (String planet : player.getExhaustedPlanetsAbilities()) {
            PlanetModel model = Mapper.getPlanet(planet);
            if (model != null)
                buttons.add(Buttons.blue(
                        prefix + "legendary|" + planet,
                        "Ready " + model.getName() + " Ability",
                        MiscEmojis.LegendaryPlanet));
        }
        for (UnitModel monument : MonumentsService.getExhaustedMonuments(game, player)) {
            buttons.add(
                    Buttons.blue(prefix + "monument|" + monument.getId(), "Ready " + monument.getName() + " Monument"));
        }
        for (String ability : player.getExhaustedAbilities()) {
            AbilityModel model = Mapper.getAbility(ability);
            if (model != null && player.hasAbility(ability)) {
                buttons.add(Buttons.gray(
                        prefix + "ability|" + ability,
                        "Ready " + model.getName() + " Ability",
                        player.getFactionEmoji()));
            }
        }
        return buttons;
    }

    private static String resolveTheAntipodeComponent(Game game, Player player, String type, String componentID) {
        return switch (type) {
            case "planet" ->
                player.getExhaustedPlanets().remove(componentID)
                        ? Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(componentID, game)
                        : null;
            case "breakthrough" -> {
                if (!player.isBreakthroughUnlocked(componentID) || !player.isBreakthroughExhausted(componentID))
                    yield null;
                player.setBreakthroughExhausted(componentID, false);
                yield Mapper.getBreakthrough(componentID).getNameRepresentation();
            }
            case "unlockBreakthrough" -> {
                if (player.isBreakthroughUnlocked(componentID) || Mapper.getBreakthrough(componentID) == null)
                    yield null;
                BreakthroughCommandHelper.unlockBreakthrough(game, player, componentID);
                yield Mapper.getBreakthrough(componentID).getNameRepresentation();
            }
            case "leader" -> {
                var leader = player.getLeaderByID(componentID).orElse(null);
                if (leader == null || !leader.isExhausted()) yield null;
                RefreshLeaderService.refreshLeader(player, leader, game);
                yield leader.getLeaderModel()
                        .map(LeaderModel::getNameRepresentation)
                        .orElse(componentID);
            }
            case "unlockLeader" -> {
                var leader = player.getLeaderByID(componentID).orElse(null);
                if (leader == null || !leader.isLocked()) yield null;
                UnlockLeaderService.unlockLeader(componentID, game, player);
                yield leader.getLeaderModel()
                        .map(LeaderModel::getNameRepresentation)
                        .orElse(componentID);
            }
            case "relic" -> {
                if (!player.getExhaustedRelics().contains(componentID)) yield null;
                player.removeExhaustedRelic(componentID);
                RelicModel model = Mapper.getRelic(componentID);
                yield model == null ? componentID : model.getNameRepresentation();
            }
            case "tech" -> {
                if (!player.getExhaustedTechs().contains(componentID)) yield null;
                player.refreshTech(componentID);
                TechnologyModel model = Mapper.getTech(componentID);
                yield model == null ? componentID : model.getNameRepresentation();
            }
            case "legendary" -> {
                if (!player.getExhaustedPlanetsAbilities().remove(componentID)) yield null;
                PlanetModel model = Mapper.getPlanet(componentID);
                yield model == null ? componentID : model.getLegendaryNameRepresentation();
            }
            case "monument" ->
                MonumentsService.readyMonument(game, player, componentID)
                        ? Mapper.getUnit(componentID).getName() + " Monument"
                        : null;
            case "ability" ->
                player.removeExhaustedAbility(componentID)
                        ? Mapper.getAbility(componentID).getName() + " Ability"
                        : null;
            default -> null;
        };
    }

    private static void sendEntropicScarButtons(MessageChannel channel, Game game, Player player, int page) {
        List<Button> buttons = getEntropicScarButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    channel, player.getRepresentationNoPing() + " has no eligible system for the Entropic Scar token.");
            return;
        }
        String message = player.getRepresentationNoPing() + ", choose where to place the Entropic Scar token.";
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                message,
                NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + PLACE_ENTROPIC_SCAR, page));
    }

    private static void sendEntropicScarButtons(ButtonInteractionEvent event, Game game, Player player, int page) {
        List<Button> buttons = getEntropicScarButtons(game, player);
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " has no eligible system for the Entropic Scar token.");
            return;
        }
        String message = player.getRepresentationNoPing() + ", choose where to place the Entropic Scar token.";
        MessageHelper.editMessageWithButtons(
                event,
                message,
                NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + PLACE_ENTROPIC_SCAR, page));
    }

    private static List<Button> getEntropicScarButtons(Game game, Player player) {
        Set<String> positions = new LinkedHashSet<>();
        for (Tile tile : game.getTileMap().values()) {
            if (!tile.containsPlayersUnits(player)) continue;
            positions.add(tile.getPosition());
            positions.addAll(FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false));
        }
        return positions.stream()
                .map(game::getTileByPosition)
                .filter(tile -> tile != null && !tile.getTileModel().isHyperlane())
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + PLACE_ENTROPIC_SCAR + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    private static boolean canPlaceEntropicScar(Game game, Player player, Tile tile) {
        if (tile.containsPlayersUnits(player)) return true;
        return FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false).stream()
                .map(game::getTileByPosition)
                .anyMatch(adjacent -> adjacent != null && adjacent.containsPlayersUnits(player));
    }
}
