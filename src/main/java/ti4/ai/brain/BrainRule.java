package ti4.ai.brain;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;

@FunctionalInterface
public interface BrainRule extends Function<AiTurnContext, Optional<AiDecision>>, Serializable {

    record Named(String name, String spanName, BrainRule rule) {

        Named(String name, BrainRule rule) {
            this(name, "rule." + name, rule);
        }
    }

    static List<Named> named(List<BrainRule> rules) {
        List<Named> named = new ArrayList<>(rules.size());
        for (int index = 0; index < rules.size(); index++) {
            named.add(new Named(nameOf(rules.get(index), index), rules.get(index)));
        }
        return List.copyOf(named);
    }

    private static String nameOf(BrainRule rule, int index) {
        try {
            Method writeReplace = rule.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            SerializedLambda lambda = (SerializedLambda) writeReplace.invoke(rule);
            return StringUtils.substringAfterLast("/" + lambda.getImplClass(), "/") + "." + lambda.getImplMethodName();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "rule" + index;
        }
    }
}
