package ti4.ai.actioncards;

record Plan(double value, String target, String announcement, boolean urgent) {

    static Plan of(double value) {
        return new Plan(value, "", "", false);
    }

    Plan plus(double extra) {
        return new Plan(value + extra, target, announcement, urgent);
    }

    Plan urgently() {
        return new Plan(value, target, announcement, true);
    }

    static Plan aimedAt(double value, String target, String announcement) {
        return new Plan(value, target, announcement, false);
    }
}
