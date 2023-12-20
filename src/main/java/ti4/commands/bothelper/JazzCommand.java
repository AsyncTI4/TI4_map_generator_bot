package ti4.commands.bothelper;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.AsyncTI4DiscordBot;
//import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Emojis;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.Map.Entry;

public class JazzCommand extends BothelperSubcommandData {
    public JazzCommand() {
        super("jazz_command", "jazzxhands");
        // addOptions(new OptionData(OptionType.INTEGER, "num_dice", "description", true).setRequiredRange(0, 1000));
        // addOptions(new OptionData(OptionType.INTEGER, "threshold", "description", true).setRequiredRange(1, 10));
        // addOptions(new OptionData(OptionType.INTEGER, "num_dice_2", "description", true).setRequiredRange(0, 1000));
        // addOptions(new OptionData(OptionType.INTEGER, "threshold_2", "description", true).setRequiredRange(1, 10));
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

        // List<String> colorsToCheck = List.of("black", "blue", "green", "gray", "orange", "pink", "purple", "red", "yellow", "petrol", "brown", "tan", "forest", "chrome", "sunset", "turquoise", "gold",
        //     "lightgray", "bloodred", "chocolate", "teal", "emerald", "navy", "lime", "lavender", "rose", "spring", "rainbow", "orca", "ethereal");
        List<String> colorsToCheck = List.of("gray", "black", "blue", "green", "orange", "pink", "purple", "red", "yellow", "petrol", "brown", "tan", "forest", "chrome", "sunset", "turquoise", "gold",
            "lightgray", "teal", "bloodred", "emerald", "navy", "rose", "lime", "lavender", "spring", "chocolate", "rainbow", "ethereal", "orca", "splitred", "splitblue", "splitgreen", "splitpurple",
            "splitorange", "splityellow", "splitpink", "splitgold", "splitlime", "splittan", "splitteal", "splitturquoise", "splitbloodred", "splitchocolate", "splitemerald", "splitnavy",
            "splitpetrol", "splitrainbow");

        //StringBuilder sb = new StringBuilder("# Big Table of Colors\n> ⬜");
        StringBuilder sb2 = new StringBuilder("\t");
        for (String c : colorsToCheck) {
            //sb.append(" ").append(Emojis.getColorEmoji(c));
            sb2.append(c).append("\t");
        }
        //sb.append("\n");
        sb2.append("\n");

        Map<String, Map<String, Double>> contrastMap = new HashMap<>();
        for (int i = 0; i < colorsToCheck.size(); i++) {
            String c1 = colorsToCheck.get(i);
            // sb.append("> ").append(Emojis.getColorEmoji(c1));
            sb2.append(c1);

            for (int j = 0; j < colorsToCheck.size(); j++) {
                String c2 = colorsToCheck.get(j);

                double contrast = ButtonHelper.colorContrast(c1, c2);
                // sb.append(" ").append(numToEmoji((int) Math.round(Math.floor(contrast))));
                sb2.append(String.format("\t%f", contrast));

                if (contrastMap.get(c1) == null) {
                    contrastMap.put(c1, new HashMap<>());
                }
                contrastMap.get(c1).put(c2, contrast);
            }
            // sb.append("\n");
            sb2.append("\n");
            // if (i % 5 == 0) {
            //     MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
            //     sb = new StringBuilder();
            // }
        }
        // MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
        MessageHelper.sendMessageToChannel(event.getChannel(), sb2.toString());
        //ButtonHelper.resolveSetupColorChecker(getActiveGame());

        // double best = -1.0;
        // String set = "";
        // int i = 0;
        // int t18 = 0, t20 = 0, t22 = 0, t24 = 0, t26 = 0, t28 = 0, t30 = 0;
        // HashMap<Double, Integer> uggo = new HashMap<>();
        // for (int x1 = 0; x1 < colorsToCheck.size(); x1++) {
        //     for (int x2 = x1 + 1; x2 < colorsToCheck.size(); x2++) {
        //         for (int x3 = x2 + 1; x3 < colorsToCheck.size(); x3++) {
        //             for (int x4 = x3 + 1; x4 < colorsToCheck.size(); x4++) {
        //                 for (int x5 = x4 + 1; x5 < colorsToCheck.size(); x5++) {
        //                     for (int x6 = x5 + 1; x6 < colorsToCheck.size(); x6++) {
        //                         ++i;
        //                         String c1 = colorsToCheck.get(x1);
        //                         String c2 = colorsToCheck.get(x2);
        //                         String c3 = colorsToCheck.get(x3);
        //                         String c4 = colorsToCheck.get(x4);
        //                         String c5 = colorsToCheck.get(x5);
        //                         String c6 = colorsToCheck.get(x6);

        //                         double minimum = contrastMap.get(c1).get(c2);
        //                         minimum = Math.min(minimum, contrastMap.get(c1).get(c3));
        //                         minimum = Math.min(minimum, contrastMap.get(c1).get(c4));
        //                         minimum = Math.min(minimum, contrastMap.get(c1).get(c5));
        //                         minimum = Math.min(minimum, contrastMap.get(c1).get(c6));

        //                         minimum = Math.min(minimum, contrastMap.get(c2).get(c3));
        //                         minimum = Math.min(minimum, contrastMap.get(c2).get(c4));
        //                         minimum = Math.min(minimum, contrastMap.get(c2).get(c5));
        //                         minimum = Math.min(minimum, contrastMap.get(c2).get(c6));

        //                         minimum = Math.min(minimum, contrastMap.get(c3).get(c4));
        //                         minimum = Math.min(minimum, contrastMap.get(c3).get(c5));
        //                         minimum = Math.min(minimum, contrastMap.get(c3).get(c6));

        //                         minimum = Math.min(minimum, contrastMap.get(c4).get(c5));
        //                         minimum = Math.min(minimum, contrastMap.get(c4).get(c6));

        //                         minimum = Math.min(minimum, contrastMap.get(c5).get(c6));

        //                         if (minimum > best) {
        //                             best = minimum;
        //                             set = String.join(" ", c1, c2, c3, c4, c5, c6);
        //                         }
        //                         Integer v = uggo.get(minimum);
        //                         uggo.put(minimum, v == null ? 1 : v + 1);

        //                         if (minimum > 1.8) t18++;
        //                         if (minimum > 2.0) t20++;
        //                         if (minimum > 2.2) t22++;
        //                         if (minimum > 2.4) t24++;
        //                         if (minimum > 2.6) t26++;
        //                         if (minimum > 2.8) t28++;
        //                         if (minimum > 3.0) t30++;
        //                     }
        //                 }
        //             }
        //         }
        //     }
        // }
        // StringBuilder dump = new StringBuilder();
        // for (Entry<Double, Integer> e : uggo.entrySet()) {
        //     dump.append(e.getKey()).append(" ").append(e.getValue()).append("\n");
        // }

        // try {
        //     File outputFile = new File("temp.txt");
        //     if (outputFile.createNewFile()) {
        //         FileWriter write = new FileWriter(outputFile);
        //         write.write(dump.toString());
        //         write.close();
        //     }
        // } catch (Exception e) {
        // }
        // StringBuilder output = new StringBuilder("best colors: " + set + "\nratio: " + best + "\nnum checked: " + i);
        // output.append("\n> ").append("1.8: ").append(t18);
        // output.append("\n> ").append("2.0: ").append(t20);
        // output.append("\n> ").append("2.2: ").append(t22);
        // output.append("\n> ").append("2.4: ").append(t24);
        // output.append("\n> ").append("2.6: ").append(t26);
        // output.append("\n> ").append("2.8: ").append(t28);
        // output.append("\n> ").append("3.0: ").append(t30);
        // MessageHelper.sendMessageToChannel(event.getChannel(), dump.toString());
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