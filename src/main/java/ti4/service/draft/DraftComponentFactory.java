package ti4.service.draft;

import java.lang.reflect.Constructor;
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
    private static final Map<String, Class<? extends Draftable>> KNOWN_DRAFTABLE_TYPES =
            findAllDerivedClasses(Draftable.class).stream().collect(Collectors.toMap(d -> d.getSimpleName(), d -> d));

    private static final Map<String, Class<? extends DraftOrchestrator>> KNOWN_ORCHESTRATOR_TYPES =
            findAllDerivedClasses(DraftOrchestrator.class).stream()
                    .collect(Collectors.toMap(o -> o.getSimpleName(), o -> o));

    public List<String> getKnownDraftableTypes() {
        return KNOWN_DRAFTABLE_TYPES.keySet().stream().sorted().collect(Collectors.toList());
    }

    public Draftable createDraftable(String type) {
        for (String knownType : KNOWN_DRAFTABLE_TYPES.keySet()) {
            if (knownType.toLowerCase().equals(type.toLowerCase())) {
                try {
                    return KNOWN_DRAFTABLE_TYPES
                            .get(knownType)
                            .getDeclaredConstructor()
                            .newInstance();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to instantiate draftable of type: " + type, e);
                }
            }
        }

        return null;
    }

    public List<String> getKnownOrchestratorTypes() {
        return KNOWN_ORCHESTRATOR_TYPES.keySet().stream().sorted().collect(Collectors.toList());
    }

    public DraftOrchestrator createOrchestrator(String type) {
        for (String knownType : KNOWN_ORCHESTRATOR_TYPES.keySet()) {
            if (knownType.toLowerCase().equals(type.toLowerCase())) {
                try {
                    return KNOWN_ORCHESTRATOR_TYPES
                            .get(knownType)
                            .getDeclaredConstructor()
                            .newInstance();
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
                    Constructor<?> constructor = subtype.getDeclaredConstructor();
                    if (constructor == null) {
                        continue;
                    }
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
