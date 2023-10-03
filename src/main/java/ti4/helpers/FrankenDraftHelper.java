package ti4.helpers;

import java.util.*;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.generator.Mapper;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.draft.DraftBag;
import ti4.draft.DraftItem;


public class FrankenDraftHelper {
    public static List<Button> getFrankenBagButtons(Game activeGame, Player player){
        List<Button> buttons = new ArrayList<>();
        DraftBag bagToPass = player.getCurrentDraftBag();
        int categoryCounter = 0;
        bagToPass.Contents.sort(Comparator.comparing(draftItem -> draftItem.ItemCategory));
        DraftItem.Category lastCategory = bagToPass.Contents.get(0).ItemCategory;
        for(DraftItem item : bagToPass.Contents) {
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
        DraftBag currentBag = player.getCurrentDraftBag();
        System.out.println(selectedAlias);
        DraftItem selectedItem = DraftItem.GenerateFromAlias(selectedAlias);

        int limit = activeGame.getActiveBagDraft().GetItemLimitForCategory(selectedItem.ItemCategory);
        int currentAmountInBag = 0;
        int currentAmountInQueue = 0;
        for(DraftItem item : player.getDraftHand().Contents){
            if(item.ItemCategory == selectedItem.ItemCategory){
                currentAmountInBag = currentAmountInBag+1;
            }
        }
        for(DraftItem item : player.getFrankenDraftQueue().Contents){
            if(item.ItemCategory == selectedItem.ItemCategory){
                currentAmountInQueue = currentAmountInQueue+1;
            }
        }
        if(currentAmountInBag+currentAmountInQueue >= limit){
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getTrueIdentity(player, activeGame) + " you are at or exceeding the limit for this category. Please pick something else");
            return;
        }
        if(player.isReadyToPassBag()){
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getTrueIdentity(player, activeGame) + " you are already ready to pass your bag, you cannot pick another item. ");
            return;
        }
        
        if(currentAmountInQueue > 0 && currentBag.Contents.size() > 20){
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getTrueIdentity(player, activeGame) + " you cannot pick 2 of the same thing. Please pick something else");
            return;
        }

        currentBag.Contents.removeIf((DraftItem bagItem) -> bagItem.getAlias().equals(selectedAlias));
        player.queueFrankenItemToDraft(DraftItem.GenerateFromAlias(selectedAlias));

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
                PromptPlayerBagSelection(activeGame, player);
            }
           
           MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg);
        } else {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg);
            PassBags(activeGame);
        }

        DisplayPlayerHand(activeGame, player);
        event.getMessage().delete().queue();
    }

    private static void DisplayPlayerHand(Game activeGame, Player player) {
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                "Your current Hand of drafted cards: \n" + getCurrentPersonalBagRepresentation(activeGame, player));
    }

    public static void showPlayerBag(Game activeGame, Player player) {
        ThreadChannel bagChannel = activeGame.getActiveBagDraft().regenerateBagChannel(activeGame, player);
        MessageHelper.sendMessageToChannel(bagChannel, ButtonHelper.getTrueIdentity(player, activeGame) + " here is your bag");
        for (DraftItem item : player.getCurrentDraftBag().Contents) {
            MessageHelper.sendMessageToChannel(bagChannel, item.getAlias());
        }
        MessageHelper.sendMessageToChannel(bagChannel,
                "You are drafting the following from this bag: \n"+getDraftQueueRepresentation(activeGame, player));
    }

    public static void PassBags(Game activeGame) {
        for(Player p : activeGame.getRealPlayers()) {
            DraftBag queuedItems = p.getFrankenDraftQueue();
            p.getDraftHand().Contents.addAll(queuedItems.Contents);
            for(DraftItem item : queuedItems.Contents) {
                p.getCurrentDraftBag().Contents.removeIf((DraftItem i) -> i.getAlias().equals(item.getAlias()));
            }
            p.resetFrankenItemDraftQueue();
            p.setReadyToPassBag(false);
        }

        // pass bags
        List<Player> players = activeGame.getRealPlayers();
        DraftBag firstPlayerBag = players.get(0).getCurrentDraftBag();
        for (int i = 0; i < players.size()-1; i++) {
            players.get(i).setCurrentDraftBag(players.get(i+1).getCurrentDraftBag());
        }
        players.get(players.size()-1).setCurrentDraftBag(firstPlayerBag);

        for(Player p2 : activeGame.getRealPlayers()){
            MessageHelper.sendMessageToChannel(p2.getCardsInfoThread(), ButtonHelper.getTrueIdentity(p2, activeGame)+"You have been passed a new bag of cards!");
            showPlayerBag(activeGame, p2);
            PromptPlayerBagSelection(activeGame, p2);
            DisplayPlayerHand(activeGame, p2);
        }
         MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), "Bags have been passed");
    }

    private static void PromptPlayerBagSelection(Game activeGame, Player p2) {

        return;
        /*MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(),
                ButtonHelper.getTrueIdentity(p2, activeGame)+"Please select a card to draft:", getFrankenBagButtons(activeGame, p2));
*/    }

    public static String getCurrentPersonalBagRepresentation(Game activeGame, Player player){
        StringBuilder sb = new StringBuilder();
        DraftBag currentBag = player.getDraftHand();
        int itemNum = 1;
        for(DraftItem item : currentBag.Contents) {
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
        DraftBag currentBag = player.getFrankenDraftQueue();
        int itemNum = 1;
        for(DraftItem item : currentBag.Contents) {
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
            player.setDraftHand(new DraftBag());
        }
    }

    public static void startDraft(Game activeGame) {
        List<DraftBag> bags = activeGame.getActiveBagDraft().generateBags(activeGame);
        Collections.shuffle(bags);
        List<Player> realPlayers = activeGame.getRealPlayers();
        for (int i = 0; i < realPlayers.size(); i++) {
            Player player = realPlayers.get(i);
            player.setCurrentDraftBag(bags.get(i));
            player.resetFrankenItemDraftQueue();
            player.setReadyToPassBag(false);

            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), ButtonHelper.getTrueIdentity(player, activeGame)+"Franken Draft has begun!");
            showPlayerBag(activeGame, player);
            PromptPlayerBagSelection(activeGame, player);
        }

        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), Helper.getGamePing(activeGame.getGuild(), activeGame) + " draft started. As a reminder, for the first bag you pick 3 items, and for "+
                "all the bags after that you pick 2 items. New buttons will generate after each pick. The first few picks, the buttons overflow discord button limitations, so while some buttons will get" +
                " cleared away when you pick, others may remain. Please just leave those buttons be and use any new buttons generated. Once you have made your 2 picks (3 in the first bag), the bags will automatically be passed once everyone is ready.");

        GameSaveLoadManager.saveMap(activeGame);
    }
}