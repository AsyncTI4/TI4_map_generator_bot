package ti4.discord.interactions.commands.fow;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class FOWCommand extends AbstractParentCommand {

    public FOWCommand() {
        super(
                Constants.FOW,
                "Fog of War",
                new AddFogTile(),
                new RemoveFogTile(),
                new AddVisionToken(),
                new RemoveVisionToken(),
                new CheckChannels(),
                new PingActivePlayer(),
                new PingSystem(),
                new TargetPlanetPing(),
                new TargetSystemPing(),
                new TargetPlayerPing(),
                new Whisper(),
                new Announce(),
                new FOWOptions(),
                new ShowGameAsPlayer(),
                new PrivateCommunicationsCheck(),
                new GMCommand(),
                new CreateFoWGameButton(),
                new SetupWizard(),
                new MapSegmentCommand(),
                new MapOverviewCommand(),
                new GalaxyCommand(),
                new FowInfo(),
                new FowEndedStats());
    }
}
