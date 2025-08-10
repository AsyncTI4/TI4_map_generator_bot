package ti4.helpers;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Arrays;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

public class ToStringHelper {

    public static ToStringHelper of(Object self) {
        return new ToStringHelper(self.getClass().getSimpleName());
    }

    public static ToStringHelper of(Class<?> clazz) {
        return new ToStringHelper(clazz.getSimpleName());
    }

    public static ToStringHelper of(String className) {
        return new ToStringHelper(className);
    }

    private final String className;
    private final ValueHolder holderHead;
    private ValueHolder holderTail;
    private boolean omitNullValues;

    private ToStringHelper(String className) {
        holderHead = new ValueHolder();
        holderTail = holderHead;
        omitNullValues = false;
        this.className = Objects.requireNonNull(className);
    }

    @CanIgnoreReturnValue
    public ToStringHelper omitNullValues() {
        omitNullValues = true;
        return this;
    }

    @CanIgnoreReturnValue
    public ToStringHelper add(String name, @Nullable Object value) {
        return addHolder(name, value);
    }

    @CanIgnoreReturnValue
    public ToStringHelper add(String name, boolean value) {
        return addHolder(name, String.valueOf(value));
    }

    @CanIgnoreReturnValue
    public ToStringHelper add(String name, char value) {
        return addHolder(name, String.valueOf(value));
    }

    @CanIgnoreReturnValue
    public ToStringHelper add(String name, double value) {
        return addHolder(name, String.valueOf(value));
    }

    @CanIgnoreReturnValue
    public ToStringHelper add(String name, float value) {
        return addHolder(name, String.valueOf(value));
    }

    @CanIgnoreReturnValue
    public ToStringHelper add(String name, int value) {
        return addHolder(name, String.valueOf(value));
    }

    @CanIgnoreReturnValue
    public ToStringHelper add(String name, long value) {
        return addHolder(name, String.valueOf(value));
    }

    @CanIgnoreReturnValue
    public ToStringHelper addValue(@Nullable Object value) {
        return addHolder(value);
    }

    @CanIgnoreReturnValue
    public ToStringHelper addValue(boolean value) {
        return addHolder(String.valueOf(value));
    }

    @CanIgnoreReturnValue
    public ToStringHelper addValue(char value) {
        return addHolder(String.valueOf(value));
    }

    @CanIgnoreReturnValue
    public ToStringHelper addValue(double value) {
        return addHolder(String.valueOf(value));
    }

    @CanIgnoreReturnValue
    public ToStringHelper addValue(float value) {
        return addHolder(String.valueOf(value));
    }

    @CanIgnoreReturnValue
    public ToStringHelper addValue(int value) {
        return addHolder(String.valueOf(value));
    }

    @CanIgnoreReturnValue
    public ToStringHelper addValue(long value) {
        return addHolder(String.valueOf(value));
    }

    public String toString() {
        boolean omitNullValuesSnapshot = omitNullValues;
        String nextSeparator = "";
        StringBuilder builder = (new StringBuilder(32)).append(className).append('{');

        for (ValueHolder valueHolder = holderHead.next; valueHolder != null; valueHolder = valueHolder.next) {
            Object value = valueHolder.value;
            if (!omitNullValuesSnapshot || value != null) {
                builder.append(nextSeparator);
                nextSeparator = ", ";
                if (valueHolder.name != null) {
                    builder.append(valueHolder.name).append('=');
                }

                if (value != null && value.getClass().isArray()) {
                    Object[] objectArray = {value};
                    String arrayString = Arrays.deepToString(objectArray);
                    builder.append(arrayString, 1, arrayString.length() - 1);
                } else {
                    builder.append(value);
                }
            }
        }

        return builder.append('}').toString();
    }

    private ValueHolder addHolder() {
        ValueHolder valueHolder = new ValueHolder();
        holderTail = holderTail.next = valueHolder;
        return valueHolder;
    }

    private ToStringHelper addHolder(@Nullable Object value) {
        ValueHolder valueHolder = addHolder();
        valueHolder.value = value;
        return this;
    }

    private ToStringHelper addHolder(String name, @Nullable Object value) {
        ValueHolder valueHolder = addHolder();
        valueHolder.value = value;
        valueHolder.name = Objects.requireNonNull(name);
        return this;
    }

    private static final class ValueHolder {

        @Nullable
        String name;

        @Nullable
        Object value;

        @Nullable
        ValueHolder next;
    }
}
