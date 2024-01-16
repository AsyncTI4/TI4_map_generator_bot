package ti4.model;

import java.awt.Color;

import lombok.Data;

@Data
public class ColorModel {

    //For now these are hardcoded. TODO: add to json file
    public static Color primaryColor(String color) {
        if (color == null) {
            return Color.WHITE;
        }
        if (color.startsWith("split")) {
            color = color.replace("split", "");
        }
        if (color.equals("orca")) {
            color = "lightgray";
        }
        return switch (color) {
            case "black" -> new Color(5, 5, 5);
            case "blue" -> new Color(2, 63, 201);
            case "green" -> new Color(0, 173, 61);
            case "gray", "grey" -> new Color(114, 123, 143);
            case "orange" -> new Color(205, 123, 0);
            case "pink" -> new Color(204, 0, 185);
            case "purple" -> new Color(112, 0, 141);
            case "red" -> new Color(230, 1, 1);
            case "yellow" -> new Color(187, 194, 0);
            case "petrol" -> new Color(92, 156, 160);
            case "brown" -> new Color(134, 95, 55);
            case "tan" -> new Color(160, 149, 110);
            case "forest" -> new Color(98, 139, 105);
            case "chrome" -> new Color(130, 135, 81);
            case "sunset" -> new Color(182, 3, 136);
            case "turquoise" -> new Color(0, 176, 175);
            case "gold" -> new Color(167, 165, 0);
            case "lightgray" -> new Color(193, 193, 198);
            case "bloodred" -> new Color(126, 0, 27);
            case "chocolate" -> new Color(79, 37, 32);
            case "teal" -> new Color(0, 220, 241);
            case "emerald" -> new Color(0, 143, 10);
            case "navy" -> new Color(3, 26, 150);
            case "lime" -> new Color(130, 210, 45);
            case "lavender" -> new Color(154, 142, 230);
            case "rose" -> new Color(210, 149, 207);
            case "spring" -> new Color(221, 232, 146);
            case "rainbow" -> new Color(16, 188, 20);
            case "ethereal" -> new Color(52, 85, 202);
            case "orca" -> primaryColor("black");
            default -> Color.WHITE;
        };
    }

    //For now these are hardcoded. TODO: add to json file
    public static Color secondaryColor(String color) {
        if (color == null) {
            return null;
        }
        return switch (color) {
            // lightgray secondaries
            case "splitbloodred" -> primaryColor("lightgray");
            case "splitchocolate" -> primaryColor("lightgray");
            case "splitemerald" -> primaryColor("lightgray");
            case "splitnavy" -> primaryColor("lightgray");
            case "splitpurple" -> primaryColor("lightgray");
            case "splitpetrol" -> primaryColor("lightgray");
            case "splitrainbow" -> primaryColor("lightgray");
            // orca is special, counts as lightgray secondary
            case "orca", "splitblack", "splitlightgray" -> primaryColor("lightgray");
            // black secondaries
            case "splityellow" -> primaryColor("black");
            case "splittan" -> primaryColor("black");
            case "splitturquoise" -> primaryColor("black");
            case "splitteal" -> primaryColor("black");
            case "splitpink" -> primaryColor("black");
            case "splitred" -> primaryColor("black");
            case "splitorange" -> primaryColor("black");
            case "splitlime" -> primaryColor("black");
            case "splitgreen" -> primaryColor("black");
            case "splitgold" -> primaryColor("black");
            case "splitblue" -> primaryColor("black");
            default -> null;
        };
        //colors that don't exist in split variants
        // case "splitgray" -> new Color(0, 0, 0);
        // case "splitbrown" -> new Color(0, 0, 0);
        // case "splitforest" -> new Color(0, 0, 0);
        // case "splitchrome" -> new Color(0, 0, 0);
        // case "splitsunset" -> new Color(0, 0, 0);
        // case "splitlavender" -> new Color(0, 0, 0);
        // case "splitrose" -> new Color(0, 0, 0);
        // case "splitspring" -> new Color(0, 0, 0);
    }
}
