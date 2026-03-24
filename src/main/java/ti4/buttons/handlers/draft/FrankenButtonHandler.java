package ti4.buttons.handlers.draft;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.Component;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.buttons.Buttons;
import ti4.draft.BagDraft;
import ti4.draft.DraftBag;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.draft.InauguralSpliceFrankenDraft;
import ti4.draft.TwilightsFallFrankenDraft;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.message.MessageHelper;
import ti4.message.componentsV2.MessageV2Builder;
import ti4.message.componentsV2.MessageV2Editor;
import ti4.message.logging.BotLogger;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;
import ti4.service.draft.DraftButtonService;
import ti4.service.draft.MantisMapBuildContext;
import ti4.service.draft.MantisMapBuildService;
import ti4.service.fow.GMService;
import ti4.service.franken.FrankenAbilityService;
import ti4.service.franken.FrankenBreakthroughService;
import ti4.service.franken.FrankenDraftBagService;
import ti4.service.franken.FrankenFactionTechService;
import ti4.service.franken.FrankenHomeService;
import ti4.service.franken.FrankenLeaderService;
import ti4.service.franken.FrankenMapBuildContextHelper;
import ti4.service.franken.FrankenPlotService;
import ti4.service.franken.FrankenPromissoryService;
import ti4.service.franken.FrankenStartingTechService;
import ti4.service.franken.FrankenStatsService;
import ti4.service.franken.FrankenUnitService;

@UtilityClass
public class FrankenButtonHandler {

    @ButtonHandler("frankenItemAdd")
    private static void resolveFrankenItemAddButton(ButtonInteractionEvent event, Player player, String buttonID) {
        String frankenItem = buttonID.replace("frankenItemAdd", "");
        DraftItem draftItem = DraftItem.generateFromAlias(frankenItem);
        resolveFrankenItemAdd(event, player, draftItem);
        refreshContainers(event, player);
    }

    public static void resolveFrankenItemAdd(ButtonInteractionEvent event, Player player, DraftItem item) {
        applyFrankenItemToPlayer(event, player, item);
        // Handle Errata
        if (!player.getGame().isTwilightsFallMode()) {
            if (item.hasAdditionalComponents()) { // Auto-add Additional Components
                MessageHelper.sendEphemeralMessageToEventChannel(event, "Some additional items were added:");
                for (DraftErrataModel i : item.getErrata().getAdditionalComponents()) {
                    DraftItem addl = DraftItem.generate(i.getItemCategory(), i.getItemId());
                    applyFrankenItemToPlayer(event, player, addl);
                }
            }
            if (item.hasOptionalSwaps()) { // Offer Optional Swaps
                StringBuilder msg =
                        new StringBuilder("Added the following optional swaps to their respective categories:");
                for (DraftErrataModel i : item.getErrata().getOptionalSwaps()) {
                    DraftItem addl = DraftItem.generate(i.getItemCategory(), i.getItemId());
                    player.getDraftHand().Contents.add(addl);
                    msg.append("\n> ").append(addl.getTitle(player.getGame()));
                }
                MessageHelper.sendEphemeralMessageToEventChannel(event, msg.toString());
            }
        }
    }

    @ButtonHandler("frankenItemRemove")
    private static void resolveFrankenItemRemoveButton(ButtonInteractionEvent event, String buttonID, Player player) {
        String frankenItem = buttonID.replace("frankenItemRemove", "");
        DraftItem draftItem = DraftItem.generateFromAlias(frankenItem);
        resolveFrankenItemRemove(event, player, draftItem);
        refreshContainers(event, player);
    }

    public static void resolveFrankenItemRemove(ButtonInteractionEvent event, Player player, DraftItem item) {
        removeFrankenItemFromPlayer(event, player, item);
        if (!player.getGame().isTwilightsFallMode()) {
            if (item.hasAdditionalComponents()) { // Auto-add Additional Components
                MessageHelper.sendEphemeralMessageToEventChannel(event, "Some additional items were removed:");
                for (DraftErrataModel i : item.getErrata().getAdditionalComponents()) {
                    DraftItem addl = DraftItem.generate(i.getItemCategory(), i.getItemId());
                    removeFrankenItemFromPlayer(event, player, addl);
                }
            }
            if (item.hasOptionalSwaps()) { // Remove Optional Swaps
                StringBuilder msg = new StringBuilder(
                        "## ⚠️ REMOVED the following optional swaps from their respective categories:");
                for (DraftErrataModel i : item.getErrata().getOptionalSwaps()) {
                    DraftItem addl = DraftItem.generate(i.getItemCategory(), i.getItemId());
                    msg.append("\n> ").append(addl.getTitle(player.getGame()));

                    player.getDraftHand().Contents.remove(addl);
                    if (!player.getDraftHand().Contents.contains(addl)) {
                        // doesn't have the item available from multiple sources
                        removeFrankenItemFromPlayer(event, player, addl);
                        msg.append(" ---> AND removed it from your faction.");
                    }
                }
                MessageHelper.sendEphemeralMessageToEventChannel(event, msg.toString());
            }
        }
    }

