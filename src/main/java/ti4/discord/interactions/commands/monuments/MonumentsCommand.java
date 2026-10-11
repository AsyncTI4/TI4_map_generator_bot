package ti4.discord.interactions.commands.monuments;

import ti4.discord.interactions.commands.AbstractParentCommand;

public class MonumentsCommand extends AbstractParentCommand {

    public MonumentsCommand() {
        super(
                "monument",
                "Monument",
                MonumentToggle.ready(),
                MonumentToggle.exhaust(),
                new BelkoseaToken(BelkoseaToken.Action.ADD),
                new BelkoseaToken(BelkoseaToken.Action.REMOVE),
                new BelkoseaToken(BelkoseaToken.Action.MOVE));
    }
}
