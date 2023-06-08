package ti4.helpers;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.io.File;
import java.util.*;

import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardspn.PNInfo;
import ti4.commands.explore.ExpFrontier;
import ti4.commands.explore.SendFragments;
import ti4.commands.player.PlanetRefresh;
import ti4.commands.special.KeleresHeroMentak;
import ti4.commands.tokens.AddCC;
import ti4.commands.tokens.RemoveCC;
import ti4.commands.units.AddUnits;
import ti4.commands.units.MoveUnits;
import ti4.generator.GenerateMap;
import ti4.generator.Mapper;
import ti4.map.Leader;
import ti4.map.Map;
import ti4.map.MapSaveLoadManager;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;


public class ButtonHelper {
    public static List<Button> getStartOfTurnButtons(Player player, Map activeMap, boolean doneActionThisTurn) {
        String finChecker = "FFCC_"+player.getFaction() + "_";
        List<Button> startButtons = new ArrayList<>();
        Button tacticalAction = Button.success(finChecker+"tacticalAction", "Tactical Action");
        Button componentAction = Button.success(finChecker+"componentAction", "Component Action");
        startButtons.add(tacticalAction);
        startButtons.add(componentAction);
        boolean hadAnyUnplayedSCs = false;
        for(Integer SC : player.getSCs())
        {
            if(!activeMap.getPlayedSCs().contains(SC))
            {
                hadAnyUnplayedSCs = true;
                Button strategicAction = Button.success(finChecker+"strategicAction_"+SC, "Play SC #"+SC);
                startButtons.add(strategicAction);
            }
        }

        if(!hadAnyUnplayedSCs && !doneActionThisTurn)
        {
            Button pass = Button.danger(finChecker+"passForRound", "Pass");
            startButtons.add(pass);
        }
        if(doneActionThisTurn)
        {
            Button pass = Button.danger("turnEnd", "End Turn");
            startButtons.add(pass);
        }

        Button transaction = Button.primary("transaction", "Transaction");
        startButtons.add(transaction);
        if(activeMap.getLatestTransactionMsg() != null && !activeMap.getLatestTransactionMsg().equalsIgnoreCase(""))
        {
            activeMap.getMainGameChannel().deleteMessageById(activeMap.getLatestTransactionMsg()).queue();
        }
        

        return startButtons;
    }
    public static List<Button> getPossibleRings(Player player, Map activeMap) {
        String finChecker = "FFCC_"+player.getFaction() + "_";
        List<Button> ringButtons = new ArrayList<>();
        Tile centerTile = activeMap.getTileByPosition("000");
        Button rex = Button.success(finChecker+"ringTile_000", centerTile.getRepresentationForButtons(activeMap, player));
        ringButtons.add(rex);
        int rings = activeMap.getRingCount();
        for(int x = 1; x < rings+1; x++)
        {
            Button ringX = Button.success(finChecker+"ring_"+x, "Ring #"+x);
            ringButtons.add(ringX);
        }
        Button corners = Button.success(finChecker+"ring_corners", "Corners");
        ringButtons.add(corners);
        return ringButtons;
    }
    public static List<Button> getTileInARing(Player player, Map activeMap, String buttonID, GenericInteractionCreateEvent event) {
        String finChecker = "FFCC_"+player.getFaction() + "_";
        List<Button> ringButtons = new ArrayList<>();
        String ringNum = buttonID.replace("ring_", "");
        
        if(ringNum.equalsIgnoreCase("corners")){
            Tile tr = activeMap.getTileByPosition("tl");
            if(tr != null && !AddCC.hasCC(event, player.getColor(), tr)){
                Button corners = Button.success(finChecker+"ringTile_tl", tr.getRepresentationForButtons(activeMap, player));
                ringButtons.add(corners);
            }
            tr = activeMap.getTileByPosition("tr");
            if(tr != null&& !AddCC.hasCC(event, player.getColor(), tr)){
                Button corners = Button.success(finChecker+"ringTile_tr", tr.getRepresentationForButtons(activeMap, player));
                ringButtons.add(corners);
            }
            tr = activeMap.getTileByPosition("bl");
            if(tr != null&& !AddCC.hasCC(event, player.getColor(), tr)){
                Button corners = Button.success(finChecker+"ringTile_bl", tr.getRepresentationForButtons(activeMap, player));
                ringButtons.add(corners);
            }
            tr = activeMap.getTileByPosition("br");
            if(tr != null&& !AddCC.hasCC(event, player.getColor(), tr)){
                Button corners = Button.success(finChecker+"ringTile_br", tr.getRepresentationForButtons(activeMap, player));
                ringButtons.add(corners);
            }
        }
        else{
            int ringN = Integer.parseInt(ringNum.charAt(0)+"");
            int totalTiles = ringN*6;
            if(ringNum.contains("_")){
                String side = ringNum.substring(ringNum.lastIndexOf("_")+1, ringNum.length());
                if(side.equalsIgnoreCase("left")){
                    for(int x = totalTiles/2; x < totalTiles+1; x++)
                    {
                        String pos = ringN+""+x;
                        Tile tile = activeMap.getTileByPosition(pos);
                        if(tile != null && !tile.getRepresentationForButtons(activeMap, player).contains("Hyperlane") && !AddCC.hasCC(event, player.getColor(), tile)){
                            Button corners = Button.success(finChecker+"ringTile_"+pos, tile.getRepresentationForButtons(activeMap, player));
                            ringButtons.add(corners);
                        }
                    }
                    String pos = ringN+"01";
                    Tile tile = activeMap.getTileByPosition(pos);
                    if(tile != null&& !tile.getRepresentationForButtons(activeMap, player).contains("Hyperlane")&& !AddCC.hasCC(event, player.getColor(), tile)){
                        Button corners = Button.success(finChecker+"ringTile_"+pos, tile.getRepresentationForButtons(activeMap, player));
                        ringButtons.add(corners);
                    }
                }
                else{
                    for(int x = 1; x < (totalTiles/2)+1; x++)
                    {
                        String pos = ringN+""+x;
                        if(x<10){
                            pos = ringN+"0"+x;
                        }
                        Tile tile = activeMap.getTileByPosition(pos);
                        if(tile != null&& !tile.getRepresentationForButtons(activeMap, player).contains("Hyperlane")&& !AddCC.hasCC(event, player.getColor(), tile)){
                            Button corners = Button.success(finChecker+"ringTile_"+pos, tile.getRepresentationForButtons(activeMap, player));
                            ringButtons.add(corners);
                        }
                    }
                }
            }
            else{
                if(ringN <5){
                    for(int x = 1; x < totalTiles+1; x++)
                    {
                        String pos = ringN+""+x;
                        if(x<10){
                            pos = ringN+"0"+x;
                        }
                        Tile tile = activeMap.getTileByPosition(pos);
                        if(tile != null&& !tile.getRepresentationForButtons(activeMap, player).contains("Hyperlane")&& !AddCC.hasCC(event, player.getColor(), tile)){
                            Button corners = Button.success(finChecker+"ringTile_"+pos, tile.getRepresentationForButtons(activeMap, player));
                            ringButtons.add(corners);
                        }
                    }
                }
                else{
                    Button ringLeft = Button.success(finChecker+"ring_"+ringN+"_left", "Left Half");
                    ringButtons.add(ringLeft);
                    Button ringRight = Button.success(finChecker+"ring_"+ringN+"_right", "Right Half");
                    ringButtons.add(ringRight);
                }
            }
        }

        return ringButtons;
    }
    public static void exploreDET(Player player, Map activeMap, ButtonInteractionEvent event) {
        Tile tile =  activeMap.getTileByPosition(activeMap.getActiveSystem());
        if(!FoWHelper.playerHasShipsInSystem(player, tile))
        {
            return;
        }
        if(player.hasTech("det") && tile.getUnitHolders().get("space").getTokenList().contains(Mapper.getTokenID(Constants.FRONTIER)))
        {
            new ExpFrontier().expFront(event, tile, activeMap, player);
        }
    }
    public static List<Button> scanlinkResolution(Player player, Map activeMap, ButtonInteractionEvent event) {
        Tile tile =  activeMap.getTileByPosition(activeMap.getActiveSystem());
        List<Button> buttons = new ArrayList<Button>();
        for(UnitHolder planetUnit : tile.getUnitHolders().values()){
            if(planetUnit.getName().equalsIgnoreCase("space")){
                continue;
            }
            Planet planetReal =  (Planet) planetUnit;
            String planet = planetReal.getName();    
            if (planetReal != null && planetReal.getOriginalPlanetType() != null && player.getPlanets().contains(planet) && (planetReal.getOriginalPlanetType().equalsIgnoreCase("industrial") || planetReal.getOriginalPlanetType().equalsIgnoreCase("cultural") || planetReal.getOriginalPlanetType().equalsIgnoreCase("hazardous"))) {
                String drawColor = planetReal.getOriginalPlanetType();
                Button resolveExplore2 = Button.success("movedNExplored_filler_"+planet+"_"+drawColor, "Explore "+Helper.getPlanetRepresentation(planet, activeMap));
                buttons.add(resolveExplore2);
            }
        }
        return buttons;
       
    }



