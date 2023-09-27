package ti4.helpers;

import java.util.*;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands.milty.MiltyDraftManager;
import ti4.commands.milty.MiltyDraftTile;
import ti4.commands.milty.StartMilty;
import ti4.generator.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.Franken.FrankenBag;
import ti4.model.Franken.FrankenItem;
import ti4.model.TechnologyModel;



public class FrankenDraftHelper {
    public static List<Button> getFrankenBagButtons(Game activeGame, Player player){
        List<Button> buttons = new ArrayList<>();
        FrankenBag bagToPass = player.getCurrentFrankenBag();
        int categoryCounter = 0;
        bagToPass.Contents.sort(Comparator.comparing((FrankenItem a) -> a.ItemCategory));
        FrankenItem.Category lastCategory = bagToPass.Contents.get(0).ItemCategory;
        for(FrankenItem item : bagToPass.Contents) {
            if (item.ItemCategory != lastCategory) {
                lastCategory = item.ItemCategory;
                categoryCounter = (categoryCounter + 1) % 4;
            }
            FactionModel faction = Mapper.getFactionSetup(item.ItemId);

            switch (categoryCounter) {
                case 0:
                    if(faction != null){
                        buttons.add(Button.primary("frankenDraftAction;"+item.getAlias(),item.toHumanReadable()).withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord(faction.getAlias()))));
                    }else{
                        buttons.add(Button.primary("frankenDraftAction;"+item.getAlias(),item.toHumanReadable()));
                    }
                    break;
                case 1:
                    if(faction != null){
                        buttons.add(Button.danger("frankenDraftAction;"+item.getAlias(),item.toHumanReadable()).withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord(faction.getAlias()))));
                    }else{
                        buttons.add(Button.danger("frankenDraftAction;"+item.getAlias(),item.toHumanReadable()));
                    }
                    break;
                case 2:
                    if(faction != null){
                        buttons.add(Button.secondary("frankenDraftAction;"+item.getAlias(),item.toHumanReadable()).withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord(faction.getAlias()))));
                    }else{
                        buttons.add(Button.secondary("frankenDraftAction;"+item.getAlias(),item.toHumanReadable()));
                    }
                    break;
                case 3:
                    if(faction != null){
                        buttons.add(Button.success("frankenDraftAction;"+item.getAlias(),item.toHumanReadable()).withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord(faction.getAlias()))));
                    }else{
                        buttons.add(Button.success("frankenDraftAction;"+item.getAlias(),item.toHumanReadable()));
                    }
                    break;
            }
        }
        return buttons;
    }

    public static void resolveFrankenDraftAction(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID){
        String selectedAlias = buttonID.split(";")[1];
        FrankenBag currentBag = player.getCurrentFrankenBag();
        System.out.println(selectedAlias);
        FrankenItem selectedItem = FrankenItem.GenerateFromAlias(selectedAlias);

        int limit = FrankenItem.GetBagLimit(selectedItem.ItemCategory, activeGame.getPoweredStatus(), false);
        int currentAmount = 0;
        for(FrankenItem item : player.getFrankenHand().Contents){
            if(item.ItemCategory == selectedItem.ItemCategory){
                currentAmount = currentAmount+1;
            }
        }
        for(FrankenItem item : player.getFrankenDraftQueue().Contents){
            if(item.ItemCategory == selectedItem.ItemCategory){
                currentAmount = currentAmount+1;
            }
        }
        if(currentAmount >= limit){
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getTrueIdentity(player, activeGame) + " you are at or exceeding the limit for this category. Please pick something else");
            return;
        }
        
        if(currentAmount > 0 && player.getFrankenHand().Contents.size() < 1){
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getTrueIdentity(player, activeGame) + " you cannot pick 2 of the same thing in the first bag draft. Please pick something else");
            return;
        }

        currentBag.Contents.removeIf((FrankenItem bagItem) -> bagItem.getAlias().equals(selectedAlias));
        player.queueFrankenItemToDraft(FrankenItem.GenerateFromAlias(selectedAlias));

        if(currentBag.Contents.size() % 2 == 0 && player.getFrankenDraftQueue().Contents.size() > 1){
            player.setReadyToPassBag(true);
        }
        boolean everyoneReady = true;
        for(Player p2 : activeGame.getRealPlayers()){
            if(!p2.isReadyToPassBag()){
                everyoneReady = false;
            }
        }
         String msg = ButtonHelper.getTrueIdentity(player, activeGame) + " you picked "+selectedAlias;
        
        if(!everyoneReady){
            if(player.isReadyToPassBag()){
                msg = msg + ". But not everyone has picked yet. Please wait and you will be pinged when the last person has picked.";
                MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), ButtonHelper.getIdent(player) + " is ready to pass their bag");
            }else{
                msg = msg + ". Please pick another item from this bag.";
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(activeGame), ButtonHelper.getTrueIdentity(player, activeGame)+"Use buttons to select something", getFrankenBagButtons(activeGame, player));
            }
           
           MessageHelper.sendMessageToChannel(player.getCardsInfoThread(activeGame), msg);
        } else {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(activeGame), msg);
            PassBags(activeGame);
        }

        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(activeGame), "You are drafting the following from this bag: \n"+getDraftQueueRepresentation(activeGame, player));

        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(activeGame), "You previously drafted: \n"+getCurrentPersonalBagRepresentation(activeGame, player));
        event.getMessage().delete().queue();
    }

    public static void PassBags(Game activeGame) {
        for(Player p : activeGame.getRealPlayers()) {
            FrankenBag queuedItems = p.getFrankenDraftQueue();
            p.getFrankenHand().Contents.addAll(queuedItems.Contents);
            for(FrankenItem item : queuedItems.Contents) {
                p.getCurrentFrankenBag().Contents.removeIf((FrankenItem i) -> i.getAlias().equals(item.getAlias()));
            }
            p.resetFrankenItemDraftQueue();
        }

        // pass bags
        List<Player> players = activeGame.getRealPlayers();
        FrankenBag firstPlayerBag = players.get(0).getCurrentFrankenBag();
        for (int i = 0; i < players.size()-1; i++) {
            players.get(i).setCurrentFrankenBag(players.get(i+1).getCurrentFrankenBag());
        }
        players.get(players.size()-1).setCurrentFrankenBag(firstPlayerBag);

        for(Player p2 : activeGame.getRealPlayers()){
            MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(activeGame), ButtonHelper.getTrueIdentity(p2, activeGame)+"You have been passed a bag, use buttons to select something", getFrankenBagButtons(activeGame, p2));
            MessageHelper.sendMessageToChannel(p2.getCardsInfoThread(activeGame), ButtonHelper.getTrueIdentity(p2, activeGame)+"Here is a text version of the bag you were passed so you will not forget what was in it later on when you pass it: \n"+getCurrentBagToPassRepresentation(activeGame, p2));
        }
         MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), "Bags have been passed");
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
            itemNum++;
        }

        return sb.toString();
    }

    public static String getDraftQueueRepresentation(Game activeGame, Player player){
        StringBuilder sb = new StringBuilder();
        FrankenBag currentBag = player.getFrankenDraftQueue();
        int itemNum = 1;
        for(FrankenItem item : currentBag.Contents) {
            sb.append(itemNum);
            sb.append(": ");
            sb.append(item.toHumanReadable());
            sb.append("\n");
            itemNum++;
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
            itemNum++;
        }

        return sb.toString();
    }

    public static void clearPlayerHands(Game activeGame) {
        for(Player player: activeGame.getRealPlayers()) {
            player.setFrankenHand(new FrankenBag());
        }
    }

    private static void filterUndraftablesAndShuffle(List<FrankenItem> items) {
        items.removeIf((FrankenItem item) -> item.Undraftable);
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
            allItems.add(FrankenItem.Generate(category, factionId));
        }
        filterUndraftablesAndShuffle(allItems);
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
            allItems.add(FrankenItem.Generate(FrankenItem.Category.DRAFTORDER, Integer.toString(i+1)));
        }
        filterUndraftablesAndShuffle(allItems);
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
            allItems.add(FrankenItem.Generate(blue ? FrankenItem.Category.BLUETILE : FrankenItem.Category.REDTILE,
                        tile.getTile().getTileID()));
        }
        filterUndraftablesAndShuffle(allItems);
        return allItems;
    }

    public static List<FrankenItem> buildAbilitySet(Game activeGame) {
        List<String> allFactions = getAllFactionIds(activeGame);
        List<FrankenItem> allAbilityItems = new ArrayList<>();
        for (var factionId : allFactions) {
            FactionModel faction  = Mapper.getFactionSetup(factionId);
            for (var ability : faction.getAbilities()) {
                allAbilityItems.add(FrankenItem.Generate(FrankenItem.Category.ABILITY,ability));
            }
        }

        filterUndraftablesAndShuffle(allAbilityItems);
        return allAbilityItems;
    }

    public static List<FrankenItem> buildFactionTechSet(Game activeGame) {
        List<String> allFactions = getAllFactionIds(activeGame);
        List<FrankenItem> allDraftableTechs = new ArrayList<>();
        for (var factionId : allFactions) {
            FactionModel faction = Mapper.getFactionSetup(factionId);
            for(var tech : faction.getFactionTech()) {
                allDraftableTechs.add(FrankenItem.Generate(FrankenItem.Category.TECH, tech));
            }
        }
        filterUndraftablesAndShuffle(allDraftableTechs);
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
            player.resetFrankenItemDraftQueue();
            player.setReadyToPassBag(false);
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(activeGame), ButtonHelper.getTrueIdentity(player, activeGame)+"Franken Draft has begun, use buttons to select something", getFrankenBagButtons(activeGame, player));
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(activeGame), ButtonHelper.getTrueIdentity(player, activeGame)+"Here is a text version of the bag so you will not forget what was in it later on when you pass it: \n"+getCurrentBagToPassRepresentation(activeGame, player));

        }
         MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), Helper.getGamePing(activeGame.getGuild(), activeGame) + " draft started. As a reminder, for the first bag you pick 3 items, and for "+
            "all the bags after that you pick 2 items. New buttons will generate after each pick. The first few picks, the buttons overflow discord button limitations, so while some buttons will get" +
            " cleared away when you pick, others may remain. Please just leave those buttons be and use any new buttons generated. Once you have made your 2 picks (3 in the first bag), the bags will automatically be passed once everyone is ready.");
    }
}