package ti4.image.draw;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

public final class RowWrap {

    private RowWrap() {}

    public static <T> List<List<T>> wrapIntoRows(
            List<T> items, ToIntFunction<T> width, int separatorWidth, int rowWidth, int lastRowWidth) {
        List<List<T>> rows = fillRows(items, width, separatorWidth, rowWidth);
        if (rows.isEmpty()) {
            return rows;
        }
        List<T> lastRow = rows.removeLast();
        int split = lastRow.size() - 1;
        while (split > 0
                && rowWidth(lastRow.subList(split - 1, lastRow.size()), width, separatorWidth) <= lastRowWidth) {
            split--;
        }
        if (split > 0) {
            rows.add(new ArrayList<>(lastRow.subList(0, split)));
        }
        rows.add(new ArrayList<>(lastRow.subList(split, lastRow.size())));
        return rows;
    }

    public static <T> List<List<T>> balancedRows(
            List<T> items, ToIntFunction<T> width, int separatorWidth, int maxWidth) {
        List<List<T>> filledRows = fillRows(items, width, separatorWidth, maxWidth);
        if (filledRows.size() < 2) {
            return filledRows;
        }
        int perRow = (items.size() + filledRows.size() - 1) / filledRows.size();
        List<List<T>> evenRows = new ArrayList<>();
        for (int start = 0; start < items.size(); start += perRow) {
            evenRows.add(new ArrayList<>(items.subList(start, Math.min(start + perRow, items.size()))));
        }
        boolean evenRowsFit = evenRows.stream().allMatch(row -> rowWidth(row, width, separatorWidth) <= maxWidth);
        return evenRowsFit ? evenRows : filledRows;
    }

    private static <T> int rowWidth(List<T> row, ToIntFunction<T> width, int separatorWidth) {
        return row.stream().mapToInt(width).sum() + separatorWidth * (row.size() - 1);
    }

    private static <T> List<List<T>> fillRows(List<T> items, ToIntFunction<T> width, int separatorWidth, int maxWidth) {
        List<List<T>> rows = new ArrayList<>();
        List<T> row = new ArrayList<>();
        int usedWidth = 0;
        for (T item : items) {
            int itemWidth = width.applyAsInt(item);
            if (!row.isEmpty() && usedWidth + separatorWidth + itemWidth > maxWidth) {
                rows.add(row);
                row = new ArrayList<>();
                usedWidth = 0;
            }
            usedWidth += (row.isEmpty() ? 0 : separatorWidth) + itemWidth;
            row.add(item);
        }
        if (!row.isEmpty()) {
            rows.add(row);
        }
        return rows;
    }
}
