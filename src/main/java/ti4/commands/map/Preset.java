package ti4.commands.map;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class Preset extends MapSubcommandData {
    public Preset() {
        super(Constants.PRESET, "Create a map from a template");
        addOption(OptionType.STRING, Constants.MAP_TEMPLATE, "Which map template do you want to use", true, true);
        addOption(OptionType.STRING, Constants.SLICE_1, "Player 1's milty draft slice", false);
        addOption(OptionType.STRING, Constants.SLICE_2, "Player 2's slice", false);
        addOption(OptionType.STRING, Constants.SLICE_3, "Player 3's slice", false);
        addOption(OptionType.STRING, Constants.SLICE_4, "Player 4's slice", false);
        addOption(OptionType.STRING, Constants.SLICE_5, "Player 5's slice", false);
        addOption(OptionType.STRING, Constants.SLICE_6, "Player 6's slice", false);
        //addOption(OptionType.STRING, Constants.SLICE_7, "Player 7's slice", false);
        //addOption(OptionType.STRING, Constants.SLICE_8, "Player 8's slice", false);
    }

    public static List<String> templates = List.of("Solo (1 slice)", "Minimal Solo (1 slice)", "TspMap (6 slices)", "Magi's Madness (0 slices)");

    @Override
    public void execute(SlashCommandInteractionEvent event) {

        OptionMapping template = event.getOption(Constants.MAP_TEMPLATE);
        if (template == null) return;

        Game game = getGame();
        String slice1 = event.getOption(Constants.SLICE_1, null, OptionMapping::getAsString);
        String slice2 = event.getOption(Constants.SLICE_2, null, OptionMapping::getAsString);
        String slice3 = event.getOption(Constants.SLICE_3, null, OptionMapping::getAsString);
        String slice4 = event.getOption(Constants.SLICE_4, null, OptionMapping::getAsString);
        String slice5 = event.getOption(Constants.SLICE_5, null, OptionMapping::getAsString);
        String slice6 = event.getOption(Constants.SLICE_6, null, OptionMapping::getAsString);

        String[] s1 = slice1 != null ? slice1.replace(",", " ").replace("  ", " ").split(" ") : null;
        String[] s2 = slice2 != null ? slice2.replace(",", " ").replace("  ", " ").split(" ") : null;
        String[] s3 = slice3 != null ? slice3.replace(",", " ").replace("  ", " ").split(" ") : null;
        String[] s4 = slice4 != null ? slice4.replace(",", " ").replace("  ", " ").split(" ") : null;
        String[] s5 = slice5 != null ? slice5.replace(",", " ").replace("  ", " ").split(" ") : null;
        String[] s6 = slice6 != null ? slice6.replace(",", " ").replace("  ", " ").split(" ") : null;

        if (s1 != null && s1.length != 5) s1 = null;
        if (s2 != null && s2.length != 5) s2 = null;
        if (s3 != null && s3.length != 5) s3 = null;
        if (s4 != null && s4.length != 5) s4 = null;
        if (s5 != null && s5.length != 5) s5 = null;
        if (s6 != null && s6.length != 5) s6 = null;

        String ph = "-1"; //placeholder string. Either will never exist or will be a hyperlane added later
        String home = "0g"; //Home system placeholder
        String error = "Not enough slices for selected preset, or slices are improperly formatted";
        switch (template.getAsString()) {
            case "Solo (1 slice)" -> {
                if (s1 == null) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), error);
                    return;
                }
                List<String> tiles = List.of("85a3", "85a4", "85a5", s1[4], "85a1", "85a2", //ring 1
                    "85a3", "84a0", "85a4", "84a1", "85a5", "87a5", s1[1], s1[3], "85a1", "84a4", "85a2", "84a5", //ring 2
                    "85a3", "84a0", "84a0", "85a4", "84a1", "84a1", "85a5", "84a2", s1[2], home, s1[0], "84a3", "85a1", "84a4", "84a4", "85a2", "84a5", "84a5");//ring 3
                String mapString = String.join(" ", tiles);
                AddTileList.addTileListToMap(game, mapString, event);
            }
            case "Minimal Solo (1 slice)" -> {
                if (s1 == null) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), error);
                    return;
                }
                List<String> tiles = List.of("{"+s1[4]+"}", //ring 0
                    "18", "84a1", "84a1", s1[1], s1[3], "90b", // ring 1
                    "85a3", "85a4", ph, ph, ph, s1[2], home, s1[0], "85a1", "85a2", ph, "85a2"); // ring 3
                String mapString = String.join(" ", tiles);
                AddTileList.addTileListToMap(game, mapString, event);
            }
            case "TspMap (6 slices)" -> {
                if (s1 == null || s2 == null || s3 == null || s4 == null || s5 == null || s6 == null) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), error);
                    return;
                }

                List<String> tiles = List.of(s1[4], ph, ph, s4[4], ph, ph, // ring 1
                    home, s1[2], s2[4], ph, s3[4], s4[1], home, s4[0], s5[4], ph, s6[4], s1[1], // ring 2
                    ph, s1[3], s2[1], home, s2[2], s3[2], home, s3[1], ph, s4[3], s4[2], s5[2], home, s5[1], s6[1], home, s6[2], s1[0], //ring 3
                    ph, ph, ph, s2[0], s2[3], ph, ph, ph, s3[3], s3[0], ph, ph, ph, ph, ph, s5[0], ph, s5[3], ph, s6[3], ph, s6[0], ph, ph); //ring 4
                // 29,27,41,62,43
                // left, fwd, right, equi, mecatol
                String mapString = String.join(" ", tiles);
                AddTileList.addTileListToMap(game, mapString, event);

                // finish adding hyperlanes and adjacencies
                InitTspmap.addTspmapHyperlanes(game);
                InitTspmap.addTspmapEdgeAdjacencies(game);
            }
            case "Magi's Madness (0 slices)" -> {
                String mapString = "{85A2} 70 84A5 0g 83A2 69 0g 62 42 90B1 36 27 34 65 68 46 76 74 75 0g 88B2 26 44 37 66 25 43 48 19 18 78"
                    + " 35 61 79 49 64 45 29 80 50 67 33 0g 22 87B3 86A5 73 0g 38 77 39 40 41 59 0g 47 72 24 0g 28 91B1";
                AddTileList.addTileListToMap(game, mapString, event);
            }
        }
    }
}
