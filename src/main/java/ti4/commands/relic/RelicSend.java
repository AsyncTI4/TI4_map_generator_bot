package ti4.commands.relic;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.CommandHelper;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.relic.SendRelicService;

class RelicSend extends GameStateSubcommand {

    public RelicSend() {
        super(Constants.RELIC_SEND, "Send a relic to another Player", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.RELIC, "Relic to send from Target to Source")
                .setAutoComplete(true)
                .setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Target Faction or Color")
                .setAutoComplete(true)
                .setRequired(true));
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Source Faction or Color (default is you)")
                        .setAutoComplete(true));
    }

    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player1 = getPlayer();
        Player player2 = CommandHelper.getOtherPlayerFromEvent(game, event);
        if (player2 == null) {
            MessageHelper.replyToMessage(event, "Unable to determine who the target player is.");
            return;
        }

        if (player1.equals(player2)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "The two players provided are the same player");
            return;
        }

        String relicID = event.getOption(Constants.RELIC, null, OptionMapping::getAsString);
        List<String> player1Relics = player1.getRelics();
        if (!player1Relics.contains(relicID)) {
            MessageHelper.sendMessageToEventChannel(event, player1.getUserName() + " does not have relic: " + relicID);
            return;
        }

        SendRelicService.handleSendRelic(event, game, player1, player2, relicID);
    }
}