    public static List<Button> getTilesToMoveFrom(Player player, Map activeMap, GenericInteractionCreateEvent event) {
        String finChecker = "FFCC_"+player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for (java.util.Map.Entry<String, Tile> tileEntry : new HashMap<>(activeMap.getTileMap()).entrySet()) {
			if (FoWHelper.playerHasUnitsInSystem(player, tileEntry.getValue()) && !AddCC.hasCC(event, player.getColor(), tileEntry.getValue())) {
                Tile tile = tileEntry.getValue();
                Button validTile = Button.success(finChecker+"tacticalMoveFrom_"+tileEntry.getKey(), tile.getRepresentationForButtons(activeMap, player));
                buttons.add(validTile);
			}
		}
        Button validTile = Button.danger(finChecker+"concludeMove", "Done Moving");
        buttons.add(validTile);
        Button validTile2 = Button.primary(finChecker+"ChooseDifferentDestination", "Activate A Differen System");
        buttons.add(validTile2);
        return buttons;
    }
    public static List<Button> moveAndGetLandingTroopsButtons(Player player, Map activeMap, ButtonInteractionEvent event) {
        String finChecker = "FFCC_"+player.getFaction() + "_";
        
        List<Button> buttons = new ArrayList<>();
        HashMap<String, Integer> displacedUnits =  activeMap.getMovedUnitsFromCurrentActivation();
        HashMap<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        Tile tile = activeMap.getTileByPosition(activeMap.getActiveSystem());
        tile = MoveUnits.flipMallice(event, tile, activeMap);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not flip Mallice");
            return buttons;
        }
        int cc = player.getTacticalCC();
        
