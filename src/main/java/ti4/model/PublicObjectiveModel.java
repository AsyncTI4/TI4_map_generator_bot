package ti4.model;

import ti4.helpers.Helper;

public class PublicObjectiveModel implements ModelInterface {
    private String alias;
    private String name;
    private String phase;
    private String text;
    private int points;
    private String source;

  public boolean isValid() {
        return alias != null
            && name != null
            && phase != null
            && text != null
            && source != null;
    }

    public String getAlias() {
        return alias;
    }

    public String getName() {
        return name;
    }

    public String getPhase() {
        return phase;
    }

    public String getText() {
        return text;
    }

    public int getPoints() {
        return points;
    }

    public String getSource() {
        return source;
    }

    public String getRepresentation() {
        String emoji = Helper.getEmojiFromDiscord("Public" + points + "alt");
        return emoji + "**__" + name + "__**: " + text + " (" + points + " VP)";
    }
}
