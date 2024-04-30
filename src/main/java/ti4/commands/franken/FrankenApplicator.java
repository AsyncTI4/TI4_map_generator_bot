package ti4.commands.franken;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.commands.player.Stats;
import ti4.draft.DraftItem;
import ti4.draft.items.*;
import ti4.generator.Mapper;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.DraftErrataModel;

public class FrankenApplicator {

    public static void applyFrankenItemIDToPlayer(GenericInteractionCreateEvent event, String itemID, Player player) {
        DraftItem draftItem = DraftItem.GenerateFromAlias(itemID);
        if (draftItem == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Cannot apply Franken Item: `" + itemID + "` does not exist.");
            return;
        }
        applyFrankenItemToPlayer(event, draftItem, player);
    }

    public static void applyFrankenItemToPlayer(GenericInteractionCreateEvent event, DraftItem draftItem, Player player) {
        String itemID = draftItem.ItemId;
        switch (draftItem.ItemCategory) {
            case ABILITY -> AbilityAdd.addAbilities(event, player, List.of(itemID));
            case TECH -> FactionTechAdd.addFactionTechs(event, player, List.of(itemID));
            case AGENT, COMMANDER, HERO -> LeaderAdd.addLeaders(event, player, List.of(itemID));
            case MECH, FLAGSHIP -> sendNotImplementedMessage(event);
            case COMMODITIES -> Stats.setTotalCommodities(event, player, ((CommoditiesDraftItem) draftItem).getCommodities());
            case PN -> sendNotImplementedMessage(event);
            case HOMESYSTEM -> sendNotImplementedMessage(event);
            case STARTINGTECH -> sendNotImplementedMessage(event);
            case STARTINGFLEET -> sendNotImplementedMessage(event);
        }
        DraftErrataModel errata = Mapper.getFrankenErrata().get(draftItem.getAlias());
        if (errata != null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Errata: " + errata.getAlias());
        }
    }

    public static void sendNotImplementedMessage(GenericInteractionCreateEvent event) {
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Applying this item via button is not yet implemented - please use `/franken` commands");
    }
}
