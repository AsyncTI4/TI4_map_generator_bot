package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import ti4.spring.service.persistence.GameEntity;
import ti4.spring.service.persistence.GameEntityMapper;
import ti4.spring.service.persistence.PersistedGameState;
import ti4.spring.service.persistence.PlayerEntity;
import ti4.spring.service.persistence.TitleEntity;
import ti4.spring.service.persistence.UserEntity;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

class MappingShapeFingerprintTest {

    // Warmup trusts a stored row, and the nightly reconcile skips a game, only while the row's mapping_version
    // equals GameEntityMapper.MAPPING_VERSION. This test walks the full type tree of everything that is stored
    // for a game (ManagedGameState, the reconciler's PersistedGameState view and the JPA entities), following
    // every nested ti4 type, and hashes it with MAPPING_VERSION. Any added, removed, renamed or retyped field
    // anywhere in that tree fails the test until MAPPING_VERSION is raised and the fingerprint updated.
    // MappingFingerprintTest covers changes to how values are derived.
    private static final String EXPECTED_FINGERPRINT =
            "152cbd98f80a4b7a7b229dafa15e93166aef5a76b5e5767066cfb825b2819e70";
    private static final List<Class<?>> STORED_ROOTS = List.of(
            ManagedGameState.class,
            PersistedGameState.class,
            GameEntity.class,
            PlayerEntity.class,
            TitleEntity.class,
            UserEntity.class);

    @Test
    void storedTypeTreeMatchesTheFingerprintForThisMappingVersion() throws Exception {
        String json = JsonMapper.builder()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build()
                .writeValueAsString(typeTree());
        // Jackson indents with the platform line separator; hash LF so Windows and CI agree.
        String fingerprint =
                sha256("mapping_version=" + GameEntityMapper.MAPPING_VERSION + "\n" + json.replace("\r\n", "\n"));

        assertThat(fingerprint)
                .as("The shape of what is stored for a game changed:\n" + json
                        + "\nRaise GameEntityMapper.MAPPING_VERSION so rows written by older code are rebuilt from"
                        + " their game files, then set EXPECTED_FINGERPRINT to " + fingerprint)
                .isEqualTo(EXPECTED_FINGERPRINT);
    }

    private static Map<String, List<String>> typeTree() {
        Map<String, List<String>> tree = new TreeMap<>();
        Deque<Class<?>> pending = new ArrayDeque<>(STORED_ROOTS);
        while (!pending.isEmpty()) {
            Class<?> type = pending.pop();
            if (tree.containsKey(type.getName())) continue;
            List<String> members = new ArrayList<>();
            for (Map.Entry<String, Type> member : storedMembers(type).entrySet()) {
                members.add(member.getKey() + ": " + member.getValue().getTypeName());
                collectNestedTi4Types(member.getValue(), pending);
            }
            tree.put(type.getName(), members);
        }
        return tree;
    }

    private static Map<String, Type> storedMembers(Class<?> type) {
        Map<String, Type> members = new TreeMap<>();
        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                members.put(component.getName(), component.getGenericType());
            }
            return members;
        }
        if (type.isEnum()) {
            for (Object constant : type.getEnumConstants()) {
                members.put(((Enum<?>) constant).name(), type);
            }
            return members;
        }
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
            members.put(field.getName(), field.getGenericType());
        }
        return members;
    }

    private static void collectNestedTi4Types(Type type, Deque<Class<?>> pending) {
        if (type instanceof ParameterizedType parameterized) {
            collectNestedTi4Types(parameterized.getRawType(), pending);
            for (Type argument : parameterized.getActualTypeArguments()) {
                collectNestedTi4Types(argument, pending);
            }
        } else if (type instanceof Class<?> type1) {
            Class<?> component = type1.isArray() ? type1.getComponentType() : type1;
            if (component.getName().startsWith("ti4.")) pending.push(component);
        }
    }

    private static String sha256(String text) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
