package ti4.helpers;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;

import ti4.commands.milty.MiltyDraftManager;
import ti4.commands.milty.MiltyDraftTile;
import ti4.generator.Mapper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.model.TechnologyModel;



public class FrankenDraftHelper {
    
    public static List<String> getRandomFactionAbilities(int count, List<String> alreadyHeld){
        List<String> factionAbilities = new ArrayList<String>();
        HashMap<String, String> abilities = Mapper.getFactionAbilities();
        List<String> keys = new ArrayList<String>();
        keys.addAll(abilities.keySet());
        keys.removeAll(alreadyHeld);
        for(int x = 0; x < count; x++){
            boolean foundOne = false;
            while(!foundOne){
                int randNum = new Random().nextInt(0,keys.size());
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
        List<String> desiredThing = new ArrayList<String>();
        HashMap<String, String> allDesiredThings = Mapper.getLeaderRepresentations();
        List<String> keys = new ArrayList<String>();
        keys.addAll(allDesiredThings.keySet());
        keys.removeAll(alreadyHeld);
        keys.removeIf(key -> !key.contains(leaderType));
        for(int x = 0; x < count; x++){
            int randNum = new Random().nextInt(0,keys.size());
            String ability = keys.get(randNum);
            desiredThing.add(ability);
            keys.remove(randNum);
        }
        return desiredThing;
    }
    public static List<String> getRandomUnitType(int count, List<String> alreadyHeld, String unitType){
        List<String> desiredThing = new ArrayList<String>();
        HashMap<String, String> allDesiredThings = Mapper.getUnitRepresentations();
        List<String> keys = new ArrayList<String>();
        keys.addAll(allDesiredThings.keySet());
        keys.removeAll(alreadyHeld);
        keys.removeIf(key -> !key.contains(unitType));
        for(int x = 0; x < count; x++){
            int randNum = new Random().nextInt(0,keys.size());
            String ability = keys.get(randNum);
            desiredThing.add(ability);
            keys.remove(randNum);
        }
        return desiredThing;
    }
     public static List<String> getRandomFactionThing(int count, List<String> alreadyHeld, String factionThing){
        List<String> desiredThing = new ArrayList<String>();
        List<String> allDesiredThings = Mapper.getFactions();
        List<String> keys = new ArrayList<String>();
        for(String thing : allDesiredThings){
            keys.add(thing + " "+factionThing);
        }
        keys.removeAll(alreadyHeld);
        for(int x = 0; x < count; x++){
            int randNum = new Random().nextInt(0,keys.size());
            String ability = keys.get(randNum);
            desiredThing.add(ability);
            keys.remove(randNum);
        }
        return desiredThing;
    }
    public static void makeBags(Map activeMap){
        for(Player player : activeMap.getRealPlayers()){
            
        }
    }
    public static List<String> getRandomTiles(int count, List<MiltyDraftTile> typeOfTile){
        List<String> desiredThing = new ArrayList<String>();
        for(int x = 0; x < count; x++){
            Collections.shuffle(typeOfTile);
            String ability = typeOfTile.get(0).getTile().getRepresentation();
            desiredThing.add(ability);
            typeOfTile.remove(0);
        }
        return desiredThing;
    }
    public static List<String> getRandomFactionTech(int count, List<String> alreadyHeld){
        List<String> desiredThing = new ArrayList<String>();
        HashMap<String, TechnologyModel> allDesiredThings = Mapper.getTechs();
        HashMap<String, TechnologyModel> allDesiredThings2 = Mapper.getTechs();
        for(TechnologyModel bleh : allDesiredThings2.values()){
            if(bleh.getFaction() == null || bleh.getFaction().equalsIgnoreCase(""));
            {
                String key = bleh.getAlias();
                allDesiredThings.remove(key);
            }
        }
        List<String> keys = new ArrayList<String>();
        keys.addAll(allDesiredThings.keySet());
        keys.removeAll(alreadyHeld);
        for(int x = 0; x < count; x++){
            int randNum = new Random().nextInt(0,keys.size());
            String ability = keys.get(randNum);
            desiredThing.add(ability);
            keys.remove(randNum);
        }
        return desiredThing;
    }

}