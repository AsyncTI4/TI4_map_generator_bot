package ti4.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;
import org.junit.jupiter.api.Test;

/**
 * Discord shows every private thread to anyone with Manage Threads on the parent channel, so staff roles
 * (Admin/Developer/Bothelper) could read other players' private threads in games they were playing in.
 */
class PrivateThreadVisibilityServiceTest {

    private final IPermissionContainer channel = mock(IPermissionContainer.class);

    @Test
    void staffPlayerWithManageThreadsIsDenied() {
        Member staff = member(false, true);
        assertThat(PrivateThreadVisibilityService.needsThreadManagementDenied(staff, channel))
                .isTrue();
    }

    @Test
    void regularPlayerNeedsNoOverride() {
        Member player = member(false, false);
        assertThat(PrivateThreadVisibilityService.needsThreadManagementDenied(player, channel))
                .isFalse();
    }

    @Test
    void existingDenyIsKeptEvenThoughItHidesTheEffectivePermission() {
        // once denied, hasPermission(channel, MANAGE_THREADS) is false; re-running the fix must not drop the deny
        Member staff = member(false, false);
        PermissionOverride override = mock(PermissionOverride.class);
        when(override.getDenied()).thenReturn(EnumSet.of(Permission.MANAGE_THREADS));
        when(channel.getPermissionOverride(staff)).thenReturn(override);

        assertThat(PrivateThreadVisibilityService.needsThreadManagementDenied(staff, channel))
                .isTrue();
    }

    @Test
    void administratorsCannotBeOverriddenSoAreSkipped() {
        Member admin = member(true, true);
        assertThat(PrivateThreadVisibilityService.needsThreadManagementDenied(admin, channel))
                .isFalse();
    }

    private Member member(boolean administrator, boolean manageThreadsInChannel) {
        Member member = mock(Member.class);
        when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(administrator);
        when(member.hasPermission(channel, Permission.MANAGE_THREADS)).thenReturn(manageThreadsInChannel);
        return member;
    }
}
