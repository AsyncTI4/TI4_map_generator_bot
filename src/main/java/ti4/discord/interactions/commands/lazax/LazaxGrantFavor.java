package ti4.discord.interactions.commands.lazax;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.service.CombatReplayHouseFavorService;
import ti4.discord.interactions.commands.Subcommand;
import ti4.message.MessageHelper;
import ti4.spring.context.SpringContext;

class LazaxGrantFavor extends Subcommand {

    private static final String HOUSE = "house";
    private static final String AMOUNT = "amount";

    LazaxGrantFavor() {
        super("grant_favor", "Adjust Favor for a Lazax delegation.");
        addOptions(new OptionData(OptionType.STRING, HOUSE, "Delegation to receive Favor", true)
                .addChoice(CombatReplayHouse.HACAN.displayName(), CombatReplayHouse.HACAN.name())
                .addChoice(CombatReplayHouse.MENTAK.displayName(), CombatReplayHouse.MENTAK.name())
                .addChoice(CombatReplayHouse.NAALU.displayName(), CombatReplayHouse.NAALU.name()));
        addOptions(new OptionData(OptionType.INTEGER, AMOUNT, "Favor to add or remove", true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!LazaxCommandAuthorization.isSeasonAdmin(event)) {
            LazaxReplyHelper.replyEphemeral(event, "You are not authorized to grant Lazax Favor.");
            return;
        }

        CombatReplayHouse house = CombatReplayHouse.fromName(event.getOption(HOUSE, "", OptionMapping::getAsString));
        int amount = event.getOption(AMOUNT, 0, OptionMapping::getAsInt);
        if (house == null || amount == 0) {
            LazaxReplyHelper.replyEphemeral(event, "Choose a delegation and a non-zero Favor amount.");
            return;
        }

        CombatReplayHouseFavorService.FavorLedger ledger =
                SpringContext.getBean(CombatReplayHouseFavorService.class).adjustFavorForAdmin(house, amount);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), grantFavorMessage(house, amount, ledger));
        LazaxReplyHelper.replyEphemeral(
                event,
                "Adjusted "
                        + house.displayName()
                        + " Delegation Favor by `"
                        + formatSignedAmount(amount)
                        + "`. Available Favor is now `"
                        + ledger.balance()
                        + "`.");
    }

    @Override
    public boolean isEphemeral(SlashCommandInteractionEvent event) {
        return true;
    }

    static String grantFavorMessage(
            CombatReplayHouse house, int amount, CombatReplayHouseFavorService.FavorLedger ledger) {
        return "## Favor Granted\n"
                + house.displayName()
                + " Delegation "
                + adjustmentVerb(amount)
                + " `"
                + formatSignedAmount(amount)
                + "` Favor.\n"
                + "**Total Favor:** `"
                + ledger.balance()
                + "`";
    }

    private static String adjustmentVerb(int amount) {
        return amount < 0 ? "loses" : "receives";
    }

    private static String formatSignedAmount(int amount) {
        return amount > 0 ? "+" + amount : Integer.toString(amount);
    }
}
