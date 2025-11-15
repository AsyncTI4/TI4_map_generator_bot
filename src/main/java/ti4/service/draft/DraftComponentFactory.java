package ti4.service.draft;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.reflections.Reflections;

@UtilityClass
public class DraftComponentFactory {
    private final Map<String, Class<? extends Draftable>> KNOWN_DRAFTABLE_TYPES =
            findAllDerivedClasses(Draftable.class).stream().collect(Collectors.toMap(Class::getSimpleName, d -> d));

    private final Map<String, Class<? extends DraftOrchestrator>> KNOWN_ORCHESTRATOR_TYPES =
            findAllDerivedClasses(DraftOrchestrator.class).stream()
                    .collect(Collectors.toMap(Class::getSimpleName, o -> o));

    public List<String> getKnownDraftableClasses() {
        return KNOWN_DRAFTABLE_TYPES.keySet().stream().sorted().collect(Collectors.toList());
    }

    public Draftable createDraftable(String type) {
        for (Map.Entry<String, Class<? extends Draftable>> entry : KNOWN_DRAFTABLE_TYPES.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(type)) {
                try {
                    return entry.getValue().getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to instantiate draftable of type: " + type, e);
                }
            }
        }

        return null;
    }

    public List<String> getKnownOrchestratorClasses() {
        return KNOWN_ORCHESTRATOR_TYPES.keySet().stream().sorted().collect(Collectors.toList());
    }

    public DraftOrchestrator createOrchestrator(String type) {
        for (Map.Entry<String, Class<? extends DraftOrchestrator>> entry : KNOWN_ORCHESTRATOR_TYPES.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(type)) {
                try {
                    return entry.getValue().getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to instantiate orchestrator of type: " + type, e);
                }
            }
        }

        return null;
    }

    private <T> List<Class<? extends T>> findAllDerivedClasses(Class<T> baseClass) {
        try {
            List<Class<? extends T>> derivedClasses = new ArrayList<>();
            Reflections reflections = new Reflections(DraftComponentFactory.class.getPackageName());
            Set<Class<? extends T>> subtypes = reflections.getSubTypesOf(baseClass);
            for (Class<? extends T> subtype : subtypes) {
                if (subtype.isInterface() || Modifier.isAbstract(subtype.getModifiers())) {
                    continue;
                }
                try {
                    // Ensure parameterless constructor
                    subtype.getDeclaredConstructor();
                } catch (NoSuchMethodException e) {
                    continue;
                }
                derivedClasses.add(subtype);
            }
            return derivedClasses;
        } catch (Exception e) {
            throw new RuntimeException("Failed to scan for derived classes of " + baseClass.getName(), e);
        }
    }
}
