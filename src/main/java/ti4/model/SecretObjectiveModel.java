package ti4.model;

public class SecretObjectiveModel implements ModelInterface {
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
