package ti4.helpers;

import java.util.*;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.draft.DraftBag;
import ti4.draft.DraftItem;


public class FrankenDraftHelper {
    public static List<Button> getSelectionButtons(List<DraftItem> draftables){
        List<Button> buttons = new ArrayList<>();
        draftables.sort(Comparator.comparing(draftItem -> draftItem.ItemCategory));
        DraftItem.Category lastCategory =draftables.get(0).ItemCategory;
        int categoryCounter = 0;
        for(DraftItem item : draftables) {
            if (item.ItemCategory != lastCategory) {
                lastCategory = item.ItemCategory;
                categoryCounter = (categoryCounter + 1) % 4;
            }

            ButtonStyle style = switch (categoryCounter) {
                case 0 -> ButtonStyle.PRIMARY;
                case 1 -> ButtonStyle.DANGER;
                case 2 -> ButtonStyle.SECONDARY;
                default -> ButtonStyle.SUCCESS;
            };
            buttons.add(Button.of(style, "frankenDraftAction;" + item.getAlias(), item.getShortDescription()).withEmoji(Emoji.fromFormatted(item.getItemEmoji())));
        }
        return buttons;
    }

    public static void resolveFrankenDraftAction(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID){
        String selectedAlias = buttonID.split(";")[1];
        DraftBag currentBag = player.getCurrentDraftBag();
        DraftItem selectedItem = DraftItem.GenerateFromAlias(selectedAlias);

        if (!selectedItem.isDraftable(player)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Something went wrong. You are not allowed to draft " + selectedItem.getShortDescription() + " right now. Please select another item.");
            return;
        }
        currentBag.Contents.removeIf((DraftItem bagItem) -> bagItem.getAlias().equals(selectedAlias));
        player.queueDraftItem(DraftItem.GenerateFromAlias(selectedAlias));


        if (player.getDraftHand().Contents.size() < 3) {
            player.setReadyToPassBag(player.getDraftQueue().Contents.size() >= 3);
        } else if (player.getCurrentDraftBag().Contents.stream().noneMatch(draftItem -> draftItem.isDraftable(player))){
            player.setReadyToPassBag(true);
        } else if (player.getDraftQueue().Contents.size() >= 2) {
            player.setReadyToPassBag(true);
        }

        if (!player.isReadyToPassBag()) {
            showPlayerBag(activeGame, player);
        }

        String msg = ButtonHelper.getTrueIdentity(player, activeGame) + " you picked "+selectedItem.getShortDescription();

        boolean everyoneReady = true;
        for(Player p2 : activeGame.getRealPlayers()){
            if (!p2.isReadyToPassBag()) {
                everyoneReady = false;
                break;
            }
        }

        if(!everyoneReady){
            if(player.isReadyToPassBag()){
                msg = msg + ". But not everyone has picked yet. Please wait and you will be pinged when the last person has picked.";
                MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), ButtonHelper.getIdent(player) + " is ready to pass their bag");
            }else{
                msg = msg + ". Please pick another item from this bag.";
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
                "Your current Hand of drafted cards: \n" + getCurrentHandRepresentation(activeGame, player));
    }

    public static void showPlayerBag(Game activeGame, Player player) {
        ThreadChannel bagChannel = activeGame.getActiveBagDraft().regenerateBagChannel(activeGame, player);

        List<DraftItem> draftables = new ArrayList<>(player.getCurrentDraftBag().Contents);
        List<DraftItem> undraftables = new ArrayList<>(player.getCurrentDraftBag().Contents);
        draftables.removeIf(draftItem -> !draftItem.isDraftable(player));
        undraftables.removeIf(draftItem -> draftItem.isDraftable(player));

        MessageHelper.sendMessageToChannel(bagChannel, getCurrentBagRepresentation(draftables, undraftables));
        MessageHelper.sendMessageToChannel(bagChannel,
                "You are drafting the following from this bag: \n"+getDraftQueueRepresentation(activeGame, player));

        if (draftables.isEmpty()) {
            MessageHelper.sendMessageToChannel(bagChannel,ButtonHelper.getTrueIdentity(player, activeGame) + " you cannot legally draft anything from this bag right now. " +
                    "It will pass to the next player once all other players are ready.");
        }
        else {
            MessageHelper.sendMessageToChannelWithButtons(bagChannel,
                    ButtonHelper.getTrueIdentity(player, activeGame) + " please select an item to draft:", getSelectionButtons(draftables));
        }
    }

    public static void PassBags(Game activeGame) {
        for(Player p : activeGame.getRealPlayers()) {
            DraftBag queuedItems = p.getDraftQueue();
            p.getDraftHand().Contents.addAll(queuedItems.Contents);
            for(DraftItem item : queuedItems.Contents) {
                p.getCurrentDraftBag().Contents.removeIf((DraftItem i) -> i.getAlias().equals(item.getAlias()));
            }
            p.resetDraftQueue();
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
            DisplayPlayerHand(activeGame, p2);
        }
         MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), "Bags have been passed");
    }


    public static String getCurrentBagRepresentation(List<DraftItem> draftables, List<DraftItem> undraftables) {
        StringBuilder sb = new StringBuilder();
        sb.append("The following draftable items are in your bag:\n");
        for(DraftItem item : draftables) {
            buildItemDescription(item, sb);
            sb.append("\n");
        }

        sb.append("The following items are in your bag but may not be drafted, either because you are at your hand limit, " +
                "or because you just drafted a similar item, or because you have not drafted one of each item type yet:\n");

        for(DraftItem item : undraftables) {
            sb.append(item.getItemEmoji()).append(" ");
            sb.append("**").append(item.getShortDescription()).append("**\n");
            sb.append("\n");
        }

        return sb.toString();

    }

    public static String getCurrentHandRepresentation(Game activeGame, Player player){
        StringBuilder sb = new StringBuilder();
        DraftBag currentBag = player.getDraftHand();
        for(DraftItem item : currentBag.Contents) {
            buildItemDescription(item, sb);
            sb.append("\n");
        }

        return sb.toString();
    }

    public static String getDraftQueueRepresentation(Game activeGame, Player player){
        StringBuilder sb = new StringBuilder();
        DraftBag currentBag = player.getDraftQueue();
        for(DraftItem item : currentBag.Contents) {
            buildItemDescription(item, sb);
            sb.append("\n");
        }

        return sb.toString();
    }

    private static void buildItemDescription(DraftItem item, StringBuilder sb) {
        try {
            sb.append(item.getItemEmoji()).append(" ");
            sb.append("**").append(item.getShortDescription()).append("**\n");
            sb.append(item.getLongDescription());
        }
        catch (Exception e) {
            sb.append("ERROR BUILDING DESCRIPTION FOR " + item.getAlias());
        }
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
            player.resetDraftQueue();
            player.setReadyToPassBag(false);

            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), ButtonHelper.getTrueIdentity(player, activeGame)+"Franken Draft has begun!");
            showPlayerBag(activeGame, player);
        }

        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), Helper.getGamePing(activeGame.getGuild(), activeGame) + " draft started. As a reminder, for the first bag you pick 3 items, and for "+
                "all the bags after that you pick 2 items. New buttons will generate after each pick. The first few picks, the buttons overflow discord button limitations, so while some buttons will get" +
                " cleared away when you pick, others may remain. Please just leave those buttons be and use any new buttons generated. Once you have made your 2 picks (3 in the first bag), the bags will automatically be passed once everyone is ready.");

        GameSaveLoadManager.saveMap(activeGame);
    }
}