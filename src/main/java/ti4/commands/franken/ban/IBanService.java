package ti4.commands.franken.ban;

import ti4.map.Game;
import ti4.service.franken.FrankenBanList;

public interface IBanService {
    String applyOption(Game game, String optionName, String value);

    String applyBanList(Game game, FrankenBanList banList);
}
