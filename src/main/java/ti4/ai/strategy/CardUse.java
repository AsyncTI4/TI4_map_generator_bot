package ti4.ai.strategy;

public enum CardUse {
    LEADERSHIP_TOKENS("command tokens for more actions"),
    LEADERSHIP_TEMPO("win the race for the custodians"),
    DIPLOMACY_PROTECT("lock a threatened system"),
    DIPLOMACY_READY("ready planets"),
    POLITICS_SPEAKER("become speaker"),
    POLITICS_ACTION_CARDS("draw action cards"),
    POLITICS_AGENDA("look at the agenda deck"),
    CONSTRUCTION_DOCK("place a space dock"),
    CONSTRUCTION_PDS("place PDS"),
    CONSTRUCTION_OBJECTIVE("complete a structure objective"),
    CONSTRUCTION_PRODUCE("produce at a space dock"),
    TRADE_GOODS("gain trade goods"),
    TRADE_COMMODITIES("replenish commodities"),
    TRADE_SPEND_OBJECTIVE("pay for a spend objective"),
    TRADE_LEVERAGE("trade leverage"),
    WARFARE_TOKEN("+1 command token"),
    WARFARE_EXTRA_ACTION("an extra tactical action"),
    WARFARE_DOUBLE_BUILD("double build at home"),
    WARFARE_BUILD_THEN_MOVE("build at home, then move out"),
    WARFARE_FAR_REACH("move twice"),
    WARFARE_UNBLOCK("reactivate a system"),
    TECHNOLOGY_RESEARCH("research"),
    TECHNOLOGY_PROPAGATION("Propagation command tokens"),
    IMPERIAL_SCORE("score an extra public objective"),
    IMPERIAL_MECATOL("Imperial point for Mecatol Rex"),
    IMPERIAL_SECRET("draw a secret"),
    OTHER_CARD("homebrew card");

    private final String label;

    CardUse(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
