package ti4.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.milty.MiltyDraftManager;
import ti4.commands.milty.MiltyDraftTile;
import ti4.commands.milty.StartMilty;
import ti4.generator.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;



public class FrankenDraftHelper {
    
    public static List<String> getRandomFactionAbilities(int count, List<String> alreadyHeld){
        List<String> factionAbilities = new ArrayList<>();
        HashMap<String, String> abilities = Mapper.getFactionAbilities();
        abilities.replaceAll((k, v) -> {
                    int index = v.indexOf("|");
                    String abilityName = v.substring(0, index);
                    String factionName = v.substring(index + 1, StringUtils.ordinalIndexOf(v, "|", 2));
                    factionName = Mapper.getFactionRepresentations().get(factionName);
                    return factionName + " - " + abilityName;
                });

        
        List<String> keys = new ArrayList<>(abilities.keySet());
        //keys.removeAll(alreadyHeld);
        keys.removeIf(key -> abilities.get(key).contains("(DS)"));
        List<String> newKeys = new ArrayList<>();
        for(String key : keys){
            newKeys.add("Ability: " +abilities.get(key));
        }
        newKeys.removeAll(alreadyHeld);
        newKeys.removeIf(key -> key.toLowerCase().contains("mitosis") || key.toLowerCase().contains("redemption") || key.toLowerCase().contains("revelation") ||key.toLowerCase().contains("tribuni") || key.toLowerCase().contains("fragile") ||key.toLowerCase().contains("hubris") || key.toLowerCase().contains("devour") || key.toLowerCase().contains("fragile") || key.toLowerCase().contains("propagation"));
        for(int x = 0; x < count; x++){
            boolean foundOne = false;
            while(!foundOne){
                int randNum = ThreadLocalRandom.current().nextInt(0,newKeys.size());
                String ability = newKeys.get(randNum);
                if(!factionAbilities.contains(ability)){
                    factionAbilities.add(ability);
                    newKeys.remove(randNum);
                    foundOne = true;
                }
            }
        }
        return factionAbilities;
    }
    //  public static List<String> getRandomLeaderType(int count, List<String> alreadyHeld, String leaderType){
    //     List<String> desiredThing = new ArrayList<>();
    //     HashMap<String, String> allDesiredThings = Mapper.getLeaderRepresentations();
    //      List<String> keys = new ArrayList<>(allDesiredThings.keySet());
    //     keys.removeAll(alreadyHeld);
    //     keys.removeIf(key -> !key.contains(leaderType));
    //     for(int x = 0; x < count; x++){
    //         int randNum = ThreadLocalRandom.current().nextInt(0,keys.size());
    //         String ability = keys.get(randNum);
    //         desiredThing.add(ability);
    //         keys.remove(randNum);
    //     }
    //     return desiredThing;
    // }
    public static List<String> getRandomUnitType(int count, List<String> alreadyHeld, String unitType){
        List<String> desiredThing = new ArrayList<>();
        //HashMap<String, String> allDesiredThings = Mapper.getu;
        List<String> keys = new ArrayList<>();
        keys.addAll(Mapper.getUnits().keySet());
        keys.removeAll(alreadyHeld);
        keys.removeIf(key -> !key.contains(unitType) || !Mapper.getUnits().get(key).getSource().equals("pok"));
        for(int x = 0; x < count; x++){
            int randNum = ThreadLocalRandom.current().nextInt(0,keys.size());
            String ability = keys.get(randNum);
            desiredThing.add(ability);
            keys.remove(randNum);
        }
        return desiredThing;
    }
    //fleet, commodities, HS, PN, Starting tech
     public static List<String> getRandomFactionThing(int count, List<String> alreadyHeld, String factionThing, Game activeGame){
        List<String> desiredThing = new ArrayList<>();
        List<String> allDesiredThings = new ArrayList<>();
        List<String> keys = new ArrayList<>();

        
        
        if (activeGame != null && activeGame.isDiscordantStarsMode()) {
            allDesiredThings.addAll(Mapper.getFactionRepresentations().values());
                        allDesiredThings.removeIf(token -> token.toLowerCase().contains("franken")|| token.toLowerCase().contains("lazax") || token.toLowerCase().contains("admins"));
        } else {
            allDesiredThings.addAll(Mapper.getFactionRepresentations().values());
            allDesiredThings.removeIf(token -> token.toUpperCase().endsWith("(DS)") || token.toLowerCase().contains("franken")|| token.toLowerCase().contains("lazax") || token.toLowerCase().contains("admins"));
        }
        if(!factionThing.equalsIgnoreCase("Hero")){
            allDesiredThings.removeIf(token -> token.toLowerCase().contains("keleresa")|| token.toLowerCase().contains("keleres argent")|| token.toLowerCase().contains("keleres xxcha")|| token.toLowerCase().contains("keleresx"));
        }


        for(String thing : allDesiredThings){
            keys.add(factionThing+": "+thing + " "+factionThing);
        }
        keys.removeAll(alreadyHeld);
        keys.removeIf(token -> token.toLowerCase().contains("sardakk norr starting tech") || token.toLowerCase().contains("nar mech") || token.toLowerCase().contains("muaat mech") || token.toLowerCase().contains("mentak oalition pn"));

        for(int x = 0; x < count; x++){
            int randNum = ThreadLocalRandom.current().nextInt(0,keys.size());
            String ability = keys.get(randNum);
            desiredThing.add(ability);
            keys.remove(randNum);
        }
        return desiredThing;
    }

