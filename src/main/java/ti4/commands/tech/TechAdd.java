package ti4.commands.tech;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelperFactionSpecific;
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
        ButtonHelperFactionSpecific.resolveNekroCommanderCheck(player, techID, getActiveGame());
        String message = Helper.getPlayerRepresentation(player, getActiveGame()) + " added tech: " + Helper.getTechRepresentation(techID);
        if("iihq".equalsIgnoreCase(AliasHandler.resolveTech(techID))){
            message = message + "\n Automatically added the Custodia Vigilia planet";
        }
        sendMessage(message);
    }
}
