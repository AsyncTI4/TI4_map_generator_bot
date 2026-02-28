package ti4.service.franken;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.separator.Separator.Spacing;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.requests.RestAction;
import org.apache.commons.lang3.function.Consumers;
import ti4.buttons.Buttons;
import ti4.draft.BagDraft;
import ti4.draft.DraftBag;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.draft.FrankenDraft;
import ti4.draft.InauguralSpliceFrankenDraft;
import ti4.draft.items.AgentDraftItem;
import ti4.draft.items.HeroDraftItem;
import ti4.helpers.discord.ContainerHelper;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageManager.GameMessage;
import ti4.message.GameMessageType;
import ti4.message.MessageHelper;
import ti4.message.componentsV2.MessageV2Builder;
import ti4.message.logging.BotLogger;
import ti4.service.fow.GMService;
import ti4.service.game.SetOrderService;
import ti4.service.milty.MiltyService;

@UtilityClass
public class FrankenDraftBagService {

    public static final String ACTION_NAME = "frankenDraftAction;";

    public static List<Color> getAccents() {
        return new ArrayList<>(List.of(
                Color.decode("#ff0000"),
                Color.decode("#f07008"),
                Color.decode("#f7f318"),
                Color.decode("#0dd117"),
                Color.decode("#1900ff"),
                Color.decode("#b307ec")));
    }

    public static final List<DraftCategory> componentCategories = List.of(
            DraftCategory.ABILITY,
            DraftCategory.TECH,
            DraftCategory.BREAKTHROUGH,
            DraftCategory.AGENT,
            DraftCategory.COMMANDER,
            DraftCategory.HERO,
            DraftCategory.MECH,
            DraftCategory.FLAGSHIP,
            DraftCategory.COMMODITIES,
            DraftCategory.PN,
            DraftCategory.STARTINGTECH,
            DraftCategory.UNIT,
            DraftCategory.MAHACTKING);

    public static final List<DraftCategory> TFcomponentCategories = List.of(
            DraftCategory.ABILITY,
            DraftCategory.TECH,
            DraftCategory.AGENT,
            DraftCategory.COMMANDER,
            DraftCategory.HERO,
            DraftCategory.MECH,
            DraftCategory.FLAGSHIP,
            DraftCategory.COMMODITIES,
            DraftCategory.PN,
            DraftCategory.STARTINGTECH,
            DraftCategory.UNIT,
            DraftCategory.MAHACTKING,
            DraftCategory.REDTILE,
            DraftCategory.BLUETILE,
            DraftCategory.STARTINGFLEET,
            DraftCategory.HOMESYSTEM,
            DraftCategory.DRAFTORDER);

    public static void applyDraftBags(GenericInteractionCreateEvent event, Game game) {
        applyDraftBags(event, game, true);
    }

    public static void applyDraftBags(GenericInteractionCreateEvent event, Game game, boolean includeGameSetup) {
        if (includeGameSetup) {
            setSpeakerOrder(game);
        }

        List<Color> accents = getAccents();
        for (Player player : game.getPlayers().values()) {
            player.addStoredValue("frankenBuilt", "n");
            for (DraftCategory category : componentCategories) {
                MessageV2Builder builder = new MessageV2Builder(player.getCardsInfoThread());
                Container c = postDraftCategoryContainer(player, category);
                if (c == null) continue;
                builder.append(c.withAccentColor(accents.getFirst()));
                Collections.rotate(accents, -1);
                builder.send();
            }

            if (game.isTwilightsFallMode()) {
                List<Button> buttons = new ArrayList<>();

                List<MessageEmbed> embeds = new ArrayList<>();
                embeds.add(Mapper.getTech("wavelength").getRepresentationEmbed());
                embeds.add(Mapper.getTech("antimatter").getRepresentationEmbed());

                String msg = player.getRepresentation()
                        + ", you should only keep 2 abilities, 1 genome, and 1 unit out of those you drafted."
                        + " Instead of keeping one (or two) of those, you may use these buttons to take one (or two) of these generic technologies.";
                MessageHelper.sendMessageToChannelWithEmbeds(player.getCardsInfoThread(), msg, embeds);
                buttons.add(Buttons.green("getTech_wavelength__noPay__comp", "Select Wavelength"));
                buttons.add(Buttons.green("getTech_antimatter__noPay__comp", "Select Antimatter"));
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Get Technology", buttons);
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Get Technology", buttons);
            }

            MessageV2Builder builder = new MessageV2Builder(player.getCardsInfoThread());
            builder.append(getFrankenPlayerSummaryContainer(player));
            builder.send();
        }

        if (includeGameSetup) {
            if (!game.isFowMode()) {
                game.setShowMapSetup(true);
            }

            MessageChannel channel = game.isFowMode() ? GMService.getGMChannel(game) : game.getMainGameChannel();
            MessageV2Builder builder = new MessageV2Builder(channel);
            builder.append("Press this button after every player has chosen their components.");
            builder.append(Buttons.DEAL_2_SO);
            builder.send();
        }
    }

