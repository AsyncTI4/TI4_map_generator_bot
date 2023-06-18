package ti4.model;

public class TechnologyModel implements ModelInterface {
    private String alias;
    private String name;
    private String type;
    private String requirements;
    private String faction;
    private String baseUpgrade;
    private String source;
    private String text;

    public TechnologyModel() {}

    public boolean isValid() {
        return alias != null
            && name != null
            && text != null
            && source != null
            && baseUpgrade != null
            && faction != null
            && requirements != null
            && type != null;
    }

    public String getAlias() {
        return alias;
    }

    public String getName() { 
        return name; 
    }

    public String getType() { 
        return type; 
    }

    public String getRequirements() { 
        return requirements; 
    }

    public String getFaction() { 
        return faction; 
    }

    public String getBaseUpgrade() { 
        return baseUpgrade; 
    }

    public String getSource() { 
        return source; 
    }

    public String getText() { 
        return text; 
    }
}
