package ti4.commands.cards;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.LinkedHashMap;

public class DrawSO extends CardsSubcommandData {
    public DrawSO() {
        super(Constants.DRAW_SO, "Draw Secret Objective");
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Count of how many to draw, default 1"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
        OptionMapping option = event.getOption(Constants.COUNT);
        int count = 1;
        if (option != null){
            int providedCount = option.getAsInt();
            count = providedCount > 0 ? providedCount : 1;
        }
        LinkedHashMap<String, Integer> secretObjectives = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            secretObjectives = activeMap.drawSecretObjective(player.getUserID());
        }
        sentSecretObjectivesToUser(event, activeMap, secretObjectives);
    }

    public static void sentSecretObjectivesToUser(SlashCommandInteractionEvent event, Map activeMap, LinkedHashMap<String, Integer> secretObjectives) {
        StringBuilder sb = new StringBuilder();
        sb.append("Game: ").append(activeMap.getName()).append("\n");
        sb.append("Secret Objectives:").append("\n");
        int index = 1;
        if (secretObjectives != null) {
            for (java.util.Map.Entry<String, Integer> so : secretObjectives.entrySet()) {
                sb.append(index).append(". (").append(so.getValue()).append(") - ").append(Mapper.getSecretObjective(so.getKey())).append("\n");
                index++;
            }
        }
        MessageHelper.sentToMessageToUser(event, sb.toString());
    }
}
