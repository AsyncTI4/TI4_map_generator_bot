package ti4.ai.scoring;

public record SpendCost(int resources, int influence, int tradeGoods, int tokens) {

    public static final SpendCost NONE = new SpendCost(0, 0, 0, 0);

    public static SpendCost resources(int amount) {
        return new SpendCost(amount, 0, 0, 0);
    }

    public static SpendCost influence(int amount) {
        return new SpendCost(0, amount, 0, 0);
    }

    public static SpendCost tradeGoods(int amount) {
        return new SpendCost(0, 0, amount, 0);
    }

    public static SpendCost tokens(int amount) {
        return new SpendCost(0, 0, 0, amount);
    }

    public static SpendCost each(int amount) {
        return new SpendCost(amount, amount, amount, 0);
    }

    public SpendCost plus(SpendCost other) {
        return new SpendCost(
                resources + other.resources,
                influence + other.influence,
                tradeGoods + other.tradeGoods,
                tokens + other.tokens);
    }

    public boolean isNone() {
        return resources == 0 && influence == 0 && tradeGoods == 0 && tokens == 0;
    }
}
