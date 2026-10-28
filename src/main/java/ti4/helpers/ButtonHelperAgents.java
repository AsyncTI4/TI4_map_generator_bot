package ti4.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.base.arborec.ArborecButtonHandlers;
import ti4.discord.interactions.buttons.ids.AgentStepIds;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.SourceEmojis;
import ti4.service.emoji.TechEmojis;
import ti4.service.game.MonumentsService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.leader.RefreshLeaderService;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.modules.ArtunoAgent;
import ti4.service.leader.agent.modules.CabalAgent;
import ti4.service.leader.agent.modules.SardakkAgent;
import ti4.service.leader.agent.modules.homebrew.FlorzenAgent;
import ti4.service.leader.agent.modules.homebrew.ToldarAgent;
import ti4.service.leader.genome.homebrew.tknova.InvestmentGenome;
import ti4.service.planet.PlanetService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.ParsedUnit;
import ti4.service.unit.RemoveUnitService;

public final class ButtonHelperAgents {

    public static void cabalAgentInitiation(Game game, Player p2) {
        for (Player cabal : game.getRealPlayers()) {
            if (cabal == p2) {
                continue;
            }
            if (cabal.hasUnexhaustedLeader("cabalagent")) {
                List<Button> buttons = new ArrayList<>();
                String msg = (cabal.hasUnexhaustedLeader("yssarilagent")
                                ? cabal.getRepresentationNoPing()
                                : cabal.getRepresentationUnfogged())
                        + " you may use "
                        + AgentLifecycle.agentNameForHolder(game, cabal, "cabalagent") + ", on "
                        + p2.getFactionEmojiOrColor() + " who has "
                        + p2.getCommoditiesTotal() + " commodit" + (p2.getCommoditiesTotal() == 1 ? "y" : "ies") + ".";
                buttons.add(CabalAgent.button(cabal, p2));
                buttons.add(Buttons.red(cabal.factionButtonChecker() + "deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(cabal.getCardsInfoThread(), msg, buttons);
            }
        }
        toldarAgentInitiation(game, p2, p2.getCommoditiesTotal());
    }

    public static void toldarAgentInitiation(Game game, Player p2, int amount) {
        ToldarAgent.offerOnCommoditiesReplenished(game, p2, amount);
    }

    public static List<Button> getUnitsToArboAgent(Player player, Tile tile) {
        String factionChecker = player.factionButtonChecker();
        Set<UnitType> allowedUnits = Set.of(
                UnitType.Destroyer,
                UnitType.Cruiser,
                UnitType.Carrier,
                UnitType.Dreadnought,
                UnitType.Flagship,
                UnitType.Warsun);

        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            UnitHolder unitHolder = entry.getValue();
            Map<UnitKey, Integer> units = unitHolder.getUnits();
            if (unitHolder instanceof Planet) continue;

            Map<UnitKey, Integer> tileUnits = new HashMap<>(units);
            for (Map.Entry<UnitKey, Integer> unitEntry : tileUnits.entrySet()) {
                UnitKey unitKey = unitEntry.getKey();
                if (!player.unitBelongsToPlayer(unitKey)) continue;

                if (!allowedUnits.contains(unitKey.unitType())) {
                    continue;
                }

                UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                String prettyName = unitModel == null ? unitKey.unitType().humanReadableName() : unitModel.getName();
                String unitName = unitKey.unitName();
                int totalUnits = unitEntry.getValue();
                int damagedUnits = 0;

                if (unitHolder.getUnitDamage() != null) {
                    damagedUnits = unitHolder.getUnitDamage().getOrDefault(unitKey, 0);
                }

                for (int x = 1; x < damagedUnits + 1 && x < 2; x++) {
                    String buttonID = factionChecker + "arboAgentOn_" + tile.getPosition() + "_" + unitName + "damaged";
                    Button validTile2 = Buttons.red(buttonID, "Remove A Damaged " + prettyName, unitKey.unitEmoji());
                    buttons.add(validTile2);
                }
                totalUnits -= damagedUnits;
                for (int x = 1; x < totalUnits + 1 && x < 2; x++) {
                    Button validTile2 = Buttons.red(
                            factionChecker + "arboAgentOn_" + tile.getPosition() + "_" + unitName,
                            "Remove " + x + " " + prettyName,
                            unitKey.unitEmoji());
                    buttons.add(validTile2);
                }
            }
        }
        Button validTile2 = Buttons.red(factionChecker + "deleteButtons", "Decline");
        buttons.add(validTile2);
        return buttons;
    }

    public static List<Button> getArboAgentReplacementOptions(
            Player player, Game game, GenericInteractionCreateEvent event, Tile tile, String unit) {
        String factionChecker = player.factionButtonChecker();
        List<Button> buttons = new ArrayList<>();

        boolean damaged = false;
        if (unit.contains("damaged")) {
            unit = unit.replace("damaged", "");
            damaged = true;
        }
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColor());
        var parsedUnit = new ParsedUnit(unitKey);
        RemoveUnitService.removeUnit(event, tile, game, parsedUnit, damaged);
        String msg = (damaged ? "A damaged " : "") + unitKey.unitEmoji() + " was removed by "
                + player.getFactionEmoji()
                + ". A ship costing up to 2 more than it may now be placed.";

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);

