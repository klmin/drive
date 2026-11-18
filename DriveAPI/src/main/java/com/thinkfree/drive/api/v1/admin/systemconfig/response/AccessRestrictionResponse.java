package com.thinkfree.drive.api.v1.admin.systemconfig.response;

import java.util.List;

public record AccessRestrictionResponse(
        boolean enabled,
        List<String> allowedCountries,
        List<String> allowedIps
) {
}
