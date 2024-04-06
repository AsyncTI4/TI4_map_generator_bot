package ti4.commands.player;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class Pass extends PlayerSubcommandData {
    public Pass() {
        super(Constants.PASS, "Pass");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        if (player == null) {
            sendMessage("You're not a player of this game");
            return;
        }

        if (!activeGame.getPlayedSCs().containsAll(player.getSCs())) {
            sendMessage("You have not played your strategy cards, you cannot pass.");
            return;
        }
        player.setPassed(true);
        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "olradincommander")) {
            ButtonHelperCommanders.olradinCommanderStep1(player, activeGame);
        }
        String text = player.getRepresentation() + " PASSED";
        sendMessage(text);
        if (player.hasTech("absol_aida")) {
            String msg = player.getRepresentation() + " since you have absol AIDEV, you can research 1 Unit Upgrade here for 6 influence";
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
            if (!player.hasAbility("propagation")) {
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                    player.getRepresentation(true, true) + " you can use the button to get your tech",
                    List.of(Buttons.GET_A_TECH));
            } else {
                List<Button> buttons = ButtonHelper.getGainCCButtons(player);
                String message2 = player.getRepresentation() + "! Your current CCs are " + player.getCCRepresentation()
                    + ". Use buttons to gain CCs";
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
                activeGame.setCurrentReacts("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
            }
        }
        if (player.hasAbility("deliberate_action") && (player.getTacticalCC() == 0 || player.getStrategicCC() == 0 || player.getFleetCC() == 0)) {
            String msg = player.getRepresentation()
                + " since you have deliberate action ability and passed while one of your pools was at 0, you can gain a CC to that pool";
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
            List<Button> buttons = ButtonHelper.getGainCCButtons(player);
            String message2 = player.getRepresentation() + "! Your current CCs are " + player.getCCRepresentation()
                + ". Use buttons to gain CCs";
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message2, buttons);
        }
        TurnEnd.pingNextPlayer(event, activeGame, player, true);
    }
}
