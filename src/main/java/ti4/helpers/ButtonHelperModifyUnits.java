package ti4.helpers;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import java.util.*;
import ti4.commands.tokens.AddCC;
import ti4.commands.units.AddUnits;
import ti4.commands.units.MoveUnits;
import ti4.commands.units.RemoveUnits;
import ti4.generator.Mapper;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

public class ButtonHelperModifyUnits {

    public static List<Button> getRetreatSystemButtons(Player player, Game activeGame,  String pos1) {
        String finChecker = "FFCC_"+player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for(String pos2 : FoWHelper.getAdjacentTiles(activeGame, pos1, player, false)){
            if(pos1.equalsIgnoreCase(pos2)){
                continue;
            }
            Tile tile2 = activeGame.getTileByPosition(pos2);
            buttons.add(Button.secondary(finChecker+"retreatUnitsFrom_"+pos1+"_"+pos2, "Retreat to "+tile2.getRepresentationForButtons(activeGame, player)));
        }
        return buttons;
    }

    public static List<Button> getRetreatingGroundTroopsButtons(Player player, Game activeGame, ButtonInteractionEvent event, String pos1, String pos2) {
        String finChecker = "FFCC_"+player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        Tile tile = activeGame.getTileByPosition(pos1);
        String colorID = Mapper.getColorID(player.getColor());
        String mechKey = colorID + "_mf.png";
        String infKey = colorID + "_gf.png";
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            String name = entry.getKey();
            String representation = planetRepresentations.get(name);
            if (representation == null){
                representation = name;
            }
            UnitHolder unitHolder = entry.getValue();
            if (unitHolder instanceof Planet planet) {
                int limit;
                if(planet.getUnits().get(infKey) != null){
                    limit = planet.getUnits().get(infKey);
                    for(int x = 1; x < limit +1; x++){
                        if(x > 2){
                            break;
                        }
                        Button validTile2 = Button.success(finChecker+"retreatGroundUnits_"+pos1+"_"+pos2+"_"+x+"infantry_"+representation, "Retreat "+x+" Infantry on "+Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("infantry")));
                        buttons.add(validTile2);
                    }
                }
                if(planet.getUnits().get(mechKey) != null){
                    for(int x = 1; x < planet.getUnits().get(mechKey) +1; x++){
                        if(x > 2){
                            break;
                        }
                        Button validTile2 = Button.primary(finChecker+"retreatGroundUnits_"+pos1+"_"+pos2+"_"+x+"mech_"+representation, "Retreat "+x+" Mech(s) on "+Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("mech")));
                        buttons.add(validTile2);
                    }
                }
            }
        }
        Button concludeMove = Button.secondary(finChecker+"deleteButtons", "Done Retreating troops");
        buttons.add(concludeMove);
        if(player.getLeaderIDs().contains("naazcommander") && !player.hasLeaderUnlocked("naazcommander")){
                ButtonHelper.commanderUnlockCheck(player, activeGame, "naaz", event);
        }
        if(player.getLeaderIDs().contains("empyreancommander") && !player.hasLeaderUnlocked("empyreancommander")){
                ButtonHelper.commanderUnlockCheck(player, activeGame, "empyrean", event);
        }
        return buttons;
    }
    public static void retreatGroundUnits(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String buttonLabel){
        String rest = buttonID.replace("retreatGroundUnits_", "").replace("'","");
        String pos1 = rest.substring(0, rest.indexOf("_"));
        rest = rest.replace(pos1 + "_", "");
        String pos2 = rest.substring(0, rest.indexOf("_"));
        rest = rest.replace(pos2 + "_", "");
        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1);
        String unitkey;
        String planet = "";
        if (rest.contains("_")) {
            unitkey = rest.split("_")[0];
            planet = rest.split("_")[1].replace(" ", "").toLowerCase();
        } else {
            unitkey = rest;
        }
        if(buttonLabel.toLowerCase().contains("damaged")){
            new AddUnits().unitParsing(event, player.getColor(),
            activeGame.getTileByPosition(pos2), amount +" " +unitkey, activeGame);
             activeGame.getTileByPosition(pos2).addUnitDamage("space", unitkey,amount);
        }else{
             new AddUnits().unitParsing(event, player.getColor(),
            activeGame.getTileByPosition(pos2), amount +" " +unitkey, activeGame);
        }

        String key = Mapper.getUnitID(AliasHandler.resolveUnit(unitkey), player.getColor());
        //activeMap.getTileByPosition(pos1).removeUnit(planet,key, amount);
        new RemoveUnits().removeStuff(event, activeGame.getTileByPosition(pos1), amount, planet, key, player.getColor(), false, activeGame);

        List<Button> systemButtons = getRetreatingGroundTroopsButtons(player, activeGame, event, pos1, pos2);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), ident+" Retreated "+amount+ " "+unitkey + " on "+planet +" to "+ activeGame.getTileByPosition(pos2).getRepresentationForButtons(activeGame,player));
        event.getMessage().editMessage(event.getMessage().getContentRaw())
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }
    public static void retreatSpaceUnits(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player){
        String both = buttonID.replace("retreatUnitsFrom_","");
        String pos1 = both.split("_")[0];
        String pos2 = both.split("_")[1];
        Tile tile1 = activeGame.getTileByPosition(pos1);
        Tile tile2 = activeGame.getTileByPosition(pos2);
        tile2 = MoveUnits.flipMallice(event, tile2, activeGame);
        AddCC.addCC(event, player.getColor(), tile2, true);
        Map<String, String> unitRepresentation = Mapper.getUnitImageSuffixes();
        Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        String cID = Mapper.getColorID(player.getColor());
        for (Map.Entry<String, UnitHolder> entry : tile1.getUnitHolders().entrySet()) {
            String name = entry.getKey();
            String representation = planetRepresentations.get(name);
            if (representation == null){
            }
            UnitHolder unitHolder = entry.getValue();
            HashMap<String, Integer> units1 = unitHolder.getUnits();
            Map<String, Integer> units = new HashMap<>(units1);
            if (unitHolder instanceof Planet planet) {
            }
            else{
                for (Map.Entry<String, Integer> unitEntry : units.entrySet()) {
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
                            new RemoveUnits().removeStuff(event, tile1, totalUnits, "space", unitID, player.getColor(), false, activeGame);
                            new AddUnits().unitParsing(event, player.getColor(),tile2, amount + " " + unitKey, activeGame);
                            if(damagedUnits > 0){
                                activeGame.getTileByPosition(pos2).addUnitDamage("space", unitID, damagedUnits);
                            }
                        }
                    }
                }
            }             
        }
    }
    public static void umbatTile(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident){
        String pos = buttonID.replace("umbatTile_", "");
        List<Button> buttons;
        buttons = Helper.getPlaceUnitButtons(event, player, activeGame,
                activeGame.getTileByPosition(pos), "muaatagent", "place");
        String message = Helper.getPlayerRepresentation(player, activeGame) + " Use the buttons to produce units. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        event.getMessage().delete().queue();
    }
    public static void genericPlaceUnit(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String trueIdentity, String finsFactionCheckerPrefix){
        String unitNPlanet = buttonID.replace("place_", "");
        String unitLong = unitNPlanet.substring(0, unitNPlanet.indexOf("_"));
        String planetName = unitNPlanet.replace(unitLong + "_", "");
        String unit = AliasHandler.resolveUnit(unitLong);

        String successMessage;
        String playerRep = Helper.getPlayerRepresentation(player, activeGame);
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
                if(player.getLeaderIDs().contains("cabalcommander") && !player.hasLeaderUnlocked("cabalcommander")){
                    ButtonHelper.commanderUnlockCheck(player, activeGame, "cabal", event);
                }
            } else {
                new AddUnits().unitParsing(event, player.getColor(),
                        activeGame.getTile(AliasHandler.resolveTile(planetName)), unit + " " + planetName,
                    activeGame);
                successMessage = "Placed a " + Helper.getEmojiFromDiscord("spacedock") + " on "
                        + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
            }
        } else if ("pds".equalsIgnoreCase(unitLong)) {
            new AddUnits().unitParsing(event, player.getColor(),
                    activeGame.getTile(AliasHandler.resolveTile(planetName)), unitLong + " " + planetName,
                activeGame);
            successMessage = "Placed a " + Helper.getEmojiFromDiscord("pds") + " on "
                    + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
        } else {
             Tile tile;
             String producedOrPlaced = "Produced";
            if ("gf".equalsIgnoreCase(unit) || "mf".equalsIgnoreCase(unit) || "2gf".equalsIgnoreCase(unitLong)) {
                if ("2gf".equalsIgnoreCase(unitLong)) {
                    if(!planetName.contains("space")){
                        new AddUnits().unitParsing(event, player.getColor(),
                            activeGame.getTile(AliasHandler.resolveTile(planetName)), "2 gf " + planetName,
                            activeGame);
                        successMessage = producedOrPlaced+" 2 " + Helper.getEmojiFromDiscord("infantry") + " on "+ Helper.getPlanetRepresentation(planetName, activeGame) + ".";
                    }else {
                        tile = activeGame.getTileByPosition(planetName.replace("space",""));
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, "2 gf",
                            activeGame);
                        successMessage = producedOrPlaced+" 2 " + Helper.getEmojiFromDiscord("infantry") + " in space.";
                    }
                } else {
                   
                    if(!planetName.contains("space")){
                        tile = activeGame.getTile(AliasHandler.resolveTile(planetName));
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, unit + " " + planetName,
                            activeGame);
                        successMessage = producedOrPlaced+" a " + Helper.getEmojiFromDiscord(unitLong) + " on "+ Helper.getPlanetRepresentation(planetName, activeGame) + ".";
                    } else {
                        tile = activeGame.getTileByPosition(planetName.replace("space",""));
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, unit,
                            activeGame);
                        successMessage = producedOrPlaced+" a " + Helper.getEmojiFromDiscord(unitLong) + " in space.";
                    }
                    
                }
            } else {
                if ("2ff".equalsIgnoreCase(unitLong)) {
                    new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(planetName),
                            "2 ff", activeGame);
                    successMessage = "Produced 2 " + Helper.getEmojiFromDiscord("fighter") + " in tile "
                            + AliasHandler.resolveTile(planetName) + ".";
                } else {
                    new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(planetName),
                            unit, activeGame);
                    successMessage = "Produced a " + Helper.getEmojiFromDiscord(unitLong) + " in tile "
                            + AliasHandler.resolveTile(planetName) + ".";
                }

            }
        }
        if (("sd".equalsIgnoreCase(unit) || "pds".equalsIgnoreCase(unitLong) ) && event.getMessage().getContentRaw().contains("for construction")) {

            if (activeGame.isFoWMode() || !"action".equalsIgnoreCase(activeGame.getCurrentPhase())) {
                MessageHelper.sendMessageToChannel(event.getChannel(), playerRep + " " + successMessage);
            } else {
                List<ThreadChannel> threadChannels = activeGame.getActionsChannel().getThreadChannels();
                if (threadChannels == null)
                    return;
                String threadName = activeGame.getName() + "-round-" + activeGame.getRound() + "-construction";
                // SEARCH FOR EXISTING OPEN THREAD
                for (ThreadChannel threadChannel_ : threadChannels) {
                    if (threadChannel_.getName().equals(threadName)) {
                        MessageHelper.sendMessageToChannel(threadChannel_,
                                playerRep + " " + successMessage);
                    }
                }
            }
            if(player.hasLeader("mahactagent", activeGame)){
                String message = playerRep + " Would you like to put a cc from reinforcements in the same system?";
                Button placeCCInSystem = Button.success(
                        finsFactionCheckerPrefix + "reinforcements_cc_placement_" + planetName,
                        "Place A CC From Reinforcements In The System.");
                Button NoDontWantTo = Button.primary(finsFactionCheckerPrefix + "deleteButtons",
                        "Don't Place A CC In The System.");
                List<Button> buttons = List.of(placeCCInSystem, NoDontWantTo);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            }else{
                if(!player.getSCs().contains(Integer.parseInt("4")) && "action".equalsIgnoreCase(activeGame.getCurrentPhase())){
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
                        List<ThreadChannel> threadChannels = activeGame.getActionsChannel().getThreadChannels();
                        if (threadChannels == null)
                            return;
                        String threadName = activeGame.getName() + "-round-" + activeGame.getRound() + "-construction";
                        // SEARCH FOR EXISTING OPEN THREAD
                        for (ThreadChannel threadChannel_ : threadChannels) {
                            if (threadChannel_.getName().equals(threadName)) {
                                MessageHelper.sendMessageToChannel(threadChannel_,
                                        playerRep + " Placed A CC From Reinforcements In The "
                                                + Helper.getPlanetRepresentation(planetName, activeGame) + " system");
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
                        + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), successMessage);
                event.getMessage().delete().queue();
            } else {
                event.getMessage().editMessage(editedMessage).queue();
            }

        }

        if(player.getLeaderIDs().contains("titanscommander") && !player.hasLeaderUnlocked("titanscommander")){
            ButtonHelper.commanderUnlockCheck(player, activeGame, "titans", event);
        }
        if(player.getLeaderIDs().contains("saarcommander") && !player.hasLeaderUnlocked("saarcommander")){
            ButtonHelper.commanderUnlockCheck(player, activeGame, "saar", event);
        }
        if(player.getLeaderIDs().contains("mentakcommander") && !player.hasLeaderUnlocked("mentakcommander")){
            ButtonHelper.commanderUnlockCheck(player, activeGame, "mentak", event);
        }
        if(player.getLeaderIDs().contains("l1z1xcommander") && !player.hasLeaderUnlocked("l1z1xcommander")){
            ButtonHelper.commanderUnlockCheck(player, activeGame, "l1z1x", event);
        }
        if(player.getLeaderIDs().contains("muaatcommander") && !player.hasLeaderUnlocked("muaatcommander") && "warsun".equalsIgnoreCase(unitLong)){
            ButtonHelper.commanderUnlockCheck(player, activeGame, "muaat", event);
        }
        if(player.getLeaderIDs().contains("argentcommander") && !player.hasLeaderUnlocked("argentcommander")){
            ButtonHelper.commanderUnlockCheck(player, activeGame, "argent", event);
        }
        
        if(player.getLeaderIDs().contains("naazcommander") && !player.hasLeaderUnlocked("naazcommander")){
            ButtonHelper.commanderUnlockCheck(player, activeGame, "naaz", event);
        }
        if(player.getLeaderIDs().contains("arboreccommander") && !player.hasLeaderUnlocked("arboreccommander")){
            ButtonHelper.commanderUnlockCheck(player, activeGame, "arborec", event);
        }
    }

    public static void placeUnitAndDeleteButton(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String trueIdentity){
        String unitNPlanet = buttonID.replace("placeOneNDone_", "");
        String skipbuild= unitNPlanet.substring(0, unitNPlanet.indexOf("_"));
        unitNPlanet = unitNPlanet.replace(skipbuild+"_","");
        String unitLong = unitNPlanet.substring(0, unitNPlanet.indexOf("_"));
        String planetName = unitNPlanet.replace(unitLong + "_", "");
        String unit = AliasHandler.resolveUnit(unitLong);
        String producedOrPlaced = "Produced";
         if("skipbuild".equalsIgnoreCase(skipbuild)){
            producedOrPlaced = "Placed";
        }
        String successMessage;
        String playerRep = Helper.getPlayerRepresentation(player, activeGame);
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
                successMessage = "Placed a " + Helper.getEmojiFromDiscord("spacedock") + " on "
                        + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
            }
        } else if ("pds".equalsIgnoreCase(unitLong)) {
            new AddUnits().unitParsing(event, player.getColor(),
                    activeGame.getTile(AliasHandler.resolveTile(planetName)), unitLong + " " + planetName,
                activeGame);
            successMessage = "Placed a " + Helper.getEmojiFromDiscord("pds") + " on "
                    + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
        } else {
            Tile tile;
            if ("gf".equalsIgnoreCase(unit) || "mf".equalsIgnoreCase(unit) || "2gf".equalsIgnoreCase(unitLong)) {
                if ("2gf".equalsIgnoreCase(unitLong)) {
                    if(!planetName.contains("space")){
                        new AddUnits().unitParsing(event, player.getColor(),
                            activeGame.getTile(AliasHandler.resolveTile(planetName)), "2 gf " + planetName,
                            activeGame);
                        successMessage = producedOrPlaced+" 2 " + Helper.getEmojiFromDiscord("infantry") + " on "+ Helper.getPlanetRepresentation(planetName, activeGame) + ".";
                    }else {
                        tile = activeGame.getTileByPosition(planetName.replace("space",""));
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, "2 gf ",
                            activeGame);
                        successMessage = producedOrPlaced+" 2 " + Helper.getEmojiFromDiscord("infantry") + " in space.";
                    }
                } else {
                   
                    if(!planetName.contains("space")){
                        tile = activeGame.getTile(AliasHandler.resolveTile(planetName));
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, unit + " " + planetName,
                            activeGame);
                        successMessage = producedOrPlaced+" a " + Helper.getEmojiFromDiscord(unitLong) + " on "+ Helper.getPlanetRepresentation(planetName, activeGame) + ".";
                    } else {
                        tile = activeGame.getTileByPosition(planetName.replace("space",""));
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, unit,
                            activeGame);
                        successMessage = producedOrPlaced+" a " + Helper.getEmojiFromDiscord(unitLong) + " in space.";
                    }
                    
                }
            } else {
                if ("2ff".equalsIgnoreCase(unitLong)) {
                    new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(planetName),
                            "2 ff", activeGame);
                    successMessage = producedOrPlaced+" 2 " + Helper.getEmojiFromDiscord("fighter") + " in tile "
                            + AliasHandler.resolveTile(planetName) + ".";
                } else {
                    new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(planetName),
                            unit, activeGame);
                    successMessage = producedOrPlaced+" a " + Helper.getEmojiFromDiscord(unitLong) + " in tile "
                            + AliasHandler.resolveTile(planetName) + ".";
                }

            }
            if(player.getLeaderIDs().contains("l1z1xcommander") && !player.hasLeaderUnlocked("l1z1xcommander")){
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
        if(!"skipbuild".equalsIgnoreCase(skipbuild)){
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message2, buttons);
        }


        if(player.getLeaderIDs().contains("titanscommander") && !player.hasLeaderUnlocked("titanscommander")){
            ButtonHelper.commanderUnlockCheck(player, activeGame, "titans", event);
        }
        if(player.getLeaderIDs().contains("saarcommander") && !player.hasLeaderUnlocked("saarcommander")){
            ButtonHelper.commanderUnlockCheck(player, activeGame, "saar", event);
        }
        if(player.getLeaderIDs().contains("mentakcommander") && !player.hasLeaderUnlocked("mentakcommander")){
            ButtonHelper.commanderUnlockCheck(player, activeGame, "mentak", event);
        }
        if(player.getLeaderIDs().contains("l1z1xcommander") && !player.hasLeaderUnlocked("l1z1xcommander")){
            ButtonHelper.commanderUnlockCheck(player, activeGame, "l1z1x", event);
        }
        if(player.getLeaderIDs().contains("muaatcommander") && !player.hasLeaderUnlocked("muaatcommander") && "warsun".equalsIgnoreCase(unitLong)){
            ButtonHelper.commanderUnlockCheck(player, activeGame, "muaat", event);
        }
        if(player.getLeaderIDs().contains("argentcommander") && !player.hasLeaderUnlocked("argentcommander")){
            ButtonHelper.commanderUnlockCheck(player, activeGame, "argent", event);
        }
        if(player.getLeaderIDs().contains("naazcommander") && !player.hasLeaderUnlocked("naazcommander")){
            ButtonHelper.commanderUnlockCheck(player, activeGame, "naaz", event);
        }
        if(player.getLeaderIDs().contains("arboreccommander") && !player.hasLeaderUnlocked("arboreccommander")){
            ButtonHelper.commanderUnlockCheck(player, activeGame, "arborec", event);
        }




        event.getMessage().delete().queue();
    }
    public static void spaceLandedUnits(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String buttonLabel){
        String rest = buttonID.replace("spaceUnits_", "");
        String pos = rest.substring(0, rest.indexOf("_"));
        rest = rest.replace(pos + "_", "");
        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1);
        String unitkey;
        String planet = "";
        if (rest.contains("_")) {
            unitkey = rest.split("_")[0];
            planet = rest.split("_")[1].replace(" ", "").toLowerCase().replace("-","").replace("'","");
        } else {
            unitkey = rest;
        }
        if(buttonLabel.toLowerCase().contains("damaged")){
            new AddUnits().unitParsing(event, player.getColor(),
            activeGame.getTileByPosition(pos), amount +" " +unitkey, activeGame);
             activeGame.getTileByPosition(pos).addUnitDamage("space", unitkey,amount);
        }else{
             new AddUnits().unitParsing(event, player.getColor(),
            activeGame.getTileByPosition(pos), amount +" " +unitkey, activeGame);
        }
       
        String key = Mapper.getUnitID(AliasHandler.resolveUnit(unitkey), player.getColor());
        activeGame.getTileByPosition(pos).removeUnit(planet,key, amount);
        List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, activeGame, event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), ident+"Undid landing of "+amount+ " "+unitkey + " on "+planet);
        event.getMessage().editMessage(event.getMessage().getContentRaw())
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }
    public static void landingUnits(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String buttonLabel){
        String rest = buttonID.replace("landUnits_", "");
        String pos = rest.substring(0, rest.indexOf("_"));
        rest = rest.replace(pos + "_", "");
        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1);
        String unitkey;
        String planet = "";
        if (rest.contains("_")) {
            unitkey = rest.split("_")[0];
            planet = rest.split("_")[1].replace(" ", "").toLowerCase().replace("'","").replace("-","");
        } else {
            unitkey = rest;
        }
        if(buttonLabel.toLowerCase().contains("damaged")){
            
            new AddUnits().unitParsing(event, player.getColor(),
            activeGame.getTileByPosition(pos), amount +" " +unitkey+" "+planet, activeGame);
             activeGame.getTileByPosition(pos).addUnitDamage(planet, unitkey,amount);
        }else{
             new AddUnits().unitParsing(event, player.getColor(),
            activeGame.getTileByPosition(pos), amount +" " +unitkey+" "+planet, activeGame);
        }
       
        String key = Mapper.getUnitID(AliasHandler.resolveUnit(unitkey), player.getColor());
        activeGame.getTileByPosition(pos).removeUnit("space",key, amount);
        List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, activeGame, event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), ident+" Landed "+amount+ " "+unitkey + " on "+planet);
        event.getMessage().editMessage(event.getMessage().getContentRaw())
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }
    public static void resolvingCombatDrones(ButtonInteractionEvent event, Game activeGame, Player player, String ident, String buttonLabel){
        Tile tile = activeGame.getTileByPosition(activeGame.getActiveSystem());
        int numff = 0;
        for(UnitHolder unitHolder : tile.getUnitHolders().values()){
            if(unitHolder instanceof Planet){
                continue;
            }
            
            String colorID = Mapper.getColorID(player.getColor());
            String ffKey = colorID + "_ff.png";
            if (unitHolder.getUnits() != null) {
                if (unitHolder.getUnits().get(ffKey) != null) {
                    numff = unitHolder.getUnits().get(ffKey);
                }
            }
        }
        if(numff > 0){
            new RemoveUnits().unitParsing(event, player.getColor(), tile, numff + " fighters", activeGame);
            new AddUnits().unitParsing(event, player.getColor(), tile, numff + " infantry", activeGame);
        }
        List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, activeGame, event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), ident+" Turned "+numff+ " fighters into infantry using the combat drone ability");
                event.getMessage().editMessage(event.getMessage().getContentRaw())
                            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();

    }
    public static void movingUnitsInTacticalAction(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String buttonLabel){
        String remove = "Move";
        HashMap<String, Integer> currentSystem = activeGame.getCurrentMovedUnitsFrom1System();
       HashMap<String, Integer> currentActivation = activeGame.getMovedUnitsFromCurrentActivation();
       String rest;
       if(buttonID.contains("Remove")){
           remove = "Remove";
           rest = buttonID.replace("unitTacticalRemove_", "").toLowerCase();
       }else{
           rest = buttonID.replace("unitTacticalMove_", "").toLowerCase();
       }
       String pos = rest.substring(0, rest.indexOf("_"));
       Tile tile = activeGame.getTileByPosition(pos);
       rest = rest.replace(pos + "_", "");

       if(rest.contains("reverseall") || rest.contains("moveall")){
          
           if(rest.contains("reverse"))
           {
               for(String unit : currentSystem.keySet()){
                   
                   String unitkey;
                   String planet = "";
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
                       activeGame.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitkey,
                               currentActivation.get(unitkey) - amount);
                   }
                   if(unitkey.contains("damaged")){
                       unitkey = unitkey.replace("damaged", "");
                       damagedMsg = " damaged ";
                   }
                   new AddUnits().unitParsing(event, player.getColor(),
                   activeGame.getTileByPosition(pos), (amount) + " " + unitkey + " " + planet, activeGame);
                   if(damagedMsg.contains("damaged")){
                       if ("".equalsIgnoreCase(planet)) {
                           planet = "space";
                       }
                       String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unitkey), player.getColor());
                       activeGame.getTileByPosition(pos).addUnitDamage(planet, unitID, (amount));
                   }
               }
              
               activeGame.resetCurrentMovedUnitsFrom1System();
           }else{
               Map<String, String> unitRepresentation = Mapper.getUnitImageSuffixes();
               Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
               String cID = Mapper.getColorID(player.getColor());
               for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
                   String name = entry.getKey();
                   String representation = planetRepresentations.get(name);
                   if (representation == null){
                   }
                   UnitHolder unitHolder = entry.getValue();
                   HashMap<String, Integer> units1 = unitHolder.getUnits();
                   Map<String, Integer> units = new HashMap<>(units1);
               
                   if (unitHolder instanceof Planet planet) {
                       for (Map.Entry<String, Integer> unitEntry : units.entrySet()) {
                           String key = unitEntry.getKey();
                           if ((key.endsWith("gf.png") || key.endsWith("mf.png")) &&key.contains(cID)) {
                               String unitKey = key.replace(cID+"_", "");
                               unitKey = unitKey.replace(".png", "");
                               unitKey = ButtonHelper.getUnitName(unitKey);
                               int amount = unitEntry.getValue();
                               rest = unitKey.toLowerCase()+"_"+unitHolder.getName().toLowerCase();
                               if (currentSystem.containsKey(rest)) {
                                   activeGame.setSpecificCurrentMovedUnitsFrom1System(rest, currentSystem.get(rest) + amount);
                               } else {
                                   activeGame.setSpecificCurrentMovedUnitsFrom1System(rest, amount);
                               }
                               if (currentActivation.containsKey(unitKey)) {
                                   activeGame.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitKey,
                                           currentActivation.get(unitKey) + amount);
                               } else {
                                   activeGame.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitKey, amount);
                               }
                               String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unitKey), player.getColor());

                               new RemoveUnits().removeStuff(event, activeGame.getTileByPosition(pos), unitEntry.getValue(), unitHolder.getName(), unitID, player.getColor(), false, activeGame);

                             //  validTile2 = Button.danger(finChecker+"unitTactical"+moveOrRemove+"_"+tile.getPosition()+"_"+x+unitKey+"_"+representation, moveOrRemove+" "+x+" Infantry from "+Helper.getPlanetRepresentation(representation.toLowerCase(), activeMap)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("infantry")));
                           }
                       }
                   }
                   else{
                       for (Map.Entry<String, Integer> unitEntry : units.entrySet()) {

                           String key = unitEntry.getKey();
                           for (String unitRepresentationKey : unitRepresentation.keySet()) {
                               if (key.endsWith(unitRepresentationKey) && key.contains(cID)) {
                                   
                                   String unitKey = key.replace(cID+"_", "");
                                   
                                   int totalUnits = unitEntry.getValue();
                                   int amount;
                                   unitKey  = unitKey.replace(".png", "");
                                   unitKey = ButtonHelper.getUnitName(unitKey);
                                   int damagedUnits = 0;
                                   if(unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null){
                                       damagedUnits = unitHolder.getUnitDamage().get(key);
                                   }
                                   String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unitKey), player.getColor());

                                   new RemoveUnits().removeStuff(event, activeGame.getTileByPosition(pos), totalUnits, "space", unitID, player.getColor(), false, activeGame);
                                   if(damagedUnits > 0){
                                       rest = unitKey+"damaged";
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
                                   rest = unitKey;
                                   amount = totalUnits - damagedUnits;
                                   if(amount > 0){
                                       if (currentSystem.containsKey(rest)) {
                                           activeGame.setSpecificCurrentMovedUnitsFrom1System(rest, currentSystem.get(rest) + amount);
                                       } else {
                                           activeGame.setSpecificCurrentMovedUnitsFrom1System(rest, amount);
                                       }
                                       if (currentActivation.containsKey(unitKey)) {
                                           activeGame.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitKey,
                                                   currentActivation.get(unitKey) + amount);
                                       } else {
                                           activeGame.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitKey, amount);
                                       }
                                   }
                                   
                                   
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
       String unitkey;
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
                   activeGame.getTileByPosition(pos), (amount * -1) + " " + unitkey + " " + planet, activeGame);
           if(buttonLabel.toLowerCase().contains("damaged")){
               if ("".equalsIgnoreCase(planet)) {
                   planet = "space";
               }
               activeGame.getTileByPosition(pos).addUnitDamage(planet, unitID, (amount * -1));
           }
       } else {
           String planetName;
           if ("".equalsIgnoreCase(planet)) {
               planetName = "space";
           } else {
               planetName = planet.replace("'", "");
               planetName = AliasHandler.resolvePlanet(planetName);
           }

           new RemoveUnits().removeStuff(event, activeGame.getTileByPosition(pos), amount, planetName, unitID, player.getColor(), buttonLabel.toLowerCase().contains("damaged"), activeGame);
          // String key = Mapper.getUnitID(AliasHandler.resolveUnit(unitkey), player.getColor());
           //activeMap.getTileByPosition(pos).removeUnit(planetName, key, amount);
       }
       if(buttonLabel.toLowerCase().contains("damaged")){ 
           unitkey = unitkey + "damaged";
           rest = rest+"damaged";
       }
       if (currentSystem.containsKey(rest)) {
           activeGame.setSpecificCurrentMovedUnitsFrom1System(rest, currentSystem.get(rest) + amount);
       } else {
           activeGame.setSpecificCurrentMovedUnitsFrom1System(rest, amount);
       }
       if(currentSystem.get(rest) == 0){
           currentSystem.remove(rest);
       }
       if (currentActivation.containsKey(unitkey)) {
           activeGame.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitkey,
                   currentActivation.get(unitkey) + amount);
       } else {
           activeGame.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitkey, amount);
       }
       String message = ButtonHelper.buildMessageFromDisplacedUnits(activeGame, false, player, remove);
       List<Button> systemButtons = ButtonHelper.getButtonsForAllUnitsInSystem(player, activeGame,
               activeGame.getTileByPosition(pos), remove);
       event.getMessage().editMessage(message)
               .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }
    public static void assignHits(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String buttonLabel){
            String rest;
            rest = buttonID.replace("assignHits_", "");
            String pos = rest.substring(0, rest.indexOf("_"));
            Tile tile = activeGame.getTileByPosition(pos);
            rest = rest.replace(pos + "_", "");
            Player cabal = Helper.getPlayerFromAbility(activeGame, "amalgamation");
            if(rest.contains("All")){
                    Map<String, String> unitRepresentation = Mapper.getUnitImageSuffixes();
                    Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
                    String cID = Mapper.getColorID(player.getColor());
                    for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
                        String name = entry.getKey();
                        String representation = planetRepresentations.get(name);
                        UnitHolder unitHolder = entry.getValue();
                        HashMap<String, Integer> units1 = unitHolder.getUnits();
                        Map<String, Integer> units = new HashMap<>(units1);
                        if (unitHolder instanceof Planet) {
                            for (Map.Entry<String, Integer> unitEntry : units.entrySet()) {
                                String key = unitEntry.getKey();
                                if (key.contains(cID)) {
                                    String unitKey = key.replace(cID+"_", "");
                                    unitKey = unitKey.replace(".png", "");
                                    unitKey = ButtonHelper.getUnitName(unitKey);
                                    int amount = unitEntry.getValue();
                                    String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unitKey), player.getColor());
                                    new RemoveUnits().removeStuff(event, activeGame.getTileByPosition(pos), unitEntry.getValue(), unitHolder.getName(), unitID, player.getColor(), false, activeGame);
                                    if(cabal != null && FoWHelper.playerHasUnitsOnPlanet(cabal, tile, unitHolder.getName())&&!cabal.getFaction().equalsIgnoreCase(player.getFaction())){
                                        ButtonHelperFactionSpecific.cabalEatsUnit(player, activeGame, cabal, unitEntry.getValue(), unitKey, event);
                                    }
                                    if((player.getUnitsOwned().contains("mahact_infantry") || player.hasTech("cl2"))&& unitKey.toLowerCase().contains("inf")){
                                        ButtonHelperFactionSpecific.offerMahactInfButtons(player, activeGame);
                                    }
                                    if(player.hasInf2Tech() && unitKey.toLowerCase().contains("inf")){
                                        ButtonHelper.resolveInfantryDeath(activeGame, player, amount);
                                    }

                                }
                            }
                        }
                        else{
                            for (Map.Entry<String, Integer> unitEntry : units.entrySet()) {
                                String key = unitEntry.getKey();
                                for (String unitRepresentationKey : unitRepresentation.keySet()) {
                                    if (key.endsWith(unitRepresentationKey) && key.contains(cID)) {                         
                                        String unitKey = key.replace(cID+"_", "");                    
                                        int totalUnits = unitEntry.getValue();
                                        unitKey  = unitKey.replace(".png", "");
                                        unitKey = ButtonHelper.getUnitName(unitKey);
                                        String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unitKey), player.getColor());
                                        new RemoveUnits().removeStuff(event, activeGame.getTileByPosition(pos), totalUnits, "space", unitID, player.getColor(), false, activeGame);
                                        if(cabal != null && FoWHelper.playerHasShipsInSystem(cabal, tile)&&!cabal.getFaction().equalsIgnoreCase(player.getFaction())){
                                            ButtonHelperFactionSpecific.cabalEatsUnit(player, activeGame, cabal, totalUnits, unitKey, event);
                                        }
                                    }
                                }         
                        }             
                    }
                }
                String message2 = ident + " Removed all units";
                String message = event.getMessage().getContentRaw();
                List<Button> systemButtons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, activeGame, tile);
                event.getMessage().editMessage(message)
                        .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message2);

                return;
            }
            int amount = Integer.parseInt(rest.charAt(0) + "");
            rest = rest.substring(1);
            String unitkey;
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
        String planetName;
            if ("".equalsIgnoreCase(planet)) {
                planetName = "space";
                if(cabal != null && !cabal.getFaction().equalsIgnoreCase(player.getFaction())&& FoWHelper.playerHasShipsInSystem(cabal, tile)){
                    ButtonHelperFactionSpecific.cabalEatsUnit(player, activeGame, cabal, amount, unitkey, event);
                }
            } else {
                planetName = planet.replace("'", "");
                planetName = AliasHandler.resolvePlanet(planetName);
                if(cabal != null && !cabal.getFaction().equalsIgnoreCase(player.getFaction())&& FoWHelper.playerHasUnitsOnPlanet(cabal, tile, planetName)){
                    ButtonHelperFactionSpecific.cabalEatsUnit(player, activeGame, cabal, amount, unitkey, event);
                }
                if((player.getUnitsOwned().contains("mahact_infantry") || player.hasTech("cl2"))&& unitkey.toLowerCase().contains("inf")){
                    ButtonHelperFactionSpecific.offerMahactInfButtons(player, activeGame);
                }
                if(player.hasInf2Tech() && unitkey.toLowerCase().contains("inf")){
                    ButtonHelper.resolveInfantryDeath(activeGame, player, amount);
                }
            }
        new RemoveUnits().removeStuff(event, activeGame.getTileByPosition(pos), amount, planetName, unitID, player.getColor(), buttonLabel.toLowerCase().contains("damaged"), activeGame);
            
            
            String message = event.getMessage().getContentRaw();
            
            String message2 =  ident+ " Removed "+amount + " "+unitkey+" from "+planetName+" in tile "+tile.getRepresentationForButtons(activeGame, player);
            
            List<Button> systemButtons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, activeGame, tile);
            event.getMessage().editMessage(message)
                        .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message2);
    }
    public static void repairDamage(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident){
        String rest;
        rest = buttonID.replace("repairDamage_", "");
        
        String pos = rest.substring(0, rest.indexOf("_"));
        Tile tile = activeGame.getTileByPosition(pos);
        rest = rest.replace(pos + "_", "");
        
        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1);
        String unitkey;
        String planet = "";
        if (rest.contains("_")) {
            unitkey = rest.split("_")[0];
            planet = rest.split("_")[1].toLowerCase().replace(" ", "");
        } else {
            unitkey = rest;
        }
        String planetName;
        unitkey = unitkey.replace("damaged", "");
        planet = planet.replace("damaged", "");
        String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unitkey), player.getColor());
        if ("".equalsIgnoreCase(planet)) {
            planetName = "space";
        } else {
            planetName = planet.replace("'", "");
            planetName = AliasHandler.resolvePlanet(planetName);
        }       
        tile.removeUnitDamage(planetName, unitID, amount);             
        String message = event.getMessage().getContentRaw();
        String message2 =  ident+ " Repaired "+amount + " "+unitkey+" from "+planetName+" in tile "+tile.getRepresentationForButtons(activeGame, player);
        List<Button> systemButtons = ButtonHelper.getButtonsForRepairingUnitsInASystem(player, activeGame, tile);
        event.getMessage().editMessage(message)
                    .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message2);
    }
    public static void assignDamage(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident){
        String rest;
        rest = buttonID.replace("assignDamage_", "");
        
        String pos = rest.substring(0, rest.indexOf("_"));
        Tile tile = activeGame.getTileByPosition(pos);
        rest = rest.replace(pos + "_", "");
        
        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1);
        String unitkey;
        String planet = "";
        if (rest.contains("_")) {
            unitkey = rest.split("_")[0];
            planet = rest.split("_")[1].toLowerCase().replace(" ", "");
        } else {
            unitkey = rest;
        }
        String planetName;
        unitkey = unitkey.replace("damaged", "");
        planet = planet.replace("damaged", "");
        String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unitkey), player.getColor());
        if ("".equalsIgnoreCase(planet)) {
            planetName = "space";
        } else {
            planetName = planet.replace("'", "");
            planetName = AliasHandler.resolvePlanet(planetName);
        }       
        tile.addUnitDamage(planetName, unitID, amount);               
        String message = event.getMessage().getContentRaw();
        String message2 =  ident+ " Sustained "+amount + " "+unitkey+" from "+planetName+" in tile "+tile.getRepresentationForButtons(activeGame, player);
        List<Button> systemButtons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, activeGame, tile);
        event.getMessage().editMessage(message)
                    .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message2);
        for(int x=0; x < amount; x++){
            ButtonHelperFactionSpecific.resolveLetnevCommanderCheck(player, activeGame, event);
        }
    }

}