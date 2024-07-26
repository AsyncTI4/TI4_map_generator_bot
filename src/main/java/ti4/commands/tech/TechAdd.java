package ti4.commands.tech;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class TechAdd extends TechAddRemove {
    public TechAdd() {
        super(Constants.TECH_ADD, "Add Technology");
    }

    @Override
    public void doAction(Player player, String techID, SlashCommandInteractionEvent event) {
        addTech(event, getActiveGame(), player, techID);
    }

    public static void addTech(GenericInteractionCreateEvent event, Game game, Player player, String techID) {
        player.addTech(techID);
        ButtonHelperCommanders.resolveNekroCommanderCheck(player, techID, game);
        String message = player.getRepresentation() + " added technology: " + Mapper.getTech(techID).getRepresentation(false);
        if ("iihq".equalsIgnoreCase(AliasHandler.resolveTech(techID))) {
            message = message + "\n Automatically added the Custodia Vigilia planet.";
        }
        if (player.getLeaderIDs().contains("mirvedacommander") && !player.hasLeaderUnlocked("mirvedacommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "mirveda", event);
        }
        if (player.getLeaderIDs().contains("jolnarcommander") && !player.hasLeaderUnlocked("jolnarcommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "jolnar", event);
        }
        if (player.getLeaderIDs().contains("nekrocommander") && !player.hasLeaderUnlocked("nekrocommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "nekro", event);
        }
        if (player.getLeaderIDs().contains("dihmohncommander") && !player.hasLeaderUnlocked("dihmohncommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "dihmohn", event);
        }
        MessageHelper.sendMessageToEventChannel(event, message);
    }
}
