package ti4.image;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

/**
 * Two render threads share every static field the render path reads, and Mapper reloads while renders run. A static
 * field there must be final and hold something safe to share (immutable, concurrent, or a cache), or be volatile and be
 * replaced as a whole. This scan fails on a new field that is neither, so it gets a deliberate decision.
 */
class RenderStaticStateTest extends BaseTi4Test {

    private static final Path MAIN = Path.of("src", "main", "java");
    private static final List<String> SCANNED_CLASSES = List.of(
            "ti4.model.Mapper",
            "ti4.model.TileHelper",
            "ti4.model.PositionMapper",
            "ti4.helpers.RenderRandom",
            "ti4.ResourceHelper");

    // Mutable statics that are written only while the bot starts, before any render runs.
    private static final Set<String> LOADED_AT_STARTUP = Set.of(
            "ti4.model.TileHelper.tileIdsToTileModels",
            "ti4.model.TileHelper.planetIdsToPlanetModels",
            "ti4.model.TileHelper.tileIdsToPlanetModels");

    // Properties is a synchronized Hashtable, so it is safe to share and not listed.
    private static final Set<Class<?>> UNSAFE_CONTAINERS = Set.of(
            java.util.HashMap.class,
            java.util.LinkedHashMap.class,
            java.util.TreeMap.class,
            java.util.EnumMap.class,
            java.util.HashSet.class,
            java.util.LinkedHashSet.class,
            java.util.TreeSet.class,
            java.util.ArrayList.class,
            java.util.LinkedList.class,
            java.util.ArrayDeque.class);

    @Test
    void renderStaticsAreSafeToShareBetweenThreads() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Class<?> type : scannedTypes()) {
            for (Field field : type.getDeclaredFields()) {
                String problem = problemWith(field);
                String name = type.getName() + "." + field.getName();
                if (problem != null && !LOADED_AT_STARTUP.contains(name)) violations.add(name + ": " + problem);
            }
        }
        assertEquals(List.of(), violations, "static fields shared by render threads");
    }

    private static String problemWith(Field field) throws IllegalAccessException {
        int modifiers = field.getModifiers();
        if (!Modifier.isStatic(modifiers) || field.isSynthetic() || field.isEnumConstant()) return null;
        if (Modifier.isVolatile(modifiers)) return null;
        if (!Modifier.isFinal(modifiers))
            return "mutable static field; make it final, or volatile and replace it whole";
        if (field.getType().isArray()) return "static array can be written by any thread";
        field.setAccessible(true);
        Object value = field.get(null);
        if (value != null && UNSAFE_CONTAINERS.contains(value.getClass())) {
            return "shared " + value.getClass().getSimpleName() + "; use an immutable or concurrent collection";
        }
        return null;
    }

    private static List<Class<?>> scannedTypes() throws IOException, ClassNotFoundException {
        List<Class<?>> types = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN.resolve(Path.of("ti4", "image")))) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".java")).toList()) {
                types.addAll(withNestedTypes(Class.forName(classNameOf(file))));
            }
        }
        for (String name : SCANNED_CLASSES) types.addAll(withNestedTypes(Class.forName(name)));
        return types;
    }

    private static Collection<Class<?>> withNestedTypes(Class<?> type) {
        List<Class<?>> types = new ArrayList<>(List.of(type));
        for (Class<?> nested : type.getDeclaredClasses()) types.addAll(withNestedTypes(nested));
        return types;
    }

    private static String classNameOf(Path file) {
        String relative = MAIN.relativize(file).toString();
        return relative.substring(0, relative.length() - ".java".length())
                .replace('\\', '.')
                .replace('/', '.');
    }
}
