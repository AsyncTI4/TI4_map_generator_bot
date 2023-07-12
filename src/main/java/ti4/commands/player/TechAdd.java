package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Player;

public class TechAdd extends TechAddRemove {
    public TechAdd() {
        super(Constants.TECH_ADD, "Add Tech");
    }

    @Override
    public void doAction(Player player, String techID, SlashCommandInteractionEvent event) {
        player.addTech(techID);
        String message = Helper.getPlayerRepresentation(player, getActiveMap()) + " added tech: " + Helper.getTechRepresentation(techID);
        if(AliasHandler.resolveTech(techID).equalsIgnoreCase("iihq")){
            message = message + "\n Automatically added the Custodia Vigilia planet";
        }
        sendMessage(message);
    }
}
