package ti4.discord.interactions.buttons.handlers.unit.monuments;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.game.MonumentsService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class MonumentsDSButtonHandler {
    private static final String USE_CONCLAVE = "useAiConclave";
    private static final String DEPLOY_TUCC = "deployTuccAcademy_";
    private static final String EXPLORE_TUCC = "exploreTuccAcademy_";
    private static final String CELDAURI_MONUMENT_COMMIT = "celdauriMonumentCommit_";
    private static final String USE_CYCLOTRON = "useCyclotron";
    private static final String USE_TWILIGHT_THRONE = "useTwilightThrone";
    private static final String REVEAL_FREE_SYSTEMS_PROMISSORY = "revealFreeSystemsPromissory_";
    private static final String PRODUCE_FLORZEN_STASIS_FIGHTER = "produceFlorzenStasisFighter_";
    private static final String RELEASE_FLORZEN_STASIS_FIGHTER = "releaseFlorzenStasisFighter_";
    private static final String USE_FLORZEN_STASIS_PRODUCTION = "useFlorzenStasisProduction";
    private static final String FLORZEN_STASIS_PRODUCTION = "florzenMonument_";
    private static final String FLORZEN_STASIS_HELD = "florzenStasisHeld_";

    // Twilight Throne
    public static Button getTwilightThroneButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + USE_TWILIGHT_THRONE, "Use Twilight Throne", FactionEmojis.edyn);
    }

    @ButtonHandler(USE_TWILIGHT_THRONE)
    public static void resolveTwilightThrone(ButtonInteractionEvent event, Game game, Player player) {
        if (!game.isMonumentsMode()
                || !MonumentsService.isMonumentOnBoard(game, player, "edyn_monument")
                || !MonumentsService.isMonumentReady(game, player, "edyn_monument")) {
            return;
        }

        MonumentsService.exhaustMonument(game, player, "edyn_monument");
        Planet monumentPlanet = MonumentsService.getPlayerMonumentPlanet(game, player);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " exhausted _Twilight Throne_ to produce a mech on "
                        + monumentPlanet.getRepresentation(game)
                        + " without spending resources.");
        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                player.getRepresentation() + ", use the production button to place the mech.",
                Buttons.green(
                        player.factionButtonChecker() + "placeOneNDone_skipbuild_mech_" + monumentPlanet.getName(),
                        "Produce Mech on " + Helper.getPlanetRepresentation(monumentPlanet.getName(), game),
                        UnitEmojis.mech));

        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    // Flotilla Cyclotron
    public static Button offerCyclotronButton(Player player) {
        return Buttons.green(
                player.factionButtonChecker() + USE_CYCLOTRON,
                "Use Flotilla Cyclotron (+1 Move)",
                FactionEmojis.dihmohn);
    }

    @ButtonHandler(USE_CYCLOTRON)
    public static void resolveCyclotron(ButtonInteractionEvent event, Game game, Player player) {
        if (!game.isMonumentsMode()
                || !MonumentsService.isMonumentOnBoard(game, player, "dihmohn_monument")
                || !MonumentsService.isMonumentReady(game, player, "dihmohn_monument")) {
            return;
        }
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "dihmohn_monument");
        if (monumentTile == null) {
            return;
        }
        MonumentsService.exhaustMonument(game, player, "dihmohn_monument");

        game.setStoredValue(
                "dihmohnCyclotron_" + player.getFaction(), monumentTile.getPosition());

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " exhausted _Flotilla Cyclotron_ to add +1 movement to all their ships in "
                        + monumentTile.getRepresentation()
                        + " until the end of this tactical action.");

        ButtonHelper.deleteMessage(event);
    }

    // Tucc Academy
    public static boolean isPlanetNotAdjacentToHomeSystem(Game game, Planet planet, Player player) {
        Tile homeSystem = player.getHomeSystemTile();
        Tile planetTile = game.getTileFromPlanet(planet.getName());
        if (homeSystem == null || planetTile == null) {
            return false;
        }
        return !homeSystem.getPosition().equals(planetTile.getPosition())
                && !FoWHelper.getAdjacentTilesAndNotThisTile(game, homeSystem.getPosition(), player, false)
                        .contains(planetTile.getPosition());
    }

    public static Button getTuccAcademyButton(Player monumentPlayer, Player exploringPlayer, Planet exploredPlanet) {
        return Buttons.green(
                monumentPlayer.factionButtonChecker() + DEPLOY_TUCC + exploredPlanet.getName() + "|" + exploringPlayer.getFaction(),
                "Deploy Tucc Academy",
                FactionEmojis.bentor);
    }

    public static List<Button> getTuccAcademyExploreButtons(Game game, Player player, Planet planet) {
        return planet.getPlanetTypes().stream()
                .filter(trait -> List.of("cultural", "industrial", "hazardous").contains(trait))
                .map(trait -> Buttons.gray(
                        player.factionButtonChecker() + EXPLORE_TUCC + planet.getName() + "|" + trait,
                        "Explore " + planet.getRepresentation(game) + " As " + StringUtils.capitalize(trait),
                        ExploreEmojis.getTraitEmoji(trait)))
                .toList();
    }

    @ButtonHandler(DEPLOY_TUCC)
    public static void deployTuccAcademy(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!game.isMonumentsMode() || MonumentsService.isMonumentOnBoard(game, player, "bentor_monument")) {
            return;
        }

        String[] payload = buttonID.substring(DEPLOY_TUCC.length()).split("\\|", 2);
        String planetName = payload.length == 2 ? payload[0] : null;
        Player exploringPlayer = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[1]) : null;
        Tile planetTile = planetName == null ? null : game.getTileFromPlanet(planetName);
        Planet planet = planetTile == null ? null : planetTile.getPlanetUnitHolders().stream()
                .filter(candidate -> candidate.getName().equals(planetName))
                .findFirst()
                .orElse(null);
        UnitModel monument = Mapper.getUnit("bentor_monument");
        if (planet == null
                || exploringPlayer == null
                || exploringPlayer == player
                || !isPlanetNotAdjacentToHomeSystem(game, planet, exploringPlayer)
                || monument == null
                || !monument.canBePlacedOnPlanetTypes(planet.getPlanetTypes())
                || planet.getUnitKeys().isEmpty()) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        String coexistFlag = game.getStoredValue("coexistFlag");
        game.setStoredValue("coexistFlag", "yes");

        AddUnitService.addUnits(
                event, game.getTileFromPlanet(planetName), game, player.getColor(), "1 monument " + planetName);

        if (coexistFlag.isEmpty()) {
            game.removeStoredValue("coexistFlag");
        } else {
            game.setStoredValue("coexistFlag", coexistFlag);
        }

        planet = MonumentsService.getPlayerMonumentPlanet(game, player);

        List<Button> buttons = getTuccAcademyExploreButtons(game, player, planet);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " placed their monument into coexistence on " + planet.getRepresentation(game)
                        + ", and may now explore it.",
                buttons);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(EXPLORE_TUCC)
    public static void exploreTuccAcademyPlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(EXPLORE_TUCC.length()).split("\\|", 2);
        Tile tile = payload.length == 2 ? game.getTileFromPlanet(payload[0]) : null;
        Planet planet = tile == null ? null : tile.getPlanetUnitHolders().stream()
                .filter(candidate -> candidate.getName().equals(payload[0]))
                .findFirst()
                .orElse(null);
        if (planet == null
                || !MonumentsService.isMonumentOnBoard(game, player, "bentor_monument")
                || MonumentsService.getPlayerMonumentPlanet(game, player) != planet
                || !planet.getPlanetTypes().contains(payload.length == 2 ? payload[1] : "")) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        ExploreService.explorePlanet(event, tile, planet.getName(), payload[1], player, false, game, 1, true);
        ButtonHelper.deleteMessage(event);
    }

    // Anvil of Atlas
    public static boolean producedNonFighterShipInMonumentSystem(Game game, Player player) {
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "axis_monument");
        if (monumentTile == null) {
            return false;
        }

        for (String producedUnitKey : player.getCurrentProducedUnits().keySet()) {
            int locationSeparator = producedUnitKey.lastIndexOf('_');
            int tileSeparator = producedUnitKey.lastIndexOf('_', locationSeparator - 1);
            if (tileSeparator < 0 || locationSeparator < 0) {
                continue;
            }

            String unitAlias = producedUnitKey.substring(0, tileSeparator);
            String tilePosition = producedUnitKey.substring(tileSeparator + 1, locationSeparator);
            if (!monumentTile.getPosition().equals(tilePosition)) {
                continue;
            }

            UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unitAlias), player.getColor());
            UnitModel producedUnit = player.getUnitsByAsyncID(unitKey.asyncID()).stream()
                    .findFirst()
                    .orElse(null);

            if (producedUnit != null && producedUnit.getIsShip() && producedUnit.getUnitType() != UnitType.Fighter) {
                return true;
            }
        }

        return false;
    }

    // Independence Tower
    public static void offerFreeSystemsMonumentPromissoryReveal(Game game, Player leadershipPlayer) {
        if (game == null || leadershipPlayer == null || !game.isMonumentsMode()) {
            return;
        }
        for (Player monumentOwner : game.getRealPlayers()) {
            Tile monumentTile = MonumentsService.getMonumentTile(game, monumentOwner, "free_systems_monument");
            if (monumentTile == null || monumentOwner != leadershipPlayer) {
                continue;
            }
            for (Player recipient : game.getRealPlayers()) {
                if (recipient == monumentOwner
                        || !hasUnitsInOrAdjacentToFreeSystemsMonument(game, recipient, monumentTile)) {
                    continue;
                }
                List<Button> buttons = recipient.getPromissoryNotes().keySet().stream()
                        .filter(pn -> !recipient.getPromissoryNotesInPlayArea().contains(pn))
                        .filter(pn -> game.getPNOwner(pn) == monumentOwner)
                        .map(Mapper::getPromissoryNote)
                        .filter(java.util.Objects::nonNull)
                        .map(pn -> Buttons.gray(
                                recipient.factionButtonChecker() + REVEAL_FREE_SYSTEMS_PROMISSORY
                                        + monumentOwner.getFaction() + "|" + pn.getAlias(),
                                "Reveal " + pn.getName()))
                        .toList();
                if (!buttons.isEmpty()) {
                    MessageHelper.sendMessageToChannelWithButtons(
                            recipient.getCorrectChannel(),
                            recipient.getRepresentationNoPing()
                                    + ", reveal one of "
                                    + monumentOwner.getRepresentationNoPing()
                                    + "'s promissory notes from your hand to gain 1 command token with _Independence Tower_.",
                            buttons);
                }
            }
        }
    }

    @ButtonHandler(REVEAL_FREE_SYSTEMS_PROMISSORY)
    public static void revealFreeSystemsMonumentPromissory(
            ButtonInteractionEvent event, Game game, Player recipient, String buttonID) {
        String[] payload =
                buttonID.substring(REVEAL_FREE_SYSTEMS_PROMISSORY.length()).split("\\|", 2);
        Player monumentOwner = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        Tile monumentTile = monumentOwner == null
                ? null
                : MonumentsService.getMonumentTile(game, monumentOwner, "free_systems_monument");
        PromissoryNoteModel promissoryNote = payload.length == 2 ? Mapper.getPromissoryNote(payload[1]) : null;
        if (monumentOwner == null
                || monumentTile == null
                || promissoryNote == null
                || recipient == monumentOwner
                || !recipient.getPromissoryNotes().containsKey(promissoryNote.getAlias())
                || recipient.getPromissoryNotesInPlayArea().contains(promissoryNote.getAlias())
                || game.getPNOwner(promissoryNote.getAlias()) != monumentOwner
                || !hasUnitsInOrAdjacentToFreeSystemsMonument(game, recipient, monumentTile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        MessageEmbed embed = promissoryNote.getRepresentationEmbed(false, false, false);
        MessageHelper.sendMessageEmbedsToCardsInfoThread(
                monumentOwner,
                recipient.getRepresentationNoPing() + " revealed a promissory note with _Independence Tower_.",
                List.of(embed));
        MessageHelper.sendMessageToChannelWithButtons(
                recipient.getCorrectChannel(),
                recipient.getRepresentationNoPing()
                        + " revealed _"
                        + promissoryNote.getName()
                        + "_ and gains 1 command token with _Independence Tower_.",
                ButtonHelper.getGainCCButtons(recipient));
        ButtonHelper.deleteMessage(event);
    }

    private static boolean hasUnitsInOrAdjacentToFreeSystemsMonument(Game game, Player player, Tile monumentTile) {
        if (FoWHelper.playerHasUnitsInSystem(player, monumentTile)) {
            return true;
        }
        return FoWHelper.getAdjacentTiles(game, monumentTile.getPosition(), player, false).stream()
                .map(game::getTileByPosition)
                .anyMatch(tile -> tile != null && FoWHelper.playerHasUnitsInSystem(player, tile));
    }

    // Corsairs' Cove
    public static Button getFlorzenStasisFighterButton(Game game, Player player, Tile tile) {
        if (!MonumentsService.isMonumentOnBoard(game, player, "florzen_monument")) {
            return null;
        }
        return Buttons.gray(
                player.factionButtonChecker() + PRODUCE_FLORZEN_STASIS_FIGHTER + tile.getPosition(),
                "Produce 1 Fighter to Stasis",
                UnitEmojis.fighter);
    }

    @ButtonHandler(PRODUCE_FLORZEN_STASIS_FIGHTER)
    public static void produceFlorzenStasisFighter(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(PRODUCE_FLORZEN_STASIS_FIGHTER.length()));
        if (tile == null || !MonumentsService.isMonumentOnBoard(game, player, "florzen_monument")) {
            return;
        }
        player.setStasisFighters(player.getStasisFighters() + 1);
        player.produceUnit("ff_" + tile.getPosition() + "_" + Constants.SPACE);
        String[] held =
                game.getStoredValue(FLORZEN_STASIS_HELD + player.getFaction()).split("\\|", 2);
        int heldCount = held.length == 2 && tile.getPosition().equals(held[0]) ? Integer.parseInt(held[1]) : 0;
        game.setStoredValue(FLORZEN_STASIS_HELD + player.getFaction(), tile.getPosition() + "|" + (heldCount + 1));
        event.getMessage()
                .editMessage(Helper.buildProducedUnitsMessage(player, game)
                        + "\n-# "
                        + (heldCount + 1)
                        + " fighter"
                        + (heldCount == 0 ? " is" : "s are")
                        + " held in stasis on _Corsairs' Cove_.")
                .queue();
    }

    public static boolean canUseFlorzenStasisProduction(Game game, Player player) {
        return game.isMonumentsMode()
                && player.getStasisFighters() > 0
                && MonumentsService.isMonumentOnBoard(game, player, "florzen_monument");
    }

    public static Button getFlorzenStasisProductionButton(Player player) {
        return Buttons.green(
                player.factionButtonChecker() + USE_FLORZEN_STASIS_PRODUCTION,
                "Use Corsairs' Cove",
                UnitEmojis.fighter);
    }

    @ButtonHandler(USE_FLORZEN_STASIS_PRODUCTION)
    public static void useFlorzenStasisProduction(ButtonInteractionEvent event, Game game, Player player) {
        if (!canUseFlorzenStasisProduction(game, player)) {
            return;
        }
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "florzen_monument");
        if (monumentTile == null) {
            return;
        }
        player.resetProducedUnits();
        game.setStoredValue(FLORZEN_STASIS_PRODUCTION + player.getFaction(), monumentTile.getPosition() + "|0");
        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + RELEASE_FLORZEN_STASIS_FIGHTER + monumentTile.getPosition(),
                        "Produce 1 Fighter",
                        UnitEmojis.fighter),
                Buttons.red(
                        player.factionButtonChecker() + "deleteButtons_florzenMonument_" + monumentTile.getPosition(),
                        "Done Producing Units"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", produce up to "
                        + player.getStasisFighters()
                        + " fighters in "
                        + monumentTile.getRepresentationForButtons(game, player)
                        + " without spending resources with _Corsairs' Cove_.",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(RELEASE_FLORZEN_STASIS_FIGHTER)
    public static void releaseFlorzenStasisFighter(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String tilePosition = buttonID.substring(RELEASE_FLORZEN_STASIS_FIGHTER.length());
        String[] production = game.getStoredValue(FLORZEN_STASIS_PRODUCTION + player.getFaction())
                .split("\\|", 2);
        Tile tile = game.getTileByPosition(tilePosition);
        int produced = production.length == 2 ? Integer.parseInt(production[1]) : 0;
        if (!canUseFlorzenStasisProduction(game, player)
                || tile == null
                || production.length != 2
                || !tilePosition.equals(production[0])
                || produced >= player.getStasisFighters()) {
            return;
        }
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 fighter");
        player.produceUnit("ff_" + tilePosition + "_" + Constants.SPACE);
        int totalProduced = produced + 1;
        game.setStoredValue(FLORZEN_STASIS_PRODUCTION + player.getFaction(), tilePosition + "|" + totalProduced);
        var message = event.getMessage()
                .editMessage(player.getRepresentationNoPing()
                        + " has produced "
                        + totalProduced
                        + " of "
                        + player.getStasisFighters()
                        + " fighters from _Corsairs' Cove_.");
        if (totalProduced == player.getStasisFighters()) {
            message.setComponents(ButtonHelper.turnButtonListIntoActionRowList(List.of(Buttons.red(
                    player.factionButtonChecker() + "deleteButtons_florzenMonument_" + tilePosition,
                    "Done Producing Units"))));
        }
        message.queue();
    }

    public static void resolveFlorzenStasisProduction(Game game, Player player, String buttonID) {
        String[] production = game.getStoredValue(FLORZEN_STASIS_PRODUCTION + player.getFaction())
                .split("\\|", 2);
        String tilePosition = buttonID.substring(FLORZEN_STASIS_PRODUCTION.length());
        if (production.length != 2 || !tilePosition.equals(production[0])) {
            return;
        }
        int produced = Integer.parseInt(production[1]);
        player.setStasisFighters(Math.max(0, player.getStasisFighters() - produced));
        game.removeStoredValue(FLORZEN_STASIS_PRODUCTION + player.getFaction());
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                "-# "
                        + produced
                        + " fighter"
                        + (produced == 1 ? " was" : "s were")
                        + " produced for free with _Corsairs' Cove_; "
                        + player.getStasisFighters()
                        + " remain"
                        + (player.getStasisFighters() == 1 ? "s" : "")
                        + " in stasis.");
    }

    public static int getFlorzenStasisFightersInProduction(Game game, Player player, String producedUnit) {
        String[] held =
                game.getStoredValue(FLORZEN_STASIS_HELD + player.getFaction()).split("\\|", 2);
        if (held.length != 2 || !producedUnit.equals("ff_" + held[0] + "_" + Constants.SPACE)) {
            return 0;
        }
        return Integer.parseInt(held[1]);
    }

    public static void resetFlorzenStasisFighters(Game game, Player player) {
        String[] held =
                game.getStoredValue(FLORZEN_STASIS_HELD + player.getFaction()).split("\\|", 2);
        if (held.length == 2) {
            player.setStasisFighters(Math.max(0, player.getStasisFighters() - Integer.parseInt(held[1])));
        }
        game.removeStoredValue(FLORZEN_STASIS_HELD + player.getFaction());
    }

    // Hyperlane Relay
    public static List<Button> getCeldauriMonumentCommitButtons(Game game, Player player, Tile activeTile) {
        Planet monumentPlanet = MonumentsService.getPlayerMonumentPlanet(game, player);
        Tile monumentTile = MonumentsService.getPlayerMonumentTile(game, player);
        if (activeTile == null
                || monumentPlanet == null
                || monumentTile == null
                || !MonumentsService.isMonumentOnBoard(game, player, "celdauri_monument")
                || CommandCounterHelper.hasCC(player, monumentTile)) {
            return List.of();
        }

        List<UnitType> groundForces = new ArrayList<>(List.of(UnitType.Infantry, UnitType.Mech));
        if (player.hasUnlockedBreakthrough("xytherisbt") && player.hasUpgradedUnit("pds2")) {
            groundForces.add(UnitType.Pds);
        }

        List<Button> buttons = new ArrayList<>();
        for (Planet destination : activeTile.getPlanetUnitHolders()) {
            if (destination.getName().equals(monumentPlanet.getName())
                    || destination.getTokenList().stream().anyMatch(token -> token.contains(Constants.DMZ_LARGE))
                    || destination.getUnitCount(UnitType.Spacedock, player) < 1) {
                continue;
            }
            for (UnitType unitType : groundForces) {
                if (monumentPlanet.getUnitCount(unitType, player) < 1) {
                    continue;
                }
                String unitName = unitType.humanReadableName();
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + CELDAURI_MONUMENT_COMMIT + destination.getName() + "|"
                                + unitType.name(),
                        "Commit 1 " + unitName + " from "
                                + Helper.getPlanetRepresentation(monumentPlanet.getName(), game) + " to "
                                + Helper.getPlanetRepresentation(destination.getName(), game),
                        switch (unitType) {
                            case Infantry -> UnitEmojis.infantry;
                            case Mech -> UnitEmojis.mech;
                            case Pds -> UnitEmojis.pds;
                            default -> null;
                        }));
            }
        }
        return buttons;
    }

    @ButtonHandler(CELDAURI_MONUMENT_COMMIT)
    public static void resolveCeldauriMonumentCommit(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(CELDAURI_MONUMENT_COMMIT.length()).split("\\|", 2);
        Planet monumentPlanet = MonumentsService.getPlayerMonumentPlanet(game, player);
        Tile monumentTile = MonumentsService.getPlayerMonumentTile(game, player);
        Tile destinationTile = payload.length == 2 ? game.getTileFromPlanet(payload[0]) : null;
        Planet destination = destinationTile == null
                ? null
                : destinationTile.getPlanetUnitHolders().stream()
                        .filter(planet -> planet.getName().equals(payload[0]))
                        .findFirst()
                        .orElse(null);
        UnitType unitType;
        try {
            unitType = payload.length == 2 ? UnitType.valueOf(payload[1]) : null;
        } catch (IllegalArgumentException e) {
            unitType = null;
        }
        if (monumentPlanet == null
                || monumentTile == null
                || destination == null
                || unitType == null
                || !MonumentsService.isMonumentOnBoard(game, player, "celdauri_monument")
                || CommandCounterHelper.hasCC(player, monumentTile)
                || destination.getUnitCount(UnitType.Spacedock, player) < 1
                || (unitType != UnitType.Infantry
                        && unitType != UnitType.Mech
                        && (unitType != UnitType.Pds
                                || !player.hasUnlockedBreakthrough("xytherisbt")
                                || !player.hasUpgradedUnit("pds2")))
                || monumentPlanet.getUnitCount(unitType, player) < 1) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        List<RemoveUnitService.RemovedUnit> removedUnits = RemoveUnitService.removeUnits(
                event,
                monumentTile,
                game,
                player.getColor(),
                "1 " + unitType.name().toLowerCase() + " " + monumentPlanet.getName());
        AddUnitService.addUnits(
                event,
                destinationTile,
                game,
                player.getColor(),
                "1 " + unitType.name().toLowerCase() + " " + destination.getName(),
                removedUnits);
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                player.getRepresentationNoPing() + " committed 1 " + unitType.humanReadableName() + " from "
                        + Helper.getPlanetRepresentation(monumentPlanet.getName(), game) + " to "
                        + Helper.getPlanetRepresentation(destination.getName(), game) + " with _Hyperlane Relay_.");
        if (monumentPlanet.getUnitCount(unitType, player) < 1) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        }
    }

    // AI Conclave
    public static Button getAiConclaveButton(Game game, Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + USE_CONCLAVE,
                "Explore "
                        + MonumentsService.getMonumentTile(game, player, "augurs_monument")
                                .getRepresentationForButtons(game, player),
                FactionEmojis.augers);
    }

    @ButtonHandler(USE_CONCLAVE)
    public static void resolveAiConclave(ButtonInteractionEvent event, Game game, Player player) {
        if (!game.isMonumentsMode() || !MonumentsService.isMonumentOnBoard(game, player, "augurs_monument")) {
            return;
        }

        Planet monumentPlanet = MonumentsService.getPlayerMonumentPlanet(game, player);
        if (monumentPlanet == null) {
            return;
        }

        int startingTg = player.getTg();
        player.gainTG(player.getSoScored(), true);
        int gainedTg = player.getTg() - startingTg;

        List<Button> buttons = ButtonHelper.getPlanetExplorationButtons(game, monumentPlanet, player);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", you gained " + gainedTg + " because you have " + player.getSoScored() + " scored secrets."
                        + "\n-# This occurs after exploring the planet, so these trade goods cannot be used during this exploration.",
                buttons);

        ButtonHelper.deleteMessage(event);
    }
}
