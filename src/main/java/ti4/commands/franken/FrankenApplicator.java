package ti4.commands.franken;

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
            case ABILITY -> new AbilityDraftItem(itemID);
            case TECH -> new TechDraftItem(itemID);
            case AGENT, COMMANDER, HERO -> new AgentDraftItem(itemID);
            case MECH, FLAGSHIP -> new MechDraftItem(itemID);
            case COMMODITIES -> Stats.setTotalCommodities(event, player, ((CommoditiesDraftItem) draftItem).getCommodities());
            case PN -> new PNDraftItem(itemID);
            case HOMESYSTEM -> new HomeSystemDraftItem(itemID);
            case STARTINGTECH -> new StartingTechDraftItem(itemID);
            case STARTINGFLEET -> new StartingFleetDraftItem(itemID);
            case BLUETILE -> new BlueTileDraftItem(itemID);
            case REDTILE -> new RedTileDraftItem(itemID);
            case DRAFTORDER -> new SpeakerOrderDraftItem(itemID);
        }
        DraftErrataModel errata = Mapper.getFrankenErrata().get(draftItem.getAlias());
        if (errata != null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Errata: " + errata.getAlias());
        }
    }
}
