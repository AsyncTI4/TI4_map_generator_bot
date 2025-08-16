package ti4.spring.service.auth;

import org.springframework.stereotype.Component;
import ti4.map.persistence.GameManager;
import ti4.spring.exception.InvalidGameNameException;
import ti4.spring.exception.UserNotInGameForbiddenException;

@Component("security")
class GameSecurityService {

    public boolean canAccessGame(String gameName) {
        if (!GameManager.isValid(gameName)) throw new InvalidGameNameException(gameName);

        String contextUserId = RequestContext.getUserId();
        var managedGame = GameManager.getManagedGame(gameName);
        var gamePlayerIds = managedGame.getPlayerIds();
        if (!gamePlayerIds.contains(contextUserId)) throw new UserNotInGameForbiddenException(gameName, contextUserId);
        return true;
    }
}
