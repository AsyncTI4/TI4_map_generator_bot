package ti4.buttons.handlers;

import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.draft.BagDraft;
import ti4.draft.DraftBag;
import ti4.draft.DraftItem;
import ti4.draft.items.CommoditiesDraftItem;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;
import ti4.service.franken.FrankenAbilityService;
import ti4.service.franken.FrankenDraftBagService;
import ti4.service.franken.FrankenFactionTechService;
import ti4.service.franken.FrankenLeaderService;
import ti4.service.franken.FrankenPromissoryService;
import ti4.service.franken.FrankenUnitService;
import ti4.service.player.PlayerStatsService;
import ti4.service.tech.PlayerTechService;

@UtilityClass
class FrankenButtonHandler {

    @ButtonHandler("frankenItemAdd")
    public static void resolveFrankenItemAddButton(ButtonInteractionEvent event, String buttonID, Player player) {
        String frankenItem = buttonID.replace("frankenItemAdd", "");
        DraftItem draftItem = DraftItem.generateFromAlias(frankenItem);

        applyFrankenItemToPlayer(event, draftItem, player);
        event.editButton(draftItem.getRemoveButton()).queue();

        // Handle Errata
        if (draftItem.Errata != null) {
            if (draftItem.Errata.AdditionalComponents != null) { // Auto-add Additional Components
                MessageHelper.sendMessageToEventChannel(event, "Some additional items were added:");
                for (DraftErrataModel i : draftItem.Errata.AdditionalComponents) {
                    DraftItem item = DraftItem.generate(i.ItemCategory, i.ItemId);
                    applyFrankenItemToPlayer(event, item, player);
                }
            }
            if (draftItem.Errata.OptionalSwaps != null) { // Offer Optional Swaps
                for (DraftErrataModel i : draftItem.Errata.OptionalSwaps) {
                    DraftItem item = DraftItem.generate(i.ItemCategory, i.ItemId);
                    Button button = item.getAddButton();
                    String message = "You have the option to swap in the following item:\n" + item.getLongDescription();
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, List.of(button));
                }
            }
        }
    }

    @ButtonHandler("frankenItemRemove")
    public static void resolveFrankenItemRemoveButton(ButtonInteractionEvent event, String buttonID, Player player) {
        String frankenItem = buttonID.replace("frankenItemRemove", "");
        DraftItem draftItem = DraftItem.generateFromAlias(frankenItem);

        removeFrankenItemFromPlayer(event, draftItem, player);
        event.editButton(draftItem.getAddButton()).queue();

        // Handle Errata
        if (draftItem.Errata != null) {
            if (draftItem.Errata.AdditionalComponents != null) { // Auto-add Additional Components
                MessageHelper.sendMessageToEventChannel(event, "Some additional items were added:");
                for (DraftErrataModel i : draftItem.Errata.AdditionalComponents) {
                    DraftItem item = DraftItem.generate(i.ItemCategory, i.ItemId);
                    removeFrankenItemFromPlayer(event, item, player);
                }
            }
            if (draftItem.Errata.OptionalSwaps != null) { // Offer Optional Swaps
                for (DraftErrataModel i : draftItem.Errata.OptionalSwaps) {
                    DraftItem item = DraftItem.generate(i.ItemCategory, i.ItemId);
                    Button button = item.getAddButton();
                    String message = "WARNING! The following items were optional and may or may not have been removed by pressing the parent button:\n" + item.getLongDescription();
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, List.of(button));
                }
            }
        }
    }

    private static void applyFrankenItemToPlayer(GenericInteractionCreateEvent event, DraftItem draftItem, Player player) {
        String itemID = draftItem.ItemId;
        switch (draftItem.ItemCategory) {
            case ABILITY -> FrankenAbilityService.addAbilities(event, player, List.of(itemID));
            case TECH -> FrankenFactionTechService.addFactionTechs(event, player, List.of(itemID));
            case AGENT, COMMANDER, HERO -> FrankenLeaderService.addLeaders(event, player, List.of(itemID));
            case MECH, FLAGSHIP -> FrankenUnitService.addUnits(event, player, List.of(itemID));
            case COMMODITIES -> PlayerStatsService.setTotalCommodities(event, player, (player.getCommoditiesTotal(true) + ((CommoditiesDraftItem) draftItem).getCommodities()));
            case PN -> FrankenPromissoryService.addPromissoryNotes(event, player.getGame(), player, List.of(itemID));
            case STARTINGTECH -> addStartingTech(event, player, itemID);
        }
    }

    private static void removeFrankenItemFromPlayer(GenericInteractionCreateEvent event, DraftItem draftItem, Player player) {
        String itemID = draftItem.ItemId;
        switch (draftItem.ItemCategory) {
            case ABILITY -> FrankenAbilityService.removeAbilities(event, player, List.of(itemID));
            case TECH -> FrankenFactionTechService.removeFactionTechs(event, player, List.of(itemID));
            case AGENT, COMMANDER, HERO -> FrankenLeaderService.removeLeaders(event, player, List.of(itemID));
            case MECH, FLAGSHIP -> FrankenUnitService.removeUnits(event, player, List.of(itemID));
            case COMMODITIES -> PlayerStatsService.setTotalCommodities(event, player, (player.getCommoditiesTotal() - ((CommoditiesDraftItem) draftItem).getCommodities()));
            case PN -> FrankenPromissoryService.removePromissoryNotes(event, player, List.of(itemID));
            case STARTINGTECH -> removeStartingTech(event, player, itemID);
        }
    }

    private static void addStartingTech(GenericInteractionCreateEvent event, Player player, String itemID) {
        FactionModel faction = Mapper.getFaction(itemID);
        List<String> startingTech = faction.getStartingTech();
        if (startingTech == null) return;
        for (String tech : startingTech) {
            PlayerTechService.addTech(event, player.getGame(), player, tech);
        }
    }

    private static void removeStartingTech(GenericInteractionCreateEvent event, Player player, String itemID) {
        FactionModel faction = Mapper.getFaction(itemID);
        List<String> startingTech = faction.getStartingTech();
        if (startingTech == null) return;
        for (String tech : startingTech) {
            PlayerTechService.removeTech(event, player, tech);
        }
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
                    FrankenDraftBagService.showPlayerBag(game, player);
                    return;
                }
                case "confirm_draft" -> {
                    player.getDraftHand().Contents.addAll(player.getDraftQueue().Contents);
                    player.resetDraftQueue();
                    draft.setPlayerReadyToPass(player, true);

                    // Clear out all existing messages
                    draft.findExistingBagChannel(player).getHistory().retrievePast(100).queue(m -> {
                        if (!m.isEmpty()) {
                            draft.findExistingBagChannel(player).deleteMessages(m).queue();
                        }
                    });
                    MessageHelper.sendMessageToChannel(draft.findExistingBagChannel(player), "Your Draft Bag is ready to pass and you are waiting for the other players to finish drafting.");
                    MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "You are passing the following cards to your right:\n" + FrankenDraftBagService.getBagReceipt(player.getCurrentDraftBag()));
                    FrankenDraftBagService.displayPlayerHand(game, player);
                    if (draft.isDraftStageComplete()) {
                        MessageHelper.sendMessageToChannel(game.getActionsChannel(), game.getPing() + " the draft stage of the FrankenDraft is complete. Please select your abilities from your drafted hands.");
                        FrankenDraftBagService.applyDraftBags(event, game);
                        return;
                    }
                    int passCounter = 0;
                    while (draft.allPlayersReadyToPass()) {
                        FrankenDraftBagService.passBags(game);
                        passCounter++;
                        if (passCounter > game.getRealPlayers().size()) {
                            MessageHelper.sendMessageToChannel(game.getActionsChannel(), game.getPing() + " an error has occurred where nobody is able to draft any cards, but there are cards still in the bag. Please notify @developer");
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
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Something went wrong. You are not allowed to draft " + selectedItem.getShortDescription() + " right now. Please select another item.");
            return;
        }
        currentBag.Contents.removeIf((DraftItem bagItem) -> bagItem.getAlias().equals(action));
        player.queueDraftItem(DraftItem.generateFromAlias(action));

        if (!draft.playerHasDraftableItemInBag(player) && !draft.playerHasItemInQueue(player)) {
            draft.setPlayerReadyToPass(player, true);
        }

        FrankenDraftBagService.showPlayerBag(game, player);
        event.getMessage().delete().queue();
    }
}
