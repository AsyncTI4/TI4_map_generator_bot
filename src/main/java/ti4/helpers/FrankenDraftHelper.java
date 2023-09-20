package ti4.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import java.util.concurrent.ThreadLocalRandom;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.milty.MiltyDraftTile;
import ti4.generator.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.model.TechnologyModel;



public class FrankenDraftHelper {
    
    public static List<String> getRandomFactionAbilities(int count, List<String> alreadyHeld){
        List<String> factionAbilities = new ArrayList<>();
        HashMap<String, String> abilities = Mapper.getFactionAbilities();
        List<String> keys = new ArrayList<>(abilities.keySet());
        keys.removeAll(alreadyHeld);
        for(int x = 0; x < count; x++){
            boolean foundOne = false;
            while(!foundOne){
                int randNum = ThreadLocalRandom.current().nextInt(0,keys.size());
                String ability = keys.get(randNum);
                if(!factionAbilities.contains(ability)){
                    factionAbilities.add(ability);
                    keys.remove(randNum);
                    foundOne = true;
                }
            }
        }
        return factionAbilities;
    }
     public static List<String> getRandomLeaderType(int count, List<String> alreadyHeld, String leaderType){
        List<String> desiredThing = new ArrayList<>();
        HashMap<String, String> allDesiredThings = Mapper.getLeaderRepresentations();
         List<String> keys = new ArrayList<>(allDesiredThings.keySet());
        keys.removeAll(alreadyHeld);
        keys.removeIf(key -> !key.contains(leaderType));
        for(int x = 0; x < count; x++){
            int randNum = ThreadLocalRandom.current().nextInt(0,keys.size());
            String ability = keys.get(randNum);
            desiredThing.add(ability);
            keys.remove(randNum);
        }
        return desiredThing;
    }
    public static List<String> getRandomUnitType(int count, List<String> alreadyHeld, String unitType){
        List<String> desiredThing = new ArrayList<>();
        HashMap<String, String> allDesiredThings = Mapper.getUnitRepresentations();
        List<String> keys = new ArrayList<>(allDesiredThings.keySet());
        keys.removeAll(alreadyHeld);
        keys.removeIf(key -> !key.contains(unitType));
        for(int x = 0; x < count; x++){
            int randNum = ThreadLocalRandom.current().nextInt(0,keys.size());
            String ability = keys.get(randNum);
            desiredThing.add(ability);
            keys.remove(randNum);
        }
        return desiredThing;
    }
     public static List<String> getRandomFactionThing(int count, List<String> alreadyHeld, String factionThing){
        List<String> desiredThing = new ArrayList<>();
        List<String> allDesiredThings = Mapper.getFactions();
        List<String> keys = new ArrayList<>();
        for(String thing : allDesiredThings){
            keys.add(thing + " "+factionThing);
        }
        keys.removeAll(alreadyHeld);
        for(int x = 0; x < count; x++){
            int randNum = ThreadLocalRandom.current().nextInt(0,keys.size());
            String ability = keys.get(randNum);
            desiredThing.add(ability);
            keys.remove(randNum);
        }
        return desiredThing;
    }
    public static void makeBags(Game activeGame){
        for(Player player : activeGame.getRealPlayers()){
            
        }
    }
    public static List<String> getRandomTiles(int count, List<MiltyDraftTile> typeOfTile){
        List<String> desiredThing = new ArrayList<>();
        for(int x = 0; x < count; x++){
            Collections.shuffle(typeOfTile);
            String ability = typeOfTile.get(0).getTile().getRepresentation();
            desiredThing.add(ability);
            typeOfTile.remove(0);
        }
        return desiredThing;
    }
    public static List<String> getRandomFactionTech(int count, List<String> alreadyHeld){
        List<String> desiredThing = new ArrayList<>();
        HashMap<String, TechnologyModel> allDesiredThings = Mapper.getTechs();
        HashMap<String, TechnologyModel> allDesiredThings2 = Mapper.getTechs();
        for(TechnologyModel bleh : allDesiredThings2.values()){
            if(bleh.getFaction() == null || "".equalsIgnoreCase(bleh.getFaction()));
            {
                String key = bleh.getAlias();
                allDesiredThings.remove(key);
            }
        }
        List<String> keys = new ArrayList<>(allDesiredThings.keySet());
        keys.removeAll(alreadyHeld);
        for(int x = 0; x < count; x++){
            int randNum = ThreadLocalRandom.current().nextInt(0,keys.size());
            String ability = keys.get(randNum);
            desiredThing.add(ability);
            keys.remove(randNum);
        }
        return desiredThing;
    }

}