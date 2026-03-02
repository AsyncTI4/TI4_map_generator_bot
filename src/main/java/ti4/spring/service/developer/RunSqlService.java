package ti4.spring.service.developer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;

@Service
public class RunSqlService {

    private static final int MAX_ROWS = 50;
    private static final int MAX_SQL_LENGTH = 400;
    private static final int MAX_RESPONSE_LENGTH = 12_000;

    private final DataSource dataSource;

    public RunSqlService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public String executeSql(String sql) {
        String trimmedSql = sql == null ? "" : sql.trim();
        if (trimmedSql.isEmpty()) {
            return "SQL cannot be empty.";
        }

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            boolean hasResultSet = statement.execute(trimmedSql);
            if (hasResultSet) {
                try (ResultSet resultSet = statement.getResultSet()) {
                    return formatResultSet(trimmedSql, resultSet);
                }
            }

            int rowsAffected = statement.getUpdateCount();
            return "Executed SQL successfully. Rows affected: " + rowsAffected + "\n```sql\n"
                    + truncateSql(trimmedSql)
                    + "\n```";
        } catch (SQLException e) {
            return "SQL execution failed: " + e.getMessage() + "\n```sql\n" + truncateSql(trimmedSql) + "\n```";
        }
    }

    private static String formatResultSet(String sql, ResultSet resultSet) throws SQLException {
        ResultSetMetaData metadata = resultSet.getMetaData();
        int columnCount = metadata.getColumnCount();

        StringBuilder builder = new StringBuilder();
        builder.append("```\n");

        for (int i = 1; i <= columnCount; i++) {
            if (i > 1) builder.append('\t');
            builder.append(metadata.getColumnLabel(i));
        }
        builder.append('\n');

        int rowCount = 0;
        while (resultSet.next() && rowCount < MAX_ROWS) {
            for (int i = 1; i <= columnCount; i++) {
                if (i > 1) builder.append('\t');
                Object value = resultSet.getObject(i);
                builder.append(value == null ? "NULL" : value);
            }
            builder.append('\n');
            rowCount++;
        }

        if (rowCount == MAX_ROWS && resultSet.next()) {
            builder.append("...truncated to ").append(MAX_ROWS).append(" rows\n");
        }

        builder.append("```\n");
        builder.append("Ran:\n```sql\n").append(truncateSql(sql)).append("\n```");

        if (builder.length() > MAX_RESPONSE_LENGTH) {
            return builder.substring(0, MAX_RESPONSE_LENGTH - 3) + "...";
        }
        return builder.toString();
    }

    private static String truncateSql(String sql) {
        String cleaned = sql.replace("```", "'''");
        if (cleaned.length() <= MAX_SQL_LENGTH) {
            return cleaned;
        }
        return cleaned.substring(0, MAX_SQL_LENGTH - 3) + "...";
    }
}
