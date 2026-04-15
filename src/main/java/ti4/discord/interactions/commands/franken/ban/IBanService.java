package ti4.discord.interactions.commands.franken.ban;

import ti4.game.Game;
import ti4.service.franken.FrankenBanList;

public interface IBanService {
    String applyOption(Game game, String optionName, String value);

    String applyBanList(Game game, FrankenBanList banList);
}
