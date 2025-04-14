package ti4.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.tokens.AddTokenCommand;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.fow.FOWPlusService;
import ti4.service.fow.RiftSetModeService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.turn.StartTurnService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.ParsedUnit;
import ti4.service.unit.RemoveUnitService;
import ti4.settings.users.UserSettingsManager;

public class ButtonHelperTacticalAction {

    @ButtonHandler("unitTactical")
    public static void movingUnitsInTacticalAction(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String buttonLabel = event.getButton().getLabel();
        String remove = "Move";
        Map<String, Integer> currentSystem = game.getCurrentMovedUnitsFrom1System();
        Map<String, Integer> currentActivation = game.getMovedUnitsFromCurrentActivation();
        String rest;
        if (buttonID.contains("Remove")) {
            remove = "Remove";
            rest = buttonID.replace("unitTacticalRemove_", "").toLowerCase();
        } else {
            rest = buttonID.replace("unitTacticalMove_", "").toLowerCase();
        }
        String pos = rest.substring(0, rest.indexOf("_"));
        Tile tile = game.getTileByPosition(pos);
        rest = rest.replace(pos + "_", "");

        if (rest.contains("reverseall") || rest.contains("moveall")) {
            if (rest.contains("reverse")) {
                for (String unit : currentSystem.keySet()) {

                    String unitkey;
                    String planet = "";
                    String damagedMsg = "";
                    int amount = currentSystem.get(unit);
                    if (unit.contains("_")) {
                        unitkey = unit.split("_")[0];
                        planet = unit.split("_")[1];
                    } else {
                        unitkey = unit;
                    }
                    if (currentActivation.containsKey(unitkey)) {
                        game.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitkey,
                            currentActivation.get(unitkey) - amount);
                    }
                    if (unitkey.contains("damaged")) {
                        unitkey = unitkey.replace("damaged", "");
                        damagedMsg = " damaged ";
                    }
                    AddUnitService.addUnits(event, game.getTileByPosition(pos), game, player.getColor(),
                        (amount) + " " + unitkey + " " + planet);
                    if (damagedMsg.contains("damaged")) {
                        if ("".equalsIgnoreCase(planet)) {
                            planet = "space";
                        }
                        UnitKey unitID = Mapper.getUnitKey(AliasHandler.resolveUnit(unitkey), player.getColor());
                        game.getTileByPosition(pos).addUnitDamage(planet, unitID, (amount));
                    }
                }

                game.resetCurrentMovedUnitsFrom1System();
            } else {
                for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
                    UnitHolder unitHolder = entry.getValue();
                    Map<UnitKey, Integer> units1 = unitHolder.getUnits();
                    Map<UnitKey, Integer> units = new HashMap<>(units1);

                    if (unitHolder instanceof Planet) {
                        for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                            if (!player.unitBelongsToPlayer(unitEntry.getKey()))
                                continue;
                            UnitKey unitKey = unitEntry.getKey();
                            if (unitKey.getUnitType() == UnitType.Infantry || unitKey.getUnitType() == UnitType.Mech) {
                                String unitName = unitKey.unitName();
                                int amount = unitEntry.getValue();
                                int totalUnits = amount;
                                int damagedUnits = 0;
                                if (unitHolder.getUnitDamage() != null
                                    && unitHolder.getUnitDamage().get(unitKey) != null) {
                                    damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                                }
                                if (damagedUnits > 0) {
                                    amount = damagedUnits;
                                    unitName = unitName.toLowerCase() + "damaged";
                                    rest = unitName + "_" + unitHolder.getName().toLowerCase();

                                    if (currentSystem.containsKey(rest)) {
                                        game.setSpecificCurrentMovedUnitsFrom1System(rest,
                                            currentSystem.get(rest) + amount);
                                    } else {
                                        game.setSpecificCurrentMovedUnitsFrom1System(rest, amount);
                                    }
                                    if (currentActivation.containsKey(unitName)) {
                                        game.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitName,
                                            currentActivation.get(unitName) + amount);
                                    } else {
                                        game.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitName, amount);
                                    }
                                }
                                amount = totalUnits - damagedUnits;
                                if (amount > 0) {
                                    rest = unitName.toLowerCase() + "_" + unitHolder.getName().toLowerCase();
                                    if (currentSystem.containsKey(rest)) {
                                        game.setSpecificCurrentMovedUnitsFrom1System(rest,
                                            currentSystem.get(rest) + amount);
                                    } else {
                                        game.setSpecificCurrentMovedUnitsFrom1System(rest, amount);
                                    }
                                    if (currentActivation.containsKey(unitName)) {
                                        game.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitName,
                                            currentActivation.get(unitName) + amount);
                                    } else {
                                        game.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitName, amount);
                                    }
                                }

