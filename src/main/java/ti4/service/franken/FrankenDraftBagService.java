package ti4.service.franken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import ti4.buttons.Buttons;
import ti4.draft.BagDraft;
import ti4.draft.DraftBag;
import ti4.draft.DraftItem;
import ti4.draft.items.SpeakerOrderDraftItem;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.map.Game;
import ti4.map.Player;
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
                    buttons.add(item.getAddButton().withEmoji(Emoji.fromFormatted(item.getItemEmoji())));
                }
                String message = getLongCategoryRepresentation(draft, bag, category) +
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

            ButtonStyle style = switch (categoryCounter) {
                case 0 -> ButtonStyle.PRIMARY;
                case 1 -> ButtonStyle.DANGER;
                case 2 -> ButtonStyle.SECONDARY;
                default -> ButtonStyle.SUCCESS;
            };
            Button b = Button.of(style, player.getFinsFactionCheckerPrefix() + ACTION_NAME + item.getAlias(), item.getShortDescription()).withEmoji(Emoji.fromFormatted(item.getItemEmoji()));
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
            MessageHelper.sendMessageToChannelWithButtons(bagChannel, "# __Queue:__\n> You are drafting the following from this bag:\n" + getDraftQueueRepresentation(player), queueButtons);

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
        updateDraftStatusMessage(game);
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

    public static String getDraftQueueRepresentation(Player player) {
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
        if (game.getBagDraftStatusMessageID() == null || "null".equals(game.getBagDraftStatusMessageID())) {
            String messageID = game.getActionsChannel().sendMessage(statusMessage).complete().getId();
            game.setBagDraftStatusMessageID(messageID);
            return;
        }
        game.getActionsChannel().retrieveMessageById(game.getBagDraftStatusMessageID()).queue(
            message -> message.editMessage(statusMessage).queue());
    }
}
