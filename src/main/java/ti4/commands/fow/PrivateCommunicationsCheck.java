package ti4.commands.fow;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.fow.FowCommunicationThreadService;

class PrivateCommunicationsCheck extends GameStateSubcommand {

    public PrivateCommunicationsCheck() {
        super(Constants.CHECK_PRIVATE_COMMUNICATIONS, "Check the status of private communication threads and offer a button to suggest new ones.", false, true);
        addOption(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color to which check as", false, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!FowCommunicationThreadService.isActive(getGame())) {
            MessageHelper.replyToMessage(event, "Bot managed communication threads are not enabled.\nEnable them with `/fow fow_options`");
            return;
        }

        Player player = getPlayer();
        List<Button> buttons = new ArrayList<>();
        FowCommunicationThreadService.checkCommThreadsAndNewNeighbors(getGame(), player, buttons);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), "Communication threads with neighbors checked."
            + (buttons.isEmpty() ? " No new neighbors found." : ""), buttons);
    }
}