package ti4.service.emoji;

public enum ColorEmojis implements TI4Emoji {

    // Colors
    black, bloodred, blue, brown, chocolate, chrome, rainbow, rose, emerald, ethereal, forest, gold, gray, green, lavender, //
    lightgray, lime, navy, orange, orca, petrol, pink, purple, red, spring, sunset, tan, teal, turquoise, yellow, //
    splitbloodred, splitblue, splitchocolate, splitemerald, splitgold, splitgreen, splitlime, splitnavy, splitorange, //
    splitpetrol, splitpink, splitpurple, splitrainbow, splitred, splittan, splitteal, splitturquoise, splityellow, riftset; //

    @Override
    public String toString() {
        return emojiString();
    }
}
