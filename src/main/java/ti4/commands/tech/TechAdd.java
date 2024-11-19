package ti4.commands.tech;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.leader.CommanderUnlockCheckService;

public class TechAdd extends TechAddRemove {
    public TechAdd() {
        super(Constants.TECH_ADD, "Add Tech");
    }

    @Override
    public void doAction(Player player, String techID, SlashCommandInteractionEvent event) {
        addTech(event, getActiveGame(), player, techID);
    }

    public static void addTech(GenericInteractionCreateEvent event, Game game, Player player, String techID) {
        player.addTech(techID);
        ButtonHelperCommanders.resolveNekroCommanderCheck(player, techID, game);
        String message = player.getRepresentation() + " added tech: " + Mapper.getTech(techID).getRepresentation(false);
        if ("iihq".equalsIgnoreCase(AliasHandler.resolveTech(techID))) {
            message = message + "\n Automatically added the Custodia Vigilia planet";
        }
        CommanderUnlockCheckService.checkPlayer(player, "mirveda", "jolnar", "nekro", "dihmohn");
        MessageHelper.sendMessageToEventChannel(event, message);
    }
}
