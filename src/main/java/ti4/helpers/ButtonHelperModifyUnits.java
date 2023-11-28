package ti4.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands.tokens.AddCC;
import ti4.commands.units.AddUnits;
import ti4.commands.units.MoveUnits;
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

public class ButtonHelperModifyUnits {

    public static void infiltratePlanet(Player player, Game activeGame, UnitHolder uH, ButtonInteractionEvent event) {
        int sdAmount = 0;
        int pdsAmount = 0;
        for (Player p2 : activeGame.getRealPlayers()) {
            sdAmount = uH.getUnitCount(UnitType.CabalSpacedock, p2.getColor()) + sdAmount + uH.getUnitCount(UnitType.Spacedock, p2.getColor());
            new RemoveUnits().unitParsing(event, p2.getColor(), activeGame.getTileFromPlanet(uH.getName()), sdAmount + " sd " + uH.getName(), activeGame);
            new RemoveUnits().unitParsing(event, p2.getColor(), activeGame.getTileFromPlanet(uH.getName()), sdAmount + " csd " + uH.getName(), activeGame);
            pdsAmount = uH.getUnitCount(UnitType.Pds, p2.getColor()) + pdsAmount;
            new RemoveUnits().unitParsing(event, p2.getColor(), activeGame.getTileFromPlanet(uH.getName()), pdsAmount + " pds " + uH.getName(), activeGame);
        }
        if (pdsAmount > 0) {
            new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileFromPlanet(uH.getName()), pdsAmount + " pds " + uH.getName(), activeGame);
        }
        if (player.hasUnit("cabal_spacedock") && sdAmount > 0) {
            new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileFromPlanet(uH.getName()), sdAmount + " csd " + uH.getName(), activeGame);
        } else {
            if (sdAmount > 0) {
                new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileFromPlanet(uH.getName()), sdAmount + " sd " + uH.getName(), activeGame);
            }
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), ButtonHelper.getIdentOrColor(player, activeGame) + " replaced " + pdsAmount + " pds and " + sdAmount + " space docks on "
            + Helper.getPlanetRepresentation(uH.getName(), activeGame) + " with their own units");

    }

    public static List<Button> getRetreatSystemButtons(Player player, Game activeGame, String pos1, boolean skilled) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        String skilledS = "";
        if (skilled) {
            skilledS = "_skilled";
        }
        for (String pos2 : FoWHelper.getAdjacentTiles(activeGame, pos1, player, false)) {
            if (pos1.equalsIgnoreCase(pos2)) {
                continue;
            }
            Tile tile2 = activeGame.getTileByPosition(pos2);
            if (!FoWHelper.otherPlayersHaveShipsInSystem(player, tile2, activeGame)) {
                if (!FoWHelper.otherPlayersHaveUnitsInSystem(player, tile2, activeGame) || skilled) {
                    if (FoWHelper.playerIsInSystem(activeGame, tile2, player) || player.hasTech("det") || skilled) {
                        buttons.add(Button.secondary(finChecker + "retreatUnitsFrom_" + pos1 + "_" + pos2 + skilledS, "Retreat to " + tile2.getRepresentationForButtons(activeGame, player)));
                    }
                }
            }

        }
        return buttons;
    }

    public static List<Button> getRetreatingGroundTroopsButtons(Player player, Game activeGame, ButtonInteractionEvent event, String pos1, String pos2) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        Tile tile = activeGame.getTileByPosition(pos1);
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            String name = entry.getKey();
            String representation = planetRepresentations.get(name);
            if (representation == null) {
                representation = name;
            }
            UnitHolder unitHolder = entry.getValue();
            if (unitHolder instanceof Planet planet) {
                int limit = unitHolder.getUnitCount(UnitType.Infantry, player.getColor());
                for (int x = 1; x < limit + 1; x++) {
                    if (x > 2) {
                        break;
                    }
                    Button validTile2 = Button.success(finChecker + "retreatGroundUnits_" + pos1 + "_" + pos2 + "_" + x + "infantry_" + representation,
                        "Retreat " + x + " Infantry on " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame)).withEmoji(Emoji.fromFormatted(Emojis.infantry));
                    buttons.add(validTile2);
                }
                limit = unitHolder.getUnitCount(UnitType.Mech, player.getColor());
                for (int x = 1; x < limit + 1; x++) {
                    if (x > 2) {
                        break;
                    }
                    Button validTile2 = Button.primary(finChecker + "retreatGroundUnits_" + pos1 + "_" + pos2 + "_" + x + "mech_" + representation,
                        "Retreat " + x + " Mech(s) on " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame)).withEmoji(Emoji.fromFormatted(Emojis.mech));
                    buttons.add(validTile2);
                }

            }
        }
        Button concludeMove = Button.secondary(finChecker + "deleteButtons", "Done Retreating troops");
        buttons.add(concludeMove);
        if (player.getLeaderIDs().contains("naazcommander") && !player.hasLeaderUnlocked("naazcommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "naaz", event);
        }
        if (player.getLeaderIDs().contains("empyreancommander") && !player.hasLeaderUnlocked("empyreancommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "empyrean", event);
        }
        return buttons;
    }

    public static void finishLanding(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player) {
        if (!event.getMessage().getContentRaw().contains("Moved all units to the space area.")) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), event.getMessage().getContentRaw());
        }

        String message = "Landed troops. Use buttons to decide if you want to build or finish the activation";
        ButtonHelperFactionSpecific.checkBlockadeStatusOfEverything(player, activeGame, event);
        Tile tile = activeGame.getTileByPosition(activeGame.getActiveSystem());
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            if ("space".equalsIgnoreCase(unitHolder.getName())) {
                continue;
            }
            List<Player> players = ButtonHelper.getPlayersWithUnitsOnAPlanet(activeGame, tile, unitHolder.getName());
            if (players.size() > 1 && !player.getAllianceMembers().contains(players.get(0).getFaction()) && !player.getAllianceMembers().contains(players.get(1).getFaction())) {
                Player player2 = players.get(0);
                if (player2 == player) {
                    player2 = players.get(1);
                }
                String threadName = ButtonHelper.combatThreadName(activeGame, player, player2, tile);
                if (!activeGame.isFoWMode()) {
                    ButtonHelper.makeACombatThread(activeGame, activeGame.getActionsChannel(), player, player2, threadName, tile, event, "ground");
                } else {
                    ButtonHelper.makeACombatThread(activeGame, player.getPrivateChannel(), player, player2, threadName, tile, event, "ground");
                    ButtonHelper.makeACombatThread(activeGame, player2.getPrivateChannel(), player2, player, threadName, tile, event, "ground");
                    for (Player player3 : activeGame.getRealPlayers()) {
                        if (player3 == player2 || player3 == player) {
                            continue;
                        }
                        if (!tile.getRepresentationForButtons(activeGame, player3).contains("(")) {
                            continue;
                        }
                        ButtonHelper.makeACombatThread(activeGame, player3.getPrivateChannel(), player3, player3, threadName, tile, event, "ground");
                    }
                }
            }
            if (unitHolder.getUnitCount(UnitType.Fighter, player.getColor()) > 1) {
                List<Button> b2s = new ArrayList<>();
                b2s.add(Button.success("returnFFToSpace_" + tile.getPosition(), "Return Fighters to Space"));
                b2s.add(Button.danger("deleteButtons", "Delete These Buttons"));
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation(true, true)
                    + " you can use this button to return naalu fighters to space after combat concludes. This only needs to be done once. Reminder you cant take over a planet with only fighters.");
            }
        }
        List<Button> systemButtons = ButtonHelper.landAndGetBuildButtons(player, activeGame, event);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
        event.getMessage().delete().queue();
    }

    public static void retreatGroundUnits(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String buttonLabel) {
        String rest = buttonID.replace("retreatGroundUnits_", "").replace("'", "");
        String pos1 = rest.substring(0, rest.indexOf("_"));
        rest = rest.replace(pos1 + "_", "");
        String pos2 = rest.substring(0, rest.indexOf("_"));
        rest = rest.replace(pos2 + "_", "");
        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1);
        String unitType;
        String planet = "";
        if (rest.contains("_")) {
            unitType = rest.split("_")[0];
            planet = rest.split("_")[1].replace(" ", "").toLowerCase();
        } else {
            unitType = rest;
        }

        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unitType), player.getColor());
        if (buttonLabel.toLowerCase().contains("damaged")) {
            new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(pos2), amount + " " + unitType, activeGame);
            activeGame.getTileByPosition(pos2).addUnitDamage("space", unitKey, amount);
        } else {
            new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(pos2), amount + " " + unitType, activeGame);
        }

        //activeMap.getTileByPosition(pos1).removeUnit(planet,key, amount);
        new RemoveUnits().removeStuff(event, activeGame.getTileByPosition(pos1), amount, planet, unitKey, player.getColor(), false, activeGame);

        List<Button> systemButtons = getRetreatingGroundTroopsButtons(player, activeGame, event, pos1, pos2);
        String retreatMessage = ident + " Retreated " + amount + " " + unitType + " on " + planet + " to " + activeGame.getTileByPosition(pos2).getRepresentationForButtons(activeGame, player);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), retreatMessage);
        event.getMessage().editMessage(event.getMessage().getContentRaw())
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }

    public static void retreatSpaceUnits(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player) {
        String both = buttonID.replace("retreatUnitsFrom_", "");
        String pos1 = both.split("_")[0];
        String pos2 = both.split("_")[1];
        Tile tile1 = activeGame.getTileByPosition(pos1);
        Tile tile2 = activeGame.getTileByPosition(pos2);
        tile2 = MoveUnits.flipMallice(event, tile2, activeGame);
        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "kollecccommander") && !buttonID.contains("skilled") && !AddCC.hasCC(event, player.getColor(), tile1)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getIdent(player) + " did not place a cc in the retreat system due to kollecc commander");
        } else {
            AddCC.addCC(event, player.getColor(), tile2, true);
        }

        for (Map.Entry<String, UnitHolder> entry : tile1.getUnitHolders().entrySet()) {
            UnitHolder unitHolder = entry.getValue();
            Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());
            if (unitHolder instanceof Planet) continue;

            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null) continue;

                UnitKey unitKey = unitEntry.getKey();
                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                int totalUnits = unitEntry.getValue();
                int damagedUnits = 0;

                if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                    damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                }

                new RemoveUnits().removeStuff(event, tile1, totalUnits, "space", unitKey, player.getColor(), false, activeGame);
                new AddUnits().unitParsing(event, player.getColor(), tile2, totalUnits + " " + unitName, activeGame);
                if (damagedUnits > 0) {
                    activeGame.getTileByPosition(pos2).addUnitDamage("space", unitKey, damagedUnits);
                }
            }
        }
    }

    public static void umbatTile(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        String pos = buttonID.replace("umbatTile_", "");
        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, activeGame, activeGame.getTileByPosition(pos), "muaatagent", "place");
        String message = player.getRepresentation() + " Use the buttons to produce units. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        event.getMessage().delete().queue();
    }

    public static void genericPlaceUnit(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String trueIdentity, String finsFactionCheckerPrefix) {
        String unitNPlanet = buttonID.replace("place_", "");
        String unitLong = unitNPlanet.substring(0, unitNPlanet.indexOf("_"));
        String planetName = unitNPlanet.replace(unitLong + "_", "");
        String unit = AliasHandler.resolveUnit(unitLong);

        String successMessage;
        String playerRep = player.getRepresentation();
        if ("sd".equalsIgnoreCase(unit)) {
            if (player.ownsUnit("saar_spacedock") || player.ownsUnit("saar_spacedock2")) {
                new AddUnits().unitParsing(event, player.getColor(),
                    activeGame.getTile(AliasHandler.resolveTile(planetName)), unit, activeGame);
                successMessage = "Placed a space dock in the space area of the "
                    + Helper.getPlanetRepresentation(planetName, activeGame) + " system.";
            } else if (player.ownsUnit("cabal_spacedock") || player.ownsUnit("cabal_spacedock2")) {
                new AddUnits().unitParsing(event, player.getColor(),
                    activeGame.getTile(AliasHandler.resolveTile(planetName)), "csd " + planetName, activeGame);
                successMessage = "Placed a cabal space dock on "
                    + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
                if (player.getLeaderIDs().contains("cabalcommander") && !player.hasLeaderUnlocked("cabalcommander")) {
                    ButtonHelper.commanderUnlockCheck(player, activeGame, "cabal", event);
                }
            } else {
                new AddUnits().unitParsing(event, player.getColor(),
                    activeGame.getTile(AliasHandler.resolveTile(planetName)), unit + " " + planetName,
                    activeGame);
                successMessage = "Placed a " + Emojis.spacedock + " on "
                    + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
            }
        } else if ("pds".equalsIgnoreCase(unitLong)) {
            new AddUnits().unitParsing(event, player.getColor(),
                activeGame.getTile(AliasHandler.resolveTile(planetName)), unitLong + " " + planetName,
                activeGame);
            successMessage = "Placed a " + Emojis.pds + " on "
                + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
        } else {
            Tile tile;
            String producedOrPlaced = "Produced";
            if ("gf".equalsIgnoreCase(unit) || "mf".equalsIgnoreCase(unit) || "2gf".equalsIgnoreCase(unitLong)) {
                if ("2gf".equalsIgnoreCase(unitLong)) {
                    if (!planetName.contains("space")) {
                        new AddUnits().unitParsing(event, player.getColor(),
                            activeGame.getTile(AliasHandler.resolveTile(planetName)), "2 gf " + planetName,
                            activeGame);
                        successMessage = producedOrPlaced + " 2 " + Emojis.infantry + " on " + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
                    } else {
                        tile = activeGame.getTileByPosition(planetName.replace("space", ""));
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, "2 gf",
                            activeGame);
                        successMessage = producedOrPlaced + " 2 " + Emojis.infantry + " in space.";
                    }
                } else {

                    if (!planetName.contains("space")) {
                        tile = activeGame.getTile(AliasHandler.resolveTile(planetName));
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, unit + " " + planetName,
                            activeGame);
                        successMessage = producedOrPlaced + " a " + Emojis.getEmojiFromDiscord(unitLong) + " on " + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
                    } else {
                        tile = activeGame.getTileByPosition(planetName.replace("space", ""));
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, unit,
                            activeGame);
                        successMessage = producedOrPlaced + " a " + Emojis.getEmojiFromDiscord(unitLong) + " in space.";
                    }

                }
            } else {
                if ("2ff".equalsIgnoreCase(unitLong)) {
                    new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(planetName),
                        "2 ff", activeGame);
                    successMessage = "Produced 2 " + Emojis.fighter + " in tile "
                        + AliasHandler.resolveTile(planetName) + ".";
                } else if ("2destroyer".equalsIgnoreCase(unitLong)) {
                    new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(planetName),
                        "2 destroyer", activeGame);
                    successMessage = "Produced 2 " + Emojis.destroyer + " in tile "
                        + AliasHandler.resolveTile(planetName) + ".";
                } else {
                    new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(planetName),
                        unit, activeGame);
                    successMessage = "Produced a " + Emojis.getEmojiFromDiscord(unitLong) + " in tile "
                        + AliasHandler.resolveTile(planetName) + ".";
                }

            }
        }
        if (("sd".equalsIgnoreCase(unit) || "pds".equalsIgnoreCase(unitLong)) && event.getMessage().getContentRaw().contains("for construction")) {

            if (activeGame.isFoWMode() || !"action".equalsIgnoreCase(activeGame.getCurrentPhase())) {
                MessageHelper.sendMessageToChannel(event.getChannel(), playerRep + " " + successMessage);
            } else {
                ButtonHelper.sendMessageToRightStratThread(player, activeGame, playerRep + " " + successMessage, "construction");
            }
            if (player.hasLeader("mahactagent") || player.hasExternalAccessToLeader("mahactagent")) {
                String message = playerRep + " Would you like to put a cc from reinforcements in the same system?";
                Button placeCCInSystem = Button.success(
                    finsFactionCheckerPrefix + "reinforcements_cc_placement_" + planetName,
                    "Place A CC From Reinforcements In The System.");
                Button placeConstructionCCInSystem = Button.secondary(
                    finsFactionCheckerPrefix + "placeHolderOfConInSystem_" + planetName,

                    "Place A CC From The Construction Holder's Reinforcements by using Mahact Agent");
                Button NoDontWantTo = Button.primary(finsFactionCheckerPrefix + "deleteButtons",
                    "Don't Place A CC In The System.");
                List<Button> buttons = List.of(placeCCInSystem, placeConstructionCCInSystem, NoDontWantTo);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            } else {
                if (!player.getSCs().contains(Integer.parseInt("4")) && "action".equalsIgnoreCase(activeGame.getCurrentPhase())) {
                    String color = player.getColor();
                    String tileID = AliasHandler.resolveTile(planetName.toLowerCase());
                    Tile tile = activeGame.getTile(tileID);
                    if (tile == null) {
                        tile = activeGame.getTileByPosition(tileID);
                    }
                    if (Mapper.isColorValid(color)) {
                        AddCC.addCC(event, color, tile);
                    }

                    if (activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(event.getChannel(),
                            playerRep + " Placed A CC From Reinforcements In The "
                                + Helper.getPlanetRepresentation(planetName, activeGame) + " system");
                    } else {
                        ButtonHelper.sendMessageToRightStratThread(player, activeGame, playerRep + " Placed A CC From Reinforcements In The "
                            + Helper.getPlanetRepresentation(planetName, activeGame) + " system", "construction");
                    }
                }
            }

            event.getMessage().delete().queue();

        } else {
            String editedMessage = event.getMessage().getContentRaw();
            if (editedMessage.contains("Produced")) {
                editedMessage = editedMessage + "\n " + successMessage;
            } else {
                editedMessage = playerRep + " " + successMessage;
            }

            if (editedMessage.contains("place 2 infantry")) {
                successMessage = "Placed 2 " + Emojis.infantry + " on "
                    + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), successMessage);
                event.getMessage().delete().queue();
            } else {
                event.getMessage().editMessage(editedMessage).queue();
            }

        }

        if (player.getLeaderIDs().contains("titanscommander") && !player.hasLeaderUnlocked("titanscommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "titans", event);
        }
        if (player.getLeaderIDs().contains("saarcommander") && !player.hasLeaderUnlocked("saarcommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "saar", event);
        }
        if (player.hasAbility("necrophage")) {
            player.setCommoditiesTotal(1 + ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, Mapper.getUnitKey(AliasHandler.resolveUnit("spacedock"), player.getColor())));
        }
        if (player.getLeaderIDs().contains("mentakcommander") && !player.hasLeaderUnlocked("mentakcommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "mentak", event);
        }
        if (player.getLeaderIDs().contains("l1z1xcommander") && !player.hasLeaderUnlocked("l1z1xcommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "l1z1x", event);
        }
        if (player.getLeaderIDs().contains("muaatcommander") && !player.hasLeaderUnlocked("muaatcommander") && "warsun".equalsIgnoreCase(unitLong)) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "muaat", event);
        }
        if (player.getLeaderIDs().contains("argentcommander") && !player.hasLeaderUnlocked("argentcommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "argent", event);
        }

        if (player.getLeaderIDs().contains("naazcommander") && !player.hasLeaderUnlocked("naazcommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "naaz", event);
        }
        if (player.getLeaderIDs().contains("arboreccommander") && !player.hasLeaderUnlocked("arboreccommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "arborec", event);
        }
    }

    public static void placeUnitAndDeleteButton(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String trueIdentity) {
        String unitNPlanet = buttonID.replace("placeOneNDone_", "");
        String skipbuild = unitNPlanet.substring(0, unitNPlanet.indexOf("_"));
        unitNPlanet = unitNPlanet.replace(skipbuild + "_", "");
        String unitLong = unitNPlanet.substring(0, unitNPlanet.indexOf("_"));
        String planetName = unitNPlanet.replace(unitLong + "_", "");
        String unit = AliasHandler.resolveUnit(unitLong);
        String producedOrPlaced = "Produced";
        if ("skipbuild".equalsIgnoreCase(skipbuild)) {
            producedOrPlaced = "Placed";
        }
        String successMessage;
        String playerRep = player.getRepresentation();
        if ("sd".equalsIgnoreCase(unit)) {
            if (player.ownsUnit("saar_spacedock") || player.ownsUnit("saar_spacedock2")) {
                new AddUnits().unitParsing(event, player.getColor(),
                    activeGame.getTile(AliasHandler.resolveTile(planetName)), unit, activeGame);
                successMessage = "Placed a space dock in the space area of the "
                    + Helper.getPlanetRepresentation(planetName, activeGame) + " system.";
            } else if (player.ownsUnit("cabal_spacedock") || player.ownsUnit("cabal_spacedock2")) {
                new AddUnits().unitParsing(event, player.getColor(),
                    activeGame.getTile(AliasHandler.resolveTile(planetName)), "csd " + planetName, activeGame);
                successMessage = "Placed a cabal space dock on "
                    + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
            } else {
                new AddUnits().unitParsing(event, player.getColor(),
                    activeGame.getTile(AliasHandler.resolveTile(planetName)), unit + " " + planetName,
                    activeGame);
                successMessage = "Placed a " + Emojis.spacedock + " on "
                    + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
            }
        } else if ("pds".equalsIgnoreCase(unitLong)) {
            new AddUnits().unitParsing(event, player.getColor(),
                activeGame.getTile(AliasHandler.resolveTile(planetName)), unitLong + " " + planetName,
                activeGame);
            successMessage = "Placed a " + Emojis.pds + " on "
                + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
        } else {
            Tile tile;
            if ("gf".equalsIgnoreCase(unit) || "mf".equalsIgnoreCase(unit) || "2gf".equalsIgnoreCase(unitLong) || "3gf".equalsIgnoreCase(unitLong)) {
                if ("2gf".equalsIgnoreCase(unitLong) || "3gf".equalsIgnoreCase(unitLong)) {
                    String amount = "" + unitLong.charAt(0);
                    if (!planetName.contains("space")) {
                        new AddUnits().unitParsing(event, player.getColor(),
                            activeGame.getTile(AliasHandler.resolveTile(planetName)), amount + " gf " + planetName,
                            activeGame);
                        successMessage = producedOrPlaced + " " + amount + " " + Emojis.infantry + " on " + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
                    } else {
                        tile = activeGame.getTileByPosition(planetName.replace("space", ""));
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, amount + " gf ",
                            activeGame);
                        successMessage = producedOrPlaced + " " + amount + " " + Emojis.infantry + " in space.";
                    }
                } else {

                    if (!planetName.contains("space")) {
                        tile = activeGame.getTile(AliasHandler.resolveTile(planetName));
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, unit + " " + planetName,
                            activeGame);
                        successMessage = producedOrPlaced + " a " + Emojis.getEmojiFromDiscord(unitLong) + " on " + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
                    } else {
                        tile = activeGame.getTileByPosition(planetName.replace("space", ""));
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, unit,
                            activeGame);
                        successMessage = producedOrPlaced + " a " + Emojis.getEmojiFromDiscord(unitLong) + " in space.";
                    }

                }
            } else {
                if ("2ff".equalsIgnoreCase(unitLong)) {
                    new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(planetName), "2 ff", activeGame);
                    successMessage = producedOrPlaced + " 2 " + Emojis.fighter + " in tile " + AliasHandler.resolveTile(planetName) + ".";
                } else if ("2destroyer".equalsIgnoreCase(unitLong)) {
                    new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(planetName), "2 destroyer", activeGame);
                    successMessage = producedOrPlaced + " 2 " + Emojis.destroyer + " in tile " + AliasHandler.resolveTile(planetName) + ".";
                } else {
                    new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(planetName), unit, activeGame);
                    successMessage = producedOrPlaced + " a " + Emojis.getEmojiFromDiscord(unitLong) + " in tile " + AliasHandler.resolveTile(planetName) + ".";
                }

            }
            if (player.getLeaderIDs().contains("l1z1xcommander") && !player.hasLeaderUnlocked("l1z1xcommander")) {
                ButtonHelper.commanderUnlockCheck(player, activeGame, "l1z1x", event);
            }
        }
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), playerRep + " " + successMessage);
        String message2 = trueIdentity + " Click the names of the planets you wish to exhaust.";
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, event);
        Button DoneExhausting;
        if (!buttonID.contains("deleteButtons")) {
            DoneExhausting = Button.danger("deleteButtons_" + buttonID, "Done Exhausting Planets");
        } else {
            DoneExhausting = Button.danger("deleteButtons", "Done Exhausting Planets");
        }
        buttons.add(DoneExhausting);
        if (!"skipbuild".equalsIgnoreCase(skipbuild)) {
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message2, buttons);
        }

        if (player.getLeaderIDs().contains("titanscommander") && !player.hasLeaderUnlocked("titanscommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "titans", event);
        }
        if (player.getLeaderIDs().contains("saarcommander") && !player.hasLeaderUnlocked("saarcommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "saar", event);
        }
        if (player.hasAbility("necrophage")) {
            player.setCommoditiesTotal(1 + ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, Mapper.getUnitKey(AliasHandler.resolveUnit("spacedock"), player.getColor())));
        }
        if (player.getLeaderIDs().contains("mentakcommander") && !player.hasLeaderUnlocked("mentakcommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "mentak", event);
        }
        if (player.getLeaderIDs().contains("l1z1xcommander") && !player.hasLeaderUnlocked("l1z1xcommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "l1z1x", event);
        }
        if (player.getLeaderIDs().contains("muaatcommander") && !player.hasLeaderUnlocked("muaatcommander") && "warsun".equalsIgnoreCase(unitLong)) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "muaat", event);
        }
        if (player.getLeaderIDs().contains("argentcommander") && !player.hasLeaderUnlocked("argentcommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "argent", event);
        }
        if (player.getLeaderIDs().contains("naazcommander") && !player.hasLeaderUnlocked("naazcommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "naaz", event);
        }
        if (player.getLeaderIDs().contains("arboreccommander") && !player.hasLeaderUnlocked("arboreccommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "arborec", event);
        }

        event.getMessage().delete().queue();
    }

    public static void spaceLandedUnits(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String buttonLabel) {
        String rest = buttonID.replace("spaceUnits_", "");
        String pos = rest.substring(0, rest.indexOf("_"));
        rest = rest.replace(pos + "_", "");
        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1);
        String unitName;
        String planet = "";
        if (rest.contains("_")) {
            unitName = rest.split("_")[0];
            planet = rest.split("_")[1].replace(" ", "").toLowerCase().replace("-", "").replace("'", "");
        } else {
            unitName = rest;
        }

        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unitName), player.getColor());
        new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(pos), amount + " " + unitName, activeGame);
        if (buttonLabel.toLowerCase().contains("damaged")) {
            activeGame.getTileByPosition(pos).addUnitDamage("space", unitKey, amount);
        }

        activeGame.getTileByPosition(pos).removeUnit(planet, unitKey, amount);
        List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, activeGame, event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), ident + "Undid landing of " + amount + " " + unitName + " on " + planet);
        event.getMessage().editMessage(event.getMessage().getContentRaw())
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }

    public static void landingUnits(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String buttonLabel) {
        String rest = buttonID.replace("landUnits_", "");
        String pos = rest.substring(0, rest.indexOf("_"));
        rest = rest.replace(pos + "_", "");
        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1);
        String unitName;
        String planet = "";
        if (rest.contains("_")) {
            unitName = rest.split("_")[0];
            planet = rest.split("_")[1].replace(" ", "").toLowerCase().replace("'", "").replace("-", "");
        } else {
            unitName = rest;
        }

        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unitName), player.getColor());
        new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(pos), amount + " " + unitName + " " + planet, activeGame);
        if (buttonLabel.toLowerCase().contains("damaged")) {
            activeGame.getTileByPosition(pos).addUnitDamage(planet, unitKey, amount);
        }

        activeGame.getTileByPosition(pos).removeUnit("space", unitKey, amount);
        List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, activeGame, event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), ident + " Landed " + amount + " " + unitName + " on " + planet);
        event.getMessage().editMessage(event.getMessage().getContentRaw())
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }

    public static void offerDomnaStep2Buttons(ButtonInteractionEvent event, Game activeGame, Player player, String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile tile = activeGame.getTileByPosition(pos);
        List<Button> buttons = new ArrayList<>();
        for (UnitKey unit : tile.getUnitHolders().get("space").getUnits().keySet()) {
            if (unit.getUnitType() == UnitType.Infantry || unit.getUnitType() == UnitType.Mech) {
                continue;
            }
            String unitName = ButtonHelper.getUnitName(unit.asyncID());
            Button validTile = Button.success("domnaStepTwo_" + pos + "_" + unitName, "Move 1 " + unit.unitName());
            buttons.add(validTile);
        }

        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Select unit you want to move", buttons);
        event.getMessage().delete().queue();
    }

    public static void offerDomnaStep3Buttons(ButtonInteractionEvent event, Game activeGame, Player player, String buttonID) {
        String pos1 = buttonID.split("_")[1];
        String unit = buttonID.split("_")[2];
        List<Button> buttons = new ArrayList<>();
        for (String pos2 : FoWHelper.getAdjacentTiles(activeGame, pos1, player, false)) {
            if (pos1.equalsIgnoreCase(pos2)) {
                continue;
            }
            Tile tile2 = activeGame.getTileByPosition(pos2);
            if (!FoWHelper.otherPlayersHaveShipsInSystem(player, tile2, activeGame)) {
                buttons.add(Button.secondary("domnaStepThree_" + pos1 + "_" + unit + "_" + pos2, "Move " + unit + " to " + tile2.getRepresentationForButtons(activeGame, player)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Select tile you want to move to", buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveDomnaStep3Buttons(ButtonInteractionEvent event, Game activeGame, Player player, String buttonID) {
        String pos1 = buttonID.split("_")[1];
        Tile tile1 = activeGame.getTileByPosition(pos1);
        String unit = buttonID.split("_")[2];
        String pos2 = buttonID.split("_")[3];
        Tile tile2 = activeGame.getTileByPosition(pos2);
        new AddUnits().unitParsing(event, player.getColor(), tile2, unit, activeGame);
        new RemoveUnits().unitParsing(event, player.getColor(), tile1, unit, activeGame);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getIdent(player) + " moved 1 " + unit + " from " + tile1.getRepresentationForButtons(activeGame, player) + " to "
            + tile2.getRepresentationForButtons(activeGame, player) + " using Domna legendary ability.");
        event.getMessage().delete().queue();
    }

    public static void offerCombatDroneButtons(ButtonInteractionEvent event, Game activeGame, Player player) {
        Tile tile = activeGame.getTileByPosition(activeGame.getActiveSystem());
        int numff;
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            if (unitHolder instanceof Planet) {
                continue;
            }
            numff = unitHolder.getUnitCount(UnitType.Fighter, player.getColor());
            for (int x = 1; x < numff + 1; x++) {
                buttons.add(Button.success("combatDroneConvert_" + x, "" + x));
            }
        }
        buttons.add(Button.danger("deleteButtons", "Delete These"));
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
            player.getRepresentation(true, true) + " choose how many fighters you want to convert to infantry", buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void offerMirvedaCommanderButtons(ButtonInteractionEvent event, Game activeGame, Player player) {
        Tile tile = activeGame.getTileByPosition(activeGame.getActiveSystem());
        int numinf;
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            if (unitHolder instanceof Planet) {
                continue;
            }
            numinf = unitHolder.getUnitCount(UnitType.Infantry, player.getColor());
            for (int x = 1; x < numinf + 1; x++) {
                buttons.add(Button.success("resolveMirvedaCommander_" + x, "" + x));
            }
        }
        buttons.add(Button.danger("deleteButtons", "Delete These"));
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
            player.getRepresentation(true, true) + " choose how many infantry you wish to convert to fighters", buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void resolvingMirvedaCommander(ButtonInteractionEvent event, Game activeGame, Player player, String ident, String buttonID) {
        Tile tile = activeGame.getTileByPosition(activeGame.getActiveSystem());
        int numff = Integer.parseInt(buttonID.split("_")[1]);
        if (numff > 0) {
            new RemoveUnits().unitParsing(event, player.getColor(), tile, numff + " infantry", activeGame);
            new AddUnits().unitParsing(event, player.getColor(), tile, numff + " fighters", activeGame);
        }
        List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, activeGame, event);
        String msg = ident + " Turned " + numff + " infantry into fighters using the combat drone ability";
        event.getMessage().editMessage(msg)
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }

    public static void resolvingCombatDrones(ButtonInteractionEvent event, Game activeGame, Player player, String ident, String buttonID) {
        Tile tile = activeGame.getTileByPosition(activeGame.getActiveSystem());
        int numff = Integer.parseInt(buttonID.split("_")[1]);
        if (numff > 0) {
            new RemoveUnits().unitParsing(event, player.getColor(), tile, numff + " fighters", activeGame);
            new AddUnits().unitParsing(event, player.getColor(), tile, numff + " infantry", activeGame);
        }
        List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, activeGame, event);
        String msg = ident + " Turned " + numff + " fighters into infantry using the combat drone ability";
        event.getMessage().editMessage(msg)
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }

    public static void movingUnitsInTacticalAction(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String buttonLabel) {
        String remove = "Move";
        HashMap<String, Integer> currentSystem = activeGame.getCurrentMovedUnitsFrom1System();
        HashMap<String, Integer> currentActivation = activeGame.getMovedUnitsFromCurrentActivation();
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
                    HashMap<UnitKey, Integer> units1 = unitHolder.getUnits();
                    Map<UnitKey, Integer> units = new HashMap<>(units1);

                    if (unitHolder instanceof Planet) {
                        for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                            if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                            UnitKey unitKey = unitEntry.getKey();
                            if ((unitKey.getUnitType() == UnitType.Infantry || unitKey.getUnitType() == UnitType.Mech)) {
                                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                                int amount = unitEntry.getValue();

                                rest = unitName.toLowerCase() + "_" + unitHolder.getName().toLowerCase();
                                if (currentSystem.containsKey(rest)) {
                                    activeGame.setSpecificCurrentMovedUnitsFrom1System(rest, currentSystem.get(rest) + amount);
                                } else {
                                    activeGame.setSpecificCurrentMovedUnitsFrom1System(rest, amount);
                                }
                                if (currentActivation.containsKey(unitName)) {
                                    activeGame.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitName,
                                        currentActivation.get(unitName) + amount);
                                } else {
                                    activeGame.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitName, amount);
                                }

                                new RemoveUnits().removeStuff(event, activeGame.getTileByPosition(pos), unitEntry.getValue(), unitHolder.getName(), unitKey, player.getColor(), false, activeGame);
                            }
                        }
                    } else {
                        for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                            if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                            UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                            if (unitModel == null) continue;

                            UnitKey unitKey = unitEntry.getKey();
                            String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                            int totalUnits = unitEntry.getValue();
                            int amount;

                            int damagedUnits = 0;
                            if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                                damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                            }

                            new RemoveUnits().removeStuff(event, activeGame.getTileByPosition(pos), totalUnits, "space", unitKey, player.getColor(), false, activeGame);
                            if (damagedUnits > 0) {
                                rest = unitName + "damaged";
                                amount = damagedUnits;
                                if (currentSystem.containsKey(rest)) {
                                    activeGame.setSpecificCurrentMovedUnitsFrom1System(rest, currentSystem.get(rest) + amount);
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
                                    activeGame.setSpecificCurrentMovedUnitsFrom1System(rest, currentSystem.get(rest) + amount);
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
            List<Button> systemButtons = ButtonHelper.getButtonsForAllUnitsInSystem(player, activeGame, activeGame.getTileByPosition(pos), remove);
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
            new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(pos), (amount * -1) + " " + unitName + " " + planet, activeGame);
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

            new RemoveUnits().removeStuff(event, activeGame.getTileByPosition(pos), amount, planetName, unitKey, player.getColor(), buttonLabel.toLowerCase().contains("damaged"), activeGame);
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
        List<Button> systemButtons = ButtonHelper.getButtonsForAllUnitsInSystem(player, activeGame, activeGame.getTileByPosition(pos), remove);
        event.getMessage().editMessage(message)
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }

    public static void assignHits(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String buttonLabel) {
        String rest;
        rest = buttonID.replace("assignHits_", "");
        String pos = rest.substring(0, rest.indexOf("_"));
        Tile tile = activeGame.getTileByPosition(pos);
        rest = rest.replace(pos + "_", "");
        Player cabal = Helper.getPlayerFromAbility(activeGame, "devour");
        if (rest.contains("All")) {
            String cID = Mapper.getColorID(player.getColor());
            for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
                UnitHolder unitHolder = entry.getValue();
                HashMap<UnitKey, Integer> units1 = unitHolder.getUnits();
                Map<UnitKey, Integer> units = new HashMap<>(units1);
                if (unitHolder instanceof Planet) {
                    if (!rest.contains("AllShips")) {

                        for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                            UnitKey unitKey = unitEntry.getKey();
                            if (!unitKey.getColorID().equals(cID)) continue;

                            String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                            int amount = unitEntry.getValue();

                            boolean cabalMech = cabal != null && unitHolder.getUnitCount(UnitType.Mech, cabal.getColor()) > 0 && cabal.hasUnit("cabal_mech")
                                && unitName.toLowerCase().contains("infantry") && !activeGame.getLaws().containsKey("articles_war");

                            new RemoveUnits().removeStuff(event, activeGame.getTileByPosition(pos), unitEntry.getValue(), unitHolder.getName(), unitKey, player.getColor(), false, activeGame);
                            if (cabal != null && FoWHelper.playerHasUnitsOnPlanet(cabal, tile, unitHolder.getName())
                                && ((!cabal.getFaction().equalsIgnoreCase(player.getFaction()) || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile) || cabalMech)
                                    || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile))) {
                                ButtonHelperFactionSpecific.cabalEatsUnit(player, activeGame, cabal, unitEntry.getValue(), unitName, event);
                            }
                            if ((player.getUnitsOwned().contains("mahact_infantry") || player.hasTech("cl2")) && unitName.toLowerCase().contains("inf")) {
                                ButtonHelperFactionSpecific.offerMahactInfButtons(player, activeGame);
                            }
                            if (player.hasInf2Tech() && unitName.toLowerCase().contains("inf")) {
                                ButtonHelper.resolveInfantryDeath(activeGame, player, amount);
                            }
                            if (unitKey.getUnitType() == UnitType.Mech && player.hasTech("sar")) {
                                for (int x = 0; x < amount; x++) {
                                    player.setTg(player.getTg() + 1);
                                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), player.getRepresentation() + " you gained 1tg (" + (player.getTg() - 1)
                                        + "->" + player.getTg() + ") from 1 of your mechs dying while you own Self-Assembly Routines. This is not an optional gain.");
                                    ButtonHelperAbilities.pillageCheck(player, activeGame);
                                }
                                ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
                            }
                            if (unitKey.getUnitType().equals(UnitType.Mech) && player.hasUnit("mykomentori_mech")) {
                                for (int x = 0; x < amount; x++) {
                                    ButtonHelper.rollMykoMechRevival(activeGame, player);
                                }
                            }
                        }
                    }
                } else {
                    for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                        UnitKey unitKey = unitEntry.getKey();
                        if (!unitKey.getColorID().equals(cID)) continue;
                        String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                        int amount = unitEntry.getValue();

                        new RemoveUnits().removeStuff(event, activeGame.getTileByPosition(pos), amount, "space", unitKey, player.getColor(), false, activeGame);
                        if (cabal != null && FoWHelper.playerHasShipsInSystem(cabal, tile)
                            && (!cabal.getFaction().equalsIgnoreCase(player.getFaction()) || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile))) {
                            ButtonHelperFactionSpecific.cabalEatsUnit(player, activeGame, cabal, amount, unitName, event);
                        }
                    }
                }
            }
            String message2 = ident + " Removed all units";
            if (rest.contains("AllShips")) {
                message2 = ident + " Removed all units in space area";
            }
            String message = event.getMessage().getContentRaw();
            List<Button> systemButtons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, activeGame, tile);
            event.getMessage().editMessage(message)
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message2);

            return;
        }
        int amount = Integer.parseInt(rest.charAt(0) + "");
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
        String planetName;
        if ("".equalsIgnoreCase(planet)) {
            planetName = "space";
        } else {
            planetName = planet.replace("'", "");
            planetName = AliasHandler.resolvePlanet(planetName);
        }

        new RemoveUnits().removeStuff(event, activeGame.getTileByPosition(pos), amount, planetName, unitKey, player.getColor(), buttonLabel.toLowerCase().contains("damaged"), activeGame);

        if ("".equalsIgnoreCase(planet)) {
            if (cabal != null && (!cabal.getFaction().equalsIgnoreCase(player.getFaction()) || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile))
                && FoWHelper.playerHasShipsInSystem(cabal, tile)) {
                ButtonHelperFactionSpecific.cabalEatsUnit(player, activeGame, cabal, amount, unitName, event);
            }
        } else {
            boolean cabalMech = false;
            if (cabal != null && activeGame.getTileByPosition(pos).getUnitHolders().get(planetName).getUnitCount(UnitType.Mech, cabal.getColor()) > 0 && cabal.hasUnit("cabal_mech")
                && unitName.toLowerCase().contains("infantry") && !activeGame.getLaws().containsKey("articles_war")) {
                cabalMech = true;
            }
            if (cabal != null && (!cabal.getFaction().equalsIgnoreCase(player.getFaction()) || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile) || cabalMech)
                && FoWHelper.playerHasUnitsOnPlanet(cabal, tile, planetName)) {
                ButtonHelperFactionSpecific.cabalEatsUnit(player, activeGame, cabal, amount, unitName, event);
            }
            if (unitKey.getUnitType().equals(UnitType.Mech) && player.hasTech("sar")) {
                for (int x = 0; x < amount; x++) {
                    player.setTg(player.getTg() + 1);
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), player.getRepresentation() + " you gained 1tg (" + (player.getTg() - 1) + "->"
                        + player.getTg() + ") from 1 of your mechs dying while you own Self-Assembly Routines. This is not an optional gain");
                    ButtonHelperAbilities.pillageCheck(player, activeGame);
                }
                ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
            }
            if (unitKey.getUnitType().equals(UnitType.Mech) && player.hasUnit("mykomentori_mech")) {
                for (int x = 0; x < amount; x++) {
                    ButtonHelper.rollMykoMechRevival(activeGame, player);
                }
            }
            if ((player.getUnitsOwned().contains("mahact_infantry") || player.hasTech("cl2")) && unitName.toLowerCase().contains("inf")) {
                ButtonHelperFactionSpecific.offerMahactInfButtons(player, activeGame);
            }
            if (player.hasInf2Tech() && unitName.toLowerCase().contains("inf")) {
                ButtonHelper.resolveInfantryDeath(activeGame, player, amount);
            }
        }

        String message = event.getMessage().getContentRaw();
        String message2 = ident + " Removed " + amount + " " + unitName + " from " + planetName + " in tile " + tile.getRepresentationForButtons(activeGame, player);
        List<Button> systemButtons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, activeGame, tile);
        event.getMessage().editMessage(message)
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message2);

    }

    public static void repairDamage(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        String rest;
        rest = buttonID.replace("repairDamage_", "");

        String pos = rest.substring(0, rest.indexOf("_"));
        Tile tile = activeGame.getTileByPosition(pos);
        rest = rest.replace(pos + "_", "");

        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1);
        String unitName;
        String planet = "";
        if (rest.contains("_")) {
            unitName = rest.split("_")[0];
            planet = rest.split("_")[1].toLowerCase().replace(" ", "");
        } else {
            unitName = rest;
        }
        String planetName;
        unitName = unitName.replace("damaged", "");
        planet = planet.replace("damaged", "");
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unitName), player.getColor());
        if ("".equalsIgnoreCase(planet)) {
            planetName = "space";
        } else {
            planetName = planet.replace("'", "");
            planetName = AliasHandler.resolvePlanet(planetName);
        }
        tile.removeUnitDamage(planetName, unitKey, amount);
        String message = event.getMessage().getContentRaw();
        String message2 = ident + " Repaired " + amount + " " + unitName + " from " + planetName + " in tile " + tile.getRepresentationForButtons(activeGame, player);
        List<Button> systemButtons = ButtonHelper.getButtonsForRepairingUnitsInASystem(player, activeGame, tile);
        event.getMessage().editMessage(message)
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message2);
    }

    public static void assignDamage(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        String rest;
        rest = buttonID.replace("assignDamage_", "");

        String pos = rest.substring(0, rest.indexOf("_"));
        Tile tile = activeGame.getTileByPosition(pos);
        rest = rest.replace(pos + "_", "");

        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1);
        String unitName;
        String planet = "";
        if (rest.contains("_")) {
            unitName = rest.split("_")[0];
            planet = rest.split("_")[1].toLowerCase().replace(" ", "");
        } else {
            unitName = rest;
        }
        String planetName;
        unitName = unitName.replace("damaged", "");
        planet = planet.replace("damaged", "");
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unitName), player.getColor());
        if ("".equalsIgnoreCase(planet)) {
            planetName = "space";
        } else {
            planetName = planet.replace("'", "");
            planetName = AliasHandler.resolvePlanet(planetName);
        }
        tile.addUnitDamage(planetName, unitKey, amount);
        String message = event.getMessage().getContentRaw();
        String message2 = ident + " Sustained " + amount + " " + unitName + " from " + planetName + " in tile " + tile.getRepresentationForButtons(activeGame, player);
        List<Button> systemButtons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, activeGame, tile);
        event.getMessage().editMessage(message)
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message2);
        for (int x = 0; x < amount; x++) {
            ButtonHelperCommanders.resolveLetnevCommanderCheck(player, activeGame, event);
        }
    }

}