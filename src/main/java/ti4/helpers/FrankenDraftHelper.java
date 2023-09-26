package ti4.helpers;

import java.util.*;

import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import software.amazon.awssdk.services.s3.endpoints.internal.Value;
import ti4.commands.milty.MiltyDraftManager;
import ti4.commands.milty.MiltyDraftTile;
import ti4.commands.milty.StartMilty;
import ti4.generator.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.Franken.FrankenBag;
import ti4.model.Franken.FrankenItem;
import ti4.model.TechnologyModel;



public class FrankenDraftHelper {
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
        StringBuilder sb = new StringBuilder();
        FrankenBag currentBag = player.getFrankenHand();
        int itemNum = 1;
        for(FrankenItem item : currentBag.Contents) {
            sb.append(itemNum);
            sb.append(": ");
            sb.append(item.toHumanReadable());
            sb.append("\n");
        }

        return sb.toString();
    }

    public static String getCurrentBagToPassRepresentation(Game activeGame, Player player){
        StringBuilder sb = new StringBuilder();
        FrankenBag currentBag = player.getCurrentFrankenBag();
        List<FrankenItem> contents = currentBag.Contents;
        contents.sort(Comparator.comparing((FrankenItem a) -> a.ItemCategory));
        int itemNum = 1;
        for(FrankenItem item : contents) {
            sb.append(itemNum);
            sb.append(": ");
            sb.append(item.toHumanReadable());
            sb.append("\n");
        }

        return sb.toString();
    }

    public static void clearPlayerHands(Game activeGame) {
        for(Player player: activeGame.getRealPlayers()) {
            player.setFrankenHand(new FrankenBag());
        }
    }

    private static void filterUndraftablesAndShuffle(List<FrankenItem> items) {
        var frankenErrata = Mapper.getFrankenErrata().values();
        for (var errata : frankenErrata) {
            items.removeIf((FrankenItem item) -> item.getAlias().equals(errata.getAlias()) && errata.Undraftable);
        }

        Collections.shuffle(items);
    }

    private static final String[] excludedFactions = {"lazax", "admins", "franken"};
    private static List<String> getAllFactionIds(Game activeGame) {
        Map<String, String> factionSet = Mapper.getFactionRepresentations();
        List<String> factionIds = new ArrayList<String>();
        factionSet.forEach((String id, String name) -> {
            if (name.contains("(DS)") && !activeGame.isDiscordantStarsMode()) {
                return;
            } else {
                for (String excludedFaction : excludedFactions) {
                    if (id.contains(excludedFaction)) {
                        return;
                    }
                }
            }
            factionIds.add(id);
        });
        return factionIds;
    }

    public static List<FrankenItem> buildGenericFactionItemSet(FrankenItem.Category category, Game activeGame) {
        List<String> factionIds = getAllFactionIds(activeGame);
        List<FrankenItem> allItems = new ArrayList<FrankenItem>();
        for (String factionId: factionIds) {
            allItems.add(new FrankenItem(category, factionId));
        }
        Collections.shuffle(allItems);
        return allItems;
    }

    // All the generic types of draftable items (i.e. things like "Argent Starting Tech"
    private static final FrankenItem.Category[] genericDraftableTypes = {
            FrankenItem.Category.AGENT,
            FrankenItem.Category.COMMANDER,
            FrankenItem.Category.HERO,
            FrankenItem.Category.COMMODITIES,
            FrankenItem.Category.PN,
            FrankenItem.Category.MECH,
            FrankenItem.Category.FLAGSHIP,
            FrankenItem.Category.HOMESYSTEM,
            FrankenItem.Category.STARTINGFLEET,
            FrankenItem.Category.STARTINGTECH
    };

    public static List<FrankenItem> buildDraftOrderSet(Game activeGame) {
        List<FrankenItem> allItems = new ArrayList<>();
        for(int i = 0; i < activeGame.getRealPlayers().size(); i++){
            allItems.add(new FrankenItem(FrankenItem.Category.DRAFTORDER, Integer.toString(i+1)));
        }
        Collections.shuffle(allItems);
        return allItems;
    }

    public static List<FrankenItem> buildTileSet(MiltyDraftManager draftManager, boolean blue) {
        List<FrankenItem> allItems = new ArrayList<>();
        List<MiltyDraftTile> allTiles;
        if (blue) {
            allTiles = draftManager.getHigh();
            allTiles.addAll(draftManager.getMid());
            allTiles.addAll(draftManager.getLow());
        } else {
            allTiles = draftManager.getRed();
        }
        for(MiltyDraftTile tile : allTiles) {
            allItems.add(new FrankenItem(blue ? FrankenItem.Category.BLUETILE : FrankenItem.Category.REDTILE,
                        tile.getTile().getTileID()));
        }
        Collections.shuffle(allItems);
        return allItems;
    }

    public static List<FrankenItem> buildAbilitySet(Game activeGame) {
        HashMap<String, String> abilities = Mapper.getFactionAbilities();
        List<String> allFactions = getAllFactionIds(activeGame);
        List<FrankenItem> allAbilityItems = new ArrayList<>();
        for (var ability : abilities.entrySet()){
            String abilityOwner = ability.getValue().split("\\|")[1];
            if (allFactions.contains(abilityOwner)) {
                allAbilityItems.add(new FrankenItem(FrankenItem.Category.ABILITY,ability.getKey()));
            }
        }

        filterUndraftablesAndShuffle(allAbilityItems);
        Collections.shuffle(allAbilityItems);
        return allAbilityItems;
    }

    private static List<String> validTechSources = List.of(new String[]{"base", "pok", "ds"});
    public static List<FrankenItem> buildFactionTechSet(Game activeGame) {
        HashMap<String, TechnologyModel> techs = Mapper.getTechs();
        List<String> allFactions = getAllFactionIds(activeGame);
        List<FrankenItem> allDraftableTechs = new ArrayList<>();
        for (var tech : techs.entrySet()) {
            String faction = tech.getValue().getFaction();
            String source = tech.getValue().getSource();
            if (allFactions.contains(faction)) {
                if (validTechSources.contains(source)) {
                    allDraftableTechs.add(new FrankenItem(FrankenItem.Category.TECH, tech.getKey()));
                }
            }
        }

        Collections.shuffle(allDraftableTechs);
        return allDraftableTechs;
    }

    public static void makeBags(Game activeGame, boolean powered){
        boolean bigMap = activeGame.getRealPlayers().size() > 6;

        Map<FrankenItem.Category, List<FrankenItem>> allDraftableItems = new HashMap<FrankenItem.Category, List<FrankenItem>>();
        for (FrankenItem.Category category: genericDraftableTypes) {
            allDraftableItems.put(category, buildGenericFactionItemSet(category, activeGame));
        }

        allDraftableItems.put(FrankenItem.Category.DRAFTORDER, buildDraftOrderSet(activeGame));

        MiltyDraftManager draftManager = activeGame.getMiltyDraftManager();
        new StartMilty().initDraftTiles(draftManager);
        allDraftableItems.put(FrankenItem.Category.REDTILE, buildTileSet(draftManager, false));
        allDraftableItems.put(FrankenItem.Category.BLUETILE, buildTileSet(draftManager, true));

        allDraftableItems.put(FrankenItem.Category.ABILITY, buildAbilitySet(activeGame));
        allDraftableItems.put(FrankenItem.Category.TECH, buildFactionTechSet(activeGame));

        List<Player> allPlayers = activeGame.getRealPlayers();
        Collections.shuffle(allPlayers);

        for(Player player : allPlayers) {
            FrankenBag bag = new FrankenBag();

            // Walk through each type of draftable...
            for (Map.Entry<FrankenItem.Category, List<FrankenItem>> draftableCollection:allDraftableItems.entrySet()) {
                FrankenItem.Category category = draftableCollection.getKey();
                int categoryLimit = FrankenItem.GetBagLimit(category, powered, bigMap);
                // ... and pull out the appropriate number of items from its collection...
                for (int i = 0; i < categoryLimit; i++) {
                    // ... and add it to the player's bag.
                    bag.Contents.add(draftableCollection.getValue().remove(0));
                }
            }

            player.setCurrentFrankenBag(bag);
        }
/*
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
 */   }
}