package ti4.commands.franken;

import java.util.List;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands.player.Stats;
import ti4.commands.tech.TechAdd;
import ti4.draft.DraftItem;
import ti4.draft.items.CommoditiesDraftItem;
import ti4.generator.Mapper;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;

public class FrankenApplicator {

    public static void resolveFrankenItemAddButton(ButtonInteractionEvent event, String buttonID, Player player) {
        String frankenItem = buttonID.replace("frankenItemAdd", "");
        DraftItem draftItem = DraftItem.GenerateFromAlias(frankenItem);
        if (draftItem == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Cannot apply Franken Item: `" + frankenItem + "` does not exist.");
            return;
        }

        int categoryLimit = player.getGame().getActiveBagDraft().getItemLimitForCategory(draftItem.ItemCategory);

        FrankenApplicator.applyFrankenItemToPlayer(event, draftItem, player);
        event.editButton(draftItem.getRemoveButton()).queue();

        // Handle Errata
        if (draftItem.Errata != null) {
            if (draftItem.Errata.AdditionalComponents != null) { // Auto-add Additional Components
                MessageHelper.sendMessageToEventChannel(event, "Some additional items were added:");
                for (DraftErrataModel i : draftItem.Errata.AdditionalComponents) {
                    DraftItem item = DraftItem.Generate(i.ItemCategory, i.ItemId);
                    applyFrankenItemToPlayer(event, item, player);
                }
            }
            if (draftItem.Errata.OptionalSwaps != null) { // Offer Optional Swaps
                for (DraftErrataModel i : draftItem.Errata.OptionalSwaps) {
                    DraftItem item = DraftItem.Generate(i.ItemCategory, i.ItemId);
                    Button button = item.getAddButton().withEmoji(Emoji.fromFormatted(item.getItemEmoji()));
                    String message = "You have the option to swap in the following item:\n" + item.getLongDescription();
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, List.of(button));
                }
            }
        }
    }

    public static void resolveFrankenItemRemoveButton(ButtonInteractionEvent event, String buttonID, Player player) {
        String frankenItem = buttonID.replace("frankenItemRemove", "");
        DraftItem draftItem = DraftItem.GenerateFromAlias(frankenItem);
        if (draftItem == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Cannot apply Franken Item: `" + frankenItem + "` does not exist.");
            return;
        }

        //remove the thing
        MessageHelper.sendMessageToChannel(event.getChannel(), "Removal of `" + frankenItem + "` via button is not supported yet. Please use `/franken *remove` commands for now.");

        event.editButton(draftItem.getAddButton()).queue();
    }

    private static void applyFrankenItemToPlayer(GenericInteractionCreateEvent event, DraftItem draftItem, Player player) {
        String itemID = draftItem.ItemId;
        switch (draftItem.ItemCategory) {
            case ABILITY -> AbilityAdd.addAbilities(event, player, List.of(itemID));
            case TECH -> FactionTechAdd.addFactionTechs(event, player, List.of(itemID));
            case AGENT, COMMANDER, HERO -> LeaderAdd.addLeaders(event, player, List.of(itemID));
            case MECH, FLAGSHIP -> sendNotImplementedMessage(event, draftItem.getAlias());
            case COMMODITIES -> Stats.setTotalCommodities(event, player, ((CommoditiesDraftItem) draftItem).getCommodities());
            case PN -> sendNotImplementedMessage(event, draftItem.getAlias());
            case STARTINGTECH -> addStartingTech(event, player, itemID);
        }
        DraftErrataModel errata = Mapper.getFrankenErrata().get(draftItem.getAlias());
        if (errata != null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Errata: " + errata.getAlias());
        }
    }

    private static void addStartingTech(GenericInteractionCreateEvent event, Player player, String itemID) {
        FactionModel faction = Mapper.getFaction(itemID);
        List<String> startingTech = faction.getStartingTech();
        for (String tech : startingTech) {
            TechAdd.addTech(event, player.getGame(), player, tech);
        }
    }

    private static void sendNotImplementedMessage(GenericInteractionCreateEvent event, String draftItemID) {
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Applying this item: `" + draftItemID + "` via button is not yet implemented - please use `/franken` commands");
    }
}
