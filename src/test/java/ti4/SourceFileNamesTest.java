package ti4;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every source file must declare a top-level type named after the file.
 *
 * <p>maven-compiler-plugin decides whether to recompile by mapping each {@code X.java} to {@code X.class}. A file that
 * holds only classes with other names never gets its {@code X.class}, so the plugin sees it as changed on every run and
 * recompiles all of src/main/java (and then src/test/java) each time: about 50 s on every {@code mvn test}, even with
 * nothing edited. Put each top-level class in its own file instead.
 */
class SourceFileNamesTest {

    private static final List<Path> SOURCE_ROOTS =
            List.of(Path.of("src", "main", "java"), Path.of("src", "test", "java"));

    @Test
    void everySourceFileDeclaresATopLevelTypeNamedAfterIt() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path root : SOURCE_ROOTS) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(SourceFileNamesTest::isTypeSource).toList()) {
                    if (!declaresOwnType(file))
                        offenders.add(root.relativize(file).toString());
                }
            }
        }

        assertThat(offenders)
                .as("source files without a top-level type of the same name; each forces a full recompile on every run")
                .isEmpty();
    }

    private static boolean isTypeSource(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".java") && !"package-info.java".equals(name) && !"module-info.java".equals(name);
    }

    private static boolean declaresOwnType(Path file) throws IOException {
        String fileName = file.getFileName().toString();
        String typeName = fileName.substring(0, fileName.length() - ".java".length());
        // Top-level declarations start at column 0 in formatted sources; nested ones are indented.
        Pattern ownType = Pattern.compile(
                "^(?:(?:public|protected|private|abstract|final|sealed|non-sealed|static|strictfp)\\s+)*"
                        + "(?:class|interface|enum|record|@interface)\\s+" + Pattern.quote(typeName) + "\\b");
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (ownType.matcher(line).find()) return true;
        }
        return false;
    }
}
