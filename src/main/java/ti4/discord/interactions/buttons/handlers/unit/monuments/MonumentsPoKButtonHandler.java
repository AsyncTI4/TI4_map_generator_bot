package ti4.discord.interactions.buttons.handlers.unit.monuments;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
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
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.ComponentActionHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.SleeperTokenHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.game.MonumentsService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.RemoveUnitService;

public class MonumentsPoKButtonHandler {
    private static final String USE_PHOENIXCAULDRON = "usePhoenixCauldron";
    private static final String SELECT_PHOENIX_SYSTEM = "selectPhoenixCauldronSystem_";
    private static final String PLACE_PANOPTICON = "placePanopticon_";
    private static final String RESOLVE_SPIRE_OF_IXTH = "resolveSpireOfIxth_";
    private static final String USE_LODESTAR = "useLodestar";
    private static final String PRODUCE_LODESTAR_FLAGSHIP = "produceLodestarFlagship_";
    private static final String USE_SCEPTER = "useScepterOfUl";
    private static final String PLACE_SCEPTER_SLEEPER = "placeScepterSleeper_";
    private static final String USE_SHE_CONSUMES_WORLDS = "useSheConsumesWorlds_";

    // Phoenix Cauldron
    public static Button getPhoenixCauldronButton(Player player) {
        return Buttons.green(
                player.factionButtonChecker() + USE_PHOENIXCAULDRON, "Use Phoenix Cauldron", FactionEmojis.Argent);
    }

