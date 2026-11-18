package com.thinkfree.drive.mcp.config;

import com.thinkfree.drive.mcp.tool.AdminApiKeyTools;
import com.thinkfree.drive.mcp.tool.AdminPendingDeleteUserTools;
import com.thinkfree.drive.mcp.tool.AdminTools;
import com.thinkfree.drive.mcp.tool.AdminUserTools;
import com.thinkfree.drive.mcp.tool.DriveFileTools;
import com.thinkfree.drive.mcp.tool.DriveFolderTools;
import com.thinkfree.drive.mcp.tool.DriveRecentTools;
import com.thinkfree.drive.mcp.tool.DriveResourceTools;
import com.thinkfree.drive.mcp.tool.DriveShareTools;
import com.thinkfree.drive.mcp.tool.DriveStarredTools;
import com.thinkfree.drive.mcp.tool.DriveTrashTools;
import com.thinkfree.drive.mcp.tool.DriveVersionTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Tool 메서드는 컴포넌트 스캔으로 수집되지 않는다 —
 * ToolCallbackProvider 빈으로 명시 등록해야 tools/list에 노출된다.
 * 툴 클래스가 늘어나면 toolObjects(...)에 나열한다.
 */
@Configuration
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider driveToolCallbackProvider(DriveResourceTools driveResourceTools,
                                                          DriveRecentTools driveRecentTools,
                                                          DriveStarredTools driveStarredTools,
                                                          DriveTrashTools driveTrashTools,
                                                          DriveShareTools driveShareTools,
                                                          DriveFolderTools driveFolderTools,
                                                          DriveFileTools driveFileTools,
                                                          DriveVersionTools driveVersionTools,
                                                          AdminTools adminTools,
                                                          AdminUserTools adminUserTools,
                                                          AdminPendingDeleteUserTools adminPendingDeleteUserTools,
                                                          AdminApiKeyTools adminApiKeyTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(driveResourceTools, driveRecentTools, driveStarredTools, driveTrashTools, driveShareTools, driveFolderTools, driveFileTools, driveVersionTools,
                        adminTools, adminUserTools, adminPendingDeleteUserTools, adminApiKeyTools)
                .build();
    }
}
