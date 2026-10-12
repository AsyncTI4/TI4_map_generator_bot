package ti4.ai.actioncards;

record Plan(double value, String target, String announcement) {

    static Plan of(double value) {
        return new Plan(value, "", "");
    }

    static Plan aimedAt(double value, String target, String announcement) {
        return new Plan(value, target, announcement);
    }
}
