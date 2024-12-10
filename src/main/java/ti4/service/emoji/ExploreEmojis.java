package ti4.service.emoji;

public enum ExploreEmojis implements TI4Emoji {

    // Frags
    HFrag, CFrag, IFrag, UFrag,

    // Types
    Cultural, Industrial, Hazardous, Frontier,

    // Other
    dmz, Relic;

    public static TI4Emoji getTraitEmoji(String type) {
        return switch (type.toLowerCase()) {
            case "cultural" -> Cultural;
            case "industrial" -> Industrial;
            case "hazardous" -> Hazardous;
            default -> Frontier;
        };
    }

    public static TI4Emoji getFragEmoji(String frag) {
        return switch (frag.toLowerCase()) {
            case "cultural", "cfrag", "crf" -> CFrag;
            case "industrial", "ifrag", "irf" -> IFrag;
            case "hazardous", "hfrag", "hrf" -> HFrag;
            default -> UFrag;
        };
    }

    @Override
    public String toString() {
        return emojiString();
    }
}
