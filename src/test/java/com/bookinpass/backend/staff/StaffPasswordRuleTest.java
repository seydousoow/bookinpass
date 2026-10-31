package com.bookinpass.backend.staff;

import com.bookinpass.backend.exception.StaffException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StaffPasswordRuleTest {

    @Test
    void aPasswordNeedsALetterAndADigit() {
        assertThatCode(() -> StaffAuthService.requireStrong("motdepasse1")).doesNotThrowAnyException();
        assertThatCode(() -> StaffAuthService.requireStrong("éééééééééé1")).doesNotThrowAnyException();
        assertThatThrownBy(() -> StaffAuthService.requireStrong("motdepasses")).isInstanceOf(StaffException.class);
        assertThatThrownBy(() -> StaffAuthService.requireStrong("12345678901")).isInstanceOf(StaffException.class);
        assertThatThrownBy(() -> StaffAuthService.requireStrong(null)).isInstanceOf(StaffException.class);
    }
}
