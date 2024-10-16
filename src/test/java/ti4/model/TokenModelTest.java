package ti4.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import ti4.generator.Mapper;
import ti4.testUtils.BaseTi4Test;

public class TokenModelTest extends BaseTi4Test {
    private static String error(TokenModel token, String descr) {
        return "Error on token [" + token.getAlias() + "]: " + descr;
    }

    @Test
    void testTokens() {
        beforeAll();
        List<TokenModel> tokens = new ArrayList<>(Mapper.getTokens2());
        assertTrue(tokens.size() > 0, "Did not import any tokens");

        Map<String, Predicate<TokenModel>> validators = new LinkedHashMap<>();
        validators.put("E1", t -> t.isValid());
        validators.put("E2", TokenModelTest::tokenExistsElsewhere);
        validators.put("E3", TokenModelTest::tokenComplete);

        for (TokenModel token : tokens)
            for (Entry<String, Predicate<TokenModel>> e : validators.entrySet())
                assertTrue(e.getValue().test(token), error(token, e.getKey()));
        for (String token : Mapper.getTokens()) {
            //assertTrue(tokenIsTokenModel(token), "Error: " + token + " is not represented in TokenModel.");
        }
    }

    private static boolean tokenExistsElsewhere(TokenModel token) {
        if (Mapper.getTokens().contains(token.getAlias()))
            return true;
        return false;
    }

    private static boolean tokenComplete(TokenModel token) {
        if (Mapper.getTokens().contains(token.getAlias()))
            return true;
        return false;
    }

    private static boolean tokenIsTokenModel(String token) {
        return Mapper.getTokens2().stream().filter(tok -> tok.getAlias().equalsIgnoreCase(token)).findAny().isPresent();
    }
}
