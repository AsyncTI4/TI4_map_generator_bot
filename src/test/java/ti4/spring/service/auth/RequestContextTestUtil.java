package ti4.spring.service.auth;

import lombok.experimental.UtilityClass;
import ti4.map.Game;

@UtilityClass
public class RequestContextTestUtil {

    public void clearContext() {
        RequestContext.clearContext();
    }

    public void setGame(Game game) {
        RequestContext.setGame(game);
    }
}
