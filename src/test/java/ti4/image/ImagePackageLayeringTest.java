package ti4.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The image code is layered: map depends on playerarea, draft, tile and card; draft also depends on tile, which draws the
 * tiles it lays out; tile, playerarea and card depend on draw, which depends on io. Playerarea, tile and card never depend
 * on each other, and nothing below map depends on map. Only imports of other
 * ti4.image packages are checked.
 */
class ImagePackageLayeringTest {

    private static final Path ROOT = Path.of("src", "main", "java", "ti4", "image");
    private static final Path MODEL_ROOT = Path.of("src", "main", "java", "ti4", "model");
    private static final Path GAME_ROOT = Path.of("src", "main", "java", "ti4", "game");
    private static final Pattern PACKAGE = Pattern.compile("^package\\s+ti4\\.image\\.(\\w+)", Pattern.MULTILINE);
    private static final Pattern IMPORT =
            Pattern.compile("^import\\s+(?:static\\s+)?ti4\\.image\\.(\\w+)\\.", Pattern.MULTILINE);

    private static final Map<String, Set<String>> ALLOWED_DEPENDENCIES = Map.of(
            "io", Set.of(),
            "draw", Set.of("io"),
            "tile", Set.of("draw", "io"),
            "playerarea", Set.of("draw", "io"),
            "draft", Set.of("tile", "draw", "io"),
            "card", Set.of("draw", "io"),
            "map", Set.of("playerarea", "tile", "draft", "card", "draw", "io"));

    @Test
    void everyImageClassLivesInAKnownPackage() throws IOException {
        List<String> misplaced = new ArrayList<>();
        for (Path file : javaFiles()) {
            Matcher matcher = PACKAGE.matcher(Files.readString(file));
            if (!matcher.find() || !ALLOWED_DEPENDENCIES.containsKey(matcher.group(1))) misplaced.add(file.toString());
        }
        assertEquals(List.of(), misplaced, "classes outside the image packages");
    }

    @Test
    void packagesOnlyDependOnLowerLayers() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : javaFiles()) {
            String text = Files.readString(file);
            Matcher owner = PACKAGE.matcher(text);
            if (!owner.find()) continue;
            String from = owner.group(1);
            Set<String> allowed = ALLOWED_DEPENDENCIES.getOrDefault(from, Set.of());
            Matcher imports = IMPORT.matcher(text);
            while (imports.find()) {
                String to = imports.group(1);
                if (!to.equals(from) && !allowed.contains(to)) {
                    violations.add(file.getFileName() + ": " + from + " -> " + to);
                }
            }
        }
        assertEquals(List.of(), violations, "image package dependencies against the layering");
    }

    @Test
    void modelAndGameNeverImportImageCode() throws IOException {
        Pattern imageImport = Pattern.compile("^import\\s+(?:static\\s+)?ti4\\.image\\.", Pattern.MULTILINE);
        List<String> violations = new ArrayList<>();
        for (Path root : List.of(MODEL_ROOT, GAME_ROOT)) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file :
                        files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    if (imageImport.matcher(Files.readString(file)).find()) violations.add(file.toString());
                }
            }
        }
        assertEquals(List.of(), violations, "model and game classes importing ti4.image");
    }

    @Test
    void sourceTreeIsFound() throws IOException {
        assertTrue(javaFiles().size() > 100, "run the test from the repository root");
    }

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> files = Files.walk(ROOT)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }
}
