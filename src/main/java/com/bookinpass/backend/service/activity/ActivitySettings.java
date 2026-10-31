package com.bookinpass.backend.service.activity;

import com.bookinpass.backend.entity.AppSetting;
import com.bookinpass.backend.repository.AppSettingRepository;
import com.bookinpass.backend.repository.StaffUserRepository;
import com.bookinpass.backend.staff.StaffPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Réglages des activités gardés en base ({@code app_setting}), modifiables au back-office. */
@Service
@RequiredArgsConstructor
public class ActivitySettings {

    static final String AUTO_MODERATION = "reviews.auto_moderation";

    private final AppSettingRepository settings;
    private final StaffUserRepository users;

    /** Avis jugés sans problème par Claude publiés tout seuls (et abus évidents rejetés). */
    @Transactional(readOnly = true)
    public boolean autoModeration() {
        return settings.findById(AUTO_MODERATION).map(s -> Boolean.parseBoolean(s.getValue())).orElse(false);
    }

    @Transactional
    public void autoModeration(boolean enabled, StaffPrincipal by) {
        AppSetting setting = settings.findById(AUTO_MODERATION).orElseGet(() -> settings.save(new AppSetting(AUTO_MODERATION, "false")));
        setting.change(Boolean.toString(enabled), users.getReferenceById(by.id()));
    }
}
