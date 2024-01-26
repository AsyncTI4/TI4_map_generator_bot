package ti4.helpers;

public final class StringHelper {

  private StringHelper() {}

  public static String ordinal(int i) {
    String[] suffixes = { "th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th" };
    switch (i % 100) {
        case 11:
        case 12:
        case 13:
          return i + "th";
        default:
          return i + suffixes[i % 10];

      }
  }

}
