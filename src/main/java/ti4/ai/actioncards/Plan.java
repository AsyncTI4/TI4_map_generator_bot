package ti4.ai.actioncards;

record Plan(double value, String target, String announcement) {

    static Plan of(double value) {
        return new Plan(value, "", "");
    }

    Plan plus(double extra) {
        return new Plan(value + extra, target, announcement);
    }

    static Plan aimedAt(double value, String target, String announcement) {
        return new Plan(value, target, announcement);
    }
}
