package ti4.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.GenerateMap;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

import java.util.*;

public class ListVoteCount extends StatusSubcommandData {
    public ListVoteCount() {
        super(Constants.VOTE_COUNT, "List Vote count for agenda");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map map = getActiveMap();
        turnOrder(event, map);
    }

    public static void turnOrder(SlashCommandInteractionEvent event, Map map) {
        StringBuilder msg = new StringBuilder();
        int i = 1;
        List<Player> orderList = map.getPlayers().values().stream().toList();
        String speakerName = map.getSpeaker();
        Optional<Player> optSpeaker = orderList.stream().filter(player -> player.getUserID().equals(speakerName))
                .findFirst();

        if (optSpeaker.isPresent()) {
            int rotationDistance = orderList.size() - orderList.indexOf(optSpeaker.get()) - 1;
            Collections.rotate(orderList, rotationDistance);
        }

        for (Player player : orderList) {
            List<String> planets = new ArrayList<>(player.getPlanets());
            planets.removeAll(player.getExhaustedPlanets());
            String userName = player.getUserName();
            String color = player.getColor();

            String text = "";
            text += Helper.getFactionIconFromDiscord(player.getFaction());
            text += " " + userName;
            if (color != null) {
                text += " (" + color + ")";
            }
            HashMap<String, UnitHolder> planetsInfo = map.getPlanetsInfo();
            int influenceCount = planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
                    .map(planet -> (Planet) planet).mapToInt(Planet::getInfluence).sum();
            text += " vote Count: **" + influenceCount + "**";
            if (player.getUserID().equals(speakerName)) {
                text += " <:Speakertoken:965363466821050389> ";
            }
            msg.append(i).append(". ").append(text).append("\n");
            i++;
        }
        MessageHelper.replyToMessage(event, msg.toString());
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        // We reply in execute command
    }
}