    public static Container getFrankenPlayerSummaryContainer(Player player) {
        Container summary = player.getRepresentationContainer();
        List<Button> buttons = new ArrayList<>(List.of(Buttons.FACTION_EMBED));
        if (player.getStoredValue("frankenBuilt").equals("n")) {
            buttons.add(Buttons.red("finishedBuilding", "Finished Building Faction"));
        }
        return ContainerHelper.appendComponents(summary, ActionRow.of(buttons));
    }

    private static void setSpeakerOrder(Game game) {
        List<User> users = game.getPlayers().values().stream()
                .filter(player -> player.getDraftHand() != null)
                .filter(player -> player.getDraftHand().getDraftedSpeakerOrder() != null)
                .sorted(Comparator.comparing(p -> p.getDraftHand().getDraftedSpeakerOrder()))
                .map(Player::getUser)
                .toList();
        SetOrderService.setPlayerOrder(null, game, users);
    }

    public static String getBagReceipt(Player player) {
        DraftBag bag = player.getCurrentDraftBag();
        StringBuilder sb = new StringBuilder();
        for (DraftCategory cat : DraftCategory.values()) {
            if (bag.getCategoryCount(cat) == 0) continue;
            sb.append(cat.title(player.getGame()));
            for (DraftItem item : bag.Contents) {
                if (item.getItemCategory() != cat) continue;
                sb.append("> ").append(item.getShortDescription()).append("\n");
            }
        }
        return sb.toString();
    }

