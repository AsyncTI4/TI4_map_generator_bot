package ti4.image;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import ti4.game.Game;

public record BoardPosition(char board, String local) {

    public static final List<Character> BOARDS = List.of('a', 'b', 'c', 'd', 'e', 'f', 'g');
    public static final String REGEX = "[a-g][0-8][0-9][0-9]";
    public static final int MAX_RING = 8;
    public static final char MAIN_BOARD = '0';
    private static final Pattern PATTERN = Pattern.compile(REGEX);
    private static final int HORIZONTAL_TILE_SPACING = 260;
    static final int RAW_STRIDE = (2 * MAX_RING + 2) * HORIZONTAL_TILE_SPACING;

    public static Optional<BoardPosition> parse(String position) {
        if (position == null
                || position.length() != 4
                || position.charAt(0) < BOARDS.getFirst()
                || position.charAt(0) > BOARDS.getLast()
                || !PATTERN.matcher(position).matches()) {
            return Optional.empty();
        }
        return Optional.of(new BoardPosition(position.charAt(0), position.substring(1)));
    }

    public static boolean isBoardPosition(String position) {
        return parse(position).isPresent();
    }

    public static Set<Character> boardsInUse(Game game) {
        Set<Character> boards = new TreeSet<>();
        for (String position : game.getTileMap().keySet()) {
            parse(position).ifPresent(boardPosition -> boards.add(boardPosition.board()));
        }
        return boards;
    }

    public static String defaultSegmentName(char board) {
        return "board-" + board;
    }

    public static char boardOf(String position) {
        return parse(position).map(BoardPosition::board).orElse(MAIN_BOARD);
    }

    public int index() {
        return BOARDS.indexOf(board);
    }

    public String withLocal(String otherLocal) {
        return board + otherLocal;
    }

    static int rawOffsetX(int boardIndex) {
        return (boardIndex + 1) * RAW_STRIDE;
    }

    static int ringCompression(int boardIndex, int ringCount) {
        return (boardIndex + 1) * (MAX_RING - ringCount) * 2 * HORIZONTAL_TILE_SPACING;
    }

    static int boardWidth(int ringCount) {
        return (2 * ringCount + 2) * HORIZONTAL_TILE_SPACING;
    }
}
