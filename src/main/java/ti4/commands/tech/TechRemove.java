package ti4.commands.tech;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class TechRemove extends TechAddRemove {

    public TechRemove() {
        super(Constants.TECH_REMOVE, "Remove Tech");
    }

    @Override
    public void doAction(Player player, String techID, SlashCommandInteractionEvent event) {
        removeTech(event, player, techID);
    }

    public static void removeTech(GenericInteractionCreateEvent event, Player player, String techID) {
        player.removeTech(techID);
        MessageHelper.sendMessageToEventChannel(event, player.getRepresentation() + " removed tech: " + Mapper.getTech(techID).getRepresentation(false));
    }
}
