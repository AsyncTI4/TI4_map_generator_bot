package ti4.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands.combat.StartCombat;
import ti4.commands.player.TurnStart;
import ti4.commands.special.CheckDistance;
import ti4.commands.tokens.AddToken;
import ti4.commands.units.AddUnits;
import ti4.commands.units.RemoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;

public class ButtonHelperTacticalAction {

    public static void movingUnitsInTacticalAction(String buttonID, ButtonInteractionEvent event, Game activeGame,
        Player player, String buttonLabel) {
        String remove = "Move";
        Map<String, Integer> currentSystem = activeGame.getCurrentMovedUnitsFrom1System();
        Map<String, Integer> currentActivation = activeGame.getMovedUnitsFromCurrentActivation();
        String rest;
        if (buttonID.contains("Remove")) {
            remove = "Remove";
            rest = buttonID.replace("unitTacticalRemove_", "").toLowerCase();
        } else {
            rest = buttonID.replace("unitTacticalMove_", "").toLowerCase();
        }
        String pos = rest.substring(0, rest.indexOf("_"));
        Tile tile = activeGame.getTileByPosition(pos);
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
                        activeGame.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitkey,
                            currentActivation.get(unitkey) - amount);
                    }
                    if (unitkey.contains("damaged")) {
                        unitkey = unitkey.replace("damaged", "");
                        damagedMsg = " damaged ";
                    }
                    new AddUnits().unitParsing(event, player.getColor(),
                        activeGame.getTileByPosition(pos), (amount) + " " + unitkey + " " + planet, activeGame);
                    if (damagedMsg.contains("damaged")) {
                        if ("".equalsIgnoreCase(planet)) {
                            planet = "space";
                        }
                        UnitKey unitID = Mapper.getUnitKey(AliasHandler.resolveUnit(unitkey), player.getColor());
                        activeGame.getTileByPosition(pos).addUnitDamage(planet, unitID, (amount));
                    }
                }

                activeGame.resetCurrentMovedUnitsFrom1System();
            } else {
                Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
                for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
                    String name = entry.getKey();
                    String representation = planetRepresentations.get(name);
                    UnitHolder unitHolder = entry.getValue();
                    Map<UnitKey, Integer> units1 = unitHolder.getUnits();
                    Map<UnitKey, Integer> units = new HashMap<>(units1);

                    if (unitHolder instanceof Planet) {
                        for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                            if (!player.unitBelongsToPlayer(unitEntry.getKey()))
                                continue;
                            UnitKey unitKey = unitEntry.getKey();
                            if ((unitKey.getUnitType() == UnitType.Infantry
                                || unitKey.getUnitType() == UnitType.Mech)) {
                                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
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
                                        activeGame.setSpecificCurrentMovedUnitsFrom1System(rest,
                                            currentSystem.get(rest) + amount);
                                    } else {
                                        activeGame.setSpecificCurrentMovedUnitsFrom1System(rest, amount);
                                    }
                                    if (currentActivation.containsKey(unitName)) {
                                        activeGame.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitName,
                                            currentActivation.get(unitName) + amount);
                                    } else {
                                        activeGame.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitName, amount);
                                    }
                                }
                                rest = unitName;
                                amount = totalUnits - damagedUnits;
                                if (amount > 0) {
                                    rest = unitName.toLowerCase() + "_" + unitHolder.getName().toLowerCase();
                                    if (currentSystem.containsKey(rest)) {
                                        activeGame.setSpecificCurrentMovedUnitsFrom1System(rest,
                                            currentSystem.get(rest) + amount);
                                    } else {
                                        activeGame.setSpecificCurrentMovedUnitsFrom1System(rest, amount);
                                    }
                                    if (currentActivation.containsKey(unitName)) {
                                        activeGame.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitName,
                                            currentActivation.get(unitName) + amount);
                                    } else {
                                        activeGame.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitName, amount);
                                    }
                                }

                                new RemoveUnits().removeStuff(event, activeGame.getTileByPosition(pos),
                                    unitEntry.getValue(), unitHolder.getName(), unitKey, player.getColor(), false,
                                    activeGame);
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
                            String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                            int totalUnits = unitEntry.getValue();
                            int amount;

                            int damagedUnits = 0;
                            if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                                damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                            }

                            new RemoveUnits().removeStuff(event, activeGame.getTileByPosition(pos), totalUnits, "space",
                                unitKey, player.getColor(), false, activeGame);
                            if (damagedUnits > 0) {
                                rest = unitName + "damaged";
                                amount = damagedUnits;
                                if (currentSystem.containsKey(rest)) {
                                    activeGame.setSpecificCurrentMovedUnitsFrom1System(rest,
                                        currentSystem.get(rest) + amount);
                                } else {
                                    activeGame.setSpecificCurrentMovedUnitsFrom1System(rest, amount);
                                }
                                if (currentActivation.containsKey(rest)) {
                                    activeGame.setSpecificCurrentMovedUnitsFrom1TacticalAction(rest,
                                        currentActivation.get(rest) + amount);
                                } else {
                                    activeGame.setSpecificCurrentMovedUnitsFrom1TacticalAction(rest, amount);
                                }
                            }
                            rest = unitName;
                            amount = totalUnits - damagedUnits;
                            if (amount > 0) {
                                if (currentSystem.containsKey(rest)) {
                                    activeGame.setSpecificCurrentMovedUnitsFrom1System(rest,
                                        currentSystem.get(rest) + amount);
                                } else {
                                    activeGame.setSpecificCurrentMovedUnitsFrom1System(rest, amount);
                                }
                                if (currentActivation.containsKey(unitName)) {
                                    activeGame.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitName,
                                        currentActivation.get(unitName) + amount);
                                } else {
                                    activeGame.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitName, amount);
                                }
                            }
                        }
                    }
                }
            }
            String message = ButtonHelper.buildMessageFromDisplacedUnits(activeGame, false, player, remove);
            List<Button> systemButtons = ButtonHelper.getButtonsForAllUnitsInSystem(player, activeGame,
                activeGame.getTileByPosition(pos), remove);
            event.getMessage().editMessage(message)
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
            return;
        }
        int amount = Integer.parseInt(rest.charAt(0) + "");
        if (rest.contains("_reverse")) {
            amount = amount * -1;
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
            new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(pos),
                (amount * -1) + " " + unitName + " " + planet, activeGame);
            if (buttonLabel.toLowerCase().contains("damaged")) {
                if ("".equalsIgnoreCase(planet)) {
                    planet = "space";
                }
                activeGame.getTileByPosition(pos).addUnitDamage(planet, unitKey, (amount * -1));
            }
        } else {
            String planetName;
            if ("".equalsIgnoreCase(planet)) {
                planetName = "space";
            } else {
                planetName = planet.replace("'", "");
                planetName = AliasHandler.resolvePlanet(planetName);
            }

            new RemoveUnits().removeStuff(event, activeGame.getTileByPosition(pos), amount, planetName, unitKey,
                player.getColor(), buttonLabel.toLowerCase().contains("damaged"), activeGame);
        }
        if (buttonLabel.toLowerCase().contains("damaged")) {
            unitName = unitName + "damaged";
            rest = rest + "damaged";
        }
        if (currentSystem.containsKey(rest)) {
            activeGame.setSpecificCurrentMovedUnitsFrom1System(rest, currentSystem.get(rest) + amount);
        } else {
            activeGame.setSpecificCurrentMovedUnitsFrom1System(rest, amount);
        }
        if (currentSystem.get(rest) == 0) {
            currentSystem.remove(rest);
        }
        if (currentActivation.containsKey(unitName)) {
            activeGame.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitName,
                currentActivation.get(unitName) + amount);
        } else {
            activeGame.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitName, amount);
        }
        String message = ButtonHelper.buildMessageFromDisplacedUnits(activeGame, false, player, remove);
        List<Button> systemButtons = ButtonHelper.getButtonsForAllUnitsInSystem(player, activeGame,
            activeGame.getTileByPosition(pos), remove);
        event.getMessage().editMessage(message)
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }

    public static void concludeTacticalAction(Player player, Game activeGame, ButtonInteractionEvent event) {
        if (!activeGame.getL1Hero()) {
            ButtonHelper.exploreDET(player, activeGame, event);
            ButtonHelperFactionSpecific.cleanCavUp(activeGame, event);
            if (player.hasAbility("cunning")) {
                List<Button> trapButtons = new ArrayList<>();
                for (UnitHolder uH : activeGame.getTileByPosition(activeGame.getActiveSystem()).getUnitHolders()
                    .values()) {
                    if (uH instanceof Planet) {
                        String planet = uH.getName();
                        trapButtons.add(Button.secondary("setTrapStep3_" + planet,
                            Helper.getPlanetRepresentation(planet, activeGame)));
                    }
                }
                trapButtons.add(Button.danger("deleteButtons", "Decline"));
                String msg = player.getRepresentation(true, true)
                    + " you can use the buttons to place a trap on a planet";
                if (trapButtons.size() > 1) {
                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                        msg, trapButtons);
                }
            }
            if (player.hasUnexhaustedLeader("celdauriagent")) {
                List<Button> buttons = new ArrayList<>();
                Button hacanButton = Button
                        .secondary("exhaustAgent_celdauriagent_" + player.getFaction(),
                                   "Use " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "George Nobin (Celdauri Agent)")
                        .withEmoji(Emoji.fromFormatted(Emojis.celdauri));
                buttons.add(hacanButton);
                buttons.add(Button.danger("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                        player.getRepresentation(true, true)
                                + " you can use " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "George Nobin (Celdauri Agent) to place an SD for 2tg/2comm",
                        buttons);
            }
        }

        if (!activeGame.isAbsolMode() && player.getRelics().contains("emphidia")
                && !player.getExhaustedRelics().contains("emphidia")) {
            String message = player.getRepresentation() + " You can use the button to explore using the crown of emphidia";
            List<Button> systemButtons2 = new ArrayList<>();
            systemButtons2.add(Button.success("crownofemphidiaexplore", "Use Crown To Explore a Planet"));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons2);
        }
        if (activeGame.getNaaluAgent()) {
            player = activeGame.getPlayer(activeGame.getActivePlayerID());
            activeGame.setNaaluAgent(false);
        }
        activeGame.setStoredValue("tnelisCommanderTracker", "");
        activeGame.setL1Hero(false);
        activeGame.setStoredValue("vaylerianHeroActive", "");
        String message = player.getRepresentation(true, true) + " Use buttons to end turn or do another action.";
        List<Button> systemButtons = TurnStart.getStartOfTurnButtons(player, activeGame, true, event);
        MessageChannel channel = event.getMessageChannel();
        if (activeGame.isFoWMode()) {
            channel = player.getPrivateChannel();
        }
        MessageHelper.sendMessageToChannelWithButtons(channel, message, systemButtons);
        event.getMessage().delete().queue();

    }

    public static void buildWithTacticalAction(Player player, Game activeGame, ButtonInteractionEvent event,
        String buttonID) {
        String pos = buttonID.replace("tacticalActionBuild_", "");
        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, activeGame, activeGame.getTileByPosition(pos),
            "tacticalAction", "place");
        String message = player.getRepresentation() + " Use the buttons to produce units. ";
        String message3 = "You have "
            + Helper.getProductionValue(player, activeGame, activeGame.getTileByPosition(pos), false)
            + " PRODUCTION value in this system\n";
        if (Helper.getProductionValue(player, activeGame, activeGame.getTileByPosition(pos), false) > 0
            && activeGame.playerHasLeaderUnlockedOrAlliance(player, "cabalcommander")) {
            message3 = message3
                + ". You also have cabal commander which allows you to produce 2 ff/inf that dont count towards production limit\n";
        }
        if (Helper.getProductionValue(player, activeGame, activeGame.getTileByPosition(pos), false) > 0
            && ButtonHelper.isPlayerElected(activeGame, player, "prophecy")) {
            message3 = message3
                + "Reminder that you have prophecy of Ixth and should produce 2 fighters if you want to keep it. Its removal is not automated\n";
        }
        MessageHelper.sendMessageToChannel(event.getChannel(),
            message3 + ButtonHelper.getListOfStuffAvailableToSpend(player, activeGame));
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        event.getMessage().delete().queue();
    }

    public static void finishMovingForTacticalAction(Player player, Game activeGame, ButtonInteractionEvent event,
        String buttonID) {
        String message = "Moved all units to the space area.";

        Tile tile = null;

        if (buttonID.contains("_")) {
            tile = activeGame.getTileByPosition(buttonID.split("_")[1]);
        } else {
            tile = activeGame.getTileByPosition(activeGame.getActiveSystem());
        }
        List<Player> playersWithPds2 = ButtonHelper.tileHasPDS2Cover(player, activeGame, tile.getPosition());

        List<Button> systemButtons;
        boolean needPDSCheck = false;
        if (activeGame.getMovedUnitsFromCurrentActivation().isEmpty()
            && !activeGame.playerHasLeaderUnlockedOrAlliance(player, "sardakkcommander")
            && tile.getUnitHolders().get("space").getUnitCount(UnitType.Infantry, player) < 1
            && tile.getUnitHolders().get("space").getUnitCount(UnitType.Mech, player) < 1) {
            message = "Nothing moved. Use buttons to decide if you want to build (if you can) or finish the activation";
            systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, activeGame, event);
            needPDSCheck = true;
            systemButtons = ButtonHelper.landAndGetBuildButtons(player, activeGame, event, tile);
        } else {
            if (!activeGame.getMovedUnitsFromCurrentActivation().isEmpty()) {
                ButtonHelper.resolveEmpyCommanderCheck(player, activeGame, tile, event);
                ButtonHelper.sendEBSWarning(player, activeGame, tile.getPosition());
                ButtonHelper.checkForIonStorm(activeGame, tile, player);
            }
            List<Button> empyButtons = new ArrayList<>();
            if (!activeGame.getMovedUnitsFromCurrentActivation().isEmpty()
                    && (tile.getUnitHolders().values().size() == 1) && player.hasUnexhaustedLeader("empyreanagent")) {
                Button empyButton = Button.secondary("exhaustAgent_empyreanagent",
                                                     "Use " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Acamar (Empyrean Agent)")
                        .withEmoji(Emoji.fromFormatted(Emojis.Empyrean));
                empyButtons.add(empyButton);
                empyButtons.add(Button.danger("deleteButtons", "Delete These Buttons"));
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                        player.getRepresentation(true, true) + " use button to exhaust " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Acamar (Empyrean Agent)", empyButtons);
            }
            if (!activeGame.getMovedUnitsFromCurrentActivation().isEmpty()
                && (tile.getUnitHolders().values().size() == 1) && player.getPlanets().contains("ghoti")) {
                player.setCommodities(player.getCommodities() + 1);
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                    player.getRepresentation()
                        + " gained 1 commodity due to ghoti planet card. Your commodities are now "
                        + player.getCommodities());
            }
            if (!activeGame.getMovedUnitsFromCurrentActivation().isEmpty()
                && (activeGame.getMovedUnitsFromCurrentActivation().containsKey("flagship")
                    || activeGame.getMovedUnitsFromCurrentActivation().containsKey("flagshipdamaged"))
                && player.hasUnit("dihmohn_flagship")) {
                List<Button> produce = new ArrayList<>();
                produce.add(Button.primary("dihmohnfs_" + activeGame.getActiveSystem(), "Produce (2) Units"));
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                    player.getRepresentation()
                        + " Your Dih-Mohn Flagship moved into the active system and you can produce 2 units with a combined cost of 4.",
                    produce);
            }
            systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, activeGame, event);
            ButtonHelperFactionSpecific.checkForStymie(activeGame, player, tile);
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (!"space".equalsIgnoreCase(unitHolder.getName())) {
                    continue;
                }
                if (!FoWHelper.playerHasUnitsInSystem(player, tile)) {
                    break;
                }
                List<Player> players = ButtonHelper.getOtherPlayersWithShipsInTheSystem(player, activeGame, tile);
                Player player2 = player;
                for (Player p2 : players) {
                    if (p2 != player && !player.getAllianceMembers().contains(p2.getFaction())) {
                        player2 = p2;
                        break;
                    }
                }
                if (player != player2) {

                    String threadName = StartCombat.combatThreadName(activeGame, player, player2, tile);
                    if (!activeGame.isFoWMode()) {
                        StartCombat.findOrCreateCombatThread(activeGame, activeGame.getActionsChannel(), player,
                            player2, threadName, tile, event, "space", "space");
                    } else {
                        StartCombat.findOrCreateCombatThread(activeGame, player.getPrivateChannel(), player, player2,
                            threadName, tile, event, "space", "space");
                        StartCombat.findOrCreateCombatThread(activeGame, player2.getPrivateChannel(), player2, player,
                            threadName, tile, event, "space", "space");
                        for (Player player3 : activeGame.getRealPlayers()) {
                            if (player3 == player2 || player3 == player) {
                                continue;
                            }
                            if (!tile.getRepresentationForButtons(activeGame, player3).contains("(")) {
                                continue;
                            }
                            StartCombat.findOrCreateCombatThread(activeGame, player3.getPrivateChannel(), player3,
                                player3, threadName, tile, event, "space", "space");
                        }
                    }
                } else {
                    needPDSCheck = true;
                }
            }
        }
        if (systemButtons.size() == 2 || activeGame.getL1Hero()) {
            systemButtons = ButtonHelper.landAndGetBuildButtons(player, activeGame, event, tile);
        }
        if (player.getLeaderIDs().contains("nivyncommander") && !player.hasLeaderUnlocked("nivyncommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "nivyn", event);
        }
        if (player.getLeaderIDs().contains("ghoticommander") && !player.hasLeaderUnlocked("ghoticommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "ghoti", event);
        }
        if (player.getLeaderIDs().contains("zeliancommander") && !player.hasLeaderUnlocked("zeliancommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "zelian", event);
        }
        ButtonHelper.fullCommanderUnlockCheck(player, activeGame, "gledge", event);
        ButtonHelper.fullCommanderUnlockCheck(player, activeGame, "mortheus", event);
        for (Player p2 : activeGame.getRealPlayers()) {
            ButtonHelper.fullCommanderUnlockCheck(p2, activeGame, "empyrean", event);
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
        if (needPDSCheck && !activeGame.getL1Hero() && playersWithPds2.size() > 0) {
            StartCombat.sendSpaceCannonButtonsToThread(ButtonHelper.getCorrectChannel(player, activeGame), activeGame,
                player, tile);
        }
        event.getMessage().delete().queue();
    }

    public static void finishMovingFromOneTile(Player player, Game activeGame, ButtonInteractionEvent event,
        String buttonID) {
        String pos = buttonID.replace("doneWithOneSystem_", "");
        Tile tile = activeGame.getTileByPosition(pos);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "From system "
            + tile.getRepresentationForButtons(activeGame, player) + " (**"
            + CheckDistance.getDistanceBetweenTwoTiles(activeGame, player, pos, activeGame.getActiveSystem())
            + " tiles away**)\n"
            + event.getMessage().getContentRaw());
        String message = "Choose a different system to move from, or finalize movement.";
        activeGame.resetCurrentMovedUnitsFrom1System();
        List<Button> systemButtons = ButtonHelper.getTilesToMoveFrom(player, activeGame, event);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
        event.getMessage().delete().queue();

    }

    public static void selectTileToMoveFrom(Player player, Game activeGame, ButtonInteractionEvent event,
        String buttonID) {
        String pos = buttonID.replace("tacticalMoveFrom_", "");
        List<Button> systemButtons = ButtonHelper.getButtonsForAllUnitsInSystem(player, activeGame,
            activeGame.getTileByPosition(pos), "Move");
        activeGame.resetCurrentMovedUnitsFrom1System();
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Chose to move from "
            + activeGame.getTileByPosition(pos).getRepresentationForButtons(activeGame, player)
            + ". Use buttons to select the units you want to move.", systemButtons);
        event.getMessage().delete().queue();
    }

    public static void selectRingThatActiveSystemIsIn(Player player, Game activeGame, ButtonInteractionEvent event) {
        if (player.getTacticalCC() < 1) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                ButtonHelper.getIdent(player) + " does not have any tactical CC.");
            return;
        }
        activeGame.setNaaluAgent(false);
        activeGame.setL1Hero(false);
        activeGame.setStoredValue("vaylerianHeroActive", "");
        activeGame.setStoredValue("tnelisCommanderTracker", "");
        activeGame.setStoredValue("planetsTakenThisRound", "");
        player.setWhetherPlayerShouldBeTenMinReminded(false);
        activeGame.resetCurrentMovedUnitsFrom1TacticalAction();

        if (player.doesPlayerPreferDistanceBasedTacticalActions() && !activeGame.isFoWMode()
            && activeGame.getRingCount() < 5) {
            alternateWayOfOfferingTiles(player, activeGame);
        } else {
            String message = "Doing a tactical action. Please select the ring of the map that the system you want to activate is located in. Reminder that a normal 6 player map is 3 rings, with ring 1 being adjacent to Rex. Mallice is in the corner";
            List<Button> ringButtons = ButtonHelper.getPossibleRings(player, activeGame);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, ringButtons);
        }
    }

    public static void alternateWayOfOfferingTiles(Player player, Game activeGame) {
        Map<String, Integer> distances = CheckDistance.getTileDistancesRelativeToAllYourUnlockedTiles(activeGame,
            player);
        List<String> initialOffering = new ArrayList<>();
        initialOffering.addAll(CheckDistance.getAllTilesACertainDistanceAway(activeGame, player, distances, 0));
        int maxDistance = 0;
        List<Button> buttons = new ArrayList<>();
        String message = "Doing a tactical action. Please select the tile you want to activate. Right now showing tiles ";
        if (initialOffering.size()
            + CheckDistance.getAllTilesACertainDistanceAway(activeGame, player, distances, 1).size() < 6) {
            initialOffering.addAll(CheckDistance.getAllTilesACertainDistanceAway(activeGame, player, distances, 1));
            maxDistance = 1;
            message = message + "0-1 tiles away";
        } else {
            message = message + "0 tiles away";
        }
        for (String pos : initialOffering) {
            buttons.add(Button.success("ringTile_" + pos,
                activeGame.getTileByPosition(pos).getRepresentationForButtons(activeGame, player)));
        }
        buttons.add(Button.secondary("getTilesThisFarAway_" + (maxDistance + 1),
            "Get Tiles " + (maxDistance + 1) + " Spaces Away"));

        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message,
            buttons);
    }

    public static void getTilesThisFarAway(Player player, Game activeGame, ButtonInteractionEvent event,
        String buttonID) {
        int desiredDistance = Integer.parseInt(buttonID.split("_")[1]);
        Map<String, Integer> distances = CheckDistance.getTileDistancesRelativeToAllYourUnlockedTiles(activeGame,
            player);
        int maxDistance = desiredDistance;
        List<Button> buttons = new ArrayList<>();
        if (desiredDistance > 0) {
            buttons.add(Button.secondary("getTilesThisFarAway_" + (maxDistance - 1),
                "Get Tiles " + (maxDistance - 1) + " Spaces Away"));
        }
        for (String pos : CheckDistance.getAllTilesACertainDistanceAway(activeGame, player, distances,
            desiredDistance)) {
            Tile tile = activeGame.getTileByPosition(pos);
            String tileRepresentation = tile.getRepresentationForButtons(activeGame, player);
            if (!tileRepresentation.contains("Hyperlane")) {
                buttons.add(Button.success("ringTile_" + pos, tileRepresentation));
            }
        }
        buttons.add(Button.secondary("getTilesThisFarAway_" + (maxDistance + 1),
            "Get Tiles " + (maxDistance + 1) + " Spaces Away"));

        String message = "Doing a tactical action. Please select the tile you want to activate";
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message,
            buttons);
        event.getMessage().delete().queue();
    }

    public static void selectActiveSystem(Player player, Game activeGame, ButtonInteractionEvent event,
        String buttonID) {
        String pos = buttonID.replace("ringTile_", "");
        activeGame.setActiveSystem(pos);
        List<Button> systemButtons = ButtonHelper.getTilesToMoveFrom(player, activeGame, event);
        MessageHelper.sendMessageToChannel(event.getChannel(), player.getRepresentation(true, true) + " activated "
            + activeGame.getTileByPosition(pos).getRepresentationForButtons(activeGame, player));

        if (!activeGame.isFoWMode()) {

            for (Player player_ : activeGame.getRealPlayers()) {
                if (!activeGame.getL1Hero() && !player.getFaction().equalsIgnoreCase(player_.getFaction())
                    && !player_.isPlayerMemberOfAlliance(player)
                    && FoWHelper.playerHasUnitsInSystem(player_, activeGame.getTileByPosition(pos))) {
                    String msgA = player_.getRepresentation()
                        + " has units in the system";
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), msgA);
                }
            }
        } else {
            List<Player> playersAdj = FoWHelper.getAdjacentPlayers(activeGame, pos, true);
            for (Player player_ : playersAdj) {
                String playerMessage = player_.getRepresentation(true, true) + " - System " + pos
                    + " has been activated ";
                MessageHelper.sendPrivateMessageToPlayer(player_, activeGame, playerMessage);
            }
            ButtonHelper.resolveOnActivationEnemyAbilities(activeGame, activeGame.getTileByPosition(pos), player, false,
                event);
        }
        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "celdauricommander")
            && ButtonHelper.getTilesOfPlayersSpecificUnits(activeGame, player, UnitType.Spacedock)
                .contains(activeGame.getTileByPosition(pos))) {
            List<Button> buttons = new ArrayList<>();
            Button getCommButton = Button.primary("gain_1_comms", "Gain 1 Commodity")
                .withEmoji(Emoji.fromFormatted(Emojis.comm));
            buttons.add(getCommButton);
            String msg = player.getRepresentation()
                + " you have Celdauri Commander and activated a system with your SD. Please hit button to get a commodity";
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg, buttons);
        }

        List<Player> playersWithPds2 = ButtonHelper.tileHasPDS2Cover(player, activeGame, pos);
        if (!activeGame.isFoWMode() && playersWithPds2.size() > 0 && !activeGame.getL1Hero()) {
            StringBuilder pdsMessage = new StringBuilder(player.getRepresentation(true, true)
                + " the selected system is in range of space cannon units owned by");
            for (Player playerWithPds : playersWithPds2) {
                pdsMessage.append(" ").append(playerWithPds.getRepresentation());
            }
            MessageHelper.sendMessageToChannel(event.getChannel(), pdsMessage.toString());
        }

        List<Button> button3 = ButtonHelperAgents.getL1Z1XAgentButtons(activeGame, player);
        if (player.hasUnexhaustedLeader("l1z1xagent") && !button3.isEmpty() && !activeGame.getL1Hero()) {
            String msg = player.getRepresentation(true, true) + " You can use buttons to resolve " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "I48S (L1Z1Z Agent) if you want";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, button3);
        }
        Tile tile = activeGame.getTileByPosition(pos);
        if (tile.getPlanetUnitHolders().isEmpty()
            && ButtonHelper.doesPlayerHaveFSHere("mortheus_flagship", player, tile)
            && !tile.getUnitHolders().get("space").getTokenList().contains(Mapper.getTokenID(Constants.FRONTIER))) {
            String msg = player.getRepresentation(true, true)
                + " automatically added 1 frontier token to the system due to Mortheus Flagship";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
            AddToken.addToken(event, tile, Constants.FRONTIER, activeGame);
        }
        List<Button> button2 = ButtonHelper.scanlinkResolution(player, activeGame, event);
        if ((player.getTechs().contains("sdn") || player.getTechs().contains("absol_sdn")) && !button2.isEmpty()
            && !activeGame.getL1Hero()) {
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Please resolve scanlink",
                button2);
            if (player.hasAbility("awaken") || player.hasUnit("titans_flagship")) {
                ButtonHelper.resolveTitanShenanigansOnActivation(player, activeGame, activeGame.getTileByPosition(pos),
                    event);
            }
        } else {
            if (player.hasAbility("awaken")) {
                ButtonHelper.resolveTitanShenanigansOnActivation(player, activeGame, activeGame.getTileByPosition(pos),
                    event);
            }

        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            "Use buttons to select the first system you want to move from", systemButtons);
        if (player.hasAbility("recycled_materials")) {
            List<Button> buttons = ButtonHelperFactionSpecific.getRohDhnaRecycleButtons(activeGame, player);
            if (!buttons.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                    "Use buttons to select which unit to recycle", buttons);
            }
        }
        if (player.hasRelic("absol_plenaryorbital") && !tile.isHomeSystem() && !tile.getUnitHolders().containsKey("mr")
            && !player.hasUnit("plenaryorbital")) {
            List<Button> buttons4 = ButtonHelper.getAbsolOrbitalButtons(activeGame, player);
            if (buttons4.size() > 0) {
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                    "You can place down the plenary orbital",
                    buttons4);
            }
        }
        if (!activeGame.isFoWMode()) {
            int abilities = 0;
            if (!activeGame.getL1Hero()) {
                abilities = ButtonHelper.resolveOnActivationEnemyAbilities(activeGame,
                    activeGame.getTileByPosition(pos), player, false, event);
            }
            // if (abilities > 0 ) {
            // List<Button> buttons = new ArrayList<>();
            // buttons.add(Button.success("doActivation_" + pos, "Confirm"));
            // buttons.add(Button.danger("deleteButtons", "This activation was a mistake"));
            // String msg = "# " + ButtonHelper.getIdent(player) + " You are about to
            // automatically trigger some abilities by activating this system. Please hit
            // confirm before continuing";
            // MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg,
            // buttons);
            // }
        }

        event.getMessage().delete().queue();

    }

}
