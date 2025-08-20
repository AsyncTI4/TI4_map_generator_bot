package ti4.helpers;

public class Stickers {

    private static final long HopesEnd = 1262605434905428087L;
    private static final long Mallice = 1262605513095516190L;
    private static final long Mecatol = 1262605300150833242L;
    private static final long Mirage = 1262605660860711012L;
    private static final long Primor = 1262605726283468860L;
    private static final long SemLore = 1262605816876503070L;

    public static long getPlanetSticker(String planet) {
        return switch (planet.toLowerCase()) {
            case "hopesend" -> HopesEnd;
            case "mallice", "lockedmallice" -> Mallice;
            case "mr" -> Mecatol;
            case "mirage" -> Mirage;
            case "primor" -> Primor;
            case "semlore" -> SemLore;

            default -> -1;
        };
    }
}
