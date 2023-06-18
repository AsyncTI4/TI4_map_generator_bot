package ti4.model;

public class PromissoryNoteModel implements ModelInterface {
    private String alias;
    private String name;
    private String faction;
    private String colour;
    private Boolean playArea;
    private String attachment;
    private String source;
    private String text; 

    public PromissoryNoteModel() {}

    public boolean isValid() {
        return alias != null
            && name != null
            && (faction != null && colour != null)
            && attachment != null
            && text != null
            && source != null;
    }

    public String getAlias() {
        return alias;
    }
    
    public String getName() {
        return name;
    }
    
    public String getFaction() {
        return faction;
    }

    public String getColour() {
        return colour;
    }

    public Boolean getPlayArea() {
        return playArea;
    }

    public String getAttachment() {
        return attachment;
    }

    public String getText() {
        return text;
    }

    public String getSource() {
        return source;
    }

    public String getOwner() {
        if (faction == null || faction.isEmpty()) return colour;
        return faction;
    }

    public boolean isPlayedDirectlyToPlayArea() {
        return playArea && !text.toLowerCase().contains("action:");
    }

}
