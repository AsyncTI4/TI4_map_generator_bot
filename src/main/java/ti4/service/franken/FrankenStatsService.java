package ti4.service.franken;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.draft.DraftItem;
import ti4.draft.items.CommoditiesDraftItem;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.MiscEmojis;

@UtilityClass
public class FrankenStatsService {

    public void setStartingComms(GenericInteractionCreateEvent event, Player player, int comms) {
        String message = "> Set base commodity to " + comms + " " + MiscEmojis.comm + ".";
        player.setCommoditiesBase(comms);
        MessageHelper.sendEphemeralMessageToEventChannel(event, message);
    }

    public void addStartingComms(GenericInteractionCreateEvent event, Player player, DraftItem item) {
        if (item instanceof CommoditiesDraftItem commItem) {
            addStartingComms(event, player, commItem.getCommodities());
        }
    }

    public void removeStartingComms(GenericInteractionCreateEvent event, Player player, DraftItem item) {
        if (item instanceof CommoditiesDraftItem commItem) {
            removeStartingComms(event, player, commItem.getCommodities());
        }
    }

    public void addStartingComms(GenericInteractionCreateEvent event, Player player, int comms) {
        int totalComms = player.getCommoditiesTotal() + comms;

        String message = "> Set base commodity to " + totalComms + " " + MiscEmojis.comm + ".";
        player.setCommoditiesBase(Math.max(0, totalComms));
        MessageHelper.sendEphemeralMessageToEventChannel(event, message);
    }

    public void removeStartingComms(GenericInteractionCreateEvent event, Player player, int comms) {
        int totalComms = player.getCommoditiesTotal() - comms;

        String message = "> Set base commodity to " + totalComms + " " + MiscEmojis.comm + ".";
        player.setCommoditiesBase(Math.max(0, totalComms));
        MessageHelper.sendEphemeralMessageToEventChannel(event, message);
    }
}
