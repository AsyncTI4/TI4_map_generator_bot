package ti4.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import ti4.image.Mapper;
import ti4.testUtils.BaseTi4Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TokenModelTest extends BaseTi4Test {
    private static String error(TokenModel token, String descr) {
        return "Error on token [" + token.getAlias() + "]: " + descr;
    }

    @Test
    void testTokens() {
        beforeAll();
        List<TokenModel> tokens = new ArrayList<>(Mapper.getTokens());
        assertFalse(tokens.isEmpty(), "Did not import any tokens");

        Map<String, Predicate<TokenModel>> validators = new LinkedHashMap<>();
        validators.put("E1", TokenModel::isValid);
        validators.put("E2", TokenModelTest::tokenExistsElsewhere);
        validators.put("E3", TokenModelTest::tokenComplete);

        for (TokenModel token : tokens)
            for (Entry<String, Predicate<TokenModel>> e : validators.entrySet())
                assertTrue(e.getValue().test(token), error(token, e.getKey()));
        for (String token : Mapper.getTokensFromproperties()) {
            //assertTrue(tokenIsTokenModel(token), "Error: " + token + " is not represented in TokenModel.");
        }
    }

    private static boolean tokenExistsElsewhere(TokenModel token) {
        return Mapper.getTokensFromproperties().contains(token.getAlias());
    }

    private static boolean tokenComplete(TokenModel token) {
        return Mapper.getTokensFromproperties().contains(token.getAlias());
    }

    private static boolean tokenIsTokenModel(String token) {
        return Mapper.getTokens().stream().anyMatch(tok -> tok.getAlias().equalsIgnoreCase(token));
    }
}
