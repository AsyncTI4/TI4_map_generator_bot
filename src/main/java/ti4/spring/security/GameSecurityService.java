package ti4.spring.security;

import org.springframework.stereotype.Component;
import ti4.map.persistence.GameManager;
import ti4.spring.context.RequestContext;

@Component("security")
class GameSecurityService {

    public boolean canAccessGame(String gameName) {
        String contextUserId = RequestContext.getUserId();
        if (!GameManager.isValid(gameName)) throw new UserNotInGameForbiddenException(gameName, contextUserId);

        var managedGame = GameManager.getManagedGame(gameName);
        var gamePlayerIds = managedGame.getPlayerIds();
        if (!gamePlayerIds.contains(contextUserId)) throw new UserNotInGameForbiddenException(gameName, contextUserId);
        return true;
    }
}
