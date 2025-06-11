package ti4.commands.relic;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

class RelicAddCodexRelics extends GameStateSubcommand {

    public RelicAddCodexRelics() {
        super(Constants.ADD_CODEX_RELICS, "Add the three codex 4 relics into the deck and shuffle", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        List<String> allRelics = game.getAllRelics();
        if (!allRelics.contains("bookoflatvinia")) {
            game.shuffleRelicBack("bookoflatvinia");
            MessageHelper.sendMessageToEventChannel(event, "Relic Book of Latvinia added into deck");
        }
        if (!allRelics.contains("circletofthevoid")) {
            game.shuffleRelicBack("circletofthevoid");
            MessageHelper.sendMessageToEventChannel(event, "Relic Circlet Of The Void added into deck");
        }
        if (!allRelics.contains("neuraloop")) {
            game.shuffleRelicBack("neuraloop");
            MessageHelper.sendMessageToEventChannel(event, "Relic Neuraloop added into deck");
        }
    }
}
