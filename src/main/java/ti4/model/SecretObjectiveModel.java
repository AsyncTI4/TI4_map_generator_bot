package ti4.model;

public class SecretObjectiveModel implements ModelInterface {
    private String alias;
    private String name;
    private String phase;
    private String window;
    private String text;
    private int points;
    private String source;

    public SecretObjectiveModel() {}

    public boolean isValid() {
        return alias != null
            && name != null
            && phase != null
            && window != null
            && text != null
            && points != 0
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

    public String getWindow() { 
        return window;
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
}
