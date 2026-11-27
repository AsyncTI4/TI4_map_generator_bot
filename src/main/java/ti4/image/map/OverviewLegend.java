package ti4.image.map;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class OverviewLegend {

    static final int GAP = 3 * HexPacker.GUTTER;
    private static final int ROW_GAP = 16;
    private static final int TEXT_GAP = 24;
    private static final int COLUMN_GAP = 2 * HexPacker.GUTTER;

    record Entry(String code, String name, Color color) {}

    record Row(Entry entry, Rectangle pill) {}

    private final List<Row> rows;
    private final Rectangle bounds;

    private OverviewLegend(List<Row> rows) {
        this.rows = List.copyOf(rows);
        Rectangle union = null;
        for (Row row : rows) {
            Rectangle line = lineOf(row);
            union = union == null ? line : union.union(line);
        }
        this.bounds = union == null ? new Rectangle() : union;
    }

    Rectangle bounds() {
        return new Rectangle(bounds);
    }

    List<Row> rows() {
        return rows;
    }

    static OverviewLegend beside(Rectangle content, List<Entry> entries) {
        int rowStep = HexIslandStyle.labelHeight() + ROW_GAP;
        int columnWidth = pillWidth(entries) + TEXT_GAP + nameWidth(entries) + COLUMN_GAP;
        int rowsBeside = Math.max(1, (content.height + ROW_GAP) / rowStep);
        OverviewLegend right =
                arrange(entries, content.x + content.width + GAP, content.y, rowsBeside, rowStep, columnWidth);
        int columnsBelow = Math.max(1, (content.width + COLUMN_GAP) / columnWidth);
        int rowsBelow = Math.max(1, (entries.size() + columnsBelow - 1) / columnsBelow);
        OverviewLegend below =
                arrange(entries, content.x, content.y + content.height + GAP, rowsBelow, rowStep, columnWidth);
        Comparator<OverviewLegend> squarest = Comparator.comparingInt((OverviewLegend legend) -> {
                    Rectangle all = content.union(legend.bounds);
                    return Math.max(all.width, all.height);
                })
                .thenComparingLong(legend -> {
                    Rectangle all = content.union(legend.bounds);
                    return (long) all.width * all.height;
                });
        return squarest.compare(right, below) <= 0 ? right : below;
    }

    private static OverviewLegend arrange(
            List<Entry> entries, int left, int top, int rowsPerColumn, int rowStep, int columnWidth) {
        int pillWidth = pillWidth(entries);
        List<Row> rows = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            int column = index / rowsPerColumn;
            int row = index % rowsPerColumn;
            rows.add(new Row(
                    entries.get(index),
                    new Rectangle(
                            left + column * columnWidth,
                            top + row * rowStep,
                            pillWidth,
                            HexIslandStyle.labelHeight())));
        }
        return new OverviewLegend(rows);
    }

    private static int pillWidth(List<Entry> entries) {
        return entries.stream()
                .mapToInt(entry -> HexIslandStyle.labelWidth(entry.code()))
                .max()
                .orElse(0);
    }

    private static int nameWidth(List<Entry> entries) {
        return entries.stream()
                .mapToInt(entry -> HexIslandStyle.textWidth(entry.name()))
                .max()
                .orElse(0);
    }

    private static Rectangle lineOf(Row row) {
        Rectangle pill = row.pill();
        return new Rectangle(
                pill.x,
                pill.y,
                pill.width + TEXT_GAP + HexIslandStyle.textWidth(row.entry().name()),
                pill.height);
    }

    void draw(Graphics2D graphics) {
        for (Row row : rows) {
            Rectangle pill = row.pill();
            HexIslandStyle.drawLabel(
                    graphics, pill, row.entry().code(), row.entry().color());
            HexIslandStyle.drawText(
                    graphics,
                    row.entry().name(),
                    pill.x + pill.width + TEXT_GAP,
                    pill.y + pill.height / 2,
                    row.entry().color());
        }
    }
}