    @ButtonHandler(USE_PHOENIXCAULDRON)
    public static void startPhoenixCauldron(ButtonInteractionEvent event, Game game, Player player) {
        if (!game.isMonumentsMode()
                || !MonumentsService.isMonumentOnBoard(game, player, "argent_monument")
                || !MonumentsService.isMonumentReady(game, player, "argent_monument")) {
            return;
        }

        List<Button> eligibleSystems = new ArrayList<>();
        for (Tile tile : MonumentsService.getTilesInOrAdjacentToPlayerMonument(game, player)) {
            if (FoWHelper.playerHasShipsInSystem(player, tile)) {
                continue;
            }
            boolean hasSustainDamageShip = tile.getSpaceUnitHolder().getUnitKeys().stream()
                    .anyMatch(unitKey -> {
                        if (player.unitBelongsToPlayer(unitKey)) {
                            return false;
                        }
                        Player targetPlayer = game.getPlayerFromColorOrFaction(unitKey.getColor());
                        if (targetPlayer == null) {
                            return false;
                        }
                        UnitModel unit = targetPlayer.getUnitFromUnitKey(unitKey);
                        return unit != null
                                && unit.getIsShip()
                                && ButtonHelper.unitCanSustainDamage(game, targetPlayer, tile, unit);
                    });
            if (!hasSustainDamageShip) {
                continue;
            }

            eligibleSystems.add(Buttons.green(
                    player.factionButtonChecker() + SELECT_PHOENIX_SYSTEM + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", please choose a system to use _Phoenix Cauldron_ on.",
                eligibleSystems);

        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        ComponentActionHelper.serveNextComponentActionButtons(event, game, player);
    }

    @ButtonHandler(SELECT_PHOENIX_SYSTEM)
    public static void resolvePhoenixCauldron(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String tilePos = buttonID.replace(SELECT_PHOENIX_SYSTEM, "");
        Tile tile = game.getTileByPosition(tilePos);
        if (tile == null
                || !MonumentsService.isMonumentReady(game, player, "argent_monument")
                || FoWHelper.playerHasShipsInSystem(player, tile)
                || MonumentsService.getTilesInOrAdjacentToPlayerMonument(game, player).stream()
                        .noneMatch(eligibleTile -> eligibleTile.getPosition().equals(tilePos))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        UnitHolder space = tile.getSpaceUnitHolder();
        boolean assignedHits = false;
        for (UnitKey unitKey : space.getUnitKeys()) {
            if (player.unitBelongsToPlayer(unitKey)) {
                continue;
            }
            Player targetPlayer = game.getPlayerFromColorOrFaction(unitKey.getColor());
            if (targetPlayer == null) {
                continue;
            }
            UnitModel unit = targetPlayer.getUnitFromUnitKey(unitKey);
            if (unit == null
                    || !unit.getIsShip()
                    || !ButtonHelper.unitCanSustainDamage(game, targetPlayer, tile, unit)) {
                continue;
            }

            for (UnitState state : space.getNonZeroUnitStates(unitKey)) {
                int count = space.getUnitCountForState(unitKey, state);

                for (int index = 0; index < count; index++) {
                    List<Button> hitButtons = new ArrayList<>();
                    Button destroy =
                            ButtonHelper.buildAssignHitButton(targetPlayer, tile, space, state, unitKey, 1, false);
                    hitButtons.add(destroy.withCustomId(destroy.getCustomId() + "deleteThisMessage"));

                    if (!state.isDamaged()) {
                        Button sustain =
                                ButtonHelper.buildAssignHitButton(targetPlayer, tile, space, state, unitKey, 1, true);
                        hitButtons.add(sustain.withCustomId(sustain.getCustomId() + "deleteThisMessage"));
                    }

                    hitButtons.add(Buttons.gray("deleteButtons", "Cancel the Hit"));

                    MessageHelper.sendMessageToChannelWithButtons(
                            event.getMessageChannel(),
                            targetPlayer.getRepresentation()
                                    + ", 1 hit has been assigned to your "
                                    + state.humanDescr()
                                    + " "
                                    + unitKey.humanReadableName()
                                    + ".",
                            hitButtons);
                    assignedHits = true;
                }
            }
        }
        if (!assignedHits) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + " used _Phoenix Cauldron_, but there were no eligible ships.");
        }
        MonumentsService.exhaustMonument(game, player, "argent_monument");
        ButtonHelper.deleteMessage(event);
    }

    // The Panopticon
    public static List<Button> getPanopticonPlacementButtons(Game game, Player player) {
        if (!game.isMonumentsMode() || !player.hasUnit("empyrean_monument")) {
            return List.of();
        }
        return game.getTileMap().values().stream()
                .filter(tile ->
                        tile.getTileModel() != null && !tile.getTileModel().isHyperlane())
                .filter(tile -> tile.getPlanetUnitHolders().isEmpty())
                .filter(tile -> !tile.isSupernova() && !tile.isFracture())
                .sorted(Comparator.comparing(Tile::getPosition))
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + PLACE_PANOPTICON + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    @ButtonHandler(PLACE_PANOPTICON)
    public static void placePanopticon(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = getPanopticonPlacementButtons(game, player);
        String message = player.getRepresentationNoPing()
                + ", please choose the empty system in which to place _The Panopticon_ in space.";
        String buttonPrefix = player.factionButtonChecker() + PLACE_PANOPTICON;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, buttonPrefix, buttonID)) {
            return;
        }
        String position = buttonID.replace(PLACE_PANOPTICON, "");
        Tile tile = game.getTileByPosition(position);
        if (tile == null
                || buttons.stream().noneMatch(button -> button.getCustomId().endsWith(PLACE_PANOPTICON + position))) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 monument");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed _The Panopticon_ in the space area of "
                        + tile.getRepresentationForButtons(game, player) + ".");
        ButtonHelper.deleteMessage(event);
    }

    public static void checkIfPanopticonIsBlockaded(Game game, GenericInteractionCreateEvent event) {
        if (game == null || !game.isMonumentsMode()) {
            return;
        }
        for (Player owner : game.getRealPlayers()) {
            Tile tile = MonumentsService.getMonumentTile(game, owner, "empyrean_monument");
            if (tile == null
                    || FoWHelper.playerHasActualShipsInSystem(owner, tile)
                    || game.getRealPlayers().stream()
                            .noneMatch(
                                    other -> other != owner && FoWHelper.playerHasActualShipsInSystem(other, tile))) {
                continue;
            }

            DestroyUnitService.destroyUnits(event, tile, game, owner.getColor(), "1 monument", false);
            MessageHelper.sendMessageToChannel(
                    owner.getCorrectChannel(),
                    owner.getRepresentation() + "Destroyed _The Panopticon_ because it was blockaded.");
        }
    }

    // Spire of Ixth
    public static void sendSpireOfIxthButtons(Game game) {
        if (game == null || !game.isMonumentsMode()) {
            return;
        }
        for (Player player : game.getRealPlayers()) {
            List<Tile> monumentTiles = new ArrayList<>();
            if (game.isFrankenGame()) {
                monumentTiles.addAll(game.getTileMap().values().stream()
                        .filter(tile -> ButtonHelper.doesPlayerHaveUnitHere("mahact_monument", player, tile))
                        .toList());
                if (MonumentsService.isMonumentOnBoard(game, player, "mahact_monument")) {
                    Tile monumentTile = MonumentsService.getMonumentTile(game, player, "mahact_monument");
                    if (monumentTile != null && !monumentTiles.contains(monumentTile)) {
                        monumentTiles.add(monumentTile);
                    }
                }
            } else if ("mahact".equals(player.getFaction())) {
                for (Player monumentOwner : game.getRealPlayers()) {
                    if (monumentOwner == player) {
                        monumentTiles.addAll(game.getTileMap().values().stream()
                                .filter(tile -> ButtonHelper.doesPlayerHaveUnitHere("mahact_monument", player, tile))
                                .toList());
                    }
                    if (MonumentsService.isMonumentOnBoard(game, monumentOwner, "mahact_monument")) {
                        Tile monumentTile = MonumentsService.getMonumentTile(game, monumentOwner, "mahact_monument");
                        if (monumentTile != null && !monumentTiles.contains(monumentTile)) {
                            monumentTiles.add(monumentTile);
                        }
                    }
                }
            }
            for (Tile monumentTile : monumentTiles) {
                List<Button> buttons = game.getRealPlayers().stream()
                        .filter(tokenOwner -> monumentTile.hasPlayerCC(tokenOwner))
                        .map(tokenOwner -> Buttons.green(
                                player.factionButtonChecker() + RESOLVE_SPIRE_OF_IXTH + monumentTile.getPosition() + "|"
                                        + tokenOwner.getColor(),
                                "Move " + tokenOwner.getFactionNameOrColor() + " Token to Fleet Pool",
                                tokenOwner.getFactionEmojiOrColor()))
                        .toList();
                if (buttons.isEmpty()) {
                    continue;
                }
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        player.getRepresentationNoPing()
                                + ", you may resolve _Spire Of Ixth_ to move a command token from "
                                + monumentTile.getRepresentationForButtons(game, player) + " to your fleet pool.",
                        buttons);
            }
        }
    }

    @ButtonHandler(RESOLVE_SPIRE_OF_IXTH)
    public static void resolveSpireOfIxth(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(RESOLVE_SPIRE_OF_IXTH.length()).split("\\|", 2);
        if (payload.length != 2) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        Tile tile = game.getTileByPosition(payload[0]);
        Player tokenOwner = game.getPlayerFromColorOrFaction(payload[1]);
        boolean canUse = false;
        if (tile != null && tokenOwner != null && tile.hasPlayerCC(tokenOwner)) {
            if (game.isFrankenGame()) {
                canUse = ButtonHelper.doesPlayerHaveUnitHere("mahact_monument", player, tile)
                        || tile == MonumentsService.getMonumentTile(game, player, "mahact_monument");
            } else if ("mahact".equals(player.getFaction())) {
                canUse = ButtonHelper.doesPlayerHaveUnitHere("mahact_monument", player, tile)
                        || game.getRealPlayers().stream()
                                .filter(monumentOwner ->
                                        MonumentsService.isMonumentOnBoard(game, monumentOwner, "mahact_monument"))
                                .map(monumentOwner ->
                                        MonumentsService.getMonumentTile(game, monumentOwner, "mahact_monument"))
                                .anyMatch(tile::equals);
            }
        }
        if (!canUse) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        tile.removeCC(Mapper.getCCID(tokenOwner.getColor()));
        player.getMahactCC().add(tokenOwner.getColor());
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " moved " + tokenOwner.getRepresentationNoPing()
                        + "'s command token from " + tile.getRepresentationForButtons(game, player)
                        + " to their fleet pool with _Spire Of Ixth_.");
        ButtonHelper.deleteMessage(event);
    }

    // Quantum Reliquary
    public static boolean canTradeRelicsWithNaazMonument(Game game, Player player, Player otherPlayer) {
        if (game == null || player == null || otherPlayer == null || !game.isMonumentsMode()) {
            return false;
        }
        return List.of(player, otherPlayer).stream()
                .filter(monumentOwner -> MonumentsService.isMonumentOnBoard(game, monumentOwner, "naaz-rokha_monument"))
                .anyMatch(monumentOwner ->
                        MonumentsService.getTilesInOrAdjacentToPlayerMonument(game, monumentOwner).stream()
                                .anyMatch(tile -> FoWHelper.playerHasUnitsInSystem(
                                        monumentOwner == player ? otherPlayer : player, tile)));
    }

    // Lodestar
    public static Button getLodestarButton(Player player) {
        return Buttons.gray(player.factionButtonChecker() + USE_LODESTAR, "Use Lodestar", FactionEmojis.Nomad);
    }

    @ButtonHandler(USE_LODESTAR)
    public static void startLodestar(ButtonInteractionEvent event, Game game, Player player) {
        Tile lodestarTile = MonumentsService.getMonumentTile(game, player, "nomad_monument");
        if (!game.isMonumentsMode() || lodestarTile == null) {
            return;
        }

        Tile flagshipTile = game.getTileMap().values().stream()
                .filter(tile -> tile.getSpaceUnitHolder().getUnitCount(UnitType.Flagship, player) > 0)
                .findFirst()
                .orElse(null);

        List<Button> buttons = new ArrayList<>();
        if (flagshipTile == null) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + PRODUCE_LODESTAR_FLAGSHIP + "false",
                    "Remove Lodestar and Produce Flagship",
                    FactionEmojis.Nomad));
        } else if (!CommandCounterHelper.hasCC(player, flagshipTile)) {
            buttons.add(Buttons.blue(
                    player.factionButtonChecker() + PRODUCE_LODESTAR_FLAGSHIP + "true",
                    "Remove Lodestar, Rebuild Flagship",
                    FactionEmojis.Nomad));
        }

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(
                    event,
                    player.getRepresentationNoPing()
                            + ", your flagship is in a system containing your command token, so _Lodestar_ cannot produce it.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose how to resolve _Lodestar_.",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(PRODUCE_LODESTAR_FLAGSHIP)
    public static void produceLodestarFlagship(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        boolean replaceFlagship = Boolean.parseBoolean(buttonID.replace(PRODUCE_LODESTAR_FLAGSHIP, ""));
        Tile lodestarTile = MonumentsService.getMonumentTile(game, player, "nomad_monument");
        Tile flagshipTile = game.getTileMap().values().stream()
                .filter(tile -> tile.getSpaceUnitHolder().getUnitCount(UnitType.Flagship, player) > 0)
                .findFirst()
                .orElse(null);

        if (!game.isMonumentsMode()
                || lodestarTile == null
                || (!replaceFlagship && flagshipTile != null)
                || (replaceFlagship && (flagshipTile == null || CommandCounterHelper.hasCC(player, flagshipTile)))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        UnitHolder monumentHolder = lodestarTile.getUnitHolders().values().stream()
                .filter(holder -> holder.getUnitCount(UnitType.Monument, player) > 0)
                .findFirst()
                .orElse(null);
        if (monumentHolder == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        RemoveUnitService.removeUnit(event, lodestarTile, game, player, monumentHolder, UnitType.Monument, 1);
        if (replaceFlagship) {
            RemoveUnitService.removeUnit(
                    event, flagshipTile, game, player, flagshipTile.getSpaceUnitHolder(), UnitType.Flagship, 1);
        }

        List<Button> productionButtons =
                new ArrayList<>(Helper.getPlaceUnitButtons(event, player, game, lodestarTile, "lodestar", "place"));
        productionButtons.removeIf(button -> {
            String label = button.getLabel();
            return label == null
                    || (!label.startsWith("Produce Flagship")
                            && !"Done Producing Units".equals(label)
                            && !"Reset Build".equals(label));
        });

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", produce your flagship in "
                        + lodestarTile.getRepresentationForButtons(game, player)
                        + " using _Lodestar_.",
                productionButtons);
        ButtonHelper.deleteMessage(event);
    }

    // Scepter of Ul
    public static Button getScepterButton(Player player) {
        return Buttons.green(player.factionButtonChecker() + USE_SCEPTER, "Use Scepter of Ul", FactionEmojis.Titans);
    }

    @ButtonHandler(USE_SCEPTER)
    public static void getScepterPlanetButtons(ButtonInteractionEvent event, Game game, Player player) {
        if (!game.isMonumentsMode()
                || !MonumentsService.isMonumentOnBoard(game, player, "titans_monument")
                || !MonumentsService.isMonumentReady(game, player, "titans_monument")) {
            return;
        }

        List<Button> eligiblePlanets = new ArrayList<>();
        for (Tile tile : MonumentsService.getTilesInOrAdjacentToPlayerMonument(game, player)) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (planet.getPlanetTypes().isEmpty() || planet.getTokenList().contains(Constants.TOKEN_SLEEPER_PNG)) {
                    continue;
                }

                eligiblePlanets.add(Buttons.green(
                        player.factionButtonChecker() + PLACE_SCEPTER_SLEEPER + planet.getName(),
                        Helper.getPlanetRepresentationNoResInf(planet.getName(), game)));
            }
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", please choose the planet on which to place a sleeper token using _Scepter of Ul_.",
                eligiblePlanets);

        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        ComponentActionHelper.serveNextComponentActionButtons(event, game, player);
    }

    @ButtonHandler(PLACE_SCEPTER_SLEEPER)
    public static void placeScepterSleeper(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.replace(PLACE_SCEPTER_SLEEPER, "");
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        Tile planetTile = game.getTileFromPlanet(planetName);

        if (!game.isMonumentsMode()
                || !MonumentsService.isMonumentOnBoard(game, player, "titans_monument")
                || !MonumentsService.isMonumentReady(game, player, "titans_monument")
                || planet == null
                || planetTile == null
                || planet.getPlanetTypes().isEmpty()
                || planet.getTokenList().contains(Constants.TOKEN_SLEEPER_PNG)
                || MonumentsService.getTilesInOrAdjacentToPlayerMonument(game, player).stream()
                        .noneMatch(tile -> tile.getPosition().equals(planetTile.getPosition()))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        SleeperTokenHelper.addOrRemoveSleeper(event, game, planetName, player);
        MonumentsService.exhaustMonument(game, player, "titans_monument");

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " exhausted _Scepter of Ul_ to place a sleeper token on "
                        + Helper.getPlanetRepresentationNoResInf(planetName, game)
                        + ".");

        ButtonHelper.deleteMessage(event);
    }

    // She Consumes Worlds
    public static void addSheConsumesWorldsButton(
            List<Button> buttons, Game game, Player player, Tile tile, Planet planet) {
        if (!game.isMonumentsMode()
                || !player.hasUnit("cabal_monument")
                || MonumentsService.hasMonumentOnBoard(game, player)
                || tile == null
                || planet == null) {
            return;
        }

        boolean hasStructure = planet.getUnitKeys().stream().anyMatch(unitKey -> switch (unitKey.unitType()) {
            case Pds, Spacedock, Monument -> true;
            default -> false;
        });
        if (!hasStructure) {
            return;
        }
        buttons.add(Buttons.green(
                player.factionButtonChecker() + USE_SHE_CONSUMES_WORLDS + planet.getName(),
                "Place She Consumes Worlds",
                FactionEmojis.Cabal));
    }

    @ButtonHandler(USE_SHE_CONSUMES_WORLDS)
    public static void resolveSheConsumesWorlds(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.replace(USE_SHE_CONSUMES_WORLDS, "");
        Tile tile = game.getTileFromPlanet(planetName);
        Planet planet = game.getUnitHolderFromPlanet(planetName);

        if (!game.isMonumentsMode()
                || !player.hasUnit("cabal_monument")
                || MonumentsService.hasMonumentOnBoard(game, player)
                || tile == null
                || planet == null
                || !FoWHelper.playerHasUnitsOnPlanet(player, planet)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<UnitKey> structures = planet.getUnitKeys().stream()
                .filter(unitKey -> switch (unitKey.unitType()) {
                    case Pds, Spacedock, Monument -> true;
                    default -> false;
                })
                .toList();
        if (structures.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<String> capturedStructures = new ArrayList<>();
        for (UnitKey unitKey : structures) {
            int count = planet.getUnitCount(unitKey);
            if (count < 1) {
                continue;
            }

            List<Integer> removed = planet.removeUnit(unitKey, count);
            player.getNomboxTile().getSpaceUnitHolder().addUnitsWithStates(unitKey, removed);
            capturedStructures.add(count + " " + unitKey.humanReadableName());
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 monument " + planetName);

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + " placed _She Consumes Worlds_ on "
                        + Helper.getPlanetRepresentation(planetName, game)
                        + " and captured "
                        + String.join(", ", capturedStructures)
                        + ".");

        ButtonHelper.deleteMessage(event);
    }
}
