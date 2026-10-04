package ti4.service.leader.agent.modules;

import static org.assertj.core.api.Assertions.assertThat;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.buttons.Buttons;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.testUtils.BaseTi4Test;

// Labels are the ones the call sites used before the modules owned them; ids keep the legacy handler id behind
// the owner prefix, so buttons already posted without the prefix keep routing to the same handler.
class AgentOfferButtonsTest extends BaseTi4Test {

    @Test
    void augersOffersToUseItOnTheExplorer() {
        AgentModuleFixture fixture = new AgentModuleFixture("augers");

        assertOffer(
                AugersAgent.offer(fixture.user, fixture.target),
                "FFCC_augers_exhaustAgent_augersagent_pi_hacan",
                "Use Ilyxum Agent on blue",
                FactionEmojis.augers,
                ButtonStyle.SUCCESS);
    }

    @Test
    void sardakkOffersOnePlanet() {
        AgentModuleFixture fixture = new AgentModuleFixture("sardakk");

        assertOffer(
                SardakkAgent.offer(fixture.user, "101", "lodor", "Lodor"),
                "FFCC_sardakk_exhaustAgent_sardakkagent_101_lodor",
                "Use N'orr Agent on Lodor",
                FactionEmojis.Sardakk,
                ButtonStyle.SUCCESS);
    }

    @Test
    void targetGainAgentsKeepTheirLabelsAndColours() {
        assertSelfOffer(
                "cymiae",
                CymiaeAgent.offer(new AgentModuleFixture("cymiae").user),
                "cymiaeagent_cymiae",
                "Use Cymiae Agent",
                FactionEmojis.cymiae,
                ButtonStyle.SECONDARY);
        assertSelfOffer(
                "gledge",
                GledgeAgent.offer(new AgentModuleFixture("gledge").user),
                "gledgeagent_gledge",
                "Use Gledge Agent",
                FactionEmojis.gledge,
                ButtonStyle.DANGER);
        assertSelfOffer(
                "khrask",
                KhraskAgent.offer(new AgentModuleFixture("khrask").user),
                "khraskagent_khrask",
                "Use Khrask Agent",
                FactionEmojis.khrask,
                ButtonStyle.SECONDARY);
        assertSelfOffer(
                "veldyr",
                VeldyrAgent.offer(new AgentModuleFixture("veldyr").user),
                "veldyragent_veldyr",
                "Use Veldyr Agent",
                FactionEmojis.veldyr,
                ButtonStyle.DANGER);
        assertSelfOffer(
                "winnu",
                WinnuAgent.offer(new AgentModuleFixture("winnu").user),
                "winnuagent",
                "Use Winnu Agent",
                FactionEmojis.Winnu,
                ButtonStyle.DANGER);
        assertSelfOffer(
                "vaden",
                VadenAgent.offer(new AgentModuleFixture("vaden").user),
                "vadenagent_vaden",
                "Use Vaden Agent",
                FactionEmojis.vaden,
                ButtonStyle.SECONDARY);
        assertSelfOffer(
                "mirveda",
                MirvedaAgent.offer(new AgentModuleFixture("mirveda").user),
                "mirvedaagent_mirveda",
                "Use Mirveda Agent",
                FactionEmojis.mirveda,
                ButtonStyle.SECONDARY);
        assertSelfOffer(
                "nokar",
                NokarAgent.offer(new AgentModuleFixture("nokar").user),
                "nokaragent_nokar",
                "Use Nokar Agent to Place 1 Destroyer",
                FactionEmojis.nokar,
                ButtonStyle.SECONDARY);
        assertSelfOffer(
                "kalora",
                KaloraAgent.offer(new AgentModuleFixture("kalora").user),
                "kaloraagent",
                "Use Valzor, the Kalora Agent",
                FactionEmojis.kalora,
                ButtonStyle.SECONDARY);
        assertSelfOffer(
                "lunarium",
                LunariumAgent.offer(new AgentModuleFixture("lunarium").user),
                "lunariumagent",
                "Use Lunarium Agent",
                FactionEmojis.lunarium,
                ButtonStyle.DANGER);
    }

