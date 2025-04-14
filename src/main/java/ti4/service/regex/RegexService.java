package ti4.service.regex;

import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ti4.helpers.Constants;
import ti4.message.BotLogger;

public class RegexService {

    public interface CheckedConsumer<T> {
        void accept(T m);
    }

    public interface CheckedPredicate<T> {
        boolean accept(T m) throws Exception;
    }

    private static <T> CheckedPredicate<T> wrap(CheckedConsumer<T> consumer) {
        return t -> {
            consumer.accept(t);
            return true;
        };
    }

    private static void defaultHandleFailure(String buttonID, Exception e) {
        BotLogger.error("Error matching regex: " + buttonID + "\n" + Constants.jazzPing(), e);
    }

    public static void throwFailure() throws Exception {
        throw new Exception("aaaaaahhh");
    }

    public static boolean runMatcher(String regex, String buttonID, CheckedConsumer<Matcher> function) {
        return runMatcher(Pattern.compile(regex), buttonID, wrap(function), e -> defaultHandleFailure(buttonID, e));
    }

    public static boolean runMatcher(Pattern regex, String buttonID, CheckedConsumer<Matcher> function) {
        return runMatcher(regex, buttonID, wrap(function), e -> defaultHandleFailure(buttonID, e));
    }

    public static boolean runMatcher(Pattern regex, String buttonID, CheckedPredicate<Matcher> function) {
        return runMatcher(regex, buttonID, function, e -> defaultHandleFailure(buttonID, e));
    }

    public static boolean runMatcher(String regex, String buttonID, CheckedConsumer<Matcher> function, Consumer<Exception> failure) {
        return runMatcher(Pattern.compile(regex), buttonID, wrap(function), failure);
    }

    public static boolean runMatcher(Pattern regex, String buttonID, CheckedConsumer<Matcher> function, Consumer<Exception> failure) {
        return runMatcher(regex, buttonID, wrap(function), failure);
    }

    public static boolean runMatcher(Pattern regex, String buttonID, CheckedPredicate<Matcher> function, Consumer<Exception> failure) {
        Matcher matcher = regex.matcher(buttonID);
        if (matcher.matches()) {
            try {
                function.accept(matcher);
                return true;
            } catch (Throwable e) {
                BotLogger.error("Exception in matcher for button press:\n> " + buttonID, e);
                return false;
            }
        } else {
            if (failure != null)
                failure.accept(null);
            return false;
        }
    }

}
