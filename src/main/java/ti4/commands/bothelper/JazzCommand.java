package ti4.commands.bothelper;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.AsyncTI4DiscordBot;
//import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;

import java.util.*;

public class JazzCommand extends BothelperSubcommandData {
    public JazzCommand() {
        super("jazz_command", "jazzxhands");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!"228999251328368640".equals(event.getUser().getId())) {
            String jazz = AsyncTI4DiscordBot.jda.getUserById("228999251328368640").getAsMention();
            if ("150809002974904321".equals(event.getUser().getId())) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You are not " + jazz + ", but you are an honorary jazz so you may proceed");
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You are not " + jazz);
                return;
            }
        }

        List<String> colorsToCheck = List.of("gray", "black", "blue", "green", "orange", "pink", "purple", "red", "yellow", "petrol", "brown", "tan", "forest", "chrome", "sunset", "turquoise", "gold",
            "lightgray", "teal", "bloodred", "emerald", "navy", "rose", "lime", "lavender", "spring", "chocolate", "rainbow", "ethereal", "orca", "splitred", "splitblue", "splitgreen", "splitpurple",
            "splitorange", "splityellow", "splitpink", "splitgold", "splitlime", "splittan", "splitteal", "splitturquoise", "splitbloodred", "splitchocolate", "splitemerald", "splitnavy",
            "splitpetrol", "splitrainbow");

        StringBuilder sb2 = new StringBuilder("\t");
        for (String c : colorsToCheck) {
            sb2.append(c).append("\t");
        }
        sb2.append("\n");

        Map<String, Map<String, Double>> contrastMap = new HashMap<>();
        for (int i = 0; i < colorsToCheck.size(); i++) {
            String c1 = colorsToCheck.get(i);
            sb2.append(c1);

            for (int j = 0; j < colorsToCheck.size(); j++) {
                String c2 = colorsToCheck.get(j);

                double contrast = ButtonHelper.colorContrast(c1, c2);
                sb2.append(String.format("\t%f", contrast));

                if (contrastMap.get(c1) == null) {
                    contrastMap.put(c1, new HashMap<>());
                }
                contrastMap.get(c1).put(c2, contrast);
            }
            sb2.append("\n");
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), sb2.toString());
    }

    public static String numToEmoji(int n) {
        return switch (n) {
            case 0 -> "0️⃣";
            case 1 -> "1️⃣";
            case 2 -> "2️⃣";
            case 3 -> "3️⃣";
            case 4 -> "4️⃣";
            case 5 -> "5️⃣";
            case 6 -> "6️⃣";
            case 7 -> "7️⃣";
            case 8 -> "8️⃣";
            case 9 -> "9️⃣";
            case 10 -> "🔟";
            case 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21 -> "✅";
            default -> "❌";
        };
    }
}