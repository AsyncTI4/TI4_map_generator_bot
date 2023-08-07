package ti4.helpers;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import java.util.*;
import ti4.commands.tokens.AddCC;
import ti4.commands.units.AddUnits;
import ti4.commands.units.MoveUnits;
import ti4.commands.units.RemoveUnits;
import ti4.generator.Mapper;
import ti4.map.Map;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

public class ButtonHelperModifyUnits {

    public static List<Button> getRetreatSystemButtons(Player player, Map activeMap,  String pos1) {
        String finChecker = "FFCC_"+player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for(String pos2 : FoWHelper.getAdjacentTiles(activeMap, pos1, player, false)){
            if(pos1.equalsIgnoreCase(pos2)){
                continue;
            }
            Tile tile2 = activeMap.getTileByPosition(pos2);
            buttons.add(Button.secondary(finChecker+"retreatUnitsFrom_"+pos1+"_"+pos2, "Retreat to "+tile2.getRepresentationForButtons(activeMap, player)));
        }
        return buttons;
    }

    public static List<Button> getRetreatingGroundTroopsButtons(Player player, Map activeMap, ButtonInteractionEvent event, String pos1, String pos2) {
        String finChecker = "FFCC_"+player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        java.util.Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        Tile tile = activeMap.getTileByPosition(pos1);
        String colorID = Mapper.getColorID(player.getColor());
        String mechKey = colorID + "_mf.png";
        String infKey = colorID + "_gf.png";
        for (java.util.Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            String name = entry.getKey();
            String representation = planetRepresentations.get(name);
            if (representation == null){
                representation = name;
            }
            UnitHolder unitHolder = entry.getValue();
            if (unitHolder instanceof Planet planet) {
                int limit = 0;
                if(planet.getUnits().get(infKey) != null){
                    limit = planet.getUnits().get(infKey);
                    for(int x = 1; x < limit +1; x++){
                        if(x > 2){
                            break;
                        }
                        Button validTile2 = Button.success(finChecker+"retreatGroundUnits_"+pos1+"_"+pos2+"_"+x+"infantry_"+representation, "Retreat "+x+" Infantry on "+Helper.getPlanetRepresentation(representation.toLowerCase(), activeMap)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("infantry")));
                        buttons.add(validTile2);
                    }
                }
                if(planet.getUnits().get(mechKey) != null){
                    for(int x = 1; x < planet.getUnits().get(mechKey) +1; x++){
                        if(x > 2){
                            break;
                        }
                        Button validTile2 = Button.primary(finChecker+"retreatGroundUnits_"+pos1+"_"+pos2+"_"+x+"mech_"+representation, "Retreat "+x+" Mech(s) on "+Helper.getPlanetRepresentation(representation.toLowerCase(), activeMap)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("mech")));
                        buttons.add(validTile2);
                    }
                }
            }
        }
        Button concludeMove = Button.secondary(finChecker+"deleteButtons", "Done Retreating troops");
        buttons.add(concludeMove);
        if(player.getLeaderIDs().contains("naazcommander") && !player.hasLeaderUnlocked("naazcommander")){
                ButtonHelper.commanderUnlockCheck(player, activeMap, "naaz", event);
        }
        if(player.getLeaderIDs().contains("empyreancommander") && !player.hasLeaderUnlocked("empyreancommander")){
                ButtonHelper.commanderUnlockCheck(player, activeMap, "empyrean", event);
        }
        return buttons;
    }
    public static void retreatGroundUnits(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident, String buttonLabel){
        String rest = buttonID.replace("retreatGroundUnits_", "");
        String pos1 = rest.substring(0, rest.indexOf("_"));
        rest = rest.replace(pos1 + "_", "");
        String pos2 = rest.substring(0, rest.indexOf("_"));
        rest = rest.replace(pos2 + "_", "");
        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1, rest.length());
        String unitkey = "";
        String planet = "";
        if (rest.contains("_")) {
            unitkey = rest.split("_")[0];
            planet = rest.split("_")[1].replace(" ", "").toLowerCase();
        } else {
            unitkey = rest;
        }
        if(buttonLabel.toLowerCase().contains("damaged")){
            new AddUnits().unitParsing(event, player.getColor(),
            activeMap.getTileByPosition(pos2), amount +" " +unitkey, activeMap);
             activeMap.getTileByPosition(pos2).addUnitDamage("space", unitkey,amount);
        }else{
             new AddUnits().unitParsing(event, player.getColor(),
            activeMap.getTileByPosition(pos2), amount +" " +unitkey, activeMap);
        }

        String key = Mapper.getUnitID(AliasHandler.resolveUnit(unitkey), player.getColor());
        //activeMap.getTileByPosition(pos1).removeUnit(planet,key, amount);
        new RemoveUnits().removeStuff(event,activeMap.getTileByPosition(pos1), amount, planet, key, player.getColor(), false);

        List<Button> systemButtons = ButtonHelperModifyUnits.getRetreatingGroundTroopsButtons(player, activeMap, event, pos1, pos2);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), ident+" Retreated "+amount+ " "+unitkey + " on "+planet +" to "+activeMap.getTileByPosition(pos2).getRepresentationForButtons(activeMap,player));
        event.getMessage().editMessage(event.getMessage().getContentRaw())
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }
    public static void retreatSpaceUnits(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player){
        String both = buttonID.replace("retreatUnitsFrom_","");
        String pos1 = both.split("_")[0];
        String pos2 = both.split("_")[1];
        Tile tile1 = activeMap.getTileByPosition(pos1);
        Tile tile2 = activeMap.getTileByPosition(pos2);
        AddCC.addCC(event, player.getColor(), tile2, true);
        java.util.Map<String, String> unitRepresentation = Mapper.getUnitImageSuffixes();
        java.util.Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        String cID = Mapper.getColorID(player.getColor());
        for (java.util.Map.Entry<String, UnitHolder> entry : tile1.getUnitHolders().entrySet()) {
            String name = entry.getKey();
            String representation = planetRepresentations.get(name);
            if (representation == null){
                representation = name;
            }
            UnitHolder unitHolder = entry.getValue();
            HashMap<String, Integer> units1 = unitHolder.getUnits();
            HashMap<String, Integer> units = new HashMap<String, Integer>();
            units.putAll(units1);
            if (unitHolder instanceof Planet planet) {
                continue;
            }
            else{
                for (java.util.Map.Entry<String, Integer> unitEntry : units.entrySet()) {
                    String key = unitEntry.getKey();
                    for (String unitRepresentationKey : unitRepresentation.keySet()) {
                        if (key.endsWith(unitRepresentationKey) && key.contains(cID)) {
                            
                            String unitKey = key.replace(cID+"_", "");
                            
                            int totalUnits = unitEntry.getValue();
                            int amount = unitEntry.getValue();
                            unitKey  = unitKey.replace(".png", "");
                            unitKey = ButtonHelper.getUnitName(unitKey);
                            int damagedUnits = 0;
                            if(unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null){
                                damagedUnits = unitHolder.getUnitDamage().get(key);
                            }
                            String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unitKey), player.getColor());
                            new RemoveUnits().removeStuff(event, tile1, totalUnits, "space", unitID, player.getColor(), false);
                            new AddUnits().unitParsing(event, player.getColor(),tile2, amount + " " + unitKey, activeMap);
                            if(damagedUnits > 0){
                                activeMap.getTileByPosition(pos2).addUnitDamage("space", unitID, damagedUnits);
                            }
                        }
                    }
                }
            }             
        }
    }
    public static void umbatTile(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident){
        String pos = buttonID.replace("umbatTile_", "");
        List<Button> buttons = new ArrayList<Button>();
        buttons = Helper.getPlaceUnitButtons(event, player, activeMap,
                activeMap.getTileByPosition(pos), "muaatagent", "place");
        String message = Helper.getPlayerRepresentation(player, activeMap) + " Use the buttons to produce units. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        event.getMessage().delete().queue();
    }
    public static void genericPlaceUnit(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident, String trueIdentity, String finsFactionCheckerPrefix){
        String unitNPlanet = buttonID.replace("place_", "");
        String unitLong = unitNPlanet.substring(0, unitNPlanet.indexOf("_"));
        String planetName = unitNPlanet.replace(unitLong + "_", "");
        String unit = AliasHandler.resolveUnit(unitLong);

        String successMessage = "";
        String playerRep = Helper.getPlayerRepresentation(player, activeMap);
        if (unit.equalsIgnoreCase("sd")) {
            if (player.ownsUnit("saar_spacedock") || player.ownsUnit("saar_spacedock2")) {
                new AddUnits().unitParsing(event, player.getColor(),
                        activeMap.getTile(AliasHandler.resolveTile(planetName)), unit, activeMap);
                successMessage = "Placed a space dock in the space area of the "
                        + Helper.getPlanetRepresentation(planetName, activeMap) + " system.";
            } else if (player.ownsUnit("cabal_spacedock") || player.ownsUnit("cabal_spacedock2")) {
                new AddUnits().unitParsing(event, player.getColor(),
                        activeMap.getTile(AliasHandler.resolveTile(planetName)), "csd " + planetName, activeMap);
                successMessage = "Placed a cabal space dock on "
                        + Helper.getPlanetRepresentation(planetName, activeMap) + ".";
            } else {
                new AddUnits().unitParsing(event, player.getColor(),
                        activeMap.getTile(AliasHandler.resolveTile(planetName)), unit + " " + planetName,
                        activeMap);
                successMessage = "Placed a " + Helper.getEmojiFromDiscord("spacedock") + " on "
                        + Helper.getPlanetRepresentation(planetName, activeMap) + ".";
            }
        } else if (unitLong.equalsIgnoreCase("pds")) {
            new AddUnits().unitParsing(event, player.getColor(),
                    activeMap.getTile(AliasHandler.resolveTile(planetName)), unitLong + " " + planetName,
                    activeMap);
            successMessage = "Placed a " + Helper.getEmojiFromDiscord("pds") + " on "
                    + Helper.getPlanetRepresentation(planetName, activeMap) + ".";
        } else {
             Tile tile = null;
             String producedOrPlaced = "Produced";
            if (unit.equalsIgnoreCase("gf") || unit.equalsIgnoreCase("mf") || unitLong.equalsIgnoreCase("2gf")) {
                if (unitLong.equalsIgnoreCase("2gf")) {
                    if(!planetName.contains("space")){
                        new AddUnits().unitParsing(event, player.getColor(),
                            activeMap.getTile(AliasHandler.resolveTile(planetName)), "2 gf " + planetName,
                            activeMap);
                        successMessage = producedOrPlaced+" 2 " + Helper.getEmojiFromDiscord("infantry") + " on "+ Helper.getPlanetRepresentation(planetName, activeMap) + ".";
                    }else {
                        tile = activeMap.getTileByPosition(planetName.replace("space",""));
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, "2 gf",
                            activeMap);
                        successMessage = producedOrPlaced+" 2 " + Helper.getEmojiFromDiscord("infantry") + " in space.";
                    }
                } else {
                   
                    if(!planetName.contains("space")){
                        tile = activeMap.getTile(AliasHandler.resolveTile(planetName));
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, unit + " " + planetName,
                            activeMap);
                        successMessage = producedOrPlaced+" a " + Helper.getEmojiFromDiscord(unitLong) + " on "+ Helper.getPlanetRepresentation(planetName, activeMap) + ".";
                    } else {
                        tile = activeMap.getTileByPosition(planetName.replace("space",""));
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, unit,
                            activeMap);
                        successMessage = producedOrPlaced+" a " + Helper.getEmojiFromDiscord(unitLong) + " in space.";
                    }
                    
                }
            } else {
                if (unitLong.equalsIgnoreCase("2ff")) {
                    new AddUnits().unitParsing(event, player.getColor(), activeMap.getTileByPosition(planetName),
                            "2 ff", activeMap);
                    successMessage = "Produced 2 " + Helper.getEmojiFromDiscord("fighter") + " in tile "
                            + AliasHandler.resolveTile(planetName) + ".";
                } else {
                    new AddUnits().unitParsing(event, player.getColor(), activeMap.getTileByPosition(planetName),
                            unit, activeMap);
                    successMessage = "Produced a " + Helper.getEmojiFromDiscord(unitLong) + " in tile "
                            + AliasHandler.resolveTile(planetName) + ".";
                }

            }
        }
        if (unit.equalsIgnoreCase("sd") || unitLong.equalsIgnoreCase("pds")) {

            if (activeMap.isFoWMode() || !activeMap.getCurrentPhase().equalsIgnoreCase("action")) {
                MessageHelper.sendMessageToChannel(event.getChannel(), playerRep + " " + successMessage);
            } else {
                List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
                if (threadChannels == null)
                    return;
                String threadName = activeMap.getName() + "-round-" + activeMap.getRound() + "-construction";
                // SEARCH FOR EXISTING OPEN THREAD
                for (ThreadChannel threadChannel_ : threadChannels) {
                    if (threadChannel_.getName().equals(threadName)) {
                        MessageHelper.sendMessageToChannel((MessageChannel) threadChannel_,
                                playerRep + " " + successMessage);
                    }
                }
            }
            if(player.hasLeader("mahactagent")){
                String message = playerRep + " Would you like to put a cc from reinforcements in the same system?";
                Button placeCCInSystem = Button.success(
                        finsFactionCheckerPrefix + "reinforcements_cc_placement_" + planetName,
                        "Place A CC From Reinforcements In The System.");
                Button NoDontWantTo = Button.primary(finsFactionCheckerPrefix + "deleteButtons",
                        "Don't Place A CC In The System.");
                List<Button> buttons = List.of(placeCCInSystem, NoDontWantTo);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            }else{
                if(!player.getSCs().contains(Integer.parseInt("4")) && activeMap.getCurrentPhase().equalsIgnoreCase("action")){
                    String color = player.getColor();
                    String tileID = AliasHandler.resolveTile(planetName.toLowerCase());
                    Tile tile = activeMap.getTile(tileID);
                    if (tile == null) {
                        tile = activeMap.getTileByPosition(tileID);
                    }
                    if (Mapper.isColorValid(color)) {
                        AddCC.addCC(event, color, tile);
                    }

                    if (activeMap.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(event.getChannel(),
                                playerRep + " Placed A CC From Reinforcements In The "
                                        + Helper.getPlanetRepresentation(planetName, activeMap) + " system");
                    } else {
                        List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
                        if (threadChannels == null)
                            return;
                        String threadName = activeMap.getName() + "-round-" + activeMap.getRound() + "-construction";
                        // SEARCH FOR EXISTING OPEN THREAD
                        for (ThreadChannel threadChannel_ : threadChannels) {
                            if (threadChannel_.getName().equals(threadName)) {
                                MessageHelper.sendMessageToChannel((MessageChannel) threadChannel_,
                                        playerRep + " Placed A CC From Reinforcements In The "
                                                + Helper.getPlanetRepresentation(planetName, activeMap) + " system");
                            }
                        }
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
                successMessage = "Placed 2 " + Helper.getEmojiFromDiscord("infantry") + " on "
                        + Helper.getPlanetRepresentation(planetName, activeMap) + ".";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), successMessage);
                event.getMessage().delete().queue();
            } else {
                event.getMessage().editMessage(editedMessage).queue();
            }

        }

        if(player.getLeaderIDs().contains("titanscommander") && !player.hasLeaderUnlocked("titanscommander")){
            ButtonHelper.commanderUnlockCheck(player, activeMap, "titans", event);
        }
        if(player.getLeaderIDs().contains("saarcommander") && !player.hasLeaderUnlocked("saarcommander")){
            ButtonHelper.commanderUnlockCheck(player, activeMap, "saar", event);
        }
        if(player.getLeaderIDs().contains("mentakcommander") && !player.hasLeaderUnlocked("mentakcommander")){
            ButtonHelper.commanderUnlockCheck(player, activeMap, "mentak", event);
        }
        if(player.getLeaderIDs().contains("l1z1xcommander") && !player.hasLeaderUnlocked("l1z1xcommander")){
            ButtonHelper.commanderUnlockCheck(player, activeMap, "l1z1x", event);
        }
        if(player.getLeaderIDs().contains("muaatcommander") && !player.hasLeaderUnlocked("muaatcommander") && unitLong.equalsIgnoreCase("warsun")){
            ButtonHelper.commanderUnlockCheck(player, activeMap, "muaat", event);
        }
        if(player.getLeaderIDs().contains("argentcommander") && !player.hasLeaderUnlocked("argentcommander")){
            ButtonHelper.commanderUnlockCheck(player, activeMap, "argent", event);
        }
        if(player.getLeaderIDs().contains("naazcommander") && !player.hasLeaderUnlocked("naazcommander")){
            ButtonHelper.commanderUnlockCheck(player, activeMap, "naaz", event);
        }
        if(player.getLeaderIDs().contains("arboreccommander") && !player.hasLeaderUnlocked("arboreccommander")){
            ButtonHelper.commanderUnlockCheck(player, activeMap, "arborec", event);
        }
    }

    public static void placeUnitAndDeleteButton(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident, String trueIdentity){
        String unitNPlanet = buttonID.replace("placeOneNDone_", "");
        String skipbuild= unitNPlanet.substring(0, unitNPlanet.indexOf("_"));
        unitNPlanet = unitNPlanet.replace(skipbuild+"_","");
        String unitLong = unitNPlanet.substring(0, unitNPlanet.indexOf("_"));
        String planetName = unitNPlanet.replace(unitLong + "_", "");
        String unit = AliasHandler.resolveUnit(unitLong);
        String producedOrPlaced = "Produced";
         if(skipbuild.equalsIgnoreCase("skipbuild")){
            producedOrPlaced = "Placed";
        }
        String successMessage = "";
        String playerRep = Helper.getPlayerRepresentation(player, activeMap);
        if (unit.equalsIgnoreCase("sd")) {
            if (player.ownsUnit("saar_spacedock") || player.ownsUnit("saar_spacedock2")) {
                new AddUnits().unitParsing(event, player.getColor(),
                        activeMap.getTile(AliasHandler.resolveTile(planetName)), unit, activeMap);
                successMessage = "Placed a space dock in the space area of the "
                        + Helper.getPlanetRepresentation(planetName, activeMap) + " system.";
            } else if (player.ownsUnit("cabal_spacedock") || player.ownsUnit("cabal_spacedock2")) {
                new AddUnits().unitParsing(event, player.getColor(),
                        activeMap.getTile(AliasHandler.resolveTile(planetName)), "csd " + planetName, activeMap);
                successMessage = "Placed a cabal space dock on "
                        + Helper.getPlanetRepresentation(planetName, activeMap) + ".";
            } else {
                new AddUnits().unitParsing(event, player.getColor(),
                        activeMap.getTile(AliasHandler.resolveTile(planetName)), unit + " " + planetName,
                        activeMap);
                successMessage = "Placed a " + Helper.getEmojiFromDiscord("spacedock") + " on "
                        + Helper.getPlanetRepresentation(planetName, activeMap) + ".";
            }
        } else if (unitLong.equalsIgnoreCase("pds")) {
            new AddUnits().unitParsing(event, player.getColor(),
                    activeMap.getTile(AliasHandler.resolveTile(planetName)), unitLong + " " + planetName,
                    activeMap);
            successMessage = "Placed a " + Helper.getEmojiFromDiscord("pds") + " on "
                    + Helper.getPlanetRepresentation(planetName, activeMap) + ".";
        } else {
            Tile tile = null;
            if (unit.equalsIgnoreCase("gf") || unit.equalsIgnoreCase("mf") || unitLong.equalsIgnoreCase("2gf")) {
                if (unitLong.equalsIgnoreCase("2gf")) {
                    if(!planetName.contains("space")){
                        new AddUnits().unitParsing(event, player.getColor(),
                            activeMap.getTile(AliasHandler.resolveTile(planetName)), "2 gf " + planetName,
                            activeMap);
                        successMessage = producedOrPlaced+" 2 " + Helper.getEmojiFromDiscord("infantry") + " on "+ Helper.getPlanetRepresentation(planetName, activeMap) + ".";
                    }else {
                        tile = activeMap.getTileByPosition(planetName.replace("space",""));
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, "2 gf ",
                            activeMap);
                        successMessage = producedOrPlaced+" 2 " + Helper.getEmojiFromDiscord("infantry") + " in space.";
                    }
                } else {
                   
                    if(!planetName.contains("space")){
                        tile = activeMap.getTile(AliasHandler.resolveTile(planetName));
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, unit + " " + planetName,
                            activeMap);
                        successMessage = producedOrPlaced+" a " + Helper.getEmojiFromDiscord(unitLong) + " on "+ Helper.getPlanetRepresentation(planetName, activeMap) + ".";
                    } else {
                        tile = activeMap.getTileByPosition(planetName.replace("space",""));
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, unit,
                            activeMap);
                        successMessage = producedOrPlaced+" a " + Helper.getEmojiFromDiscord(unitLong) + " in space.";
                    }
                    
                }
            } else {
                if (unitLong.equalsIgnoreCase("2ff")) {
                    new AddUnits().unitParsing(event, player.getColor(), activeMap.getTileByPosition(planetName),
                            "2 ff", activeMap);
                    successMessage = producedOrPlaced+" 2 " + Helper.getEmojiFromDiscord("fighter") + " in tile "
                            + AliasHandler.resolveTile(planetName) + ".";
                } else {
                    new AddUnits().unitParsing(event, player.getColor(), activeMap.getTileByPosition(planetName),
                            unit, activeMap);
                    successMessage = producedOrPlaced+" a " + Helper.getEmojiFromDiscord(unitLong) + " in tile "
                            + AliasHandler.resolveTile(planetName) + ".";
                }

            }
            if(player.getLeaderIDs().contains("l1z1xcommander") && !player.hasLeaderUnlocked("l1z1xcommander")){
                ButtonHelper.commanderUnlockCheck(player, activeMap, "l1z1x", event);
            }
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), playerRep + " " + successMessage);
        String message2 = trueIdentity + " Click the names of the planets you wish to exhaust.";
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeMap, player, event);
        Button DoneExhausting = null;
        if (!buttonID.contains("deleteButtons")) {
            DoneExhausting = Button.danger("deleteButtons_" + buttonID, "Done Exhausting Planets");
        } else {
            DoneExhausting = Button.danger("deleteButtons", "Done Exhausting Planets");
        }
        buttons.add(DoneExhausting);
        if(!skipbuild.equalsIgnoreCase("skipbuild")){
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
        }
        event.getMessage().delete().queue();
    }
    public static void spaceLandedUnits(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident, String buttonLabel){
        String rest = buttonID.replace("spaceUnits_", "");
        String pos = rest.substring(0, rest.indexOf("_"));
        rest = rest.replace(pos + "_", "");
        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1, rest.length());
        String unitkey = "";
        String planet = "";
        if (rest.contains("_")) {
            unitkey = rest.split("_")[0];
            planet = rest.split("_")[1].replace(" ", "").toLowerCase().replace("-","").replace("'","");
        } else {
            unitkey = rest;
        }
        if(buttonLabel.toLowerCase().contains("damaged")){
            new AddUnits().unitParsing(event, player.getColor(),
            activeMap.getTileByPosition(pos), amount +" " +unitkey, activeMap);
             activeMap.getTileByPosition(pos).addUnitDamage("space", unitkey,amount);
        }else{
             new AddUnits().unitParsing(event, player.getColor(),
            activeMap.getTileByPosition(pos), amount +" " +unitkey, activeMap);
        }
       
        String key = Mapper.getUnitID(AliasHandler.resolveUnit(unitkey), player.getColor());
        activeMap.getTileByPosition(pos).removeUnit(planet,key, amount);
        List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, activeMap, event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), ident+"Undid landing of "+amount+ " "+unitkey + " on "+planet);
        event.getMessage().editMessage(event.getMessage().getContentRaw())
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }
    public static void landingUnits(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident, String buttonLabel){
        String rest = buttonID.replace("landUnits_", "");
        String pos = rest.substring(0, rest.indexOf("_"));
        rest = rest.replace(pos + "_", "");
        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1, rest.length());
        String unitkey = "";
        String planet = "";
        if (rest.contains("_")) {
            unitkey = rest.split("_")[0];
            planet = rest.split("_")[1].replace(" ", "").toLowerCase().replace("'","").replace("-","");
        } else {
            unitkey = rest;
        }
        if(buttonLabel.toLowerCase().contains("damaged")){
            
            new AddUnits().unitParsing(event, player.getColor(),
            activeMap.getTileByPosition(pos), amount +" " +unitkey+" "+planet, activeMap);
             activeMap.getTileByPosition(pos).addUnitDamage(planet, unitkey,amount);
        }else{
             new AddUnits().unitParsing(event, player.getColor(),
            activeMap.getTileByPosition(pos), amount +" " +unitkey+" "+planet, activeMap);
        }
       
        String key = Mapper.getUnitID(AliasHandler.resolveUnit(unitkey), player.getColor());
        activeMap.getTileByPosition(pos).removeUnit("space",key, amount);
        List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, activeMap, event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), ident+" Landed "+amount+ " "+unitkey + " on "+planet);
        event.getMessage().editMessage(event.getMessage().getContentRaw())
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }
    public static void movingUnitsInTacticalAction(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident, String buttonLabel){
        String remove = "Move";
        HashMap<String, Integer> currentSystem = activeMap.getCurrentMovedUnitsFrom1System();
       HashMap<String, Integer> currentActivation = activeMap.getMovedUnitsFromCurrentActivation();
       String rest = "";
       if(buttonID.contains("Remove")){
           remove = "Remove";
           rest = buttonID.replace("unitTacticalRemove_", "").toLowerCase();
       }else{
           rest = buttonID.replace("unitTacticalMove_", "").toLowerCase();
       }
       String pos = rest.substring(0, rest.indexOf("_"));
       Tile tile = activeMap.getTileByPosition(pos);
       rest = rest.replace(pos + "_", "");

       if(rest.contains("reverseall") || rest.contains("moveall")){
          
           if(rest.contains("reverse"))
           {
               for(String unit : currentSystem.keySet()){
                   
                   String unitkey = "";
                   String planet = "";
                   String origUnit = unit;
                   String damagedMsg = "";
                   int amount = currentSystem.get(unit);
                   if(unit.contains("_"))
                   {
                       unitkey =unit.split("_")[0];
                       planet =unit.split("_")[1];
                   }else{
                       unitkey = unit;
                   }
                   if (currentActivation.containsKey(unitkey)) {
                       activeMap.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitkey,
                               currentActivation.get(unitkey) - amount);
                   }
                   if(unitkey.contains("damaged")){
                       unitkey = unitkey.replace("damaged", "");
                       damagedMsg = " damaged ";
                   }
                   new AddUnits().unitParsing(event, player.getColor(),
                   activeMap.getTileByPosition(pos), (amount) + " " + unitkey + " " + planet, activeMap);
                   if(damagedMsg.contains("damaged")){
                       if (planet.equalsIgnoreCase("")) {
                           planet = "space";
                       }
                       String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unitkey), player.getColor());
                       activeMap.getTileByPosition(pos).addUnitDamage(planet, unitID, (amount));
                   }
               }
              
               activeMap.resetCurrentMovedUnitsFrom1System();
           }else{
               java.util.Map<String, String> unitRepresentation = Mapper.getUnitImageSuffixes();
               java.util.Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
               String cID = Mapper.getColorID(player.getColor());
               for (java.util.Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
                   String name = entry.getKey();
                   String representation = planetRepresentations.get(name);
                   if (representation == null){
                       representation = name;
                   }
                   UnitHolder unitHolder = entry.getValue();
                   HashMap<String, Integer> units1 = unitHolder.getUnits();
                   HashMap<String, Integer> units = new HashMap<String, Integer>();
                   units.putAll(units1);
               
                   if (unitHolder instanceof Planet planet) {
                       for (java.util.Map.Entry<String, Integer> unitEntry : units.entrySet()) {
                           String key = unitEntry.getKey();
                           if ((key.endsWith("gf.png") || key.endsWith("mf.png")) &&key.contains(cID)) {
                               String unitKey = key.replace(cID+"_", "");
                               unitKey = unitKey.replace(".png", "");
                               unitKey = ButtonHelper.getUnitName(unitKey);
                               int amount = unitEntry.getValue();
                               rest = unitKey.toLowerCase()+"_"+unitHolder.getName().toLowerCase();
                               if (currentSystem.containsKey(rest)) {
                                   activeMap.setSpecificCurrentMovedUnitsFrom1System(rest, currentSystem.get(rest) + amount);
                               } else {
                                   activeMap.setSpecificCurrentMovedUnitsFrom1System(rest, amount);
                               }
                               if (currentActivation.containsKey(unitKey)) {
                                   activeMap.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitKey,
                                           currentActivation.get(unitKey) + amount);
                               } else {
                                   activeMap.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitKey, amount);
                               }
                               String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unitKey), player.getColor());

                               new RemoveUnits().removeStuff(event, activeMap.getTileByPosition(pos), unitEntry.getValue(), unitHolder.getName(), unitID, player.getColor(), false);

                             //  validTile2 = Button.danger(finChecker+"unitTactical"+moveOrRemove+"_"+tile.getPosition()+"_"+x+unitKey+"_"+representation, moveOrRemove+" "+x+" Infantry from "+Helper.getPlanetRepresentation(representation.toLowerCase(), activeMap)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("infantry")));
                           }
                       }
                   }
                   else{
                       for (java.util.Map.Entry<String, Integer> unitEntry : units.entrySet()) {

                           String key = unitEntry.getKey();
                           for (String unitRepresentationKey : unitRepresentation.keySet()) {
                               if (key.endsWith(unitRepresentationKey) && key.contains(cID)) {
                                   
                                   String unitKey = key.replace(cID+"_", "");
                                   
                                   int totalUnits = unitEntry.getValue();
                                   int amount = unitEntry.getValue();
                                   unitKey  = unitKey.replace(".png", "");
                                   unitKey = ButtonHelper.getUnitName(unitKey);
                                   int damagedUnits = 0;
                                   if(unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null){
                                       damagedUnits = unitHolder.getUnitDamage().get(key);
                                   }
                                   String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unitKey), player.getColor());

                                   new RemoveUnits().removeStuff(event, activeMap.getTileByPosition(pos), totalUnits, "space", unitID, player.getColor(), false);
                                   if(damagedUnits > 0){
                                       rest = unitKey+"damaged";
                                       amount = damagedUnits;
                                       if (currentSystem.containsKey(rest)) {
                                           activeMap.setSpecificCurrentMovedUnitsFrom1System(rest, currentSystem.get(rest) + amount);
                                       } else {
                                           activeMap.setSpecificCurrentMovedUnitsFrom1System(rest, amount);
                                       }
                                       if (currentActivation.containsKey(rest)) {
                                           activeMap.setSpecificCurrentMovedUnitsFrom1TacticalAction(rest,
                                                   currentActivation.get(rest) + amount);
                                       } else {
                                           activeMap.setSpecificCurrentMovedUnitsFrom1TacticalAction(rest, amount);
                                       }
                                   }
                                   rest = unitKey;
                                   amount = totalUnits - damagedUnits;
                                   if(amount > 0){
                                       if (currentSystem.containsKey(rest)) {
                                           activeMap.setSpecificCurrentMovedUnitsFrom1System(rest, currentSystem.get(rest) + amount);
                                       } else {
                                           activeMap.setSpecificCurrentMovedUnitsFrom1System(rest, amount);
                                       }
                                       if (currentActivation.containsKey(unitKey)) {
                                           activeMap.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitKey,
                                                   currentActivation.get(unitKey) + amount);
                                       } else {
                                           activeMap.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitKey, amount);
                                       }
                                   }
                                   
                                   
                               }
                           }
                       }
                   }             
               }
           }
           String message = ButtonHelper.buildMessageFromDisplacedUnits(activeMap, false, player, remove);
           List<Button> systemButtons = ButtonHelper.getButtonsForAllUnitsInSystem(player, activeMap,
                   activeMap.getTileByPosition(pos), remove);
           event.getMessage().editMessage(message)
                   .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
           return;
       }
       int amount = Integer.parseInt(rest.charAt(0) + "");
       if (rest.contains("_reverse")) {
           amount = amount * -1;
           rest = rest.replace("_reverse", "");
       }
       rest = rest.substring(1, rest.length());
       String unitkey = "";
       String planet = "";

       if (rest.contains("_")) {
           unitkey = rest.split("_")[0];
           planet = rest.split("_")[1].toLowerCase().replace(" ", "");
       } else {
           unitkey = rest;
       }
       unitkey = unitkey.replace("damaged", "");
       planet = planet.replace("damaged", "");
       String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unitkey), player.getColor());
       rest = rest.replace("damaged", "");
       if (amount < 0) {
           
           new AddUnits().unitParsing(event, player.getColor(),
                   activeMap.getTileByPosition(pos), (amount * -1) + " " + unitkey + " " + planet, activeMap);
           if(buttonLabel.toLowerCase().contains("damaged")){
               if (planet.equalsIgnoreCase("")) {
                   planet = "space";
               }
               activeMap.getTileByPosition(pos).addUnitDamage(planet, unitID, (amount * -1));
           }
       } else {
           String planetName = "";
           if (planet.equalsIgnoreCase("")) {
               planetName = "space";
           } else {
               planetName = planet.toLowerCase().replace(" ", "");
               planetName = planet.replace("'", "");
               planetName = AliasHandler.resolvePlanet(planetName);
           }
           
           if(buttonLabel.toLowerCase().contains("damaged")){
               new RemoveUnits().removeStuff(event, activeMap.getTileByPosition(pos), amount, planetName, unitID, player.getColor(), true);
           }else{
               new RemoveUnits().removeStuff(event, activeMap.getTileByPosition(pos), amount, planetName, unitID, player.getColor(), false);
           }
          // String key = Mapper.getUnitID(AliasHandler.resolveUnit(unitkey), player.getColor());
           //activeMap.getTileByPosition(pos).removeUnit(planetName, key, amount);
       }
       if(buttonLabel.toLowerCase().contains("damaged")){ 
           unitkey = unitkey + "damaged";
           rest = rest+"damaged";
       }
       if (currentSystem.containsKey(rest)) {
           activeMap.setSpecificCurrentMovedUnitsFrom1System(rest, currentSystem.get(rest) + amount);
       } else {
           activeMap.setSpecificCurrentMovedUnitsFrom1System(rest, amount);
       }
       if(currentSystem.get(rest) == 0){
           currentSystem.remove(rest);
       }
       if (currentActivation.containsKey(unitkey)) {
           activeMap.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitkey,
                   currentActivation.get(unitkey) + amount);
       } else {
           activeMap.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitkey, amount);
       }
       String message = ButtonHelper.buildMessageFromDisplacedUnits(activeMap, false, player, remove);
       List<Button> systemButtons = ButtonHelper.getButtonsForAllUnitsInSystem(player, activeMap,
               activeMap.getTileByPosition(pos), remove);
       event.getMessage().editMessage(message)
               .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }
    public static void assignHits(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident, String buttonLabel){
            String rest = "";
            rest = buttonID.replace("assignHits_", "");
            String pos = rest.substring(0, rest.indexOf("_"));
            Tile tile = activeMap.getTileByPosition(pos);
            rest = rest.replace(pos + "_", "");
            if(rest.contains("All")){
                    java.util.Map<String, String> unitRepresentation = Mapper.getUnitImageSuffixes();
                    java.util.Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
                    String cID = Mapper.getColorID(player.getColor());
                    for (java.util.Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
                        String name = entry.getKey();
                        String representation = planetRepresentations.get(name);
                        if (representation == null){
                            representation = name;
                        }
                        UnitHolder unitHolder = entry.getValue();
                        HashMap<String, Integer> units1 = unitHolder.getUnits();
                        HashMap<String, Integer> units = new HashMap<String, Integer>();
                        units.putAll(units1);
                        if (unitHolder instanceof Planet planet) {
                            for (java.util.Map.Entry<String, Integer> unitEntry : units.entrySet()) {
                                String key = unitEntry.getKey();
                                if (key.contains(cID)) {
                                    String unitKey = key.replace(cID+"_", "");
                                    unitKey = unitKey.replace(".png", "");
                                    unitKey = ButtonHelper.getUnitName(unitKey);
                                    int amount = unitEntry.getValue();
                                    rest = unitKey+"_"+unitHolder.getName();
                                    String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unitKey), player.getColor());
                                    new RemoveUnits().removeStuff(event, activeMap.getTileByPosition(pos), unitEntry.getValue(), unitHolder.getName(), unitID, player.getColor(), false);

                                }
                            }
                        }
                        else{
                            for (java.util.Map.Entry<String, Integer> unitEntry : units.entrySet()) {
                                String key = unitEntry.getKey();
                                for (String unitRepresentationKey : unitRepresentation.keySet()) {
                                    if (key.endsWith(unitRepresentationKey) && key.contains(cID)) {                         
                                        String unitKey = key.replace(cID+"_", "");                    
                                        int totalUnits = unitEntry.getValue();
                                        int amount = unitEntry.getValue();
                                        unitKey  = unitKey.replace(".png", "");
                                        unitKey = ButtonHelper.getUnitName(unitKey);
                                        int damagedUnits = 0;
                                        if(unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null){
                                            damagedUnits = unitHolder.getUnitDamage().get(key);
                                        }
                                        String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unitKey), player.getColor());
                                        new RemoveUnits().removeStuff(event, activeMap.getTileByPosition(pos), totalUnits, "space", unitID, player.getColor(), false);
                                    }
                                }         
                        }             
                    }
                }
                String message2 = ident + " Removed all units";
                String message = event.getMessage().getContentRaw();
                List<Button> systemButtons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, activeMap, tile);
                event.getMessage().editMessage(message)
                        .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message2);

                return;
            }
            int amount = Integer.parseInt(rest.charAt(0) + "");
            rest = rest.substring(1, rest.length());
            String unitkey = "";
            String planet = "";
            if (rest.contains("_")) {
                unitkey = rest.split("_")[0];
                planet = rest.split("_")[1].toLowerCase().replace(" ", "");
            } else {
                unitkey = rest;
            }
            unitkey = unitkey.replace("damaged", "");
            planet = planet.replace("damaged", "");
            String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unitkey), player.getColor());
            rest = rest.replace("damaged", "");
            String planetName = "";
            if (planet.equalsIgnoreCase("")) {
                planetName = "space";
            } else {
                planetName = planet.toLowerCase().replace(" ", "");
                planetName = planet.replace("'", "");
                planetName = AliasHandler.resolvePlanet(planetName);
            }
            if(buttonLabel.toLowerCase().contains("damaged")){
                new RemoveUnits().removeStuff(event, activeMap.getTileByPosition(pos), amount, planetName, unitID, player.getColor(), true);
            }else{
                new RemoveUnits().removeStuff(event, activeMap.getTileByPosition(pos), amount, planetName, unitID, player.getColor(), false);
            }
            
            String message = event.getMessage().getContentRaw();
            
            String message2 =  ident+ " Removed "+amount + " "+unitkey+" from "+planetName+" in tile "+tile.getRepresentationForButtons(activeMap, player);
            
            List<Button> systemButtons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, activeMap, tile);
            event.getMessage().editMessage(message)
                        .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message2);
    }
    public static void repairDamage(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident){
        String rest = "";
        rest = buttonID.replace("repairDamage_", "");
        
        String pos = rest.substring(0, rest.indexOf("_"));
        Tile tile = activeMap.getTileByPosition(pos);
        rest = rest.replace(pos + "_", "");
        
        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1, rest.length());
        String unitkey = "";
        String planet = "";
        if (rest.contains("_")) {
            unitkey = rest.split("_")[0];
            planet = rest.split("_")[1].toLowerCase().replace(" ", "");
        } else {
            unitkey = rest;
        }
        String planetName = "";
        unitkey = unitkey.replace("damaged", "");
        planet = planet.replace("damaged", "");
        String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unitkey), player.getColor());
        rest = rest.replace("damaged", "");
        if (planet.equalsIgnoreCase("")) {
            planetName = "space";
        } else {
            planetName = planet.toLowerCase().replace(" ", "");
            planetName = planet.replace("'", "");
            planetName = AliasHandler.resolvePlanet(planetName);
        }       
        tile.removeUnitDamage(planetName, unitID, amount);             
        String message = event.getMessage().getContentRaw();
        String message2 =  ident+ " Repaired "+amount + " "+unitkey+" from "+planetName+" in tile "+tile.getRepresentationForButtons(activeMap, player);
        List<Button> systemButtons = ButtonHelper.getButtonsForRepairingUnitsInASystem(player, activeMap, tile);
        event.getMessage().editMessage(message)
                    .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message2);
    }
    public static void assignDamage(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident){
        String rest = "";
        rest = buttonID.replace("assignDamage_", "");
        
        String pos = rest.substring(0, rest.indexOf("_"));
        Tile tile = activeMap.getTileByPosition(pos);
        rest = rest.replace(pos + "_", "");
        
        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1, rest.length());
        String unitkey = "";
        String planet = "";
        if (rest.contains("_")) {
            unitkey = rest.split("_")[0];
            planet = rest.split("_")[1].toLowerCase().replace(" ", "");
        } else {
            unitkey = rest;
        }
        String planetName = "";
        unitkey = unitkey.replace("damaged", "");
        planet = planet.replace("damaged", "");
        String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unitkey), player.getColor());
        rest = rest.replace("damaged", "");
        if (planet.equalsIgnoreCase("")) {
            planetName = "space";
        } else {
            planetName = planet.toLowerCase().replace(" ", "");
            planetName = planet.replace("'", "");
            planetName = AliasHandler.resolvePlanet(planetName);
        }       
        tile.addUnitDamage(planetName, unitID, amount);               
        String message = event.getMessage().getContentRaw();
        String message2 =  ident+ " Sustained "+amount + " "+unitkey+" from "+planetName+" in tile "+tile.getRepresentationForButtons(activeMap, player);
        List<Button> systemButtons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, activeMap, tile);
        event.getMessage().editMessage(message)
                    .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message2);
        ButtonHelperFactionSpecific.resolveLetnevCommanderCheck(player, activeMap);
    }

}