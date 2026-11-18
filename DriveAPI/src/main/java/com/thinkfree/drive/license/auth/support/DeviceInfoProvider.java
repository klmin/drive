package com.thinkfree.drive.license.auth.support;

import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.systemconfig.data.SystemConfigValueData;
import com.thinkfree.drive.systemconfig.entity.SystemConfig;
import com.thinkfree.drive.systemconfig.enums.SystemConfigKey;
import com.thinkfree.drive.systemconfig.repository.SystemConfigQueryRepository;
import com.thinkfree.drive.systemconfig.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

/**
 * 인증 요청에 싣는 설치 정보.
 *
 * deviceId 는 설치 하나에 값 하나다. 인증 이력은 라이선스 하나에 deviceId 가 둘 이상 보이면 이상 인증으로 보므로
 * 같은 DB 를 쓰는 WAS 들은 같은 값을 보내야 한다. 그래서 DB(tb_system_config)에 한 번 만들어 두고 계속 쓴다.
 * 인증 서버는 마지막 '|' 뒤를 잘라 저장하므로 값에 '|' 를 넣지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceInfoProvider {

    private final SystemConfigRepository systemConfigRepository;
    private final SystemConfigQueryRepository systemConfigQueryRepository;

    private volatile String deviceId;

    public String deviceId() {
        String local = deviceId;
        if (local != null) {
            return local;
        }

        String key = SystemConfigKey.LICENSE_DEVICE_ID.getDbKey();
        SystemConfigValueData config = systemConfigQueryRepository.findGlobalBySettingKey(key);
        if (config == null) {
            // 여러 WAS 가 동시에 처음 넣어도 먼저 들어간 값이 남고, 모두 그 값을 다시 읽는다
            systemConfigRepository.insertGlobalIfAbsent(SystemConfig.createSystemConfig(
                    null, key, UUID.randomUUID().toString(), DriveConstants.SUPER_ADMIN_USER_SEQ));
            config = systemConfigQueryRepository.findGlobalBySettingKey(key);
        }

        deviceId = config.getValueText();
        return deviceId;
    }

    /** 이 서버의 MAC 주소 SHA-256(Base64). 인증 이력에서 서버 수를 셀 때 쓴다. 구하지 못하면 null. */
    public String deviceHash() {
        try {
            NetworkInterface networkInterface = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
            byte[] mac = networkInterface == null ? null : networkInterface.getHardwareAddress();
            if (mac == null) {
                return null;
            }
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(mac));
        } catch (Exception e) {
            log.warn("[ DeviceInfoProvider ] MAC 주소를 구하지 못했다: {}", e.getMessage());
            return null;
        }
    }
}
