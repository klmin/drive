package com.thinkfree.drive.application.folder;

import com.thinkfree.drive.application.folder.command.FolderCreateCommand;
import com.thinkfree.drive.application.folder.converter.FolderCommandConverter;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.resource.exception.ResourceErrorCode;
import com.thinkfree.drive.resource.service.ResourceRetryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("폴더 생성 이름 규칙 단위테스트")
class FolderUseCaseNameRuleTest {

    @Mock private FolderCommandConverter folderCommandConverter;
    @Mock private ResourceRetryService resourceRetryService;
    @Mock private ResourceAuditService resourceAuditService;

    @InjectMocks
    private FolderUseCase folderUseCase;

    @ParameterizedTest
    @ValueSource(strings = {"x/y\\z:*?\"<>|", ".", "..", "trail ", " lead", ".hidden", "end.", "CON", "lpt1"})
    @DisplayName("규칙 위반 이름은 저장 전에 거절")
    void rejectInvalidName(String folderName) {
        var command = FolderCreateCommand.builder().folderName(folderName).build();

        assertThatThrownBy(() -> folderUseCase.create(command, 1))
                .isInstanceOf(ApiRuntimeException.class)
                .extracting("code").isEqualTo(ResourceErrorCode.INVALID_NAME);

        verify(folderCommandConverter, never()).toFolderUploadDto(any(FolderCreateCommand.class), anyInt());
        verifyNoInteractions(resourceRetryService, resourceAuditService);
    }
}