    @ButtonHandler("factionEmbedRefresh")
    private static void factionEmbedRefresh(ButtonInteractionEvent event, Player player) {
        Container container = FrankenDraftBagService.getFrankenPlayerSummaryContainer(player);

        MessageV2Editor editor = new MessageV2Editor();
        editor.replace(replaceContainer(container), container);
        if (!editor.applyToMessage(event.getMessage())) {
            MessageV2Builder builder = new MessageV2Builder(event.getMessageChannel());
            builder.append(container);
            builder.send();
        }
    }

    @ButtonHandler("finishedBuilding")
    private static void finishedBuildingFaction(Game game, Player player) {
        String key = "frankenBuilt";
        player.addStoredValue(key, "y");

        Container c = player.getRepresentationContainer();
        MessageV2Builder tabletalk = new MessageV2Builder(game.getTableTalkChannel(), true);
        tabletalk.append(c);
        tabletalk.send();

        FrankenDraftBagService.updateFinishedBuildingMessage(game);
        for (Player p : game.getRealPlayers()) {
            if ("n".equals(p.getStoredValue(key))) return;
        }

        MessageChannel channel = game.isFowMode() ? GMService.getGMChannel(game) : game.getMainGameChannel();
        MessageV2Builder builder = new MessageV2Builder(channel);
        if (game.isTwilightsFallMode()) {
            return;
        }
        builder.append(game.getPing() + " Every player has chosen their components! Press this button to continue.");
        builder.append(Buttons.DEAL_2_SO);
        builder.send();
    }

    private static void applyFrankenItemToPlayer(ButtonInteractionEvent event, Player player, DraftItem item) {
        String alias = item.getAlias();
        boolean alreadyHas = player.getStoredList("appliedFrankenItems").contains(alias);
        player.addToStoredList("appliedFrankenItems", alias);
        if (alreadyHas) return;

        String itemID = item.getItemId();
        switch (item.getItemCategory()) {
            case ABILITY -> FrankenAbilityService.addAbilities(event, player, List.of(itemID));
            case TECH -> FrankenFactionTechService.addFactionTechs(event, player, List.of(itemID));
            case BREAKTHROUGH -> FrankenBreakthroughService.addBreakthrough(event, player, itemID);
            case MAHACTKING -> {
                FactionModel faction = Mapper.getFaction(itemID);
                player.setFaction(itemID);
                List<String> units = List.of(itemID + "_flagship", itemID + "_mech", "tf_warsun");
                FrankenUnitService.addUnits(event, player, units, false);
                FrankenStatsService.setStartingComms(event, player, faction.getCommodities());
            }
            case AGENT, COMMANDER, HERO -> FrankenLeaderService.addLeaders(event, player, List.of(itemID));
            case MECH, FLAGSHIP, UNIT -> FrankenUnitService.addUnits(event, player, List.of(itemID), false);
            case COMMODITIES -> FrankenStatsService.addStartingComms(event, player, item);
            case PN -> FrankenPromissoryService.addPromissoryNotes(event, player.getGame(), player, List.of(itemID));
            case STARTINGTECH -> FrankenStartingTechService.addStartingTech(event, player, itemID);
            case PLOT -> FrankenPlotService.addPlot(event, player, itemID);
            case HOMESYSTEM -> FrankenHomeService.replaceHomeSystem(event, player, item);
            default -> MessageHelper.sendMessageToEventChannel(event, "Can't add: " + item.getItemCategory());
        }
    }

    private static void removeFrankenItemFromPlayer(ButtonInteractionEvent event, Player player, DraftItem item) {
        String alias = item.getAlias();
        player.removeFromStoredList("appliedFrankenItems", alias);
        boolean stillHas = player.getStoredList("appliedFrankenItems").contains(alias);
        if (stillHas) return;

        String itemID = item.getItemId();
        switch (item.getItemCategory()) {
            case ABILITY -> FrankenAbilityService.removeAbilities(event, player, List.of(itemID));
            case TECH -> FrankenFactionTechService.removeFactionTechs(event, player, List.of(itemID));
            case BREAKTHROUGH -> FrankenBreakthroughService.removeBreakthrough(event, player, itemID);
            case AGENT, COMMANDER, HERO -> FrankenLeaderService.removeLeaders(event, player, List.of(itemID));
            case MECH, FLAGSHIP, UNIT -> FrankenUnitService.removeUnits(event, player, List.of(itemID));
            case COMMODITIES -> FrankenStatsService.removeStartingComms(event, player, item);
            case PN -> FrankenPromissoryService.removePromissoryNotes(event, player, List.of(itemID));
            case STARTINGTECH -> FrankenStartingTechService.removeStartingTech(event, player, itemID);
            case PLOT -> FrankenPlotService.removePlot(event, player, itemID);
            case HOMESYSTEM -> FrankenHomeService.removeHomeSystem(event, player, item);
            default -> MessageHelper.sendMessageToEventChannel(event, "Can't remove: " + item.getItemCategory());
        }
    }

