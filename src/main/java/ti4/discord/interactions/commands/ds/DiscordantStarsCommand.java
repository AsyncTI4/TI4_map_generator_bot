package ti4.discord.interactions.commands.ds;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class DiscordantStarsCommand extends AbstractParentCommand {

    public DiscordantStarsCommand() {
        super(
                Constants.DS_COMMAND,
                "Discordant Stars Commands",
                new ZelianHero(),
                new TrapToken(),
                new TrapReveal(),
                new TrapSwap(),
                new FlipGrace(),
                new SetPolicy(),
                new DrawBlueBackTile(),
                new DrawRedBackTile(),
                new AddOmenDie(),
                new KyroHero(),
                new ATS(),
                new PathToken(),
                SetPlayerCounter.honor(),
                SetPlayerCounter.dishonor(),
                SetPlayerCounter.steelBalance(),
                SetPlayerCounter.starBalance(),
                new SetPlanetTradeGoods());
    }
}