    public static List<Button> getFrankenBagButtons(Game activeGame, Player player){
        List<Button> buttons = new ArrayList<>();
        List<String> bagToPass = player.getFrankenBagToPass();
        int numItem = 1;
        Collections.sort(bagToPass);
        for(String item : bagToPass){
            String itemLabel = numItem + ". "+item;
            if(item.contains("Blue Tile") || item.contains("Mech:")){
                 buttons.add(Button.primary("frankenDraftAction_"+item,itemLabel));
            }else if(item.contains("Red Tile") || item.contains("Faction Tech")){
                 buttons.add(Button.danger("frankenDraftAction_"+item,itemLabel));
            }else if(item.contains("Agent") || item.contains("Commander") || item.contains("Hero") || item.contains("Speaker Position")){
                buttons.add(Button.secondary("frankenDraftAction_"+item,itemLabel));
            }else{
                buttons.add(Button.success("frankenDraftAction_"+item,itemLabel));
            }
            numItem = numItem + 1;
            
        }
        return buttons;
    }

    public static void resolveFrankenDraftAction(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID){
        String item = buttonID.split("_")[1];
        player.addToFrankenPersonalBag(item);
        player.removeElementFromBagToPass(item);
        if(player.getFrankenBagToPass().size() % 2 == 0 && player.getFrankenBagPersonal().size() != 1){
            player.setReadyToPassBag(true);
        }
        boolean everyoneReady = true;
        for(Player p2 : activeGame.getRealPlayers()){
            if(!p2.isReadyToPassBag()){
                everyoneReady = false;
            }
        }
         String msg = ButtonHelper.getTrueIdentity(player, activeGame) + " you picked "+item;
        
        if(!everyoneReady){
            if(player.isReadyToPassBag()){
                msg = msg + ". But not everyone has picked yet. Please wait and you will be pinged when the last person has picked.";
                MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), ButtonHelper.getIdent(player) + " is ready to pass their bag");
            }else{
                msg = msg + ". Please pick another item from this bag.";
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(activeGame), ButtonHelper.getTrueIdentity(player, activeGame)+"Use buttons to select something", getFrankenBagButtons(activeGame, player));
            }
           
           MessageHelper.sendMessageToChannel(player.getCardsInfoThread(activeGame), msg);
        }else{
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(activeGame), msg);
            //passbags
            Player firstPlayer = null;
            List<String> bagToPass = new ArrayList<>();
            for(Player p2 : activeGame.getRealPlayers()){
                p2.setReadyToPassBag(false);
                if(firstPlayer == null){
                    firstPlayer = p2;
                }
                List<String> bagToPass2 = new ArrayList<>();
                bagToPass2.addAll(p2.getFrankenBagToPass());
                if(firstPlayer != p2){
                    p2.setFrankenBagToPass(bagToPass);
                    bagToPass = bagToPass2;
                }else{
                    bagToPass.addAll(p2.getFrankenBagToPass());
                }
                
            }
            firstPlayer.setFrankenBagToPass(bagToPass);
            for(Player p2 : activeGame.getRealPlayers()){
                MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(activeGame), ButtonHelper.getTrueIdentity(p2, activeGame)+"You have been passed a bag, use buttons to select something", getFrankenBagButtons(activeGame, p2));
                MessageHelper.sendMessageToChannel(p2.getCardsInfoThread(activeGame), ButtonHelper.getTrueIdentity(p2, activeGame)+"Here is a text version of the bag you were passed so you will not forget what was in it later on when you pass it: \n"+getCurrentBagToPassRepresentation(activeGame, p2));
            }
        }
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(activeGame), "Your current bag looks like: \n"+getCurrentPersonalBagRepresentation(activeGame, player));
        event.getMessage().delete().queue();
    }

    public static String getCurrentPersonalBagRepresentation(Game activeGame, Player player){
        String representation = "";
        List<String> currentBag = player.getFrankenBagPersonal();
        Collections.sort(currentBag);
        int itemNum = 1;
        for(String item : currentBag){
            representation = representation +itemNum + ". "+ item + "\n";
            itemNum = itemNum + 1;
        }

        return representation;
    }
    public static String getCurrentBagToPassRepresentation(Game activeGame, Player player){
        String representation = "";
        List<String> currentBag = player.getFrankenBagToPass();
        Collections.sort(currentBag);
        int itemNum = 1;
        for(String item : currentBag){
            representation = representation + itemNum + ". "+ item + "\n";
            itemNum = itemNum + 1;
        }

        return representation;
    }


    public static void makeBags(Game activeGame, boolean powered){
        List<String> alreadyHeld = new ArrayList<String>();
         MiltyDraftManager draftManager = activeGame.getMiltyDraftManager();
         new StartMilty().initDraftTiles(draftManager);
        List<MiltyDraftTile> red = draftManager.getRed();
        List<MiltyDraftTile> blue = draftManager.getHigh();
        blue.addAll(draftManager.getMid());
        blue.addAll(draftManager.getLow());
        for(Player player : activeGame.getRealPlayers()){
            List<String> bagToPass = new ArrayList<String>();
            // bagToPass.addAll(getRandomUnitType(2, alreadyHeld, "mech"));
           // bagToPass.addAll(getRandomUnitType(2, alreadyHeld, "flagship"));
           if(powered){
                bagToPass.addAll(getRandomFactionAbilities(4, alreadyHeld));
                bagToPass.addAll(getRandomFactionTech(3, alreadyHeld));
           }else{
            bagToPass.addAll(getRandomFactionAbilities(3, alreadyHeld));
                bagToPass.addAll(getRandomFactionTech(2, alreadyHeld));
           }
            bagToPass.addAll(getRandomFactionThing(2, alreadyHeld, "Agent", activeGame));
            bagToPass.addAll(getRandomFactionThing(2, alreadyHeld, "Commander", activeGame));
            bagToPass.addAll(getRandomFactionThing(2, alreadyHeld, "Hero", activeGame));
            bagToPass.addAll(getRandomFactionThing(2, alreadyHeld, "Mech", activeGame));
            bagToPass.addAll(getRandomFactionThing(2, alreadyHeld, "Flagship", activeGame));
            bagToPass.addAll(getRandomFactionThing(2, alreadyHeld, "Commodities",activeGame));
            bagToPass.addAll(getRandomFactionThing(2, alreadyHeld, "PN", activeGame));
            bagToPass.addAll(getRandomFactionThing(2, alreadyHeld, "Home System", activeGame));
            bagToPass.addAll(getRandomFactionThing(2, alreadyHeld, "Starting Tech", activeGame));
            bagToPass.addAll(getRandomFactionThing(2, alreadyHeld, "Fleet", activeGame));
            if(activeGame.getRealPlayers().size() < 7){
                bagToPass.addAll(getRandomTiles(3, red, alreadyHeld, "Red"));
            }else{
                bagToPass.addAll(getRandomTiles(2, red, alreadyHeld, "Red"));
            }
            bagToPass.addAll(getRandomTiles(4, blue, alreadyHeld, "Blue"));
            bagToPass.addAll(getRandomDraftOrder(alreadyHeld, activeGame));
            alreadyHeld.addAll(bagToPass);
            player.setFrankenBagToPass(bagToPass);
            player.setReadyToPassBag(false);
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(activeGame), ButtonHelper.getTrueIdentity(player, activeGame)+"Franken Draft has begun, use buttons to select something", getFrankenBagButtons(activeGame, player));
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(activeGame), ButtonHelper.getTrueIdentity(player, activeGame)+"Here is a text version of the bag so you will not forget what was in it later on when you pass it: \n"+getCurrentBagToPassRepresentation(activeGame, player));
        }
         MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), Helper.getGamePing(activeGame.getGuild(), activeGame) + " draft started. As a reminder, for the first bag you pick 3 items, and for "+
            "all the bags after that you pick 2 items. New buttons will generate after each pick. The first few picks, the buttons overflow discord button limitations, so while some buttons will get" +
            " cleared away when you pick, others may remain. Please just leave those buttons be and use any new buttons generated. Once you have made your 2 picks (3 in the first bag), the bags will automatically be passed once everyone is ready. Please note the bot does not enforce limits on how many of something you can pick. Be mindful of this and dont take more of something that you should have (dont take 8 faction abilities, for instance)");
    }
    public static List<String> getRandomTiles(int count, List<MiltyDraftTile> typeOfTile, List<String> alreadyHeld, String type){
        List<String> desiredThing = new ArrayList<>();
        typeOfTile.removeIf(tile -> alreadyHeld.contains(type + " Tile: "+tile.getTile().getRepresentation() + " ("+tile.getTile().getTileID()+")"));
        for(int x = 0; x < count; x++){
            Collections.shuffle(typeOfTile);
            String ability = type + " Tile: "+typeOfTile.get(0).getTile().getRepresentation() + " ("+typeOfTile.get(0).getTile().getTileID()+")";
            desiredThing.add(ability);
            typeOfTile.remove(0);
        }
        return desiredThing;
    }
    public static List<String> getRandomDraftOrder(List<String> alreadyHeld, Game activeGame){
        List<String> desiredThing = new ArrayList<>();
        List<String> draftOrder = new ArrayList<>();
        for(int x = 1; x < activeGame.getRealPlayers().size()+1;x++){
            draftOrder.add("Speaker Position: "+x);
        }
        draftOrder.removeIf(order -> alreadyHeld.contains(order));
        Collections.shuffle(draftOrder);
        String ability = draftOrder.get(0);
        desiredThing.add(ability);
        return desiredThing;
    }
    public static List<String> getRandomFactionTech(int count, List<String> alreadyHeld){
        List<String> desiredThing = new ArrayList<>();
        HashMap<String, TechnologyModel> allDesiredThings = Mapper.getTechs();
        
        List<String> keys = new ArrayList<>();
        keys.addAll(allDesiredThings.keySet());
        keys.removeIf(key -> allDesiredThings.get(key).getFaction() == null || allDesiredThings.get(key).getFaction().equals("") || (!allDesiredThings.get(key).getSource().equals("pok") && !allDesiredThings.get(key).getSource().equals("base")));
        List<String> newKeys = new ArrayList<>();
        int c = 1;
        for(String key : keys){
            newKeys.add("Faction Tech: " + allDesiredThings.get(key).getName());
            c = c+1;
        }
        newKeys.removeAll(alreadyHeld);
        int size = newKeys.size();
    
        for(int x = 0; x < count && size > 0; x++){
            int randNum = ThreadLocalRandom.current().nextInt(0,size);
            String ability = newKeys.get(randNum);
            desiredThing.add(ability);
            newKeys.remove(randNum);
            size = size -1;
        }
        return desiredThing;
    }

}