    @Test
    void targetedOffersCarryTheTarget() {
        AgentModuleFixture mentak = new AgentModuleFixture("mentak");
        assertOffer(
                MentakAgent.offer(mentak.user, mentak.target),
                "FFCC_mentak_exhaustAgent_mentakagent_pi_hacan",
                "Use Mentak Agent",
                FactionEmojis.Mentak,
                ButtonStyle.SUCCESS);

        AgentModuleFixture kortali = new AgentModuleFixture("kortali");
        assertOffer(
                KortaliAgent.offer(kortali.user, kortali.target),
                "FFCC_kortali_exhaustAgent_kortaliagent_blue",
                "Use Kortali Agent",
                FactionEmojis.kortali,
                ButtonStyle.SECONDARY);
    }

    @Test
    void vaylerianOffersWithOrWithoutATarget() {
        AgentModuleFixture fixture = new AgentModuleFixture("vaylerian");

        assertOffer(
                VaylerianAgent.offer(fixture.user),
                "FFCC_vaylerian_exhaustAgent_vaylerianagent",
                "Use Vaylerian Agent",
                FactionEmojis.vaylerian,
                ButtonStyle.SECONDARY);
        assertOffer(
                VaylerianAgent.offer(fixture.user, fixture.user),
                "FFCC_vaylerian_exhaustAgent_vaylerianagent_vaylerian",
                "Use Vaylerian Agent",
                FactionEmojis.vaylerian,
                ButtonStyle.SECONDARY);
    }

    @Test
    void zelianSaysYourselfOnlyWhenUsedOnTheOwner() {
        AgentModuleFixture fixture = new AgentModuleFixture("zelian");

        assertOffer(
                ZelianAgent.offer(fixture.user, fixture.user),
                "FFCC_zelian_exhaustAgent_zelianagent_zelian",
                "Use Zelian Agent Yourself",
                FactionEmojis.zelian,
                ButtonStyle.SECONDARY);
        assertThat(ZelianAgent.offer(fixture.user, fixture.target).getLabel()).isEqualTo("Use Zelian Agent on blue");
    }

    @Test
    void aPlayerWhoOnlyHasTheYssarilCopyIsOfferedCleverClever() {
        AgentModuleFixture fixture = new AgentModuleFixture("yssaril");

        assertThat(CymiaeAgent.offer(fixture.user).getLabel()).isEqualTo("Use Clever Clever Cymiae Agent");
        assertThat(SardakkAgent.offer(fixture.user, "101", "lodor", "Lodor").getLabel())
                .isEqualTo("Use Clever Clever N'orr Agent on Lodor");
    }

    @Test
    void everyOfferFitsDiscordsLimits() {
        AgentModuleFixture fixture = new AgentModuleFixture("yssaril");

        for (Button offer : new Button[] {
            AugersAgent.offer(fixture.user, fixture.target),
            MentakAgent.offer(fixture.user, fixture.target),
            NokarAgent.offer(fixture.user),
            KaloraAgent.offer(fixture.user),
            ZelianAgent.offer(fixture.user, fixture.target),
            SardakkAgent.offer(fixture.user, "999", "mallicelocked", "Mallice (Locked)")
        }) {
            assertThat(offer.getCustomId()).hasSizeLessThanOrEqualTo(100);
            assertThat(offer.getLabel()).hasSizeLessThanOrEqualTo(80);
        }
    }

    private static void assertSelfOffer(
            String ownerFaction, Button offer, String legacyIdSuffix, String label, TI4Emoji emoji, ButtonStyle style) {
        assertOffer(offer, "FFCC_" + ownerFaction + "_exhaustAgent_" + legacyIdSuffix, label, emoji, style);
    }

    private static void assertOffer(Button offer, String id, String label, TI4Emoji emoji, ButtonStyle style) {
        assertThat(offer.getCustomId()).isEqualTo(id);
        assertThat(offer.getLabel()).isEqualTo(label);
        assertThat(offer.getEmoji())
                .isEqualTo(Buttons.gray("probe", "probe", emoji).getEmoji());
        assertThat(offer.getStyle()).isEqualTo(style);
    }
}
