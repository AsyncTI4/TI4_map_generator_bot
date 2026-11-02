package ti4.image.playerarea;

import java.util.List;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.oblivion.OblivionAbilityHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.stoneborn.StonebornAbilityHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.xytheris.XytherisAbilityHandler;
import ti4.game.Player;

final class TheodisiTokenSupplies {

    private static final int FRAGMENTUM_TOKENS_PER_TYPE = 3;
    private static final List<String> FRAGMENTUM_TYPES = List.of("cultural", "hazardous", "industrial", "frontier");
    private static final List<String> FRAGMENTUM_COLOURS = List.of("blue", "red", "green", "gray");

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
        if (player.hasAbility("fragmentum_unleashed")) {
            for (int i = 0; i < FRAGMENTUM_TYPES.size(); i++) {
                xDeltaFromRight = TokenSupply.drawWideZigzag(
                        ctx,
                        slot,
                        "token_theodisi_fragmentum" + FRAGMENTUM_COLOURS.get(i) + ".png",
                        FRAGMENTUM_TOKENS_PER_TYPE,
                        StonebornAbilityHandler.getFragmentumTokenCount(ctx.game(), player, FRAGMENTUM_TYPES.get(i)),
                        xDeltaFromRight,
                        y);
            }
        }
        return xDeltaFromRight;
    }
}
