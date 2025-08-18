package ti4.commands.relic;

import java.util.List;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

class RelicAddCodexRelics extends GameStateSubcommand {

    private static final Pattern AND_PATTERN = Pattern.compile(" and ");

    RelicAddCodexRelics() {
        super(Constants.ADD_CODEX_RELICS, "Add the three codex 4 relics into the deck and shuffle", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String newRelics = "";
        int relicCount = 0;
        List<String> allRelics = game.getAllRelics();
        if (!allRelics.contains("bookoflatvinia")) {
            game.shuffleRelicBack("bookoflatvinia");
            newRelics += "_Book of Latvinia_";
            relicCount++;
        }
        if (!allRelics.contains("circletofthevoid")) {
            game.shuffleRelicBack("circletofthevoid");
            newRelics += (relicCount > 0 ? " and " : "") + "_Circlet Of The Void_";
            relicCount++;
        }
        if (!allRelics.contains("neuraloop")) {
            game.shuffleRelicBack("neuraloop");
            newRelics += (relicCount > 0 ? " and " : "") + "_Neuraloop_";
            relicCount++;
        }
        if (relicCount == 0) {
            MessageHelper.sendMessageToEventChannel(event, "No new relics have been added.");
        } else {
            MessageHelper.sendMessageToEventChannel(
                    event,
                    (relicCount == 2
                                    ? newRelics
                                    : AND_PATTERN.matcher(newRelics).replaceFirst(", "))
                            + (relicCount == 1 ? "has" : "have") + " been shuffled into the relic deck.");
        }
    }
}
