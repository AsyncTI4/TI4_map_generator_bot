package ti4.image.playerarea;

import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.oblivion.OblivionAbilityHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.xytheris.XytherisAbilityHandler;
import ti4.game.Player;

final class TheodisiTokenSupplies {

    private TheodisiTokenSupplies() {}

    static int draw(PlayerAreaContext ctx, AreaSlot slot, Player player, int xDeltaFromRight, int y) {
        if (player.hasAbility("sting_of_the_hive")) {
            xDeltaFromRight = TokenSupply.drawWideZigzag(
                    ctx,
                    slot,
                    "token_theodisi_mine.png",
                    6,
                    XytherisAbilityHandler.getAvailableStingOfTheHiveMines(ctx.game()),
                    xDeltaFromRight,
                    y);
        }
        if (player.hasAbility("reflections_of_the_void")) {
            xDeltaFromRight = TokenSupply.drawWideZigzag(
                    ctx,
                    slot,
                    "token_theodisi_oblivionreflection.png",
                    3,
                    OblivionAbilityHandler.getAvailableReflectionTokens(ctx.game()),
                    xDeltaFromRight,
                    y);
        }
        return xDeltaFromRight;
    }
}