        if (!AddCC.hasCC(event, player.getColor(), tile)) {
            cc -= 1;
            player.setTacticalCC(cc);
            AddCC.addCC(event, player.getColor(), tile, true);
        }
        for(String unit :displacedUnits.keySet()){
            int amount = displacedUnits.get(unit);
            new AddUnits().unitParsing(event, player.getColor(),
            tile, amount +" " +unit, activeMap);
        }
        activeMap.resetCurrentMovedUnitsFrom1TacticalAction();
        String colorID = Mapper.getColorID(player.getColor());
        String mechKey = colorID + "_mf.png";
        String infKey = colorID + "_gf.png";
        displacedUnits = activeMap.getCurrentMovedUnitsFrom1System();
        int mechDisplaced = 0;
        int infDisplaced = 0;
        for(String unit: displacedUnits.keySet()){
            if(unit.contains("infantry")){
                infDisplaced = infDisplaced + displacedUnits.get(unit);
            }
            if(unit.contains("mech")){
                mechDisplaced = mechDisplaced + displacedUnits.get(unit);
            }
        }
        for (java.util.Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            String name = entry.getKey();
            String representation = planetRepresentations.get(name);
            if (representation == null){
                representation = name;
            }
            UnitHolder unitHolder = entry.getValue();
            if (unitHolder instanceof Planet planet) {
                int limit = 0;
                
                if(tile.getUnitHolders().get("space").getUnits() != null && tile.getUnitHolders().get("space").getUnits().get(infKey) != null)
                {
                    limit = tile.getUnitHolders().get("space").getUnits().get(infKey) - infDisplaced;
                    for(int x = 1; x < limit +1; x++){
                        if(x > 2){
                            break;
                        }
                        Button validTile2 = Button.danger(finChecker+"landUnits_"+tile.getPosition()+"_"+x+"infantry_"+representation, "Land "+x+" Infantry on "+Helper.getPlanetRepresentation(representation.toLowerCase(), activeMap)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("infantry")));
                        buttons.add(validTile2);
                    }
                }
                if(tile.getUnitHolders().get("space").getUnits() != null && tile.getUnitHolders().get("space").getUnits().get(mechKey) != null)
                {
                    limit = tile.getUnitHolders().get("space").getUnits().get(mechKey) - mechDisplaced;
                    for(int x = 1; x < limit +1; x++){
                        if(x > 2){
                            break;
                        }
                        Button validTile2 = Button.danger(finChecker+"landUnits_"+tile.getPosition()+"_"+x+"mech_"+representation, "Land "+x+" Mech(s) on "+Helper.getPlanetRepresentation(representation.toLowerCase(), activeMap)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("mech")));
                        buttons.add(validTile2);
                    }
                }
            }
        }
        Button concludeMove = Button.primary(finChecker+"doneLanding", "Done landing troops.");
        buttons.add(concludeMove);
        return buttons;
    }
    public static String putInfWithMechsForStarforge(String pos, String successMessage, Map activeMap, Player player, ButtonInteractionEvent event) {
        
        Set<String> tiles = FoWHelper.getAdjacentTiles(activeMap,pos,player, true);
        if(!tiles.contains(pos))
        {
            tiles.add(pos);
        }
        for(String tilePos : tiles){
            Tile tile = activeMap.getTileByPosition(tilePos);
            for(UnitHolder unitHolder :tile.getUnitHolders().values())
            {

                String colorID = Mapper.getColorID(player.getColor());
                String mechKey = colorID + "_mf.png";
                int numMechs = 0;
                if (unitHolder.getUnits() != null) {

                    if (unitHolder.getUnits().get(mechKey) != null) {
                        numMechs = unitHolder.getUnits().get(mechKey);
                    }
                    if(numMechs > 0)
                    {
                        String planetName = "";
                        if(unitHolder.getName().equalsIgnoreCase("space")) {
                            planetName = " "+unitHolder.getName();
                        }
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, numMechs + " infantry"+planetName, activeMap);
                        
                        successMessage = successMessage + "\n Put "+numMechs +" "+Helper.getEmojiFromDiscord("infantry")+" with the mechs in "+tile.getRepresentationForButtons(activeMap,player);
                    }
                }
            }
        }

        return successMessage;

    }
    public static List<Button> landAndGetBuildButtons(Player player, Map activeMap, ButtonInteractionEvent event) {
        String finChecker = "FFCC_"+player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        HashMap<String, Integer> displacedUnits =  activeMap.getCurrentMovedUnitsFrom1System();
        HashMap<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        Tile tile = activeMap.getTileByPosition(activeMap.getActiveSystem());
        
        for(String unit :displacedUnits.keySet()){
            int amount = displacedUnits.get(unit);
            String[] combo = unit.split("_");
            combo[1] = combo[1].toLowerCase().replace(" ", "");
            combo[1] = combo[1].replace("'", "");
            new AddUnits().unitParsing(event, player.getColor(),
            tile, amount +" " +combo[0]+" "+combo[1], activeMap);
            String key = Mapper.getUnitID(AliasHandler.resolveUnit(combo[0]), player.getColor());
            tile.removeUnit("space",key, amount);
        }
        activeMap.resetCurrentMovedUnitsFrom1System();
        Button buildButton = Button.danger(finChecker+"tacticalActionBuild_"+activeMap.getActiveSystem(), "Build in this system.");
        buttons.add(buildButton);
        Button concludeMove = Button.danger(finChecker+"doneWithTacticalAction", "Conclude tactical action");
        buttons.add(concludeMove);
        return buttons;
    }
    public static String buildMessageFromDisplacedUnits(Map activeMap, boolean landing) {
        String message = "";
        HashMap<String, Integer> displacedUnits =  activeMap.getCurrentMovedUnitsFrom1System();
        
        for(String unit :displacedUnits.keySet())
        {
            int amount = displacedUnits.get(unit);
            String planet  = null;
            if(unit.contains("_")){
                planet = unit.substring(unit.lastIndexOf("_")+1, unit.length());
                unit = unit.replace("_"+planet, "");
            }
            if(landing)
            {
                message = message + "Landed "+amount + " " +Helper.getEmojiFromDiscord(unit.toLowerCase());
                if(planet == null){
                    message = message + "\n";
                }
                else{
                    message = message + " on the planet "+Helper.getPlanetRepresentation(planet.toLowerCase(), activeMap)+"\n";
                }
            }
            else {
                message = message + "Moved "+amount + " " +Helper.getEmojiFromDiscord(unit.toLowerCase());
                if(planet == null){
                    message = message + "\n";
                }
                else{
                    message = message + " from the planet "+Helper.getPlanetRepresentation(planet.toLowerCase(), activeMap)+"\n";
                }
            }
            
        }
        return message;
    }
    public static List<LayoutComponent> turnButtonListIntoActionRowList(List<Button> buttons) {
        final List<LayoutComponent> list = new ArrayList<>();
        List<ItemComponent> buttonRow = new ArrayList<ItemComponent>();
        for(Button button : buttons)
        {
            if(buttonRow.size() == 5)
            {
                list.add(ActionRow.of(buttonRow));
                buttonRow =  new ArrayList<ItemComponent>();
            }
            buttonRow.add(button);
        }
        if(buttonRow.size() > 0)
        {
            list.add(ActionRow.of(buttonRow));
        }
        return list;
    }

    public static String getUnitName(String id) {
        String name = "";
        switch(id){
            case "fs"-> name = "Flagship";
            case "ws"-> name = "Warsun";
            case "gf"-> name = "Infantry";
            case "mf"-> name = "Mech";
            case "sd"-> name = "Spacedock";
            case "pd"-> name = "pds";
            case "ff"-> name = "fighter";
            case "ca"-> name = "cruiser";
            case "dd"-> name = "destroyer";
            case "cv"-> name = "carrier";
            case "dn"-> name = "dreadnought";
        }
        return name;
    }
    public static List<Button> getButtonsForAllUnitsInSystem(Player player, Map activeMap, Tile tile) {
        String finChecker = "FFCC_"+player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();

        java.util.Map<String, String> unitRepresentation = Mapper.getUnits();
        HashMap<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        String cID = Mapper.getColorID(player.getColor());
        for (java.util.Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            String name = entry.getKey();
            String representation = planetRepresentations.get(name);
            if (representation == null){
                representation = name;
            }
            UnitHolder unitHolder = entry.getValue();
            HashMap<String, Integer> units = unitHolder.getUnits();
           
            if (unitHolder instanceof Planet planet) {
                for (java.util.Map.Entry<String, Integer> unitEntry : units.entrySet()) {
                    String key = unitEntry.getKey();
                    if ((key.endsWith("gf.png") || key.endsWith("mf.png")) &&key.contains(cID)) {
                        String unitKey = key.replace(cID+"_", "");
                        unitKey = unitKey.replace(".png", "");
                        unitKey = ButtonHelper.getUnitName(unitKey);
                        for(int x = 1; x < unitEntry.getValue() +1; x++){
                            if(x > 2){
                                break;
                            }
                            Button validTile2 = null;
                            if(key.contains("gf")){
                                 validTile2 = Button.danger(finChecker+"unitTacticalMove_"+tile.getPosition()+"_"+x+unitKey+"_"+representation, "Move "+x+" Infantry from "+Helper.getPlanetRepresentation(representation.toLowerCase(), activeMap)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("infantry")));
                            }
                            else{
                                 validTile2 = Button.danger(finChecker+"unitTacticalMove_"+tile.getPosition()+"_"+x+unitKey+"_"+representation, "Move "+x+" Mech from "+Helper.getPlanetRepresentation(representation.toLowerCase(), activeMap)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("mech")));
                            }
                            buttons.add(validTile2);
                        }
                    }
                }


            }
            else{
                for (java.util.Map.Entry<String, Integer> unitEntry : units.entrySet()) {

                    String key = unitEntry.getKey();
                    
                   
                    for (String unitRepresentationKey : unitRepresentation.keySet()) {
                        if (key.endsWith(unitRepresentationKey) && key.contains(cID)) {
                            
                            String unitKey = key.replace(cID+"_", "");
                            unitKey  = unitKey.replace(".png", "");
                            unitKey = ButtonHelper.getUnitName(unitKey);
                            for(int x = 1; x < unitEntry.getValue() +1; x++){
                                if(x > 2){
                                    break;
                                }
                                Button validTile2 = Button.danger(finChecker+"unitTacticalMove_"+tile.getPosition()+"_"+x+unitKey, "Move "+x+" "+unitRepresentation.get(unitRepresentationKey)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unitRepresentation.get(unitRepresentationKey).toLowerCase().replace(" ", ""))));
                                buttons.add(validTile2);
                            }
                        }
                    }
                }
            }
            
           
            
        }
        Button concludeMove = Button.primary(finChecker+"doneWithOneSystem_"+tile.getPosition(), "Done moving units from this system");
        buttons.add(concludeMove);
        HashMap<String, Integer> displacedUnits = activeMap.getCurrentMovedUnitsFrom1System();
        for(String unit :displacedUnits.keySet())
        {
            String unitkey = "";
            String planet = "";
            if(unit.contains("_"))
            {
                unitkey =unit.split("_")[0];
                planet =unit.split("_")[1];
            }
            else{
                unitkey = unit;
            }
            for(int x = 1; x < displacedUnits.get(unit)+1; x++)
            {
                String blabel =  "Undo move of "+x+" "+unitkey;
                if(!planet.equalsIgnoreCase(""))
                {
                    blabel = blabel + " from "+Helper.getPlanetRepresentation(planet.toLowerCase(), activeMap);
                }
                Button validTile2 = Button.success(finChecker+"unitTacticalMove_"+tile.getPosition()+"_"+x+unit+"_reverse",blabel).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unitkey.toLowerCase().replace(" ", ""))));
                buttons.add(validTile2);
            }
        }
        

        return buttons;
    }
    public static boolean tileHasPDS2Cover(Player player, Map activeMap, String tilePos) {
        
        Set<String> adjTiles = FoWHelper.getAdjacentTiles(activeMap, tilePos, player, false);

        for (String tilePo : adjTiles) {
			Tile tile = activeMap.getTileByPosition(tilePo);
            for(UnitHolder area : tile.getUnitHolders().values())
            {
                for(Player p :activeMap.getPlayers().values())
                {
                    if(p.isRealPlayer() && !p.getFaction().equalsIgnoreCase(player.getFaction()))
                    {
                        String unitKey1 = Mapper.getUnitID(AliasHandler.resolveUnit("pds"), p.getColor());
                        if(area.getUnits().containsKey(unitKey1) && p.hasPDS2Tech())
                        {
                            return true;
                        }
                        if(p.getFaction().equalsIgnoreCase("xxcha"))
                        {
                            String unitKey2 = Mapper.getUnitID(AliasHandler.resolveUnit("flagship"), p.getColor());
                            String unitKey3 = Mapper.getUnitID(AliasHandler.resolveUnit("mech"), p.getColor());
                            if(area.getUnits().containsKey(unitKey2) || area.getUnits().containsKey(unitKey3))
                            {
                                return true;
                            }
                        }
                    }
                }
            }
		}
        return false;
    }


    //playerHasUnitsInSystem(player, tile);




    public static List<Button> getPlayersToTransact(Map activeMap, Player p) {
        List<Button> playerButtons = new ArrayList<>();
        String finChecker = "FFCC_"+p.getFaction() + "_";
        for (Player player : activeMap.getPlayers().values()) {
            if (player.isRealPlayer()) {
                if(player.getFaction().equalsIgnoreCase(p.getFaction()))
                {
                    continue;
                }
                String faction = player.getFaction();
                if (faction != null && Mapper.isFaction(faction)) {
                    Button button = null;
                    if (!activeMap.isFoWMode()) {
                        button = Button.secondary(finChecker+"transactWith_"+faction, " ");
                        
                        String factionEmojiString = Helper.getFactionIconFromDiscord(faction);
                        button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                    } else {
                            button = Button.secondary(finChecker+"transactWith_"+player.getColor(), player.getColor());
                    }
                    playerButtons.add(button);
                }
                
            }
        }
        return playerButtons;
    }

    public static List<Button> getStuffToTransButtons(Map activeMap, Player p1, Player p2) {
        String finChecker = "FFCC_"+p1.getFaction() + "_";
        List<Button> stuffToTransButtons = new ArrayList<>();
        if(p1.getTg() > 0)
        {
            Button transact= Button.success(finChecker+"transact_TGs_"+p2.getFaction(), "TGs");
            stuffToTransButtons.add(transact);
        }
        if(p1.getCommodities() > 0)
        {
            Button transact = Button.success(finChecker+"transact_Comms_"+p2.getFaction(), "Commodities");
            stuffToTransButtons.add(transact);
        }
        if((p1.hasAbility("arbiters") || p2.hasAbility("arbiters")) && p1.getAc() > 0)
        {
            Button transact = Button.success(finChecker+"transact_ACs_"+p2.getFaction(), "Action Cards");
            stuffToTransButtons.add(transact);
        }
        if(p1.getPnCount() > 0)
        {
            Button transact = Button.success(finChecker+"transact_PNs_"+p2.getFaction(), "Promissory Notes");
            stuffToTransButtons.add(transact);
        }
        if(p1.getFragments().size() > 0)
        {
            Button transact = Button.success(finChecker+"transact_Frags_"+p2.getFaction(), "Fragments");
            stuffToTransButtons.add(transact);
        }
        return stuffToTransButtons;
    }


    public static void resolveSpecificTransButtons(Map activeMap, Player p1, String buttonID, ButtonInteractionEvent event) {
        String finChecker = "FFCC_"+p1.getFaction() + "_";
   
        List<Button> stuffToTransButtons = new ArrayList<>();
        buttonID = buttonID.replace("transact_", "");
        String thingToTrans = buttonID.substring(0, buttonID.indexOf("_"));
        String factionToTrans = buttonID.substring(buttonID.indexOf("_")+1, buttonID.length());
        Player p2 = Helper.getPlayerFromColorOrFaction(activeMap, factionToTrans);

        switch(thingToTrans)
        {
            case "TGs" -> {

                String message = "Click the amount of tgs you would like to send";
                for(int x = 1; x < p1.getTg()+1; x++)
                {
                    Button transact = Button.success(finChecker+"send_TGs_"+p2.getFaction() + "_"+x, ""+x);
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),message, stuffToTransButtons);
            }
            case "Comms" -> {
                String message = "Click the amount of commodities you would like to send";
                for(int x = 1; x < p1.getCommodities()+1; x++)
                {
                    Button transact = Button.success(finChecker+"send_Comms_"+p2.getFaction() + "_"+x, ""+x);
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),message, stuffToTransButtons);
            }
            case "ACs" -> {
                String message = Helper.getPlayerRepresentation(p1, activeMap, activeMap.getGuild(), true)+" Click the AC you would like to send";
                for(String acShortHand : p1.getActionCards().keySet())
                {
                    Button transact = Button.success(finChecker+"send_ACs_"+p2.getFaction() + "_"+p1.getActionCards().get(acShortHand), Mapper.getActionCardName(acShortHand));
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(p1.getCardsInfoThread(activeMap),message, stuffToTransButtons);
            }
            case "PNs" -> {
                PNInfo.sendPromissoryNoteInfo(activeMap, p1, false);
                String message =  Helper.getPlayerRepresentation(p1, activeMap, activeMap.getGuild(), true)+" Click the PN you would like to send";
               
                for(String pnShortHand : p1.getPromissoryNotes().keySet())
                {
                    PromissoryNoteModel promissoryNote = Mapper.getPromissoryNoteByID(pnShortHand);
                    String pnName = promissoryNote.name;
                    Button transact = Button.success(finChecker+"send_PNs_"+p2.getFaction() + "_"+p1.getPromissoryNotes().get(pnShortHand), promissoryNote.name);
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(p1.getCardsInfoThread(activeMap),message, stuffToTransButtons);
            }
            case "Frags" -> {
                String message = "Click the amount of fragments you would like to send";
                
                if(p1.getCrf() > 0)
                {
                    for(int x = 1; x < p1.getCrf()+1; x++)
                    {
                        Button transact = Button.primary(finChecker+"send_Frags_"+p2.getFaction() + "_CRF"+x, "Cultural Fragments ("+x+")");
                        stuffToTransButtons.add(transact);
                    }
                }
                if(p1.getIrf() > 0)
                {
                    for(int x = 1; x < p1.getIrf()+1; x++)
                    {
                        Button transact = Button.success(finChecker+"send_Frags_"+p2.getFaction() + "_IRF"+x, "Industrial Fragments ("+x+")");
                        stuffToTransButtons.add(transact);
                    }
                }
                if(p1.getHrf() > 0)
                {
                    for(int x = 1; x < p1.getHrf()+1; x++)
                    {
                        Button transact = Button.danger(finChecker+"send_Frags_"+p2.getFaction() + "_HRF"+x, "Hazardous Fragments ("+x+")");
                        stuffToTransButtons.add(transact);
                    }
                }
                
                if(p1.getVrf() > 0)
                {
                    for(int x = 1; x < p1.getVrf()+1; x++)
                    {
                        Button transact = Button.secondary(finChecker+"send_Frags_"+p2.getFaction() + "_URF"+x, "Frontier Fragments ("+x+")");
                        stuffToTransButtons.add(transact);
                    }
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),message, stuffToTransButtons);

            }
        }
        
    }

    public static void resolveSpecificTransButtonPress(Map activeMap, Player p1, String buttonID, ButtonInteractionEvent event) {
        String finChecker = "FFCC_"+p1.getFaction() + "_";
        buttonID = buttonID.replace("send_", "");
        List<Button> goAgainButtons = new ArrayList<>();

        String thingToTrans = buttonID.substring(0, buttonID.indexOf("_"));
        buttonID = buttonID.replace(thingToTrans+"_", "");
        String factionToTrans = buttonID.substring(0, buttonID.indexOf("_"));
        String amountToTrans = buttonID.substring(buttonID.indexOf("_")+1, buttonID.length());
        Player p2 = Helper.getPlayerFromColorOrFaction(activeMap, factionToTrans);
        String message2 = "";
        String ident = Helper.getPlayerRepresentation(p1, activeMap, activeMap.getGuild(), false);
        String ident2 = Helper.getPlayerRepresentation(p2, activeMap, activeMap.getGuild(), false);
        switch(thingToTrans)
        {
            case "TGs" -> {

                int tgAmount = Integer.parseInt(amountToTrans);
                p1.setTg(p1.getTg()-tgAmount);
                p2.setTg(p2.getTg()+tgAmount);
                message2 = ident + " sent " + tgAmount+ " TGs to "+ident2;
            }
            case "Comms" -> {
                int tgAmount = Integer.parseInt(amountToTrans);
                p1.setCommodities(p1.getCommodities()-tgAmount);
                p2.setTg(p2.getTg()+tgAmount);
                message2 = ident + " sent " + tgAmount+ " Commodities to "+ident2;
            }
            case "ACs" -> {
                message2 =ident + " sent AC #" + amountToTrans+ " to "+ident2;
               int acNum = Integer.parseInt(amountToTrans);
               String acID = null;
               for (java.util.Map.Entry<String, Integer> so : p1.getActionCards().entrySet()) {
                   if (so.getValue().equals(acNum)) {
                       acID = so.getKey();
                   }
               }
                p1.removeActionCard(acNum);
                p2.setActionCard(acID);
                ACInfo.sendActionCardInfo(activeMap, p2);
                ACInfo.sendActionCardInfo(activeMap, p1);
            }
            case "PNs" -> {
                String id = null;
		        int pnIndex;
                pnIndex = Integer.parseInt(amountToTrans);
                for (java.util.Map.Entry<String, Integer> so : p1.getPromissoryNotes().entrySet()) {
                    if (so.getValue().equals(pnIndex)) {
                        id = so.getKey();
                    }
                }
                p1.removePromissoryNote(id);
                p2.setPromissoryNote(id);
                boolean sendSftT = false;
                boolean sendAlliance = false;
                String promissoryNoteOwner = Mapper.getPromissoryNoteOwner(id);
                if ((id.endsWith("_sftt") || id.endsWith("_an")) && !promissoryNoteOwner.equals(p2.getFaction())
                        && !promissoryNoteOwner.equals(p2.getColor())) {
                    p2.setPromissoryNotesInPlayArea(id);
                    if (id.endsWith("_sftt")) {
                        sendSftT = true;
                    } else {
                        sendAlliance = true;
                    }
                }
                PNInfo.sendPromissoryNoteInfo(activeMap, p1, false);
                PNInfo.sendPromissoryNoteInfo(activeMap, p2, false);
                String text = sendSftT ? "**Support for the Throne** " : (sendAlliance ? "**Alliance** " : "");
                message2 = Helper.getPlayerRepresentation(p1, activeMap) + " sent " + Emojis.PN + text + "PN to " + ident2;   
            }
            case "Frags" -> {
               
                String fragType = amountToTrans.substring(0, 3);
                int fragNum = Integer.parseInt(amountToTrans.charAt(3)+"");
                String trait = 	switch (fragType){
                    case "CRF"-> "cultural"; 
                    case  "HRF"-> "hazardous";
                    case "IRF"->  "industrial" ;
                    case  "URF"->  "frontier" ;
                    default -> "";
                };
                new SendFragments().sendFrags(event, p1, p2, trait, fragNum, activeMap);
                message2 = "";
            }
        }
        Button button = Button.secondary(finChecker+"transactWith_"+p2.getColor(), "Send something else to player?");
        Button done = Button.secondary("deleteButtons", "Done With This Transaction");
                    
        goAgainButtons.add(button);
        goAgainButtons.add(done);
        if(activeMap.isFoWMode())
        {
            MessageHelper.sendMessageToChannel(p1.getPrivateChannel(), message2);
            MessageHelper.sendMessageToChannelWithButtons(p1.getPrivateChannel(),ident+" Use Buttons To Complete Transaction", goAgainButtons);
            MessageHelper.sendMessageToChannel(p2.getPrivateChannel(), message2);
        }
        else
        {
            MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), message2);
            MessageHelper.sendMessageToChannelWithButtons(activeMap.getMainGameChannel(),ident+" Use Buttons To Complete Transaction", goAgainButtons);
        }
        MapSaveLoadManager.saveMap(activeMap, event);

    
    }

    public static List<Button> getAllPossibleCompButtons(Map activeMap, Player p1, ButtonInteractionEvent event) {
        String finChecker = "FFCC_"+p1.getFaction() + "_";
        String prefix = "componentActionRes_";
        List<Button> compButtons = new ArrayList<>();
        //techs
        for(String tech : p1.getTechs())
        {
            String techRep = Mapper.getTechRepresentations().get(tech);

            //Columns: key = Proper Name | type | prerequisites | faction | text
            StringTokenizer techRepTokenizer = new StringTokenizer(techRep,"|");
            String techName = techRepTokenizer.nextToken();
            String techType = techRepTokenizer.nextToken();
            String techPrerequisites = techRepTokenizer.nextToken();
            String techFaction = techRepTokenizer.nextToken();
            String factionEmoji = "";
            if (!techFaction.equals(" ")) factionEmoji = Helper.getFactionIconFromDiscord(techFaction);
            String techEmoji = Helper.getEmojiFromDiscord(techType + "tech");
            String techText = techRepTokenizer.nextToken();
            if(techText.contains("ACTION"))
            {
                Button tButton = Button.danger(finChecker+prefix+"tech_"+tech, "Exhaust "+techName).withEmoji(Emoji.fromFormatted(techEmoji));
                compButtons.add(tButton);
            }
        }
        //leaders
        for(Leader leader:p1.getLeaders())
        {
            if(!leader.isExhausted() && !leader.isLocked())
            {
                String leaderID = leader.getId();

                String leaderRep =  Mapper.getLeaderRepresentations().get(leaderID.toString());
                //leaderID = 0:LeaderName ; 1:LeaderTitle ; 2:BacksideTitle/HeroAbility ; 3:AbilityWindow ; 4:AbilityText
                String[] leaderRepSplit = leaderRep.split(";");
                String leaderName = leaderRepSplit[0];
                String leaderTitle = leaderRepSplit[1];
                String heroAbilityName = leaderRepSplit[2];
                String leaderAbilityWindow = leaderRepSplit[3];
                String leaderAbilityText = leaderRepSplit[4];
                String leaderUnlockCondition = leaderRepSplit[5];
                
                String factionEmoji =Helper.getFactionLeaderEmoji(leader);
                if(leaderAbilityWindow.equalsIgnoreCase("ACTION:") || leaderName.contains("Ssruu"))
                {
                    Button lButton = Button.secondary(finChecker+prefix+"leader_"+leaderID, "Use "+leaderName).withEmoji(Emoji.fromFormatted(factionEmoji));
                    compButtons.add(lButton);
                }
            }
        }
        //Relics
        for(String relic : p1.getRelics())
        {
            
            String relicText = Mapper.getRelic(relic);
            String[] relicData =  null;
            if(relicText != null)
            {
                relicData = relicText.split(";");
            }
            if(relicData != null && relicData[0].contains("<"))
            {
                relicData[0] =relicData[0].substring(0, relicData[0].indexOf("<"));
            }
            
            if(relic.equalsIgnoreCase(Constants.ENIGMATIC_DEVICE) || (relicData != null && relicData[1].contains("Action:")))
            {
                Button rButton = null;
                if(relic.equalsIgnoreCase(Constants.ENIGMATIC_DEVICE))
                {
                    rButton = Button.danger(finChecker+prefix+"relic_"+relic, "Purge Enigmatic Device");
                } else {
                    rButton = Button.danger(finChecker+prefix+"relic_"+relic, "Purge "+relicData[0]);
                }
                compButtons.add(rButton);
            }
        }
        //PNs
        for(String pn : p1.getPromissoryNotes().keySet()){
            if(!Mapper.getPromissoryNoteOwner(pn).equalsIgnoreCase(p1.getFaction()) && !p1.getPromissoryNotesInPlayArea().contains(pn))
            {
                String pnText = Mapper.getPromissoryNote(pn, true);
                if(pnText.contains("Action:"))
                {
                    PromissoryNoteModel pnModel = Mapper.getPromissoryNotes().get(pn);
                    String pnName = pnModel.name;
                    Button pnButton = Button.danger(finChecker+prefix+"pn_"+pn, "Use "+pnName);
                    compButtons.add(pnButton);
                }
            }
        }
        //Abilities
        if(p1.hasAbility("star_forge") && p1.getStrategicCC() > 0)
        {
            Button abilityButton = Button.success(finChecker+prefix+"ability_starForge", "Starforge");
            compButtons.add(abilityButton);
        }
        if(p1.hasAbility("orbital_drop") && p1.getStrategicCC() > 0)
        {
            Button abilityButton = Button.success(finChecker+prefix+"ability_orbitalDrop", "Orbital Drop");
            compButtons.add(abilityButton);
        }
        if(p1.hasAbility("stall_tactics") && p1.getActionCards().size() > 0)
        {
            Button abilityButton = Button.success(finChecker+prefix+"ability_stallTactics", "Stall Tactics");
            compButtons.add(abilityButton);
        }
        if(p1.hasAbility("fabrication") && p1.getFragments().size() > 0)
        {
            Button abilityButton = Button.success(finChecker+prefix+"ability_fabrication", "Purge 1 Frag for a CC");
            compButtons.add(abilityButton);
        }
        //Get Relic
        if(p1.enoughFragsForRelic())
        {
            Button getRelicButton = Button.success(finChecker+prefix+"getRelic_", "Get Relic");
            compButtons.add(getRelicButton);
        }
       //ACs
        Button acButton = Button.secondary(finChecker+prefix+"actionCards_", "Play \"ACTION:\" AC");
        compButtons.add(acButton);
        //Generic
        Button genButton = Button.secondary(finChecker+prefix+"generic_", "Generic Component Action");
        compButtons.add(genButton);

        return compButtons;
    }

    public static boolean isNextToEmpyMechs(Map activeMap, Player ACPlayer, Player EmpyPlayer)
    {
        if(ACPlayer == null || EmpyPlayer == null)
        {
            return false;
        }
        boolean isNextTo = false;
        if(ACPlayer.getFaction().equalsIgnoreCase(EmpyPlayer.getFaction()))
        {
            return false;
        }
        List<Tile> tiles = getTilesOfPlayersSpecificUnit(activeMap, EmpyPlayer, "mech");
        for(Tile tile : tiles)
        {
            
            Set<String> adjTiles = FoWHelper.getAdjacentTiles(activeMap, tile.getPosition(), EmpyPlayer, true);
            for(String adjTile : adjTiles)
            {
                
                Tile adjT = activeMap.getTileMap().get(adjTile);
                if(FoWHelper.playerHasUnitsInSystem(ACPlayer, adjT))
                {
                    isNextTo = true;
                    return isNextTo;
                }
            }
        }


        return isNextTo;
    }

    public static List<Button> getPurgeFragsButtons(Map activeMap, Player p1, String unit)
    {
        List<Button> buttons = new ArrayList<Button>();

        return buttons;
    }
    public static List<Tile> getTilesOfPlayersSpecificUnit(Map activeMap, Player p1, String unit)
    {

        List<Tile> tiles = new ArrayList<Tile>();

        for(Tile tile : activeMap.getTileMap().values())
        {
            boolean tileHasIt = false;
            String unitKey = Mapper.getUnitID(AliasHandler.resolveUnit(unit), p1.getColor());
            for(UnitHolder unitH : tile.getUnitHolders().values())
            {
                if(unitH.getUnits().containsKey(unitKey))
                {
                    tileHasIt = true;
                }
            }
            if(tileHasIt && !tiles.contains(tile))
            {
                tiles.add(tile);
            }
        }


        return tiles;
        
    }
    

    public static void resolvePressedCompButton(Map activeMap, Player p1, ButtonInteractionEvent event, String buttonID) {
        String prefix = "componentActionRes_";
        String finChecker = "FFCC_"+p1.getFaction() + "_";
        buttonID = buttonID.replace(prefix, "");

        String firstPart = buttonID.substring(0, buttonID.indexOf("_"));
        buttonID = buttonID.replace(firstPart+"_", "");

        switch(firstPart) {
            case "tech" -> {
                p1.exhaustTech(buttonID);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), (Helper.getPlayerRepresentation(p1, activeMap) + " exhausted tech: " + Helper.getTechRepresentation(buttonID)));
            }
            case "leader" -> {
                Leader playerLeader = p1.getLeader(buttonID);
		
                if(buttonID.contains("agent")){
                    playerLeader.setExhausted(true);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),Helper.getFactionLeaderEmoji(playerLeader));
                    StringBuilder messageText = new StringBuilder(Helper.getPlayerRepresentation(p1, activeMap))
                            .append(" exhausted ").append(Helper.getLeaderFullRepresentation(playerLeader));
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),messageText.toString());
                    List<Button> buttons = AgendaHelper.getPlayerOutcomeButtons(activeMap, null, buttonID, null);
                    String message = "Use buttons to select the user of the agent";
                    //MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                }
                else {
                    StringBuilder message = new StringBuilder(Helper.getPlayerRepresentation(p1, activeMap)).append(" played ").append(Helper.getLeaderFullRepresentation(playerLeader));
                    if ("letnevhero".equals(playerLeader.getId()) || "nomadhero".equals(playerLeader.getId())) {
                        playerLeader.setLocked(false);
                        playerLeader.setActive(true);
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(),message.toString() + " - Leader will be PURGED after status cleanup");
                    } else {
                        boolean purged = p1.removeLeader(playerLeader);
                        if (purged) {
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(),message.toString() + " - Leader " + buttonID + " has been purged");
                        } else {
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(),"Leader was not purged - something went wrong");
                        }
                        if ("titanshero".equals(playerLeader.getId())) {
                            String titanshero = Mapper.getTokenID("titanshero");
                            Tile t= activeMap.getTile(AliasHandler.resolveTile(p1.getFaction()));
                            if(Helper.getTileFromPlanet("elysium", activeMap) != null && Helper.getTileFromPlanet("elysium", activeMap).getPosition().equalsIgnoreCase(t.getPosition())){
                                t.addToken(titanshero, "elysium");
                                MessageHelper.sendMessageToChannel(event.getMessageChannel(),"Attachment added to Elysium and it has been readied");
                                new PlanetRefresh().doAction(p1, "elysium", activeMap);
                            }
                            else{
                                MessageHelper.sendMessageToChannel(event.getMessageChannel(),"`Use the following command to add the attachment: /add_token token:titanshero`");
                            }
                        }
                        if ("solhero".equals(playerLeader.getId())) {
                            
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(),"Removed every one of your ccs from the board");
                            for(Tile t : activeMap.getTileMap().values()){
                                if (AddCC.hasCC(event, p1.getColor(), t)) {
                                    RemoveCC.removeCC(event, p1.getColor(), t, activeMap);
                                }
                            }   
                        }
                        if ("keleresheroharka".equals(playerLeader.getId())) {
                            new KeleresHeroMentak().secondHalf(activeMap, p1, event);  
                        }
                    }

                }
            }
            case "relic" -> {
                String relicId = buttonID;
                Player player = p1;
                if (player.hasRelic(relicId)) {
                    player.removeRelic(relicId);
                    player.removeExhaustedRelic(relicId);
                    String relicName = Mapper.getRelic(relicId).split(";")[0];
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),"Purged " + Emojis.Relic + " relic: " + relicName);
                    if(relicName.contains("Enigmatic")){
                        activeMap.setComponentAction(true);
                        Button getTech = Button.success("acquireATech", "Get a tech");
                        List<Button> buttons = new ArrayList();
                        buttons.add(getTech);
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use Button to get a tech", buttons);
                    }
                } else {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),"Invalid relic or player does not have specified relic");
                }
                
            }
            case "pn" -> {
                ButtonHelper.resolvePNPlay(buttonID, p1, activeMap, event);
            }
            case "ability" -> {
                if(buttonID.equalsIgnoreCase("starForge")){

                    List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnit(activeMap, p1, "warsun");
                    List<Button> buttons = new ArrayList<Button>();
                    String message = "Select the tile you would like to starforge in";
                    for(Tile tile : tiles)
                    {
                        Button starTile = Button.success("starforgeTile_"+tile.getPosition(), tile.getRepresentationForButtons(activeMap, p1));
                        buttons.add(starTile);
                    }
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                } else if(buttonID.equalsIgnoreCase("orbitalDrop")){
                    String successMessage = "Reduced strategy pool CCs by 1 ("+(p1.getStrategicCC())+"->"+(p1.getStrategicCC()-1)  +")";
                    p1.setStrategicCC(p1.getStrategicCC()-1);
                    MessageHelper.sendMessageToChannel(event.getChannel(), successMessage);
                    String message = "Select the planet you would like to place 2 infantry on.";
                    List<Button> buttons = Helper.getPlanetPlaceUnitButtons(event, p1, activeMap, "2gf");
                    buttons.add(Button.danger("orbitolDropFollowUp", "Done Dropping Infantry"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);

                }else if(buttonID.equalsIgnoreCase("fabrication")){
                    String message = "Click the fragment you'd like to purge. ";
                    List<Button> purgeFragButtons = new ArrayList<>();
                    if(p1.getCrf() > 0){
                            Button transact = Button.primary(finChecker+"purge_Frags_CRF_1", "Purge 1 Cultural Fragment");
                            purgeFragButtons.add(transact);
                    }
                    if(p1.getIrf()> 0){  
                        Button transact = Button.success(finChecker+"purge_Frags_IRF_1", "Purge 1 Industrial Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if(p1.getHrf() > 0){
                            Button transact = Button.danger(finChecker+"purge_Frags_HRF_1", "Purge 1 Hazardous Fragment");
                            purgeFragButtons.add(transact);
                    }       
                    if(p1.getVrf() > 0){
                            Button transact = Button.secondary(finChecker+"purge_Frags_URF_1", "Purge 1 Frontier Fragment");
                            purgeFragButtons.add(transact);
                    }
                    Button transact2 = Button.success(finChecker+"gain_CC", "Gain CC");
                    purgeFragButtons.add(transact2);
                    Button transact3 = Button.danger(finChecker+"finishComponentAction", "Done Resolving Fabrication");
                    purgeFragButtons.add(transact3);
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),message, purgeFragButtons);

                }else if(buttonID.equalsIgnoreCase("stallTactics")){
                    String secretScoreMsg = "_ _\nClick a button below to discard an Action Card";
                    List<Button> acButtons = ACInfo.getDiscardActionCardButtons(activeMap, p1);
                    if (acButtons != null && !acButtons.isEmpty()) {
                        List<MessageCreateData> messageList = MessageHelper.getMessageCreateDataObjects(secretScoreMsg, acButtons);
                        ThreadChannel cardsInfoThreadChannel = p1.getCardsInfoThread(activeMap);
                        for (MessageCreateData message : messageList) {
                            cardsInfoThreadChannel.sendMessage(message).queue();
                        }
                    }
                }
            }
            case "getRelic" -> {
                String message = "Click the fragments you'd like to purge. ";
                List<Button> purgeFragButtons = new ArrayList<>();
                int numToBeat = 2 - p1.getVrf();
                if((p1.hasAbility("fabrication") || p1.getPromissoryNotes().containsKey("bmf")))
                {
                    numToBeat = numToBeat -1;
                    if(p1.getPromissoryNotes().containsKey("bmf") && !p1.hasAbility("fabrication"))
                    {
                        Button transact = Button.primary(finChecker+"resolvePNPlay_bmf", "Play BMF");
                        purgeFragButtons.add(transact);
                    }
                    
                }
                if(p1.getCrf() > numToBeat)
                {
                    for(int x = numToBeat+1; x < p1.getCrf()+1; x++)
                    {
                        Button transact = Button.primary(finChecker+"purge_Frags_CRF_"+x, "Cultural Fragments ("+x+")");
                        purgeFragButtons.add(transact);
                    }
                }
                if(p1.getIrf()> numToBeat)
                {
                    for(int x = numToBeat+1; x < p1.getIrf()+1; x++)
                    {
                        Button transact = Button.success(finChecker+"purge_Frags_IRF_"+x, "Industrial Fragments ("+x+")");
                        purgeFragButtons.add(transact);
                    }
                }
                if(p1.getHrf() > numToBeat)
                {
                    for(int x = numToBeat+1; x < p1.getHrf()+1; x++)
                    {
                        Button transact = Button.danger(finChecker+"purge_Frags_HRF_"+x, "Hazardous Fragments ("+x+")");
                        purgeFragButtons.add(transact);
                    }
                }
                
                if(p1.getVrf() > 0)
                {
                    for(int x = 1; x < p1.getVrf()+1; x++)
                    {
                        Button transact = Button.secondary(finChecker+"purge_Frags_URF_"+x, "Frontier Fragments ("+x+")");
                        purgeFragButtons.add(transact);
                    }
                }
                Button transact2 = Button.danger(finChecker+"drawRelicFromFrag", "Finish Purging and Draw Relic");
                purgeFragButtons.add(transact2);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),message, purgeFragButtons);
            }
            case "generic" -> {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),"Doing unspecified component action. Maybe ping Fin to add this. ");
            }
            case "actionCards" -> {
                String secretScoreMsg = "_ _\nClick a button below to play an Action Card";
                List<Button> acButtons = ACInfo.getActionPlayActionCardButtons(activeMap, p1);
                if (acButtons != null && !acButtons.isEmpty()) {
                    List<MessageCreateData> messageList = MessageHelper.getMessageCreateDataObjects(secretScoreMsg, acButtons);
                    ThreadChannel cardsInfoThreadChannel = p1.getCardsInfoThread(activeMap);
                    for (MessageCreateData message : messageList) {
                        cardsInfoThreadChannel.sendMessage(message).queue();
                    }
                }

            }
        }

        if(!firstPart.contains("ability") && !firstPart.contains("getRelic"))
        {
            String message = "Use buttons to end turn or do another action.";
            List<Button> systemButtons = ButtonHelper.getStartOfTurnButtons(p1, activeMap, true);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
        }
        File file = GenerateMap.getInstance().saveImage(activeMap, DisplayType.all, event);
        event.getMessage().delete().queue();
        

    }
    public static void resolveMuaatCommanderCheck(Player player, Map activeMap, GenericInteractionCreateEvent event)
    {

        if(activeMap.playerHasLeaderUnlockedOrAlliance(player, "muaatcommander"))
        {
            int old = player.getTg();
            int newTg = player.getTg()+1;
            player.setTg(player.getTg()+1);
            String mMessage = Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), true)+" Since you have muaat commander unlocked, 1tg has been added automatically ("+old+"->"+newTg+")";
            if(activeMap.isFoWMode())
            {
                MessageHelper.sendMessageToChannel(player.getPrivateChannel(),mMessage);
            }
            else{
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), mMessage);
            }
        }
    }
    public static void resolvePNPlay(String id, Player player, Map activeMap, GenericInteractionCreateEvent event)
    {
        boolean longPNDisplay = false;
        
        PromissoryNoteModel promissoryNote2 = Mapper.getPromissoryNoteByID(id);
        String promissoryNote = Mapper.getPromissoryNote(id, true);
        String pnName = promissoryNote2.name;
        String[] pn = promissoryNote.split(";");
        String pnOwner = Mapper.getPromissoryNoteOwner(id);
        if (pn.length > 3 && pn[3].equals("playarea")) {
            player.setPromissoryNotesInPlayArea(id);
        } else {
            player.removePromissoryNote(id);
            for (Player player_ : activeMap.getPlayers().values()) {
                String playerColor = player_.getColor();
                String playerFaction = player_.getFaction();
                if (playerColor != null && playerColor.equals(pnOwner) || playerFaction != null && playerFaction.equals(pnOwner)) {
                    player_.setPromissoryNote(id);
                    PNInfo.sendPromissoryNoteInfo(activeMap, player_, false);
                    pnOwner = player_.getFaction();
                    break;
                }
            }
        }
        String emojiToUse = activeMap.isFoWMode() ? "" : Helper.getFactionIconFromDiscord(pnOwner);
        StringBuilder sb = new StringBuilder(Helper.getPlayerRepresentation(player, activeMap) + " played promissory note: "+pnName+"\n");
        sb.append(emojiToUse + Emojis.PN);
        String pnText = "";

        //Handle AbsolMode Political Secret
        if (activeMap.isAbsolMode() && id.endsWith("_ps")) {
            pnText = "Political Secret" + Emojis.Absol + ":  *When you cast votes:* You may exhaust up to 3 of the {colour} player's planets and cast additional votes equal to the combined influence value of the exhausted planets. Then return this card to the {colour} player.";
        } else {
            pnText = Mapper.getPromissoryNote(id, longPNDisplay);
        }
        sb.append(pnText).append("\n");

        //TERRAFORM TIP
        if (id.equalsIgnoreCase("terraform")) {
            sb.append("`/add_token token:titanspn`\n");
        }

        //Fog of war ping
        if (activeMap.isFoWMode()) {
            // Add extra message for visibility
            FoWHelper.pingAllPlayersWithFullStats(activeMap, event, player, sb.toString());
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),sb.toString());
        PNInfo.sendPromissoryNoteInfo(activeMap, player, false);
    }

}