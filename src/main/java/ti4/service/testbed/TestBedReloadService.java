package ti4.service.testbed;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import lombok.experimental.UtilityClass;
import tools.jackson.core.JacksonException;

@UtilityClass
public class TestBedReloadService {

    public record Report(int files, int localFiles, List<String> problems) {}

    private interface Checker {
        List<String> check(String json);
    }

    public static Report reload() {
        TestBedPresetService.clearCache();
        TestBedScriptService.clearCache();
        TestBedShortcuts.clearCache();
        List<String> problems = new ArrayList<>();
        int files = 0;
        files += check(TestBedPresetService.allPresetFiles(), presetChecker(), problems);
        files += check(TestBedScriptService.allScriptFiles(), scriptChecker(), problems);
        List<Path> groupFiles = new ArrayList<>(TestBedShortcuts.shortcutFiles());
        groupFiles.addAll(TestBedShortcuts.localShortcutFiles());
        files += check(groupFiles, groupChecker(), problems);
        int localFiles = TestBedPresetService.localPresetFiles().size()
                + TestBedScriptService.localScriptFiles().size()
                + TestBedShortcuts.localShortcutFiles().size();
        return new Report(files, localFiles, problems);
    }

    private static int check(List<Path> files, Checker checker, List<String> problems) {
        for (Path file : files) {
            try {
                checker.check(Files.readString(file))
                        .forEach(problem -> problems.add(file.getFileName() + ": " + problem));
            } catch (IOException e) {
                problems.add(file.getFileName() + ": could not be read (" + e.getMessage() + ")");
            } catch (JacksonException e) {
                problems.add(file.getFileName() + ": " + e.getOriginalMessage());
            }
        }
        return files.size();
    }

    private static Checker presetChecker() {
        return parsed(TestBedPresetService::parse, TestBedPresetService::validate);
    }

    private static Checker scriptChecker() {
        return parsed(TestBedScriptService::parse, TestBedScriptService::validate);
    }

    private static Checker groupChecker() {
        return parsed(TestBedShortcuts::parseGroup, TestBedShortcuts::validateGroup);
    }

    private static <T> Checker parsed(Function<String, T> parse, Function<T, List<String>> validate) {
        return json -> validate.apply(parse.apply(json));
    }
}
