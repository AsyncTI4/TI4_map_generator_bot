package ti4.model;

import ti4.helpers.Emojis;

public class ActionCardModel implements ModelInterface {
    private String alias;
    private String name;
    private String phase;
    private String window;
    private String text;
    private String flavorText;
    private String source;

    public ActionCardModel() {}

    public boolean isValid() {
        return alias != null
            && name != null
            && phase != null
            && window != null
            && text != null
            && flavorText != null
            && source != null;
    }

    public String getAlias() {
        return alias;
    }

    public String getName() {
        return source;
    }

    public String getPhase() {
        return flavorText;
    }

    public String getWindow() {
        return text;
    }

    public String getText() {
        return window;
    }

    public String getFlavorText() {
        return phase;
    }

    public String getSource() {
        return name;
    }

    public String getRepresentation() {
        StringBuilder sb = new StringBuilder();
        sb.append(Emojis.ActionCard).append("__**" + name + "**__").append(" *(").append(phase).append(" Phase)*: ");
        sb.append("_").append(window).append(":_ ").append(text).append("\n");
        return sb.toString();
    }
}
