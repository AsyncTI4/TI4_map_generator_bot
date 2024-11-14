package ti4.commands.franken;

import java.util.List;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands.player.Stats;
import ti4.commands.tech.TechAdd;
import ti4.commands.tech.TechRemove;
import ti4.draft.DraftItem;
import ti4.draft.items.CommoditiesDraftItem;
import ti4.generator.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;

public class FrankenApplicator {

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
                    Button button = item.getAddButton().withEmoji(Emoji.fromFormatted(item.getItemEmoji()));
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
                    Button button = item.getAddButton().withEmoji(Emoji.fromFormatted(item.getItemEmoji()));
                    String message = "WARNING! The following items were optional and may or may not have been removed by pressing the parent button:\n" + item.getLongDescription();
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, List.of(button));
                }
            }
        }
    }

    private static void applyFrankenItemToPlayer(GenericInteractionCreateEvent event, DraftItem draftItem, Player player) {
        String itemID = draftItem.ItemId;
        switch (draftItem.ItemCategory) {
            case ABILITY -> AbilityAdd.addAbilities(event, player, List.of(itemID));
            case TECH -> FactionTechAdd.addFactionTechs(event, player, List.of(itemID));
            case AGENT, COMMANDER, HERO -> LeaderAdd.addLeaders(event, player, List.of(itemID));
            case MECH, FLAGSHIP -> UnitAdd.addUnits(event, player, List.of(itemID));
            case COMMODITIES -> Stats.setTotalCommodities(event, player, (player.getCommoditiesTotal() + ((CommoditiesDraftItem) draftItem).getCommodities()));
            case PN -> PNAdd.addPromissoryNotes(event, player.getGame(), player, List.of(itemID));
            case STARTINGTECH -> addStartingTech(event, player, itemID);
            default -> {
            }
        }
    }

    private static void removeFrankenItemFromPlayer(GenericInteractionCreateEvent event, DraftItem draftItem, Player player) {
        String itemID = draftItem.ItemId;
        switch (draftItem.ItemCategory) {
            case ABILITY -> AbilityRemove.removeAbilities(event, player, List.of(itemID));
            case TECH -> FactionTechRemove.removeFactionTechs(event, player, List.of(itemID));
            case AGENT, COMMANDER, HERO -> LeaderRemove.removeLeaders(event, player, List.of(itemID));
            case MECH, FLAGSHIP -> UnitRemove.removeUnits(event, player, List.of(itemID));
            case COMMODITIES -> Stats.setTotalCommodities(event, player, (player.getCommoditiesTotal() - ((CommoditiesDraftItem) draftItem).getCommodities()));
            case PN -> PNRemove.removePromissoryNotes(event, player, List.of(itemID));
            case STARTINGTECH -> removeStartingTech(event, player, itemID);
            default -> {
            }
        }
    }

    private static void addStartingTech(GenericInteractionCreateEvent event, Player player, String itemID) {
        FactionModel faction = Mapper.getFaction(itemID);
        List<String> startingTech = faction.getStartingTech();
        if (startingTech == null) return;
        for (String tech : startingTech) {
            TechAdd.addTech(event, player.getGame(), player, tech);
        }
    }

    private static void removeStartingTech(GenericInteractionCreateEvent event, Player player, String itemID) {
        FactionModel faction = Mapper.getFaction(itemID);
        List<String> startingTech = faction.getStartingTech();
        if (startingTech == null) return;
        for (String tech : startingTech) {
            TechRemove.removeTech(event, player, tech);
        }
    }
}