                                var unitParsed = new ParsedUnit(unitKey, unitEntry.getValue(), unitHolder.getName());
                                RemoveUnitService.removeUnit(event, game.getTileByPosition(pos), game, unitParsed);
                            }
                        }
                    } else {
                        for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                            if (!player.unitBelongsToPlayer(unitEntry.getKey()))
                                continue;
                            UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                            if (unitModel == null)
                                continue;

                            UnitKey unitKey = unitEntry.getKey();
                            String unitName = unitKey.unitName();
                            int totalUnits = unitEntry.getValue();
                            int amount;

                            int damagedUnits = 0;
                            if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                                damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                            }

                            var unit = new ParsedUnit(unitKey, totalUnits, Constants.SPACE);
                            RemoveUnitService.removeUnit(event, game.getTileByPosition(pos), game, unit);
                            if (damagedUnits > 0) {
                                rest = unitName + "damaged";
                                amount = damagedUnits;
                                if (currentSystem.containsKey(rest)) {
                                    game.setSpecificCurrentMovedUnitsFrom1System(rest,
                                        currentSystem.get(rest) + amount);
                                } else {
                                    game.setSpecificCurrentMovedUnitsFrom1System(rest, amount);
                                }
                                if (currentActivation.containsKey(rest)) {
                                    game.setSpecificCurrentMovedUnitsFrom1TacticalAction(rest,
                                        currentActivation.get(rest) + amount);
                                } else {
                                    game.setSpecificCurrentMovedUnitsFrom1TacticalAction(rest, amount);
                                }
                            }
                            rest = unitName;
                            amount = totalUnits - damagedUnits;
                            if (amount > 0) {
                                if (currentSystem.containsKey(rest)) {
                                    game.setSpecificCurrentMovedUnitsFrom1System(rest,
                                        currentSystem.get(rest) + amount);
                                } else {
                                    game.setSpecificCurrentMovedUnitsFrom1System(rest, amount);
                                }
                                if (currentActivation.containsKey(unitName)) {
                                    game.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitName,
                                        currentActivation.get(unitName) + amount);
                                } else {
                                    game.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitName, amount);
                                }
                            }
                        }
                    }
                }
            }
            String message = ButtonHelper.buildMessageFromDisplacedUnits(game, false, player, remove, tile);
            List<Button> systemButtons = getButtonsForAllUnitsInSystem(player, game, game.getTileByPosition(pos), remove);
            MessageHelper.editMessageWithButtons(event, message, systemButtons);
            return;
        }
        int amount = Integer.parseInt(rest.charAt(0) + "");
        if (rest.contains("_reverse")) {
            amount *= -1;
            rest = rest.replace("_reverse", "");
        }
        rest = rest.substring(1);
        String unitName;
        String planet = "";

        if (rest.contains("_")) {
            unitName = rest.split("_")[0];
            planet = rest.split("_")[1].toLowerCase().replace(" ", "");
        } else {
            unitName = rest;
        }
        unitName = unitName.replace("damaged", "");
        planet = planet.replace("damaged", "");
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unitName), player.getColor());
        rest = rest.replace("damaged", "");
        if (amount < 0) {
            AddUnitService.addUnits(event, game.getTileByPosition(pos), game, player.getColor(),
                (amount * -1) + " " + unitName + " " + planet);
            if (buttonLabel.toLowerCase().contains("damaged")) {
                if ("".equalsIgnoreCase(planet)) {
                    planet = "space";
                }
                game.getTileByPosition(pos).addUnitDamage(planet, unitKey, (amount * -1));
            }
        } else {
            String planetName;
            if ("".equalsIgnoreCase(planet)) {
                planetName = "space";
            } else {
                planetName = planet.replace("'", "");
                planetName = AliasHandler.resolvePlanet(planetName);
            }

            var unitParsed = new ParsedUnit(unitKey, amount, planetName);
            RemoveUnitService.removeUnit(event, game.getTileByPosition(pos), game, unitParsed, buttonLabel.toLowerCase().contains("damaged"));
        }
        if (buttonLabel.toLowerCase().contains("damaged")) {
            unitName += "damaged";
            rest += "damaged";
        }
        if (currentSystem.containsKey(rest)) {
            game.setSpecificCurrentMovedUnitsFrom1System(rest, currentSystem.get(rest) + amount);
        } else {
            game.setSpecificCurrentMovedUnitsFrom1System(rest, amount);
        }
        if (currentSystem.containsKey(rest) && currentSystem.get(rest) == 0) {
            currentSystem.remove(rest);
        }
        if (currentActivation.containsKey(unitName)) {
            game.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitName,
                currentActivation.get(unitName) + amount);
        } else {
            game.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitName, amount);
        }
        if (currentActivation.containsKey(rest) && currentActivation.get(rest) == 0) {
            currentActivation.remove(rest);
        }
        String message = ButtonHelper.buildMessageFromDisplacedUnits(game, false, player, remove, tile);
        List<Button> systemButtons = getButtonsForAllUnitsInSystem(player, game, game.getTileByPosition(pos), remove);
        MessageHelper.editMessageWithButtons(event, message, systemButtons);
    }

    @ButtonHandler("doneWithTacticalAction")
    public static void concludeTacticalAction(Player player, Game game, ButtonInteractionEvent event) {
        if (!game.isL1Hero()) {
            RiftSetModeService.concludeTacticalAction(player, game, event);
            ButtonHelper.exploreDET(player, game, event);
            ButtonHelperFactionSpecific.cleanCavUp(game, event);
            if (player.hasAbility("cunning")) {
                List<Button> trapButtons = new ArrayList<>();
                for (UnitHolder uH : game.getTileByPosition(game.getActiveSystem()).getUnitHolders()
                    .values()) {
                    if (uH instanceof Planet) {
                        String planet = uH.getName();
                        trapButtons.add(Buttons.gray("setTrapStep3_" + planet,
                            Helper.getPlanetRepresentation(planet, game)));
                    }
                }
                trapButtons.add(Buttons.red("deleteButtons", "Decline"));
                String msg = player.getRepresentationUnfogged()
                    + " you can use the buttons to place a trap on a planet";
                if (trapButtons.size() > 1) {
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                        msg, trapButtons);
                }
            }
            if (player.hasUnexhaustedLeader("celdauriagent")) {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.gray("exhaustAgent_celdauriagent_" + player.getFaction(), "Use Celdauri Agent", FactionEmojis.celdauri));
                buttons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                        + " you may use " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                        + "George Nobin, the Celdauri" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent to place 1 space dock for 2 trade goods or 2 commodities",
                    buttons);
            }
        }

        if (!game.isAbsolMode() && player.getRelics().contains("emphidia")
            && !player.getExhaustedRelics().contains("emphidia")) {
            String message = player.getRepresentation() + ", you may use the button to explore a planet using _The Crown of Emphidia_.";
            List<Button> systemButtons2 = new ArrayList<>();
            systemButtons2.add(Buttons.green("crownofemphidiaexplore", "Use Crown of Emphidia To Explore"));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons2);
        }
        if (game.isNaaluAgent()) {
            player = game.getPlayer(game.getActivePlayerID());
            game.setNaaluAgent(false);
        }
        game.setStoredValue("tnelisCommanderTracker", "");
        game.setL1Hero(false);
        game.setStoredValue("vaylerianHeroActive", "");
        String message = player.getRepresentationUnfogged() + ", use buttons to end turn, or do another action.";
        List<Button> systemButtons = StartTurnService.getStartOfTurnButtons(player, game, true, event);
        MessageChannel channel = event.getMessageChannel();
        if (game.isFowMode()) {
            channel = player.getPrivateChannel();
        }
        MessageHelper.sendMessageToChannelWithButtons(channel, message, systemButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("tacticalActionBuild_")
    public static void buildWithTacticalAction(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.replace("tacticalActionBuild_", "");
        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game, game.getTileByPosition(pos),
            "tacticalAction", "place");
        String message = player.getRepresentation() + " Use the buttons to produce units. ";
        String message3 = "You have "
            + Helper.getProductionValue(player, game, game.getTileByPosition(pos), false)
            + " PRODUCTION value in this system.\n";
        if (Helper.getProductionValue(player, game, game.getTileByPosition(pos), false) > 0
            && game.playerHasLeaderUnlockedOrAlliance(player, "cabalcommander")) {
            message3 = message3
                + "You also have That Which Molds Flesh, the Vuil'raith commander, which allows you to produce 2 " + UnitEmojis.fighter + "/" + UnitEmojis.infantry + " that don't count towards PRODUCTION limit.\n";
        }
        if (Helper.getProductionValue(player, game, game.getTileByPosition(pos), false) > 0
            && ButtonHelper.isPlayerElected(game, player, "prophecy")) {
            message3 = message3
                + "Reminder that you have _Prophecy of Ixth_ and should produce at least 2 fighters if you wish to keep it. Its removal is not automated.\n";
        }
        MessageHelper.sendMessageToChannel(event.getChannel(),
            message3 + ButtonHelper.getListOfStuffAvailableToSpend(player, game, true));
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("concludeMove")
    public static void finishMovingForTacticalAction(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String message = "Moved all units to the space area.";

        String position = buttonID.contains("_") ? buttonID.split("_")[1] : game.getActiveSystem();
        Tile tile = game.getTileByPosition(position);
        if (FOWPlusService.isVoid(game, position)) {
            FOWPlusService.resolveVoidActivation(player, game);
            message = "All units were lost.";
        }
        List<Player> playersWithPds2 = ButtonHelper.tileHasPDS2Cover(player, game, tile.getPosition());

        List<Button> systemButtons;
        boolean needPDSCheck = false;
        if (game.getMovedUnitsFromCurrentActivation().isEmpty()
            && !game.playerHasLeaderUnlockedOrAlliance(player, "sardakkcommander")
            && tile.getUnitHolders().get("space").getUnitCount(UnitType.Infantry, player) < 1
            && tile.getUnitHolders().get("space").getUnitCount(UnitType.Mech, player) < 1
            && !FOWPlusService.isVoid(game, position)) {
            message = "Nothing moved. Use buttons to decide if you wish to build (if you can), or finish the activation.";
            systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, game, event);
            needPDSCheck = true;
            systemButtons = ButtonHelper.landAndGetBuildButtons(player, game, event, tile);
        } else {
            if (!game.getMovedUnitsFromCurrentActivation().isEmpty()) {
                ButtonHelper.resolveEmpyCommanderCheck(player, game, tile, event);
                ButtonHelper.sendEBSWarning(player, game, tile.getPosition());
                ButtonHelper.checkForIonStorm(tile, player);
                for (Player nonActivePlayer : game.getRealPlayers()) {
                    if (player == nonActivePlayer) {
                        continue;
                    }
                    if (nonActivePlayer.getTechs().contains("vw")
                        && FoWHelper.playerHasUnitsInSystem(nonActivePlayer, tile)) {

                        if (game.isFowMode()) {
                            MessageHelper.sendMessageToChannel(nonActivePlayer.getCorrectChannel(),
                                nonActivePlayer.getRepresentation() + ", your _Voidwatch_ has been triggered.");
                        }
                        List<Button> stuffToTransButtons = ButtonHelper.getForcedPNSendButtons(game, nonActivePlayer, player);
                        String message2 = player.getRepresentationUnfogged()
                            + ", you have triggered _Voidwatch_. Please select the promissory note you will send.";
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message2,
                            stuffToTransButtons);
                        if (game.isFowMode()) {
                            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation()
                                + ", you owe a promissory note to the player with units here.");
                        } else {
                            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation()
                                + ", you owe a promissory note to " + nonActivePlayer.getRepresentation() + " from triggering _Voidwatch_.");
                        }
                    }
                }
            }
            List<Button> empyButtons = new ArrayList<>();
            if (!game.getMovedUnitsFromCurrentActivation().isEmpty()
                && (tile.getUnitHolders().size() == 1) && player.hasUnexhaustedLeader("empyreanagent")) {
                Button empyButton = Buttons.gray("exhaustAgent_empyreanagent", "Use Empyrean Agent", FactionEmojis.Empyrean);
                empyButtons.add(empyButton);
                empyButtons.add(Buttons.red("deleteButtons", "Delete These Buttons"));
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                    player.getRepresentationUnfogged() + " use button to exhaust " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                        + "Acamar, the Empyrean" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent",
                    empyButtons);
            }
            if (!game.getMovedUnitsFromCurrentActivation().isEmpty()
                && (tile.getUnitHolders().size() == 1) && player.getPlanets().contains("ghoti")) {
                player.setCommodities(player.getCommodities() + 1);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    player.getRepresentation()
                        + " gained 1 commodity due to ghoti planet card. Your commodities are now "
                        + player.getCommodities());
            }
            if (!game.getMovedUnitsFromCurrentActivation().isEmpty()
                && (game.getMovedUnitsFromCurrentActivation().containsKey("flagship")
                    || game.getMovedUnitsFromCurrentActivation().containsKey("flagshipdamaged"))
                && player.hasUnit("dihmohn_flagship")) {
                List<Button> produce = new ArrayList<>();
                produce.add(Buttons.blue("dihmohnfs_" + game.getActiveSystem(), "Produce (2) Units"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    player.getRepresentation()
                        + " Your Dih-Mohn Flagship moved into the active system and you can produce 2 units with a combined cost of 4.",
                    produce);
            }
            systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, game, event);
            ButtonHelperFactionSpecific.checkForStymie(game, player, tile);

            if (FoWHelper.playerHasUnitsInSystem(player, tile)) {
                List<Player> players = ButtonHelper.getOtherPlayersWithShipsInTheSystem(player, game, tile);
                Player player2 = player;
                for (Player p2 : players) {
                    if (p2 != player && !player.getAllianceMembers().contains(p2.getFaction())) {
                        player2 = p2;
                        break;
                    }
                }
                if (player == player2) {
                    needPDSCheck = true;
                }
            }
        }

        int landingButtons = 1;
        if (!game.getStoredValue("possiblyUsedRift").isEmpty()) {
            landingButtons = 2;
        }
        if (systemButtons.size() == landingButtons || game.isL1Hero()) {
            systemButtons = ButtonHelper.landAndGetBuildButtons(player, game, event, tile);
        }
        CommanderUnlockCheckService.checkPlayer(player, "nivyn");
        CommanderUnlockCheckService.checkPlayer(player, "ghoti");
        CommanderUnlockCheckService.checkPlayer(player, "zelian");
        CommanderUnlockCheckService.checkPlayer(player, "gledge");
        CommanderUnlockCheckService.checkPlayer(player, "mortheus");
        CommanderUnlockCheckService.checkAllPlayersInGame(game, "empyrean");
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
        if ((needPDSCheck || game.isFowMode()) && !game.isL1Hero() && !playersWithPds2.isEmpty()) {
            tacticalActionSpaceCannonOffenceStep(game, player, playersWithPds2, tile);
        }
        StartCombatService.combatCheck(game, event, tile);
        ButtonHelper.deleteMessage(event);
    }

    private static void tacticalActionSpaceCannonOffenceStep(Game game, Player player, List<Player> playersWithPds2, Tile tile) {
        if (game.isFowMode()) {
            String title = "### Space Cannon Offence " + UnitEmojis.pds + "\n";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), title
                + "There are players with Space Cannon Offence coverage in this system.\n"
                + "Please resolve those before continuing or float the window if unrelevant.");
            List<Button> spaceCannonButtons = StartCombatService.getSpaceCannonButtons(game, player, tile);
            spaceCannonButtons.add(Buttons.red("declinePDS_" + tile.getTileID() + "_" + player.getFaction(), "Decline PDS"));
            for (Player playerWithPds : playersWithPds2) {
                MessageHelper.sendMessageToChannelWithButtons(playerWithPds.getCorrectChannel(), title + playerWithPds.getRepresentationUnfogged()
                    + " you have PDS coverage in " + tile.getRepresentation() + ", use buttons to resolve:", spaceCannonButtons);
            }
        } else {
            StartCombatService.sendSpaceCannonButtonsToThread(player.getCorrectChannel(), game, player, tile);
        }
    }

    @ButtonHandler("doneWithOneSystem_")
    public static void finishMovingFromOneTile(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.replace("doneWithOneSystem_", "");
        Tile tile = game.getTileByPosition(pos);
        int distance = CheckDistanceHelper.getDistanceBetweenTwoTiles(game, player, pos, game.getActiveSystem(), true);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "From system "
            + tile.getRepresentationForButtons(game, player) + " (" + distance + " tile" + (distance == 1 ? "" : "s") + " away)\n"
            + event.getMessage().getContentRaw());
        String message = "Choose a different system to move from, or finalize movement.";
        game.resetCurrentMovedUnitsFrom1System();
        List<Button> systemButtons = ButtonHelper.getTilesToMoveFrom(player, game, event);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("tacticalMoveFrom_")
    public static void selectTileToMoveFrom(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.replace("tacticalMoveFrom_", "");
        List<Button> systemButtons = getButtonsForAllUnitsInSystem(player, game, game.getTileByPosition(pos), "Move");
        game.resetCurrentMovedUnitsFrom1System();
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Chose to move from "
            + game.getTileByPosition(pos).getRepresentationForButtons(game, player)
            + ". Use buttons to select the units you wish to move.", systemButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("tacticalAction")
    public static void selectRingThatActiveSystemIsIn(Player player, Game game, ButtonInteractionEvent event) {
        if (player.getTacticalCC() < 1) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getFactionEmoji() + " does not have any command tokens in their tactic pool.");
            return;
        }
        resetStoredValuesForTacticalAction(game);
        beginTacticalAction(game, player);
    }

    public static void resetStoredValuesForTacticalAction(Game game) {
        game.setNaaluAgent(false);
        game.setL1Hero(false);
        game.setStoredValue("vaylerianHeroActive", "");
        game.setStoredValue("tnelisCommanderTracker", "");
        game.setStoredValue("planetsTakenThisRound", "");
        game.setStoredValue("fortuneSeekers", "");
        game.resetCurrentMovedUnitsFrom1TacticalAction();
    }

    public static void beginTacticalAction(Game game, Player player) {
        boolean prefersDistanceBasedTacticalActions = UserSettingsManager.get(player.getUserID()).isPrefersDistanceBasedTacticalActions();
        if (!game.isFowMode() && game.getRingCount() < 5 && prefersDistanceBasedTacticalActions) {
            alternateWayOfOfferingTiles(player, game);
        } else {
            String message = "Doing a tactical action. Please select the ring of the map that the system you wish to activate is located in.";
            if (!game.isFowMode()) {
                message += " Reminder that a normal 6 player map is 3 rings, with ring 1 being adjacent to Mecatol Rex. The Wormhole Nexus is in the corner.";
            }
            List<Button> ringButtons = ButtonHelper.getPossibleRings(player, game);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, ringButtons);
        }
    }

    public static void alternateWayOfOfferingTiles(Player player, Game game) {
        Map<String, Integer> distances = CheckDistanceHelper.getTileDistancesRelativeToAllYourUnlockedTiles(game, player);
        List<String> initialOffering = new ArrayList<>(CheckDistanceHelper.getAllTilesACertainDistanceAway(game, player, distances, 0));
        int maxDistance = 0;
        List<Button> buttons = new ArrayList<>();
        String message = "Doing a tactical action. Please select the tile you wish to activate. Right now showing tiles ";
        if (initialOffering.size()
            + CheckDistanceHelper.getAllTilesACertainDistanceAway(game, player, distances, 1).size() < 6) {
            initialOffering.addAll(CheckDistanceHelper.getAllTilesACertainDistanceAway(game, player, distances, 1));
            maxDistance = 1;
            message += "0-1 tiles away.";
        } else {
            message += "0 tiles away.";
        }
        for (String pos : initialOffering) {
            Tile tile = game.getTileByPosition(pos);
            if (ButtonHelper.canActivateTile(game, player, tile)) {
                buttons.add(Buttons.green("ringTile_" + pos, tile.getRepresentationForButtons(game, player), tile.getTileEmoji(player)));
            }
        }
        buttons.add(Buttons.gray("getTilesThisFarAway_" + (maxDistance + 1), "Get Tiles " + (maxDistance + 1) + " Spaces Away"));
        if (Constants.prisonerOneId.equals(player.getUserID())) buttons.addAll(ButtonHelper.getPossibleRings(player, game)); //TODO: Add option for this
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    @ButtonHandler("getTilesThisFarAway_")
    public static void getTilesThisFarAway(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        int desiredDistance = Integer.parseInt(buttonID.split("_")[1]);
        Map<String, Integer> distances = CheckDistanceHelper.getTileDistancesRelativeToAllYourUnlockedTiles(game, player);
        List<Button> buttons = new ArrayList<>();
        if (desiredDistance > 0) {
            buttons.add(Buttons.gray("getTilesThisFarAway_" + (desiredDistance - 1), "Get Tiles " + (desiredDistance - 1) + " Spaces Away"));
        }
        for (String pos : CheckDistanceHelper.getAllTilesACertainDistanceAway(game, player, distances, desiredDistance)) {
            Tile tile = game.getTileByPosition(pos);
            String tileRepresentation = tile.getRepresentationForButtons(game, player);
            if (ButtonHelper.canActivateTile(game, player, tile)) {
                buttons.add(Buttons.green("ringTile_" + pos, tileRepresentation, tile.getTileEmoji(player)));
            }
        }
        buttons.add(Buttons.gray("getTilesThisFarAway_" + (desiredDistance + 1), "Get Tiles " + (desiredDistance + 1) + " Spaces Away"));

        String message = "Doing a tactical action. Please select the tile you wish to activate.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("ringTile_")
    public static void selectActiveSystem(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.replace("ringTile_", "");
        game.setActiveSystem(pos);
        game.setStoredValue("possiblyUsedRift", "");
        game.setStoredValue("lastActiveSystem", pos);
        List<Button> systemButtons = ButtonHelper.getTilesToMoveFrom(player, game, event);
        Tile activeSystem = game.getTileByPosition(pos);
        StringBuilder message = new StringBuilder(player.getRepresentationUnfogged() + " activated "
            + activeSystem.getRepresentationForButtons(game, player) + ".");

        if (!game.isFowMode()) {
            for (Player player_ : game.getRealPlayers()) {
                if (!game.isL1Hero() && !player.getFaction().equalsIgnoreCase(player_.getFaction())
                    && !player_.isPlayerMemberOfAlliance(player)
                    && FoWHelper.playerHasUnitsInSystem(player_, activeSystem)) {
                    message.append("\n").append(player_.getRepresentation()).append(" has units in the system.");
                }
            }
            for (UnitHolder planet : activeSystem.getPlanetUnitHolders()) {
                if (player.getPlanets().contains(planet.getName())) {
                    continue;
                }
                for (String attachment : planet.getTokenList()) {
                    if (attachment.contains("sigma_weirdway")) {
                        message.append("\nSystem contains the Weirdway; applying -1 to the move value of your ships.");
                        break;
                    }
                }
            }
        } else {
            List<Player> playersAdj = FoWHelper.getAdjacentPlayers(game, pos, true);
            for (Player player_ : playersAdj) {
                String playerMessage = player_.getRepresentationUnfogged() + " - System " + activeSystem.getRepresentationForButtons(game, player_)
                    + " has been activated ";
                MessageHelper.sendPrivateMessageToPlayer(player_, game, playerMessage);
            }
            ButtonHelper.resolveOnActivationEnemyAbilities(game, activeSystem, player, false, event);
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "celdauricommander")
            && ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Spacedock).contains(activeSystem)) {
            List<Button> buttons = new ArrayList<>();
            Button getCommButton = Buttons.blue("gain_1_comms", "Gain 1 Commodity", MiscEmojis.comm);
            buttons.add(getCommButton);
            String msg = player.getRepresentation()
                + " you have Henry Storcher, the Celdauri Commander, and activated a system with your space dock. Please use the button to get a commodity.";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        }

        List<Player> playersWithPds2 = ButtonHelper.tileHasPDS2Cover(player, game, pos);
        if (!game.isFowMode() && !playersWithPds2.isEmpty() && !game.isL1Hero()) {
            List<String> mentions = new ArrayList<>();
            for (Player playerWithPds : playersWithPds2) {
                if (playerWithPds == player) {
                    continue;
                }
                mentions.add(playerWithPds.getRepresentation());
            }
            if (!mentions.isEmpty()) {
                message.append("\n").append(player.getRepresentationUnfogged()).append(" the selected system is in range of SPACE CANNON units owned by ").append(String.join(", ", mentions)).append(".");
            }
        }

        Tile tile = game.getTileByPosition(pos);
        if (tile.getPlanetUnitHolders().isEmpty()
            && ButtonHelper.doesPlayerHaveFSHere("mortheus_flagship", player, tile)
            && !tile.getUnitHolders().get("space").getTokenList().contains(Mapper.getTokenID(Constants.FRONTIER))) {
            String msg = player.getRepresentationUnfogged()
                + " automatically added 1 frontier token to the system due to the Particle Sieve (the Mortheus flagship).";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
            AddTokenCommand.addToken(event, tile, Constants.FRONTIER, game);
        }

        MessageHelper.sendMessageToChannel(event.getChannel(), message.toString());

        List<Button> button3 = ButtonHelperAgents.getL1Z1XAgentButtons(game, player);
        if (player.hasUnexhaustedLeader("l1z1xagent") && !button3.isEmpty() && !game.isL1Hero()) {
            String msg = player.getRepresentationUnfogged() + ", you can use buttons to resolve " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                + "I48S, the L1Z1Z" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent, if you so wish.";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, button3);
        }

        if (tile.isAnomaly() && player.getCommodities() < player.getCommoditiesTotal() && player.getActionCards().containsKey("harness")) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                player.getRepresentation() + ", you activated an anomaly, and so could now play _Harness Energy_.");
        }

        List<Button> button2 = ButtonHelper.scanlinkResolution(player, game);
        if ((player.getTechs().contains("sdn") || player.getTechs().contains("absol_sdn")) && !button2.isEmpty() && !game.isL1Hero()) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), player.getRepresentation() + ", Please resolve _Scanlink Drone Network_.", button2);
            if (player.hasAbility("awaken") || player.hasUnit("titans_flagship") || player.hasUnit("sigma_ul_flagship_1") || player.hasUnit("sigma_ul_flagship_2")) {
                ButtonHelper.resolveTitanShenanigansOnActivation(player, game, game.getTileByPosition(pos), event);
            }
        } else {
            if (player.hasAbility("awaken")) {
                ButtonHelper.resolveTitanShenanigansOnActivation(player, game, game.getTileByPosition(pos), event);
            }
        }
        if (player.hasAbility("plague_reservoir") && player.hasTech("dxa")) {
            for (Planet planetUH : tile.getPlanetUnitHolders()) {
                String planet = planetUH.getName();
                if (player.getPlanetsAllianceMode().contains(planetUH.getName())) {
                    String msg10 = player.getRepresentationUnfogged()
                        + " when you get to the invasion step of the tactical action, you may have an opportunity to use Dacxive Animators on "
                        + Helper.getPlanetRepresentation(planet, game)
                        + ". Only use this on one planet, per the plague reservoir ability.";
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg10,
                        ButtonHelper.getDacxiveButtons(planet, player));
                }
            }
        }

        // Send buttons to move
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to select the first system you wish to move from.", systemButtons);

        // Resolve other abilities
        if (player.hasAbility("recycled_materials")) {
            List<Button> buttons = ButtonHelperFactionSpecific.getRohDhnaRecycleButtons(game, player);
            if (!buttons.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                    "Use buttons to select which unit to recycle", buttons);
            }
        }
        if (player.hasRelic("absol_plenaryorbital") && !tile.isHomeSystem() && !tile.isMecatol() && !player.hasUnit("plenaryorbital")) {
            List<Button> buttons4 = ButtonHelper.getAbsolOrbitalButtons(game, player);
            if (!buttons4.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                    "You can place down the _Plenary Orbital_.",
                    buttons4);
            }
        }
        if (!game.isFowMode()) {
            if (!game.isL1Hero()) {
                ButtonHelper.resolveOnActivationEnemyAbilities(game, game.getTileByPosition(pos), player, false, event);
            }
        }
        game.setStoredValue("crucibleBoost", "");
        game.setStoredValue("flankspeedBoost", "");
        game.setStoredValue("baldrickGDboost", "");

        ButtonHelper.deleteMessage(event);
    }

    public static List<Button> getButtonsForAllUnitsInSystem(Player player, Game game, Tile tile, String moveOrRemove) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        List<UnitType> movableFromPlanets = new ArrayList<>(List.of(UnitType.Infantry, UnitType.Mech));

        Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            String name = entry.getKey();
            String planetName = planetRepresentations.get(name);
            if (planetName == null) {
                planetName = name;
            }
            UnitHolder unitHolder = entry.getValue();
            Map<UnitKey, Integer> units = unitHolder.getUnits();

            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey()))
                    continue;
                UnitKey unitKey = unitEntry.getKey();
                String unitName = unitKey.unitName();

                if (unitHolder instanceof Planet && !(movableFromPlanets.contains(unitKey.getUnitType()))) {
                    continue;
                }

                int totalUnits = unitEntry.getValue();
                int damagedUnits = 0;
                if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                    damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                }

                String buttonPrefix = finChecker + "unitTactical" + moveOrRemove + "_" + tile.getPosition() + "_";
                String buttonSuffix = "";
                String labelPrefix = moveOrRemove + " ";
                String labelSuffix = " " + unitKey.unitName();
                if (unitHolder instanceof Planet) {
                    planetName = planetName.replace(" ", "").toLowerCase().replace("'", "").replace("-", "");
                    buttonSuffix = "_" + planetName;
                    labelSuffix += " from " + Helper.getPlanetRepresentation(planetName.toLowerCase(), game);
                }

                for (int x = 1; x <= damagedUnits && x <= 2; x++) {
                    String buttonID = buttonPrefix + x + unitName + "damaged" + buttonSuffix;
                    String buttonText = labelPrefix + x + " damaged" + labelSuffix;
                    buttons.add(Buttons.red(buttonID, buttonText, unitKey.unitEmoji()));
                }
                totalUnits -= damagedUnits;
                for (int x = 1; x <= totalUnits && x <= 2; x++) {
                    String buttonID = buttonPrefix + x + unitName + buttonSuffix;
                    String buttonText = labelPrefix + x + labelSuffix;
                    buttons.add(Buttons.red(buttonID, buttonText, unitKey.unitEmoji()));
                }
            }
        }
        Button concludeMove;
        Button doAll;
        Button doAllShips;
        if ("Remove".equalsIgnoreCase(moveOrRemove)) {
            doAllShips = Buttons.gray(finChecker + "unitTactical" + moveOrRemove + "_" + tile.getPosition() + "_removeAllShips", "Remove all Ships");
            buttons.add(doAllShips);
            doAll = Buttons.gray(finChecker + "unitTactical" + moveOrRemove + "_" + tile.getPosition() + "_removeAll", "Remove all units");
            concludeMove = Buttons.blue(finChecker + "doneRemoving", "Done removing units");
        } else {
            doAll = Buttons.gray(finChecker + "unitTactical" + moveOrRemove + "_" + tile.getPosition() + "_moveAll", "Move all units");
            concludeMove = Buttons.blue(finChecker + "doneWithOneSystem_" + tile.getPosition(), "Done moving units from this system");
            if (game.playerHasLeaderUnlockedOrAlliance(player, "tneliscommander") && game.getStoredValue("tnelisCommanderTracker").isEmpty()) {
                buttons.add(Buttons.blue("declareUse_Tnelis Commander_" + tile.getPosition(), "Use Tnelis Commander", FactionEmojis.tnelis));
            }
        }
        buttons.add(doAll);
        buttons.add(concludeMove);
        Map<String, Integer> displacedUnits = game.getCurrentMovedUnitsFrom1System();
        for (String unit : displacedUnits.keySet()) {
            String unitkey;
            String planet = "";
            String origUnit = unit;
            String damagedMsg = "";
            if (unit.contains("damaged")) {
                unit = unit.replace("damaged", "");
                damagedMsg = " damaged ";
            }
            if (unit.contains("_")) {
                unitkey = unit.split("_")[0];
                planet = unit.split("_")[1];
            } else {
                unitkey = unit;
            }
            for (int x = 1; x < displacedUnits.get(origUnit) + 1; x++) {
                if (x > 2) {
                    break;
                }
                String bID = finChecker + "unitTactical" + moveOrRemove + "_" + tile.getPosition() + "_" + x
                    + unit.toLowerCase().replaceAll("[ ']", "") + damagedMsg.replace(" ", "") + "_reverse";
                String blabel = "Undo move of " + x + " " + damagedMsg + unitkey;
                if (!"".equalsIgnoreCase(planet)) {
                    blabel += " from " + Helper.getPlanetRepresentation(planet.toLowerCase(), game);
                }
                UnitType unitType = Units.findUnitType(AliasHandler.resolveUnit(unitkey.toLowerCase().replace(" ", "")));
                Button validTile2 = Buttons.green(bID, blabel, unitType.getUnitTypeEmoji());
                buttons.add(validTile2);
            }
        }
        if (!displacedUnits.isEmpty()) {
            Button validTile2 = Buttons.green(finChecker + "unitTactical" + moveOrRemove + "_" + tile.getPosition() + "_reverseAll", "Undo all");
            buttons.add(validTile2);
        }
        return buttons;
    }
}
