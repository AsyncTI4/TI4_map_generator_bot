package ti4.commands.tech;

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
        super(Constants.TECH_ADD, "Add Tech");
    }

    @Override
    public void doAction(Player player, String techID, SlashCommandInteractionEvent event) {
        player.addTech(techID);
        ButtonHelperCommanders.resolveNekroCommanderCheck(player, techID, getActiveGame());
        Game activeGame = getActiveGame();
        String message = player.getRepresentation() + " added tech: " + Mapper.getTech(techID).getRepresentation(false);
        if ("iihq".equalsIgnoreCase(AliasHandler.resolveTech(techID))) {
            message = message + "\n Automatically added the Custodia Vigilia planet";
        }
        if (player.getLeaderIDs().contains("mirvedacommander") && !player.hasLeaderUnlocked("mirvedacommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "mirveda", event);
        }
        if (player.getLeaderIDs().contains("jolnarcommander") && !player.hasLeaderUnlocked("jolnarcommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "jolnar", event);
        }
        if (player.getLeaderIDs().contains("nekrocommander") && !player.hasLeaderUnlocked("nekrocommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "nekro", event);
        }
        if (player.getLeaderIDs().contains("dihmohncommander") && !player.hasLeaderUnlocked("dihmohncommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "dihmohn", event);
        }
        MessageHelper.sendMessageToEventChannel(event, message);
    }
}
