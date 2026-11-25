package ti4.ai.runtime;

import java.util.Locale;

// The rows of docs/ai/experiments/INDEX.md, one per recorded run (AbRecord appends them; older runs were entered by
// hand from the notes they were reported in).
final class AbIndex {

    static final String HEADER = """
            # AI A/B and benchmark runs

            One row per run, oldest first. Points and win share are treatment minus control over complete pairs, mean
            ± standard error; "Round / leader / table" is the average final round, leader points and table points of the
            whole batch. Fill in the verdict after reading a run. See README.md for how to read this table.

            | Run | Date | Commit | Tested | Pairs (games) | Points | Win share | Round / leader / table | Verdict |
            | --- | --- | --- | --- | --- | --- | --- | --- | --- |
            """;

    private AbIndex() {}

    static String row(String... cells) {
        StringBuilder row = new StringBuilder("|");
        for (String cell : cells)
            row.append(' ').append(cell.replace("|", "\\|")).append(" |");
        return row.append('\n').toString();
    }

    static String stat(AbSummary.Stat stat) {
        return format(stat, "%+.2f");
    }

    static String share(AbSummary.Stat stat) {
        return format(stat, "%.2f");
    }

    private static String format(AbSummary.Stat stat, String mean) {
        if (stat.n() == 0) return "n/a";
        String shown = String.format(Locale.ROOT, mean, stat.mean());
        if (stat.n() == 1 || !Double.isFinite(stat.error())) return shown;
        return shown + String.format(Locale.ROOT, " ± %.2f", stat.error());
    }
}
