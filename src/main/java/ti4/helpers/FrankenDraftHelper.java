package ti4.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import ti4.buttons.Buttons;
import ti4.commands.franken.ApplyDraftBags;
import ti4.commands2.player.Setup;
import ti4.draft.BagDraft;
import ti4.draft.DraftBag;
import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class FrankenDraftHelper {

    public static final String ActionName = "frankenDraftAction;";

    public static List<Button> getSelectionButtons(List<DraftItem> draftables, Player player) {
        List<Button> buttons = new ArrayList<>();
        draftables.sort(Comparator.comparing(draftItem -> draftItem.ItemCategory));
        DraftItem.Category lastCategory = draftables.getFirst().ItemCategory;
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
            Button b = Button.of(style, player.getFinsFactionCheckerPrefix() + ActionName + item.getAlias(), item.getShortDescription()).withEmoji(Emoji.fromFormatted(item.getItemEmoji()));
            buttons.add(b);
        }
        return buttons;
    }

    @ButtonHandler("frankenDraftAction")
    public static void resolveFrankenDraftAction(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String action = buttonID.split(";")[1];
        BagDraft draft = game.getActiveBagDraft();

        if (!action.contains(":")) {
            switch (action) {
                case "reset_queue" -> {
                    player.getCurrentDraftBag().Contents.addAll(player.getDraftQueue().Contents);
                    player.resetDraftQueue();
                    showPlayerBag(game, player);
                    GameSaveLoadManager.saveGame(game, player.getUserName() + " reset their draft queue");
                    return;
                }
                case "confirm_draft" -> {
                    player.getDraftHand().Contents.addAll(player.getDraftQueue().Contents);
                    player.resetDraftQueue();
                    draft.setPlayerReadyToPass(player, true);
                    GameSaveLoadManager.saveGame(game, player.getUserName() + " confirmed their draft picks");

                    // Clear out all existing messages
                    draft.findExistingBagChannel(player).getHistory().retrievePast(100).queue(m -> {
                        if (!m.isEmpty()) {
                            draft.findExistingBagChannel(player).deleteMessages(m).queue();
                        }
                    });
                    MessageHelper.sendMessageToChannel(draft.findExistingBagChannel(player), "Your Draft Bag is ready to pass and you are waiting for the other players to finish drafting.");
                    MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "You are passing the following cards to your right:\n" + getBagReceipt(player.getCurrentDraftBag()));
                    displayPlayerHand(game, player);
                    if (draft.isDraftStageComplete()) {
                        MessageHelper.sendMessageToChannel(game.getActionsChannel(), game.getPing() + " the draft stage of the FrankenDraft is complete. Please select your abilities from your drafted hands.");
                        ApplyDraftBags.applyDraftBags(event, game);
                        return;
                    }
                    int passCounter = 0;
                    while (draft.allPlayersReadyToPass()) {
                        passBags(game);
                        passCounter++;
                        if (passCounter > game.getRealPlayers().size()) {
                            MessageHelper.sendMessageToChannel(game.getActionsChannel(), game.getPing() + " an error has occurred where nobody is able to draft any cards, but there are cards still in the bag. Please notify @developer");
                            break;
                        }
                    }
                    return;
                }
                case "show_bag" -> {
                    showPlayerBag(game, player);
                    return;
                }
            }
        }
        DraftBag currentBag = player.getCurrentDraftBag();
        DraftItem selectedItem = DraftItem.generateFromAlias(action);

        if (!selectedItem.isDraftable(player)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Something went wrong. You are not allowed to draft " + selectedItem.getShortDescription() + " right now. Please select another item.");
            return;
        }
        currentBag.Contents.removeIf((DraftItem bagItem) -> bagItem.getAlias().equals(action));
        player.queueDraftItem(DraftItem.generateFromAlias(action));

        if (!draft.playerHasDraftableItemInBag(player) && !draft.playerHasItemInQueue(player)) {
            draft.setPlayerReadyToPass(player, true);
        }

        showPlayerBag(game, player);

        GameSaveLoadManager.saveGame(game, player.getUserName() + " did something");
        event.getMessage().delete().queue();
    }

    public static void displayPlayerHand(Game game, Player player) {
        String message = "Your current Hand of drafted cards:\n" + getCurrentHandRepresentation(game, player);
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), message);
    }

    public static void showPlayerBag(Game game, Player player) {
        BagDraft draft = game.getActiveBagDraft();
        ThreadChannel bagChannel = draft.regenerateBagChannel(player);
        if (player.isReadyToPassBag()) {
            MessageHelper.sendMessageToChannel(draft.findExistingBagChannel(player), player.getRepresentationUnfogged() + " your Draft Bag is ready to pass and you are waiting for the other players to finish drafting.");
            return;
        }

        List<DraftItem> draftables = new ArrayList<>(player.getCurrentDraftBag().Contents);
        List<DraftItem> undraftables = new ArrayList<>(player.getCurrentDraftBag().Contents);
        draftables.removeIf(draftItem -> !draftItem.isDraftable(player));
        undraftables.removeIf(draftItem -> draftItem.isDraftable(player));

        String bagString = getCurrentBagRepresentation(draftables, undraftables);
        MessageHelper.sendMessageToChannel(bagChannel, bagString);

        int draftQueueCount = player.getDraftQueue().Contents.size();
        boolean isFirstDraft = player.getDraftHand().Contents.isEmpty();
        boolean isQueueFull = draftQueueCount >= draft.getPicksFromNextBags() && !isFirstDraft || draftQueueCount >= draft.getPicksFromFirstBag();
        if (draftables.isEmpty()) {
            MessageHelper.sendMessageToChannel(bagChannel, player.getRepresentationUnfogged() + " you cannot legally draft anything from this bag right now.");
        } else if (!isQueueFull) {
            MessageHelper.sendMessageToChannelWithButtons(bagChannel, player.getRepresentationUnfogged() + " please select an item to draft:", getSelectionButtons(draftables, player));
        }

        if (draftQueueCount > 0) {
            List<Button> queueButtons = new ArrayList<>();
            if (isQueueFull || draftables.isEmpty()) {
                queueButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "frankenDraftAction;confirm_draft", "I want to draft these cards."));
            }
            queueButtons.add(Buttons.red(player.getFinsFactionCheckerPrefix() + "frankenDraftAction;reset_queue", "I want to draft different cards."));
            MessageHelper.sendMessageToChannelWithButtons(bagChannel, "# __Queue:__\n> You are drafting the following from this bag:\n" + getDraftQueueRepresentation(game, player), queueButtons);

            if (isQueueFull || draftables.isEmpty()) {
                MessageHelper.sendMessageToChannel(bagChannel, player.getRepresentationUnfogged() + " please confirm or reset your draft picks.");
            }
        }

    }

    public static void passBags(Game game) {
        game.setBagDraftStatusMessageID(null); // Clear the status message so it will be regenerated
        game.getActiveBagDraft().passBags();
        for (Player p2 : game.getRealPlayers()) {
            showPlayerBag(game, p2);
        }
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Bags have been passed");
        FrankenDraftHelper.updateDraftStatusMessage(game);
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
        sb.append("# __Draftable:__\n");
        draftables.sort(Comparator.comparing(draftItem -> draftItem.ItemCategory));
        for (DraftItem item : draftables) {
            buildItemDescription(item, sb);
            sb.append("\n");
        }

        if (!undraftables.isEmpty()) {
            sb.append("# __Undraftable:__\n");
            sb.append("> The following items are in your bag but may not be drafted, either because you:\n");
            sb.append("> - are at your hand limit\n");
            sb.append("> - just drafted a similar item\n");
            sb.append("> - have not drafted one of each item type yet\n");

            undraftables.sort(Comparator.comparing(draftItem -> draftItem.ItemCategory));
            for (DraftItem item : undraftables) {
                sb.append("> ");
                if (item != null && item.getItemEmoji() != null) {
                    sb.append(item.getItemEmoji()).append(" ");
                }
                if (item != null && item.getShortDescription() != null) {
                    sb.append("**").append(item.getShortDescription()).append("**");
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    public static String getLongCategoryRepresentation(BagDraft draft, DraftBag bag, DraftItem.Category cat) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(cat.toString()).append(" (");
        sb.append(bag.getCategoryCount(cat)).append("/").append(draft.getItemLimitForCategory(cat));
        sb.append("):\n");
        for (DraftItem item : bag.Contents) {
            if (item.ItemCategory != cat) {
                continue;
            }
            sb.append("> ").append(item.getShortDescription()).append("\n");
            sb.append("> - ").append(item.getLongDescription()).append("\n");
        }
        return sb.toString();
    }

    public static String getCurrentHandRepresentation(Game game, Player player) {
        return game.getActiveBagDraft().getLongBagRepresentation(player.getDraftHand());
    }

    public static String getDraftQueueRepresentation(Game game, Player player) {
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
            sb.append(item.getShortDescription()).append("\n> ");
            sb.append(item.getLongDescription());
        } catch (Exception e) {
            sb.append("ERROR BUILDING DESCRIPTION FOR ").append(item.getAlias());
        }
    }

    public static void clearPlayerHands(Game game) {
        for (Player player : game.getRealPlayers()) {
            player.setDraftHand(new DraftBag());
        }
    }

    public static void startDraft(Game game) {
        BagDraft draft = game.getActiveBagDraft();
        List<DraftBag> bags = draft.generateBags(game);
        Collections.shuffle(bags);
        List<Player> realPlayers = game.getRealPlayers();
        for (int i = 0; i < realPlayers.size(); i++) {
            Player player = realPlayers.get(i);
            game.getActiveBagDraft().giveBagToPlayer(bags.get(i), player);
            player.resetDraftQueue();

            showPlayerBag(game, player);
        }
        game.setBagDraftStatusMessageID(null); // Clear the status message so it will be regenerated

        int first = draft.getPicksFromFirstBag();
        int next = draft.getPicksFromNextBags();
        String message = "# " + game.getPing() + " Franken Draft has started!\n" +
            "> As a reminder, for the first bag you pick " + first + " item" + (first == 1 ? "" : "s") + ", and for all the bags after that you pick " + next + " item" + (next == 1 ? "" : "s") + ".\n" +
            "> After each pick, the draft thread will be recreated. Sometimes discord will lag while sending long messages, so the buttons may take a few seconds to show up\n" +
            "> Once you have made your " + next + " pick" + (next == 1 ? "" : "s") + " (" + first + " in the first bag), the bags will automatically be passed once everyone is ready.";

        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message);
        GameSaveLoadManager.saveGame(game, "Franken draft was started");
    }

    public static void setUpFrankenFactions(Game game, GenericInteractionCreateEvent event, boolean force) {
        List<Player> players = new ArrayList<>(game.getPlayers().values());
        int index = 1;
        StringBuilder sb = new StringBuilder();
        sb.append("Automatically setting players up as Franken factions:");
        boolean skipped = false;
        for (Player player : players) {
            if (player.isRealPlayer() && !force) {
                sb.append("\n> ").append(player.getRepresentationNoPing()).append(" appears to be set up already and was skipped.");
                skipped = true;
                continue;
            }
            String faction = "franken" + index;
            String tempHomeSystemLocation = String.valueOf(300 + index);
            if (!Mapper.isValidFaction(faction) || !PositionMapper.isTilePositionValid(tempHomeSystemLocation)) {
                continue;
            }
            Setup.secondHalfOfPlayerSetup(player, game, player.getNextAvailableColour(), faction, tempHomeSystemLocation, event, false);
            sb.append("\n> ").append(player.getRepresentationNoPing());
            index++;
        }
        if (skipped) {
            sb.append("\nSome players were skipped. Please confirm they are set up as an empty franken shell faction before proceeding with the draft");
        }
        sb.append("\nFranken faction setup finished.\nUse `/franken set_faction_icon` to change your faction symbol. You may use any emoji the bot can use (`/search emojis`).");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
    }

    public static void updateDraftStatusMessage(Game game) {
        String statusMessage = game.getActiveBagDraft().getDraftStatusMessage();
        if (game.getBagDraftStatusMessageID() == null || "null".equals(game.getBagDraftStatusMessageID())) {
            String messageID = game.getActionsChannel().sendMessage(statusMessage).complete().getId();
            game.setBagDraftStatusMessageID(messageID);
            return;
        }
        game.getActionsChannel().retrieveMessageById(game.getBagDraftStatusMessageID()).queue(
            message -> message.editMessage(statusMessage).queue());
    }
}