        List<String> allowedUnits = Stream.of(
                        UnitType.Destroyer,
                        UnitType.Cruiser,
                        UnitType.Carrier,
                        UnitType.Dreadnought,
                        UnitType.Flagship,
                        UnitType.Warsun,
                        UnitType.Fighter)
                .map(UnitType::getValue)
                .toList();
        UnitModel removedUnit = player.getUnitsByAsyncID(unitKey.asyncID()).getFirst();
        for (String asyncID : allowedUnits) {
            UnitModel ownedUnit = player.getUnitFromAsyncID(asyncID);
            if (ownedUnit != null && ownedUnit.getCost() <= removedUnit.getCost() + 2) {
                String buttonID =
                        factionChecker + "arboAgentPutShip_" + ownedUnit.getBaseType() + "_" + tile.getPosition();
                String buttonText = "Place " + ownedUnit.getName();
                buttons.add(Buttons.green(buttonID, buttonText, ownedUnit.getUnitEmoji()));
            }
        }

        return buttons;
    }

    @ButtonHandler("spendStratNReadyAgent_")
    public static void resolveAbsolHyperAgentReady(
            String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String agent = StringUtils.substringAfterLast(buttonID, "_");
        player.setStrategicCC(player.getStrategicCC() - 1);
        ButtonHelperCommanders.resolveMuaatCommanderCheck(
                player, game, event, TechEmojis.BioticTech + "Hypermetabolism" + SourceEmojis.Absol);
        ButtonHelper.deleteMessage(event);
        Leader playerLeader = player.getLeader(agent).orElse(null);
        if (playerLeader == null) {
            if (agent.contains("titanprototype")) {
                player.removeExhaustedRelic("titanprototype");
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getFactionEmoji() + " exhausted "
                                + "_Hypermetabolism_ and spent a command token from strategy pool to ready " + agent
                                + ".");
            }
            if (agent.contains("absol")) {
                player.removeExhaustedRelic("absol_jr");
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getFactionEmoji() + " exhausted "
                                + "_Hypermetabolism_ and spent a command token from strategy pool to ready " + agent
                                + ".");
            }
            return;
        }
        RefreshLeaderService.refreshLeader(player, playerLeader, game);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " exhausted "
                        + "_Hypermetabolism_ and spent a command token from their strategy pool to ready " + agent
                        + ".");
    }

    @ButtonHandler("belkoseaYellowTechReady_")
    public static void belkoseaYellowTechReady(
            String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasTech("dsbelky") || !player.getExhaustedTechs().contains("dsbelky")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        buttonID = buttonID.replace("belkoseaYellowTechReady_", "");
        String thing = buttonID.split("_")[0];
        String detail = buttonID.replace(thing + "_", "");
        String msg = player.getFactionEmoji() + " exhausted _Synchrony Matrix_ to ready " + detail + ".";
        if ("leader".equalsIgnoreCase(thing)) {
            Leader playerLeader = player.getLeader(detail).orElse(null);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            if (playerLeader == null) {
                return;
            }
            RefreshLeaderService.refreshLeader(player, playerLeader, game);
        } else if ("monument".equalsIgnoreCase(thing)) {
            if (!MonumentsService.readyMonument(game, player, detail)) {
                return;
            }
            UnitModel monument = Mapper.getUnit(detail);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getFactionEmoji() + " exhausted _Synchrony Matrix_ to ready "
                            + (monument == null ? detail : monument.getName() + " Monument") + ".");
        } else {
            if ("planet".equalsIgnoreCase(thing)) {
                player.refreshPlanet(detail);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            } else if ("ability".equalsIgnoreCase(thing)) {
                player.removeExhaustedAbility(detail);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            } else if ("breakthrough".equalsIgnoreCase(thing)) {
                if (player.isBreakthroughUnlocked(detail) && player.isBreakthroughExhausted(detail)) {
                    player.setBreakthroughExhausted(detail, false);
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
                }
            } else {
                player.removeExhaustedRelic(detail);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            }
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("umbatTile_")
    public static void umbatTile(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String pos = buttonID.replace("umbatTile_", "");
        List<Button> buttons;
        buttons = Helper.getPlaceUnitButtons(event, player, game, game.getTileByPosition(pos), "muaatagent", "place");
        String message = player.getRepresentation() + " Use the buttons to produce units. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("pharadnAgentKill_")
    public static void pharadnAgentKill(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String planet = buttonID.replace("pharadnAgentKill_", "");

        String message = player.getRepresentation() + " chose to destroy all infantry on "
                + Helper.getPlanetRepresentation(planet, game);
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        int amountToKill = uH.getUnitCount(UnitType.Infantry, player.getColor());

        int comms = Math.min(player.getCommoditiesTotal(), amountToKill);
        message += ". " + amountToKill + " infantry were destroyed. " + comms + " commodit" + (comms == 1 ? "y" : "ies")
                + " were gained, and then all commodities were converted into trade goods.";
        player.setTg(player.getTg() + Math.min(player.getCommoditiesTotal(), player.getCommodities() + amountToKill));
        player.setCommodities(0);
        if (amountToKill > 0) {
            ButtonHelper.resolveInfantryDestroy(player, amountToKill, game.getTileFromPlanet(planet));
            RemoveUnitService.removeUnits(
                    event, game.getTileFromPlanet(planet), game, player.getColor(), amountToKill + " inf " + planet);
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("mercerMove_")
    public static void resolveMercerMove(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String planetDestination = buttonID.split("_")[1];
        Tile tileDestination = game.getTileFromPlanet(planetDestination);
        String pos = buttonID.split("_")[2];
        Tile tileRemoval = game.getTileByPosition(pos);
        String planetRemoval = buttonID.split("_")[3];
        String unit = buttonID.split("_")[4];
        UnitHolder uH = tileRemoval.getUnitHolders().get(planetRemoval);
        String message;
        String removalLocation = planetRemoval;
        if ("space".equalsIgnoreCase(planetRemoval)) {
            message = player.getFactionEmojiOrColor() + " moved 1 " + unit + " from the space area of "
                    + tileRemoval.getRepresentation() + " to "
                    + Helper.getPlanetRepresentation(planetDestination, game);
            removalLocation = "";
        } else {
            message = player.getFactionEmojiOrColor() + " moved 1 " + unit + " from "
                    + Helper.getPlanetRepresentation(planetRemoval, game) + " to "
                    + Helper.getPlanetRepresentation(planetDestination, game);
        }

        List<RemoveUnitService.RemovedUnit> removedUnits = RemoveUnitService.removeUnits(
                event, tileRemoval, game, player.getColor(), unit + " " + removalLocation);
        if (buttonID.contains("lizhobt")) {
            ButtonHelper.deleteMessage(event);
            message += " into coexistence. They used _Professional Intrigue_ to do this";
            game.setStoredValue("coexistFlag", "yes");
        }
        AddUnitService.addUnits(
                event, tileDestination, game, player.getColor(), unit + " " + planetDestination, removedUnits);
        game.removeStoredValue("coexistFlag");
        if ("mech".equalsIgnoreCase(unit)) {
            if (uH.getUnitCount(UnitType.Mech, player.getColor()) < 1) {
                ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            }
            CommanderUnlockCheckService.checkPlayer(player, "naaz");
        } else {
            if ("pds".equalsIgnoreCase(unit)) {
                if (uH.getUnitCount(UnitType.Pds, player.getColor()) < 1) {
                    ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
                }
            } else if (uH.getUnitCount(UnitType.Infantry, player.getColor()) < 1) {
                ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            }
        }
        if (tileDestination != null && tileDestination.isFracture()) {
            CommanderUnlockCheckService.checkPlayer(player, "obsidian");
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), message + ".");
    }

    @ButtonHandler("redcreussAgentPart1_")
    public static void redcreussAgentPart1(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        String message =
                ", please choose the second system that you wish to swap a ship between (and transport). The first system is position "
                        + pos + ".";
        List<Button> buttons = new ArrayList<>();
        List<Tile> tiles = ButtonHelper.getTilesWithShipsInTheSystem(player, game);
        for (Tile tile : tiles) {
            if (tile.getPosition().equalsIgnoreCase(pos)) {
                continue;
            }
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + "redcreussAgentPart2_" + pos + "_" + tile.getPosition(),
                    tile.getRepresentationForButtons()));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), player.getRepresentationUnfogged() + message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("redcreussAgentPart2_")
    public static void redcreussAgentPart2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        String pos2 = buttonID.split("_")[2];
        Tile tile2 = game.getTileByPosition(pos2);
        String message = ", please choose the units you wish to move from " + tile.getRepresentation() + " to "
                + tile2.getRepresentation();
        String message2 = ", please choose the units you wish to move from " + tile2.getRepresentation() + " to "
                + tile.getRepresentation();
        List<Button> buttons = ButtonHelperHeroes.getArgentHeroStep3Buttons(game, player, "spoof_" + pos2 + "_" + pos);
        List<Button> buttons2 = ButtonHelperHeroes.getArgentHeroStep3Buttons(game, player, "spoof_" + pos + "_" + pos2);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), player.getRepresentationUnfogged() + message, buttons);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), player.getRepresentationUnfogged() + message2, buttons2);
        ButtonHelper.deleteMessage(event);
    }

    public static boolean doesTileHaveAStructureInIt(Player player, Tile tile) {
        boolean present = false;
        for (UnitHolder uH : tile.getUnitHolders().values()) {
            if (uH.getUnitCount(UnitType.Spacedock, player.getColor()) > 0
                    || uH.getUnitCount(UnitType.Pds, player.getColor()) > 0
                    || uH.getUnitCount(UnitType.Monument, player.getColor()) > 0) {
                return true;
            }
            if (player.hasAbility("byssus")
                    && uH instanceof Planet
                    && uH.getUnitCount(UnitType.Mech, player.getColor()) > 0) {
                return true;
            }
            for (String token : uH.getTokenList()) {
                if (player.getPlanets().contains(uH.getName()) && token.contains("superweapon")) {
                    return true;
                }
            }
        }
        return present;
    }

    public static List<Tile> getAdjacentTilesWithStructuresInThem(Player player, Game game, Tile origTile) {
        List<Tile> tiles = new ArrayList<>();
        List<String> adjTiles =
                new ArrayList<>(FoWHelper.getAdjacentTiles(game, origTile.getPosition(), player, false));
        for (String posTile : adjTiles) {
            Tile adjTile = game.getTileByPosition(posTile);
            if (adjTile != null && doesTileHaveAStructureInIt(player, adjTile)) {
                tiles.add(adjTile);
            }
        }
        return tiles;
    }

    @ButtonHandler("refreshWithOlradinAgent_")
    public static void resolveRefreshWithOlradinAgent(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        AgentStepIds.Parsed step = AgentStepIds.parse(buttonID);
        String planetName = StringUtils.substringAfter(step.id(), "_");
        PlanetService.refreshPlanet(player, planetName);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getFactionEmoji() + " readied " + Helper.getPlanetRepresentation(planetName, game) + " with "
                        + AgentLifecycle.stepAgentName(game, player, "olradinagent", step.viaYssaril())
                        + ".");
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("florzenAgentStep3_")
    public static void resolveFlorzenAgentStep3(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        String planet2 = buttonID.split("_")[2];
        String attachment = buttonID.replace("florzenAgentStep3_" + planet + "_" + planet2 + "_", "");
        if (!FlorzenAgent.moveAttachment(game, planet, planet2, attachment)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "That attachment is no longer there, please resolve manually.");
            return;
        }
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getFactionEmojiOrColor() + " removed " + attachment.replace(";", "_") + " from "
                        + Helper.getPlanetRepresentation(planet, game) + " and put it on "
                        + Helper.getPlanetRepresentation(planet2, game) + " using Florzen powers.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("ghotiATG")
    public static void ghotiAgentForTg(ButtonInteractionEvent event, Game game, Player player) {
        int cTG = player.getTg();
        int fTG = cTG + 1;
        player.setTg(fTG);
        ButtonHelperAbilities.pillageCheck(player, game);
        resolveArtunoCheck(player, 1);
        String msg = player.getRepresentation() + " Used "
                + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Becece, the Ghoti"
                + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent, to gain 1 trade good ("
                + cTG + "->" + fTG + "). ";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("ghotiAProd")
    public static void ghotiAgentForProduction(Game game, ButtonInteractionEvent event, Player player) {
        String msg = "Used " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                + "Becece, the Ghoti" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                + " agent, to gain the ability to produce 2 more units. ";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        Map<String, Integer> producedUnits = player.getCurrentProducedUnits();
        List<String> uniquePlaces = new ArrayList<>();
        int count = 0;
        String tilePos = "";
        for (String unit : producedUnits.keySet()) {
            tilePos = unit.split("_")[1];
            String planetOrSpace = unit.split("_")[2];
            if (!uniquePlaces.contains(tilePos + "_" + planetOrSpace)) {
                uniquePlaces.add(tilePos + "_" + planetOrSpace);
            }
            count++;
        }
        if (count == 1) {
            ArborecButtonHandlers.resolveArboHeroBuild(game, player, event, "arboHeroBuild_" + tilePos);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("moveShipToAdjacentSystemStep1")
    public static void moveShipToAdjacentSystemStep1(Game game, Player player, ButtonInteractionEvent event) {
        String msg =
                player.getRepresentationUnfogged() + ", please choose the system that you wish to move a ship from.";
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerHasShipsInSystem(player, tile)) {
                buttons.add(Buttons.green(
                        "moveShipToAdjacentSystemStep2_" + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        if (event != null) {
            ButtonHelper.deleteMessage(event);
        }
    }

    @ButtonHandler("moveShipToAdjacentSystemStep2_")
    public static void moveShipToAdjacentSystemStep2(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String msg = player.getRepresentationUnfogged()
                + ", please choose the adjacent system you wish to move the ship to.";
        List<Button> buttons = new ArrayList<>();
        String ogTile = buttonID.split("_")[1];
        for (String pos : FoWHelper.getAdjacentTilesAndNotThisTile(game, ogTile, player, false)) {
            Tile tile = game.getTileByPosition(pos);
            if (tile.getTileModel() != null && tile.getTileModel().isHyperlane()) continue;

            if ((tile.isAsteroidField()
                            && !tile.isZelianAsteroidField()
                            && !player.getTechs().contains("amd")
                            && !player.getTechs().contains("wavelength")
                            && !player.getRelics().contains("circletofthevoid"))
                    || (tile.isSupernova()
                            && !player.hasTech("mr")
                            && !player.getRelics().contains("circletofthevoid"))
                    || (!buttonID.contains("combat") && FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game))) {
                continue;
            }
            String og = ogTile;
            if (buttonID.contains("agent") && game.getTileByPosition(ogTile).hasPlayerCC(player)) {
                og += "_agent";
            }

            buttons.add(Buttons.green(
                    "fogAllianceAgentStep3_" + tile.getPosition() + "_" + og,
                    tile.getRepresentationForButtons(game, player)));
        }
        if (buttonID.contains("combat")) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        } else {
            if (!buttonID.contains("_hero")) {
                ButtonHelper.deleteMessage(event);
            }
        }

        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    @ButtonHandler("moveGloryStart_")
    private static void offerMoveGloryOptions(Game game, Player player, GenericInteractionCreateEvent event) {
        String msg = player.getRepresentationUnfogged() + " please choose the system to move a **Glory** token from.";
        Tile tileAS = game.getTileByPosition(game.getActiveSystem());
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), msg, moveGloryButtons(game, player, tileAS));
    }

    public static List<Button> moveGloryButtons(Game game, Player player, Tile destination) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : getGloryTokenTiles(game)) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "moveGlory_" + tile.getPosition() + "_" + destination.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
        }
        return buttons;
    }

    @ButtonHandler("moveGlory_")
    public static void moveGlory(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        Tile tileAS = game.getTileByPosition(buttonID.split("_")[2]);
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        UnitHolder spaceAS = tileAS.getUnitHolders().get(Constants.SPACE);
        String tokenToMove = "";
        for (String token : space.getTokenList()) {
            if (token.contains("glory")) {
                tokenToMove = token;
            }
        }
        if (space.getTokenList().contains(tokenToMove)) {
            space.removeToken(tokenToMove);
        }
        spaceAS.addToken(tokenToMove);
        String msg = player.getFactionEmoji() + " moved a **Glory** token from " + tile.getRepresentation() + " to "
                + tileAS.getRepresentation();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("placeGlory_")
    public static void placeGlory(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        List<String> gloryTokens = getGloryTokensLeft(game);
        if (gloryTokens.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " cannot place more **Glory** tokens, you've hit the limit.");
            return;
        }
        space.addToken(gloryTokens.getFirst());

        String msg = player.getFactionEmoji() + " added a **Glory** token to " + tile.getRepresentation();
        ButtonHelperAbilities.oceanBoundCheck(game);
        CommanderUnlockCheckService.checkPlayer(player, "kjalengard");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    public static List<String> getGloryTokensLeft(Game game) {
        List<String> gloryTokens = new ArrayList<>();
        gloryTokens.add("token_ds_glory.png");
        gloryTokens.add("token_ds_glory2.png");
        gloryTokens.add("token_ds_glory3.png");
        for (Tile tile : game.getTileMap().values()) {
            List<String> gloryTokens2 = new ArrayList<>(gloryTokens);
            for (String glory : gloryTokens2) {
                if (tile.getUnitHolders().get("space").getTokenList().contains(glory)) {
                    gloryTokens.remove(glory);
                }
            }
        }
        return gloryTokens;
    }

    public static List<Tile> getGloryTokenTiles(Game game) {
        List<Tile> gloryTiles = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.getUnitHolders().get("space").getTokenList().contains("token_ds_glory.png")
                    || tile.getUnitHolders().get("space").getTokenList().contains("token_ds_glory2.png")
                    || tile.getUnitHolders().get("space").getTokenList().contains("token_ds_glory3.png")) {
                gloryTiles.add(tile);
            }
        }
        return gloryTiles;
    }

    public static List<Button> getSardakkAgentButtons(Game game, Player player) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        List<Button> buttons = new ArrayList<>();
        for (Planet planet : tile.getPlanetUnitHolders()) {
            String planetId = planet.getName();
            if (!player.getPlanetsAllianceMode().contains(planetId)) continue;
            buttons.add(SardakkAgent.button(
                    player, game.getActiveSystem(), planetId, Helper.getPlanetRepresentation(planetId, game)));
        }
        return buttons;
    }

    public static List<Button> getTFAwakenButtons(Game game, Tile tile, Player player) {
        List<Button> buttons = new ArrayList<>();
        if (tile == null) return buttons;

        for (Planet planet : tile.getPlanetUnitHolders()) {
            String planetId = planet.getName();
            if (player.getPlanetsAllianceMode().contains(planetId)
                    && FoWHelper.playerHasInfantryOnPlanet(player, tile, planetId)) {
                String planetRepresentation = Helper.getPlanetRepresentation(planetId, game);
                String buttonID = "tfawaken_" + game.getActiveSystem() + "_" + planetId;
                buttons.add(Buttons.green(buttonID, "Use Awaken on " + planetRepresentation, FactionEmojis.Titans));
            }
        }
        return buttons;
    }

    @ButtonHandler("step2axisagent_")
    public static void resolveStep2OfAxisAgent(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        String message;
        if ("cruiser".equalsIgnoreCase(buttonID.split("_")[1])) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    player.getFactionEmoji() + " Chose to place 1 cruiser with their ships from "
                            + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                            + "Shipmonger Zsknck, the Axis"
                            + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.");
            buttons.addAll(Helper.getTileWithShipsPlaceUnitButtons(player, game, "cruiser", "placeOneNDone_skipbuild"));
            message = player.getRepresentationUnfogged()
                    + ", please choose the system containing your ships where you wish to place a cruiser.";
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    player.getFactionEmoji() + " Chose to place 1 destroyer with their ships from "
                            + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                            + "Shipmonger Zsknck, the Axis"
                            + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.");
            buttons.addAll(
                    Helper.getTileWithShipsPlaceUnitButtons(player, game, "destroyer", "placeOneNDone_skipbuild"));
            message = player.getRepresentationUnfogged()
                    + ", please choose the system containing your ships where you wish to place a destroyer.";
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveArtunoCheck(Player player, int tg) {
        InvestmentGenome.offerTradeGoodsGained(player.getGame(), player, tg);
        if (player.hasUnexhaustedLeader("nomadagentartuno")) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(ArtunoAgent.button(player, tg));
            buttons.add(Buttons.DECLINE_DELETE_BUTTONS);
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + " you have the opportunity to exhaust "
                            + AgentLifecycle.agentNameForHolder(player.getGame(), player, "nomadagentartuno")
                            + ", and place " + StringHelper.pluralize(tg, "trade good") + " on her.",
                    buttons);
        }
    }

    @ButtonHandler("jolNarAgentRemoval_")
    public static void resolveJolNarAgentRemoval(
            Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String pos = buttonID.split("_")[1];
        String unitHName = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(pos);
        UnitHolder unitHolder = tile.getUnitHolders().get(unitHName);
        if ("space".equalsIgnoreCase(unitHName)) {
            unitHName = "";
        }
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                player.getFactionEmoji() + " removed 1 infantry from "
                        + ButtonHelper.getUnitHolderRep(unitHolder, tile, game) + " using "
                        + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                        + "Doctor Sucaban, the Jol-Nar"
                        + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.");
        RemoveUnitService.removeUnits(event, tile, game, player.getColor(), "1 infantry " + unitHName);
        ButtonHelper.resolveInfantryRemoval(player, 1, tile);
        if (unitHolder.getUnitCount(UnitType.Infantry, player.getColor()) < 1) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        }
    }
}
