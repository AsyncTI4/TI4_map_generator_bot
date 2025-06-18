package ti4.service.franken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import ti4.buttons.Buttons;
import ti4.draft.BagDraft;
import ti4.draft.DraftBag;
import ti4.draft.DraftItem;
import ti4.draft.FrankenDraft;
import ti4.draft.items.SpeakerOrderDraftItem;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.message.MessageHelper;
import ti4.service.game.SetOrderService;
import ti4.service.milty.MiltyService;

@UtilityClass
public class FrankenDraftBagService {

    public static final String ACTION_NAME = "frankenDraftAction;";

    static final List<DraftItem.Category> componentCategories = List.of(
        DraftItem.Category.ABILITY,
        DraftItem.Category.TECH,
        DraftItem.Category.AGENT,
        DraftItem.Category.COMMANDER,
        DraftItem.Category.HERO,
        DraftItem.Category.MECH,
        DraftItem.Category.FLAGSHIP,
        DraftItem.Category.COMMODITIES,
        DraftItem.Category.PN,
        DraftItem.Category.STARTINGTECH);

    public static void applyDraftBags(GenericInteractionCreateEvent event, Game game) {
        BagDraft draft = game.getActiveBagDraft();

        setSpeakerOrder(event, game); // Category.DRAFTORDER

        for (Player player : game.getPlayers().values()) {
            DraftBag bag = player.getDraftHand();
            for (DraftItem.Category category : componentCategories) {
                List<DraftItem> items = bag.Contents.stream().filter(item -> item.ItemCategory == category).toList();
                if (items.isEmpty()) {
                    continue;
                }
                List<Button> buttons = new ArrayList<>();
                for (DraftItem item : items) {
                    buttons.add(item.getAddButton());
                }
                String message = getLongCategoryRepresentation(draft, bag, category, game) +
                    "\nClick the buttons below to add or remove items from your faction.";
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
            }
            MessageEmbed embed = player.getRepresentationEmbed();
            MessageHelper.sendMessageToChannelWithEmbedsAndButtons(player.getCardsInfoThread(), null, List.of(embed), List.of(Buttons.FACTION_EMBED));
        }
        game.setShowMapSetup(true);
    }

    private static void setSpeakerOrder(GenericInteractionCreateEvent event, Game game) {
        List<User> users = game.getPlayers().values().stream()
            .filter(player -> player.getDraftHand() != null)
            .filter(player -> player.getDraftHand().Contents.stream().anyMatch(item -> item.ItemCategory == DraftItem.Category.DRAFTORDER))
            .sorted(Comparator.comparing(player -> {
                SpeakerOrderDraftItem order = (SpeakerOrderDraftItem) player.getDraftHand().Contents.stream()
                    .filter(item -> item.ItemCategory == DraftItem.Category.DRAFTORDER)
                    .findFirst()
                    .get();
                if (order.getSpeakerOrder() == 1) {
                    game.setSpeakerUserID(player.getUserID());
                }
                return order.getSpeakerOrder();
            }))
            .map(Player::getUser)
            .toList();
        SetOrderService.setPlayerOrder(event, game, users);
    }

    public static String getBagReceipt(DraftBag bag) {
        StringBuilder sb = new StringBuilder();
        for (DraftItem item : bag.Contents) {
            sb.append("**").append(item.getShortDescription()).append("**\n");
        }
        return sb.toString();
    }

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

