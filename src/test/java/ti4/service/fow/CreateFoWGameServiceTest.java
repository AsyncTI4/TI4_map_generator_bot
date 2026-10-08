package ti4.service.fow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CreateFoWGameServiceTest {

    // A bothelper can type a reserved name into /bothelper create_fow_game_channels; only real fog numbers pass,
    // so a typo can't create a game outside the fog numbering (the command lower-cases the input first).
    @Test
    void onlyFowFollowedByANumberIsAFogGameName() {
        assertThat(CreateFoWGameService.isFowGameName("fow123")).isTrue();
        assertThat(CreateFoWGameService.isFowGameName("fow1")).isTrue();

        assertThat(CreateFoWGameService.isFowGameName("fow")).isFalse();
        assertThat(CreateFoWGameService.isFowGameName("pbd123")).isFalse();
        assertThat(CreateFoWGameService.isFowGameName("fow12a")).isFalse();
        assertThat(CreateFoWGameService.isFowGameName("fow-12")).isFalse();
        assertThat(CreateFoWGameService.isFowGameName("myfow12")).isFalse();
    }
}