    private static boolean titlesMatch(Container c1, Container c2) {
        if (c1.getComponents().isEmpty() || c2.getComponents().isEmpty()) return false;
        ContainerChildComponent child1 = c1.getComponents().getFirst();
        ContainerChildComponent child2 = c2.getComponents().getFirst();

        if (child1 instanceof TextDisplay d1 && child2 instanceof TextDisplay d2) {
            return d1.getContent().equals(d2.getContent());
        } else return child1 instanceof Section && child2 instanceof Section;
    }

    private static Stream<Button> getButtons(ContainerChildComponent child) {
        if (child instanceof ActionRow ar) {
            return ar.getButtons().stream();
        } else if (child instanceof Button b) {
            return Stream.of(b);
        } else {
            return Stream.of();
        }
    }

    private static List<String> getContainerButtonIDs(Container c) {
        return c.getComponents().stream()
                .flatMap(FrankenButtonHandler::getButtons)
                .map(arc -> arc instanceof Button b ? b : null)
                .filter(Objects::nonNull)
                .map(Button::getCustomId)
                .toList();
    }

    private static Predicate<Component> replaceContainer(Container c2) {
        List<String> newButtons = getContainerButtonIDs(c2);
        return comp -> {
            if (comp instanceof Container container) {
                if (!titlesMatch(container, c2)) return false;
                List<String> oldButtons = getContainerButtonIDs(container);
                if (newButtons.size() != oldButtons.size()) {
                    return true;
                }
                if (container.getComponents().getFirst() instanceof Section) {
                    return true;
                }
                for (String id : newButtons) {
                    if (!oldButtons.contains(id)) {
                        return true;
                    }
                    // definitely always replace the faction embed container
                    if (id.equals(Buttons.FACTION_EMBED.getCustomId())) {
                        return true;
                    }
                }
            }
            return false;
        };
    }

    private static void refreshContainers(ButtonInteractionEvent event, Player player) {
        MessageV2Editor editor = new MessageV2Editor();
        List<Color> accents = FrankenDraftBagService.getAccents();
        for (DraftCategory cat : FrankenDraftBagService.componentCategories) {
            Container c2 = FrankenDraftBagService.postDraftCategoryContainer(player, cat);
            if (c2 == null) continue;
            c2 = c2.withAccentColor(accents.getFirst());
            editor.replace(replaceContainer(c2), c2);
        }

        Container summary = FrankenDraftBagService.getFrankenPlayerSummaryContainer(player);
        editor.replace(replaceContainer(summary), summary);

        int limit = FrankenDraftBagService.componentCategories.size() * 2 + 4;
        editor.applyAroundMessage(event.getMessage(), limit, null);
    }

