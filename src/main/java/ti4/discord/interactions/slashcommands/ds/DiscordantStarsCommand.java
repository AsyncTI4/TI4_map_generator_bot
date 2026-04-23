package ti4.discord.interactions.slashcommands.ds;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.discord.interactions.slashcommands.ParentCommand;
import ti4.discord.interactions.slashcommands.Subcommand;
import ti4.helpers.Constants;

public class DiscordantStarsCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
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
                    new HonorCount(),
                    new DishonorCount(),
                    new SteelBalanceCount(),
                    new StarBalanceCount(),
                    new SetPlanetTradeGoods())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.DS_COMMAND;
    }

    @Override
    public String getDescription() {
        return "Discordant Stars Commands";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
