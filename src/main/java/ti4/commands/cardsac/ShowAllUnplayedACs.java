package ti4.commands.cardsac;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ShowAllUnplayedACs extends ACCardsSubcommandData {
    public ShowAllUnplayedACs() {
        super(Constants.SHOW_UNPLAYED_AC, "Show all unplayed Action Cards");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        showUnplayedACs(getActiveGame(), event);
    }

    @ButtonHandler("showDeck_unplayedAC")
    public static void showUnplayedACs(Game game, GenericInteractionCreateEvent event) {
        List<String> unplayedACs = new ArrayList<>(game.getActionCards());
        for (Player player : game.getRealPlayers()) {
            unplayedACs.addAll(player.getActionCards().keySet());
        }
        List<String> unplayedACNames = new ArrayList<>();
        for (String acID : unplayedACs) {
            unplayedACNames.add(Mapper.getActionCard(acID).getName());
        }
        Collections.sort(unplayedACNames);
        Map<String, Integer> namesNValues = new HashMap<>();
        for (String acName : unplayedACNames) {
            if (namesNValues.containsKey(acName)) {
                namesNValues.put(acName, namesNValues.get(acName) + 1);
            } else {
                namesNValues.put(acName, 1);
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Game: ").append(game.getName()).append("\n");
        sb.append("Unplayed Action Cards: ").append("\n");
        int x = 1;
        List<String> sortedNamed = new ArrayList<>();
        sortedNamed.addAll(namesNValues.keySet());
        Collections.sort(sortedNamed);
        for (String id : sortedNamed) {
            sb.append(x).append(". " + id + " x" + namesNValues.get(id) + "\n");
            x++;
        }

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
    }
}