    public static void displayPlayerHand(Game game, Player player) {
        String message = "Your current Hand of drafted cards:\n" + getCurrentHandRepresentation(game, player);
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), message);
    }

    public static void showPlayerBag(Game game, Player player) {
        BagDraft draft = game.getActiveBagDraft();
        ThreadChannel bagChannel = draft.regenerateBagChannel(player);
        if (player.isReadyToPassBag()) {
            MessageHelper.sendMessageToChannel(
                    draft.findExistingBagChannel(player),
                    player.getRepresentationUnfogged()
                            + " your Draft Bag is ready to pass and you are waiting for the other players to finish drafting.");
            return;
        }

        List<DraftItem> draftables = new ArrayList<>(player.getCurrentDraftBag().Contents);
        List<DraftItem> undraftables = new ArrayList<>(player.getCurrentDraftBag().Contents);
        draftables.removeIf(draftItem -> !draftItem.isDraftable(player));
        undraftables.removeIf(draftItem -> draftItem.isDraftable(player));

        // Send the bag contents to the draft thread
        List<Color> accents = getAccents();
        MessageV2Builder builder = new MessageV2Builder(bagChannel);
        for (DraftCategory cat : DraftCategory.values()) {
            Container c = draftBagCategoryContainer(player, cat);
            if (c != null) builder.append(c.withAccentColor(accents.getFirst()));
            Collections.rotate(accents, -1);
        }
        builder.send();

        //
        int draftQueueCount = player.getDraftQueue().Contents.size();
        boolean isQueueFull = draft.playerQueueIsFull(player);

        String msg = player.getRepresentationUnfogged();
        if (draftables.isEmpty()) {
            msg += " you cannot legally draft anything from this bag right now.";
            MessageHelper.sendMessageToChannel(bagChannel, msg);
        } else if (!isQueueFull) {
            msg += ", please choose an item to draft using the buttons above.";
            MessageHelper.sendMessageToChannel(bagChannel, msg);
        }

        if (draftQueueCount > 0 || draftables.isEmpty()) {
            List<Button> queueButtons = new ArrayList<>();
            if (isQueueFull || draftables.isEmpty()) {
                queueButtons.add(Buttons.green(
                        player.getFinsFactionCheckerPrefix() + "frankenDraftAction;confirm_draft",
                        "I wish to draft these cards."));
            }
            queueButtons.add(Buttons.red(
                    player.getFinsFactionCheckerPrefix() + "frankenDraftAction;reset_queue",
                    "I wish to draft different cards."));
            MessageHelper.sendMessageToChannelWithButtons(
                    bagChannel,
                    "# __Queue:__\n> You are drafting the following from this bag:\n"
                            + getDraftQueueRepresentation(player),
                    queueButtons);

            if (isQueueFull || draftables.isEmpty()) {
                MessageHelper.sendMessageToChannel(
                        bagChannel, player.getRepresentationUnfogged() + ", please confirm or reset your draft picks.");
            }
        }
    }

    public static void passBags(Game game) {
        // Clear the status message so it will be regenerated
        GameMessageManager.remove(game.getName(), GameMessageType.BAG_DRAFT);
        game.getActiveBagDraft().passBags();
        for (Player p2 : game.getRealPlayers()) {
            showPlayerBag(game, p2);
        }
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Bags have been passed");
        updateDraftStatusMessage(game);
    }

    private static Container draftBagCategoryContainer(Player player, DraftCategory cat) {
        Game game = player.getGame();
        int catLimit = FrankenDraft.getItemLimitForCategory(cat, game);
        if (catLimit == 0) return null;

        List<ContainerChildComponent> components = new ArrayList<>();
        int amtTaken = player.getDraftHand().getCategoryCount(cat);
        amtTaken += player.getDraftQueue().getCategoryCount(cat);
        String progress = " (" + amtTaken + "/" + catLimit + ")";
        components.add(TextDisplay.of(cat.title(game) + progress));

        List<DraftItem> all = player.getCurrentDraftBag().getCategory(cat);
        if (all.isEmpty()) {
            components.add(TextDisplay.of("There are no items of this category remaining"));
        } else {
            // Add each item to the container
            for (DraftItem item : all) {
                if (components.size() > 1) components.add(Separator.createDivider(Spacing.LARGE));
                components.addAll(item.getTextDisplays(game, player, true));
            }
            // Then either...
            if (!DraftItem.isDraftable(player, cat)) {
                // ... Indicate the category is undraftable
                String undraftable = "-# " + DraftItem.undraftableReason(player, cat);
                components.add(TextDisplay.of(undraftable));
            } else {
                // ... Or add buttons to the container
                List<Button> buttons = getSelectionButtons(player, cat);
                if (!buttons.isEmpty()) components.addAll(ActionRow.partitionOf(buttons));
            }
        }
        return Container.of(components);
    }

    private static List<Button> getSelectionButtons(Player player, DraftCategory cat) {
        List<DraftItem> items = player.getCurrentDraftBag().getCategory(cat);
        List<Button> buttons = new ArrayList<>();
        boolean draftable = DraftItem.isDraftable(player, cat);
        if (!items.isEmpty()) {
            // String descrButtonID = ACTION_NAME + "showDescr_" + cat.ordinal();
            // buttons.add(Buttons.blue(descrButtonID, "Show " + cat + " Descriptions"));
            for (DraftItem item : items) {
                String buttonID = player.finChecker() + ACTION_NAME + item.getAlias();
                Button b = Buttons.green(buttonID, item.getShortDescription(), item.getItemEmoji());
                if (!draftable) b = b.asDisabled().withStyle(ButtonStyle.DANGER);
                buttons.add(b);
            }
        }
        return buttons;
    }

    public static Container postDraftCategoryContainer(Player player, DraftCategory cat) {
        Game game = player.getGame();

        List<ContainerChildComponent> components = new ArrayList<>();
        components.add(TextDisplay.of(cat.title(game)));

        List<DraftItem> all = player.getDraftHand().getCategory(cat);
        if (all.isEmpty()) {
            return null;
        } else {
            // Add each item to the container
            for (DraftItem item : all) {
                if (components.size() > 1) components.add(Separator.createDivider(Spacing.LARGE));
                components.addAll(item.getTextDisplays(game, player, true));
            }

            components.addAll(ActionRow.partitionOf(getApplyButtons(player, cat)));
        }
        return Container.of(components);
    }

    private static List<Button> getApplyButtons(Player player, DraftCategory cat) {
        List<DraftItem> items = player.getDraftHand().getCategory(cat);
        List<Button> buttons = new ArrayList<>();

        List<String> appliedItems = player.getStoredList("appliedFrankenItems");
        int limit = player.getGame().getActiveBagDraft().getKeptItemLimitForCategory(cat);
        int taken = player.getDraftHand().getCategoryAppliedCount(appliedItems, cat);
        boolean atLimit = taken >= limit;

        for (DraftItem item : items) {
            boolean alreadyHas = appliedItems.contains(item.getAlias());
            Button b = item.getAddButton().withDisabled(atLimit);
            if (alreadyHas) b = item.getRemoveButton();
            buttons.add(b);
        }
        return buttons;
    }

    public static String getLongCategoryRepresentation(BagDraft draft, DraftBag bag, DraftCategory cat, Game game) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(cat.toString()).append(" (");
        if (game.isTwilightsFallMode() && "tech".equalsIgnoreCase(cat.toString())) {
            sb = new StringBuilder();
            sb.append("### ").append("ABILITY ").append(" (");
        }
        if (game.isTwilightsFallMode() && "agent".equalsIgnoreCase(cat.toString())) {
            sb = new StringBuilder();
            sb.append("### ").append("GENOME ").append(" (");
        }
        if (draft instanceof FrankenDraft) {
            sb.append(bag.getCategoryCount(cat)).append("/").append(FrankenDraft.getItemLimitForCategory(cat, game));
        } else {
            sb.append(bag.getCategoryCount(cat)).append("/").append(draft.getItemLimitForCategory(cat));
        }
        sb.append("):\n");
        for (DraftItem item : bag.getCategory(cat)) {
            sb.append("> ").append(item.getShortDescription()).append("\n");
            if (item instanceof AgentDraftItem || item instanceof HeroDraftItem) {
                sb.append("> - ").append(item.getLongDescription(game)).append("\n");
            } else {
                sb.append("> - ").append(item.getLongDescription()).append("\n");
            }
        }
        return sb.toString();
    }

    private static String getCurrentHandRepresentation(Game game, Player player) {
        return game.getActiveBagDraft().getLongBagRepresentation(player.getDraftHand(), game);
    }

    private static String getDraftQueueRepresentation(Player player) {
        StringBuilder sb = new StringBuilder();
        DraftBag currentBag = player.getDraftQueue();
        for (DraftItem item : currentBag.Contents) {
            sb.append(buildItemDescription(item, player.getGame()));
            sb.append("\n");
        }

        return sb.toString();
    }

    private static String buildItemDescription(DraftItem item, Game game) {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("### ").append(item.getItemEmoji()).append(" ");
            sb.append(item.getShortDescription()).append("\n> ");
            sb.append(item.getLongDescription(game));
        } catch (Exception e) {
            sb.append("ERROR BUILDING DESCRIPTION FOR ").append(item.getAlias());
        }
        return sb.toString();
    }

    public static void clearPlayerHands(Game game) {
        for (Player player : game.getRealPlayers()) {
            player.setDraftHand(new DraftBag());
        }
    }

    public static void startDraft(Game game) {
        BagDraft draft = game.getActiveBagDraft();
        List<DraftBag> bags = draft.generateBags(game);
        if (bags == null) return;

        Collections.shuffle(bags);
        List<Player> realPlayers = game.getRealPlayers();
        for (int i = 0; i < realPlayers.size(); i++) {
            Player player = realPlayers.get(i);
            game.getActiveBagDraft().giveBagToPlayer(bags.get(i), player);
            player.resetDraftQueue();

            showPlayerBag(game, player);
        }

        // Clear the status message so it will be regenerated
        GameMessageManager.remove(game.getName(), GameMessageType.BAG_DRAFT);

        int first = draft.getPicksFromFirstBag();
        if (!game.getStoredValue("frankenLimitFIRSTPICK").isEmpty()) {
            first = Integer.parseInt(game.getStoredValue("frankenLimitFIRSTPICK"));
        }
        int next = draft.getPicksFromNextBags();
        if (!game.getStoredValue("frankenLimitLATERPICK").isEmpty()) {
            next = Integer.parseInt(game.getStoredValue("frankenLimitLATERPICK"));
        }
        String draftName = "Franken Draft";
        if (draft instanceof InauguralSpliceFrankenDraft) {
            draftName = "Inaugural Splice";
        }

        String message;
        if (first == next) {
            message = "# " + game.getPing() + " " + draftName + " has started!\n"
                    + "As a reminder, you will pick " + first + " item" + (first == 1 ? "" : "s") + " from each bag.\n"
                    + "After each pick, the draft thread will be recreated. Sometimes Discord will lag while sending long messages, so the buttons may take a few seconds to show up.\n"
                    + "Once you have made your "
                    + first + " pick" + (first == 1 ? "" : "s")
                    + ", the bags will automatically be passed once everyone is ready.";
        } else {
            message = "# " + game.getPing() + " " + draftName + " has started!\n"
                    + "As a reminder, for the first bag you pick "
                    + first + " item" + (first == 1 ? "" : "s") + ", and for all the bags after that you pick "
                    + next + " item" + (next == 1 ? "" : "s") + ".\n"
                    + "After each pick, the draft thread will be recreated. Sometimes Discord will lag while sending long messages, so the buttons may take a few seconds to show up.\n"
                    + "Once you have made your "
                    + next + " pick" + (next == 1 ? "" : "s") + " (" + first
                    + " in the first bag), the bags will automatically be passed once everyone is ready.";
        }

        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message);
    }

    public static void setUpFrankenFactions(Game game, GenericInteractionCreateEvent event, boolean force) {
        List<Player> players = new ArrayList<>(game.getPlayers().values());
        if (game.isFowMode()) {
            players.removeAll(game.getPlayers().values().stream()
                    .filter(player -> player.getPrivateChannel() == null)
                    .toList());
        }
        int index = 1;
        StringBuilder sb = new StringBuilder("Automatically setting players up as Franken factions:");
        List<Integer> emojiNum = new ArrayList<>(
                List.of(3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26));
        Collections.shuffle(emojiNum);
        boolean skipped = false;
        for (Player player : players) {
            if (player.isRealPlayer() && !force) {
                sb.append("\n> ")
                        .append(player.getRepresentationNoPing())
                        .append(" appears to be set up already and was skipped.");
                skipped = true;
                continue;
            }
            String faction = "franken" + (index <= 24 ? emojiNum.get(index - 1) : index);
            String tempHomeSystemLocation = String.valueOf(500 + index);
            if (!Mapper.isValidFaction(faction) || !PositionMapper.isTilePositionValid(tempHomeSystemLocation)) {
                continue;
            }
            MiltyService.secondHalfOfPlayerSetup(
                    player, game, player.getNextAvailableColour(), faction, tempHomeSystemLocation, event, false);
            sb.append("\n> ").append(player.getRepresentationNoPing());
            index++;
        }
        if (skipped && !game.isTwilightsFallMode()) {
            sb.append(
                    "\nSome players were skipped. Please confirm they are set up as an empty franken shell faction before proceeding with the draft");
        }
        sb.append(
                "\nFranken faction setup finished.\nUse `/franken set_faction_icon` to change your faction symbol. You may use any emoji the bot can use (`/search emojis`).");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
    }

    public static void updateDraftStatusMessage(Game game) {
        String msg = game.getActiveBagDraft().getDraftStatusMessage();
        GameMessageType type = GameMessageType.BAG_DRAFT;
        Optional<GameMessage> gm = GameMessageManager.getOne(game.getName(), type);
        gm.ifPresentOrElse(updateStatusMessage(game, msg, type), sendNewStatusMessage(game, msg, type));
    }

    public static void updateFinishedBuildingMessage(Game game) {
        String msg = game.getActiveBagDraft().getFinishedBuildingMessage();
        GameMessageType type = GameMessageType.BAG_DRAFT;
        Optional<GameMessage> gm = GameMessageManager.getOne(game.getName(), type);
        gm.ifPresentOrElse(updateStatusMessage(game, msg, type), sendNewStatusMessage(game, msg, type));
    }

    private static Consumer<GameMessage> updateStatusMessage(Game game, String newMsg, GameMessageType type) {
        MessageChannel channel = game.getActionsChannel();
        return gm -> channel.retrieveMessageById(gm.messageId())
                .flatMap(m -> m.editMessage(newMsg))
                .onErrorFlatMap(err -> {
                    BotLogger.catchRestError(err);
                    return sendDraftMessageToActionsChannel(game, newMsg, type);
                })
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static Runnable sendNewStatusMessage(Game game, String msg, GameMessageType type) {
        return () ->
                sendDraftMessageToActionsChannel(game, msg, type).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static RestAction<Message> sendDraftMessageToActionsChannel(Game game, String msg, GameMessageType type) {
        String name = game.getName();
        long date = game.getLastModifiedDate();
        return game.getActionsChannel()
                .sendMessage(msg)
                .onSuccess(m -> GameMessageManager.replace(name, m.getId(), type, date));
    }
}