    @ButtonHandler("frankenDraftAction")
    public static void resolveFrankenDraftAction(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String action = buttonID.split(";")[1];
        BagDraft draft = game.getActiveBagDraft();

        if (!action.contains(":")) {
            if (action.startsWith(MantisMapBuildService.ACTION_PREFIX)) {
                MantisMapBuildContext mapBuildContext = FrankenMapBuildContextHelper.createContext(game);
                String outcome = MantisMapBuildService.handleAction(event, mapBuildContext, action);
                DraftButtonService.handleButtonResult(event, outcome);
                return;
            }

            switch (action) {
                case "reset_queue" -> {
                    player.getCurrentDraftBag().Contents.addAll(player.getDraftQueue().Contents);
                    player.resetDraftQueue();
                    FrankenDraftBagService.showPlayerBag(game, player);
                    return;
                }
                case "confirm_draft" -> {
                    player.getDraftHand().Contents.addAll(player.getDraftQueue().Contents);
                    player.resetDraftQueue();
                    draft.setPlayerReadyToPass(player, true);
                    // Clear out all existing messages
                    draft.clearBagChannel(player);

                    MessageHelper.sendMessageToChannel(
                            draft.findExistingBagChannel(player),
                            "Your Draft Bag is ready to pass and you are waiting for the other players to finish drafting.");
                    MessageHelper.sendMessageToChannel(
                            player.getCardsInfoThread(),
                            "You are passing the following cards to your right:\n"
                                    + FrankenDraftBagService.getBagReceipt(player));
                    FrankenDraftBagService.displayPlayerHand(game, player);
                    if (draft.isDraftStageComplete()) {
                        if (draft instanceof InauguralSpliceFrankenDraft) {
                            // The Inaugural Splice is AFTER secret/public objectives, BEFORE the strategy phase, so
                            // need a button to start that now
                            Button startStrategyPhaseButton =
                                    Buttons.green("startOfGameStrategyPhase", "Start Strategy Phase");
                            MessageHelper.sendMessageToChannel(
                                    game.getActionsChannel(),
                                    game.getPing() + ", the inaugural splice is complete!\n\n"
                                            + "Once all players have selected their kept abilities, genome and unit, press this button to start the game.",
                                    List.of(startStrategyPhaseButton));
                            FrankenDraftBagService.applyDraftBags(event, game, false);
                        } else {
                            String draftType = (draft instanceof TwilightsFallFrankenDraft)
                                    ? "Twilight's Fall Draft"
                                    : "FrankenDraft";
                            String msg = game.getPing() + " the draft stage of the " + draftType + " is complete. ";
                            msg += "Use the buttons below to choose how to set up the map. ";
                            msg += "Once the map is finalized, select your components from your drafted hand.";

                            List<Button> buttons = new ArrayList<>();
                            buttons.add(Buttons.green("startFrankenSliceBuild", "Randomize Your Slices (Sorta)"));
                            buttons.add(Buttons.green("startFrankenMantisBuild", "Mantis Build Slices"));
                            MessageHelper.sendMessageToChannel(game.getActionsChannel(), msg, buttons);

                            FrankenDraftBagService.applyDraftBags(event, game);
                        }
                        return;
                    }
                    int passCounter = 0;
                    while (draft.allPlayersReadyToPass()) {
                        if (draft.isDraftStageComplete()) {
                            GameMessageManager.remove(game.getName(), GameMessageType.BAG_DRAFT);
                            if (draft instanceof InauguralSpliceFrankenDraft) {
                                // The Inaugural Splice is AFTER secret/public objectives, BEFORE the strategy phase, so
                                // need a button to start that now
                                Button startStrategyPhaseButton =
                                        Buttons.green("startOfGameStrategyPhase", "Start Strategy Phase");
                                MessageHelper.sendMessageToChannel(
                                        game.getActionsChannel(),
                                        game.getPing() + ", the inaugural splice is complete!\n\n"
                                                + "Once all players have selected their kept abilities, genome and unit, press this button to start the game.",
                                        List.of(startStrategyPhaseButton));
                                FrankenDraftBagService.applyDraftBags(event, game, false);
                            } else {
                                Button randomizeButton =
                                        Buttons.green("startFrankenSliceBuild", "Randomize Your Slices (Sorta)");
                                Button mantisButton = Buttons.green("startFrankenMantisBuild", "Mantis Build Slices");
                                MessageHelper.sendMessageToChannel(
                                        game.getActionsChannel(),
                                        game.getPing()
                                                + " the draft stage of the FrankenDraft is complete. Choose how to set up the map. Once the map is finalized, select your abilities from your drafted hands.",
                                        List.of(randomizeButton, mantisButton));

                                FrankenDraftBagService.applyDraftBags(event, game);
                            }
                            return;
                        }
                        FrankenDraftBagService.passBags(game);
                        passCounter++;
                        if (passCounter > game.getRealPlayers().size() * 2) {
                            MessageHelper.sendMessageToChannel(
                                    game.getActionsChannel(),
                                    game.getPing()
                                            + " an error has occurred where nobody is able to draft any cards, but there are cards still in the bag. Please notify @developer");
                            break;
                        }
                    }
                    return;
                }
                case "show_bag" -> {
                    FrankenDraftBagService.showPlayerBag(game, player);
                    return;
                }
            }
        }
        DraftBag currentBag = player.getCurrentDraftBag();
        DraftItem selectedItem = DraftItem.generateFromAlias(action);

        if (!selectedItem.isDraftable(player)) {
            if (player.getDraftHand().Contents.contains(selectedItem)) {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        "You have already automatically drafted " + selectedItem.getShortDescription()
                                + ". Please wait now until the rest of the players finish drafting.");
                ButtonHelper.deleteAllButtons(event);
                return;
            }
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Something went wrong. You are not allowed to draft " + selectedItem.getShortDescription()
                            + " right now. Please select another item.");
            return;
        }
        currentBag.Contents.removeIf((DraftItem bagItem) -> bagItem.getAlias().equals(action));
        player.queueDraftItem(DraftItem.generateFromAlias(action));

        if (!draft.playerHasDraftableItemInBag(player) && !draft.playerHasItemInQueue(player)) {
            draft.setPlayerReadyToPass(player, true);
        }

        FrankenDraftBagService.showPlayerBag(game, player);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
