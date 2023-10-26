package ti4.helpers;

import java.util.*;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import ti4.draft.BagDraft;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.draft.DraftBag;
import ti4.draft.DraftItem;

public class FrankenDraftHelper {

    public static final String ActionName = "frankenDraftAction;";

    public static List<Button> getSelectionButtons(List<DraftItem> draftables) {
        List<Button> buttons = new ArrayList<>();
        draftables.sort(Comparator.comparing(draftItem -> draftItem.ItemCategory));
        DraftItem.Category lastCategory = draftables.get(0).ItemCategory;
        int categoryCounter = 0;
        for (DraftItem item : draftables) {
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
            Button b = Button.of(style, ActionName + item.getAlias(), item.getShortDescription()).withEmoji(Emoji.fromFormatted(item.getItemEmoji()));
            if(b != null){
                buttons.add(b);
            }else{
                BotLogger.log("Tried to build a null button in getSelectionButtons with "+item.getAlias());
            }
            
        }
        return buttons;
    }

    public static void resolveFrankenDraftAction(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID) {
        String action = buttonID.split(";")[1];
        BagDraft draft = activeGame.getActiveBagDraft();

        if (!action.contains(":")) {
            switch (action) {
                case "reset_queue":
                    player.getCurrentDraftBag().Contents.addAll(player.getDraftQueue().Contents);
                    player.resetDraftQueue();
                    showPlayerBag(activeGame, player);

                    GameSaveLoadManager.saveMap(activeGame);
                    return;
                case "confirm_draft":
                    player.getDraftHand().Contents.addAll(player.getDraftQueue().Contents);
                    player.resetDraftQueue();
                    draft.setPlayerReadyToPass(player, true);

                    GameSaveLoadManager.saveMap(activeGame);

                    draft.findExistingBagChannel(player).delete().queue();
                    MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "You are passing the following cards to your right:\n" + getBagReceipt(player.getCurrentDraftBag()));
                    displayPlayerHand(activeGame, player);

                    if (draft.isDraftStageComplete()) {
                        String categoryForPlayers = Helper.getGamePing(event, activeGame);
                        MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(),
                            categoryForPlayers + " the draft stage of the FrankenDraft is complete. Please select your abilities from your drafted hands.");
                        return;
                    }

                    int passCounter = 0;
                    while (draft.allPlayersReadyToPass()) {
                        passBags(activeGame);
                        passCounter++;
                        if (passCounter > activeGame.getRealPlayers().size()) {
                            String categoryForPlayers = Helper.getGamePing(event, activeGame);
                            MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(),
                                categoryForPlayers + " an error has occurred where nobody is able to draft any cards, but there are cards still in the bag. Please notify @developer");
                            break;
                        }
                    }
                    return;
                case "show_bag":
                    showPlayerBag(activeGame, player);
                    return;
            }
        }
        String selectedAlias = action;
        DraftBag currentBag = player.getCurrentDraftBag();
        DraftItem selectedItem = DraftItem.GenerateFromAlias(selectedAlias);

        if (!selectedItem.isDraftable(player)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Something went wrong. You are not allowed to draft " + selectedItem.getShortDescription() + " right now. Please select another item.");
            return;
        }
        currentBag.Contents.removeIf((DraftItem bagItem) -> bagItem.getAlias().equals(selectedAlias));
        player.queueDraftItem(DraftItem.GenerateFromAlias(selectedAlias));

        if (!draft.playerHasDraftableItemInBag(player) && !draft.playerHasItemInQueue(player)) {
            draft.setPlayerReadyToPass(player, true);
        }

        showPlayerBag(activeGame, player);

        GameSaveLoadManager.saveMap(activeGame);
        event.getMessage().delete().queue();
    }

    private static void displayPlayerHand(Game activeGame, Player player) {
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
            "Your current Hand of drafted cards: \n" + getCurrentHandRepresentation(activeGame, player));
    }

    public static void showPlayerBag(Game activeGame, Player player) {
        ThreadChannel bagChannel = activeGame.getActiveBagDraft().regenerateBagChannel(player);

        List<DraftItem> draftables = new ArrayList<>(player.getCurrentDraftBag().Contents);
        List<DraftItem> undraftables = new ArrayList<>(player.getCurrentDraftBag().Contents);
        draftables.removeIf(draftItem -> !draftItem.isDraftable(player));
        undraftables.removeIf(draftItem -> draftItem.isDraftable(player));

        String bagString = getCurrentBagRepresentation(draftables, undraftables);
        MessageHelper.sendMessageToChannel(bagChannel, bagString);

        int draftQueueCount = player.getDraftQueue().Contents.size();
        boolean isFirstDraft = player.getDraftHand().Contents.isEmpty();
        boolean isQueueFull = draftQueueCount >= 2 && !isFirstDraft || draftQueueCount >= 3;
        if (draftables.isEmpty()) {
            MessageHelper.sendMessageToChannel(bagChannel, ButtonHelper.getTrueIdentity(player, activeGame) + " you cannot legally draft anything from this bag right now.");
        } else if (!isQueueFull) {
            MessageHelper.sendMessageToChannelWithButtons(bagChannel,
                ButtonHelper.getTrueIdentity(player, activeGame) + " please select an item to draft:", getSelectionButtons(draftables));
        }

        if (draftQueueCount > 0) {
            List<Button> queueButtons = new ArrayList<>();
            if (isQueueFull || draftables.isEmpty()) {
                queueButtons.add(Button.success("frankenDraftAction;confirm_draft", "I want to draft these cards."));
            }
            queueButtons.add(Button.danger("frankenDraftAction;reset_queue", "I want to draft different cards."));
            MessageHelper.sendMessageToChannelWithButtons(bagChannel,
                "## Queued:\n - You are drafting the following from this bag:\n" + getDraftQueueRepresentation(activeGame, player), queueButtons);

            if (isQueueFull || draftables.isEmpty()) {
                MessageHelper.sendMessageToChannel(bagChannel,
                    ButtonHelper.getTrueIdentity(player, activeGame) + " please confirm or reset your draft picks.");
            }
        }

    }

    public static void passBags(Game activeGame) {
        activeGame.getActiveBagDraft().passBags();

        for (Player p2 : activeGame.getRealPlayers()) {
            showPlayerBag(activeGame, p2);
        }
        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), "Bags have been passed");
    }

    public static String getBagReceipt(DraftBag bag) {
        StringBuilder sb = new StringBuilder();
        for (DraftItem item : bag.Contents) {
            sb.append("**").append(item.getShortDescription()).append("**\n");
        }
        return sb.toString();
    }

    public static String getCurrentBagRepresentation(List<DraftItem> draftables, List<DraftItem> undraftables) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Draftable:\n");
        draftables.sort(Comparator.comparing(draftItem -> draftItem.ItemCategory));
        for (DraftItem item : draftables) {
            buildItemDescription(item, sb);
            sb.append("\n");
        }

        if (!undraftables.isEmpty()) {
            sb.append("# Undraftable:\n");
            sb.append("The following items are in your bag but may not be drafted, either because you are at your hand limit, ");
            sb.append("or because you just drafted a similar item, or because you have not drafted one of each item type yet:\n");

            undraftables.sort(Comparator.comparing(draftItem -> draftItem.ItemCategory));
            for (DraftItem item : undraftables) {
                if(item != null && item.getItemEmoji() != null){
                    sb.append(item.getItemEmoji()).append(" ");
                }
                if(item != null &&item.getShortDescription() != null){
                    sb.append("**").append(item.getShortDescription()).append("**\n");
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    public static String getCurrentHandRepresentation(Game activeGame, Player player) {
        return activeGame.getActiveBagDraft().getLongBagRepresentation(player.getDraftHand());
    }

    public static String getDraftQueueRepresentation(Game activeGame, Player player) {
        StringBuilder sb = new StringBuilder();
        DraftBag currentBag = player.getDraftQueue();
        for (DraftItem item : currentBag.Contents) {
            buildItemDescription(item, sb);
            sb.append("\n");
        }

        return sb.toString();
    }

    private static void buildItemDescription(DraftItem item, StringBuilder sb) {
        try {
            sb.append("### ").append(item.getItemEmoji()).append(" ");
            sb.append(item.getShortDescription()).append("\n - ");
            sb.append(item.getLongDescription());
        } catch (Exception e) {
            sb.append("ERROR BUILDING DESCRIPTION FOR " + item.getAlias());
        }
    }

    public static void clearPlayerHands(Game activeGame) {
        for (Player player : activeGame.getRealPlayers()) {
            player.setDraftHand(new DraftBag());
        }
    }

    public static void startDraft(Game activeGame) {
        List<DraftBag> bags = activeGame.getActiveBagDraft().generateBags(activeGame);
        Collections.shuffle(bags);
        List<Player> realPlayers = activeGame.getRealPlayers();
        for (int i = 0; i < realPlayers.size(); i++) {
            Player player = realPlayers.get(i);
            activeGame.getActiveBagDraft().giveBagToPlayer(bags.get(i), player);
            player.resetDraftQueue();

            showPlayerBag(activeGame, player);
        }

        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), Helper.getGamePing(activeGame.getGuild(), activeGame)
            + " draft started. As a reminder, for the first bag you pick 3 items, and for " +
            "all the bags after that you pick 2 items. New buttons will generate after each pick. The first few picks, the buttons overflow discord button limitations, so while some buttons will get"
            +
            " cleared away when you pick, others may remain. Please just leave those buttons be and use any new buttons generated. Once you have made your 2 picks (3 in the first bag), the bags will automatically be passed once everyone is ready.");

        GameSaveLoadManager.saveMap(activeGame);
    }
}