package com.thinkfree.drive.api.v1.user.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.user.enums.DefaultLandingPage;
import com.thinkfree.drive.user.enums.UserLanguage;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@AllArgsConstructor
public class UserUpdateRequest {

    @Schema(title = "Phone number", description = "User's phone number. Send it together with the country calling code ct. The value replaces the stored one; leaving it out clears the stored phone number.")
    private String pn;                            // phoneNumber
    @NotNull
    @Schema(title = "Interface language", description = "Language used by Drive. auto follows the browser's language.")
    private UserLanguage ul;                      // userLanguage
    @Schema(title = "Profile image file", description = "Choose a local image to use as the user's profile picture.")
    private MultipartFile pif;                   // profileImageFile
    @NotNull
    @Schema(title = "Start page", description = "Page to open after sign-in: my-drive or home.")
    private DefaultLandingPage dlp;               // defaultLandingPage
    @Schema(title = "Country calling code", description = "Country calling code for the phone number. Send it together with pn. The value replaces the stored one; leaving it out clears the stored country code.")
    private String ct;                            // countryCode
    @Schema(title = "Remove profile image", description = "Set true to remove the current profile image. Omit or use false to keep it.")
    private Boolean dpi;                          // deleteProfileImage — 생략 시 false(이미지 유지)
}