            Button b = Buttons.green(player.getFinsFactionCheckerPrefix() + ACTION_NAME + item.getAlias(), item.getShortDescription(), item.getItemEmoji());
            switch (categoryCounter) {
                case 0 -> b = b.withStyle(ButtonStyle.PRIMARY);
                case 1 -> b = b.withStyle(ButtonStyle.DANGER);
                case 2 -> b = b.withStyle(ButtonStyle.SECONDARY);
            }
            buttons.add(b);
        }
        return buttons;
    }

    public static void displayPlayerHand(Game game, Player player) {
        String message = "Your current Hand of drafted cards:\n" + getCurrentHandRepresentation(game, player);
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), message);
    }

    public static void showPlayerBag(Game game, Player player) {
        BagDraft draft = game.getActiveBagDraft();
        ThreadChannel bagChannel = draft.regenerateBagChannel(player);
        DraftBag currentBag = player.getCurrentDraftBag().orElse(null);
        if (currentBag == null) {
            MessageHelper.sendMessageToChannel(draft.findExistingBagChannel(player), player.getRepresentationUnfogged() + " you are waiting for other players to finish drafting.");
            return;
        }

        List<DraftItem> draftables = new ArrayList<>(currentBag.Contents);
        List<DraftItem> undraftables = new ArrayList<>(currentBag.Contents);
        draftables.removeIf(draftItem -> !draftItem.isDraftable(player));
        undraftables.removeIf(draftItem -> draftItem.isDraftable(player));

        Set<String> bagStringLines = getCurrentBagRepresentation(draftables, undraftables);
        for (String line : bagStringLines) {
            MessageHelper.sendMessageToChannel(bagChannel, line);
        }

        int draftQueueCount = player.getDraftQueue().Contents.size();
        boolean isFirstDraft = player.getDraftHand().Contents.isEmpty();
        boolean isQueueFull = draftQueueCount >= draft.getPicksFromNextBags();
        if (!game.getStoredValue("frankenLimitLATERPICK").isEmpty()) {
            isQueueFull = draftQueueCount >= Integer.parseInt(game.getStoredValue("frankenLimitLATERPICK"));
        }
        if (isFirstDraft) {
            isQueueFull = draftQueueCount >= draft.getPicksFromFirstBag();
            if (!game.getStoredValue("frankenLimitFIRSTPICK").isEmpty()) {
                isQueueFull = draftQueueCount >= Integer.parseInt(game.getStoredValue("frankenLimitFIRSTPICK"));
            }
        }
        if (draftables.isEmpty()) {
            MessageHelper.sendMessageToChannel(bagChannel, player.getRepresentationUnfogged() + " you cannot legally draft anything from this bag right now.");
        } else if (!isQueueFull) {
            MessageHelper.sendMessageToChannelWithButtons(bagChannel, player.getRepresentationUnfogged() + " please select an item to draft:", getSelectionButtons(draftables, player));
        }

        if (draftQueueCount > 0) {
            List<Button> queueButtons = new ArrayList<>();
            if (isQueueFull || draftables.isEmpty()) {
                queueButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "frankenDraftAction;confirm_draft", "I wish to draft these cards."));
            }
            queueButtons.add(Buttons.red(player.getFinsFactionCheckerPrefix() + "frankenDraftAction;reset_queue", "I wish to draft different cards."));
            MessageHelper.sendMessageToChannelWithButtons(bagChannel, "# __Queue:__\n> You are drafting the following from this bag:\n" + getDraftQueueRepresentation(player), queueButtons);

            if (isQueueFull || draftables.isEmpty()) {
                MessageHelper.sendMessageToChannel(bagChannel, player.getRepresentationUnfogged() + " please confirm or reset your draft picks.");
            }
        }

    }

    public static Set<String> getCurrentBagRepresentation(List<DraftItem> draftables, List<DraftItem> undraftables) {
        Set<String> bagRepresentationLines = new LinkedHashSet<>();
        StringBuilder sb = new StringBuilder("# __Draftable:__\n");

        draftables.sort(Comparator.comparing(draftItem -> draftItem.ItemCategory));
        for (DraftItem item : draftables) {
            String nextItemDescrption = buildItemDescription(item);
            if (sb.length() + nextItemDescrption.length() > 2000) { //Split to max 2000 message lines
                bagRepresentationLines.add(sb.toString());
                sb = new StringBuilder(nextItemDescrption);
            } else {
                sb.append(nextItemDescrption);
            }
            sb.append("\n");
        }
        bagRepresentationLines.add(sb.toString());

        if (!undraftables.isEmpty()) {
            sb = new StringBuilder("# __Undraftable:__\n");
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
            bagRepresentationLines.add(sb.toString());
        }

        return bagRepresentationLines;
    }

    public static String getLongCategoryRepresentation(BagDraft draft, DraftBag bag, DraftItem.Category cat, Game game) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(cat.toString()).append(" (");
        if (draft instanceof FrankenDraft) {
            sb.append(bag.getCategoryCount(cat)).append("/").append(FrankenDraft.getItemLimitForCategory(cat, game));
        } else {
            sb.append(bag.getCategoryCount(cat)).append("/").append(draft.getItemLimitForCategory(cat));
        }
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
        return game.getActiveBagDraft().getLongBagRepresentation(player.getDraftHand(), game);
    }

    public static String getDraftQueueRepresentation(Player player) {
        StringBuilder sb = new StringBuilder();
        DraftBag currentBag = player.getDraftQueue();
        for (DraftItem item : currentBag.Contents) {
            sb.append(buildItemDescription(item));
            sb.append("\n");
        }

        return sb.toString();
    }

    private static String buildItemDescription(DraftItem item) {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("### ").append(item.getItemEmoji()).append(" ");
            sb.append(item.getShortDescription()).append("\n> ");
            sb.append(item.getLongDescription());
        } catch (Exception e) {
            sb.append("ERROR BUILDING DESCRIPTION FOR ").append(item.getAlias());
        }
        return sb.toString();
    }

    public static void clearPlayerHandsAndQueues(Game game) {
        for (Player player : game.getRealPlayers()) {
            player.setDraftHand(new DraftBag());
            player.getDraftBagQueue().clear();
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
        GameMessageManager.remove(game.getName(), GameMessageType.BAG_DRAFT); // Clear the status message so it will be regenerated

        int first = draft.getPicksFromFirstBag();
        if (!game.getStoredValue("frankenLimitFIRSTPICK").isEmpty()) {
            first = Integer.parseInt(game.getStoredValue("frankenLimitFIRSTPICK"));
        }
        int next = draft.getPicksFromNextBags();
        if (!game.getStoredValue("frankenLimitLATERPICK").isEmpty()) {
            next = Integer.parseInt(game.getStoredValue("frankenLimitLATERPICK"));
        }

        String message = "# " + game.getPing() + " Franken Draft has started!\n" +
            "> As a reminder, for the first bag you pick " + first + " item" + (first == 1 ? "" : "s") + ", and for all the bags after that you pick " + next + " item" + (next == 1 ? "" : "s") + ".\n" +
            "> After each pick, the draft thread will be recreated. Sometimes discord will lag while sending long messages, so the buttons may take a few seconds to show up\n" +
            "> Once you have made your " + next + " pick" + (next == 1 ? "" : "s") + " (" + first + " in the first bag), your bag will automatically be passed to the next player. If they already have a bag, your bag will be added to their queue.";
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message);

        // Clear the status message to avoid reusing one from previous drafts
        GameMessageManager.remove(game.getName(), GameMessageType.BAG_DRAFT);
        FrankenDraftBagService.updateDraftStatusMessage(game);
    }

    public static void setUpFrankenFactions(Game game, GenericInteractionCreateEvent event, boolean force) {
        List<Player> players = new ArrayList<>(game.getPlayers().values());
        if (game.isFowMode()) {
            players.removeAll(game.getPlayersWithGMRole());
        }
        int index = 1;
        StringBuilder sb = new StringBuilder("Automatically setting players up as Franken factions:");
        List<Integer> emojiNum = new ArrayList<>(List.of(3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26));
        Collections.shuffle(emojiNum);
        boolean skipped = false;
        for (Player player : players) {
            if (player.isRealPlayer() && !force) {
                sb.append("\n> ").append(player.getRepresentationNoPing()).append(" appears to be set up already and was skipped.");
                skipped = true;
                continue;
            }
            String faction = "franken" + (index <= 16 ? emojiNum.get(index - 1) : index);
            String tempHomeSystemLocation = String.valueOf(300 + index);
            if (!Mapper.isValidFaction(faction) || !PositionMapper.isTilePositionValid(tempHomeSystemLocation)) {
                continue;
            }
            MiltyService.secondHalfOfPlayerSetup(player, game, player.getNextAvailableColour(), faction, tempHomeSystemLocation, event, false);
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
        GameMessageManager.getOne(game.getName(), GameMessageType.BAG_DRAFT)
            .ifPresentOrElse(gameMessage -> game.getActionsChannel()
                .retrieveMessageById(gameMessage.messageId())
                .queue(message -> message.editMessage(statusMessage).queue()),
                () -> {
                    String newMessageId = game.getActionsChannel().sendMessage(statusMessage).complete().getId();
                    GameMessageManager.add(game.getName(), newMessageId, GameMessageType.BAG_DRAFT, game.getLastModifiedDate());
                });
    }
}
