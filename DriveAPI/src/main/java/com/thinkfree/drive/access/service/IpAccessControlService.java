
package com.thinkfree.drive.access.service;
import com.maxmind.geoip2.record.Country;
import com.thinkfree.drive.util.LogSafe;
import com.thinkfree.drive.access.dto.IpAccessCheckCommand;
import com.thinkfree.drive.access.exception.AccessErrorCode;
import com.thinkfree.drive.accessrestriction.service.AccessAllowedCountryService;
import com.thinkfree.drive.accessrestriction.service.AccessAllowedIpService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.systemconfig.service.SystemConfigFindService;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.userlog.service.IpLocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
@Slf4j
@Service
@RequiredArgsConstructor
public class IpAccessControlService {

    private final IpLocationService ipLocationService;
    private final AccessAllowedCountryService accessAllowedCountryService;
    private final AccessAllowedIpService accessAllowedIpService;
    private final SystemConfigFindService systemConfigFindService;

    public void checkAllowed(IpAccessCheckCommand command) {
        
        Integer tenantSeq = command.tenantSeq();
        String clientIp = command.clientIp();
        RoleType role = command.roleType();
        
        // ADMIN: IP만 체크 (슈퍼어드민이 어드민 접근 IP 화이트리스트 관리, country 미사용)
        if (role == RoleType.ADMIN) {
            List<String> allowedIpCidrs = accessAllowedIpService.findAllIpCidrs(tenantSeq, RoleType.ADMIN);
            checkIpOnly(clientIp, allowedIpCidrs);
            return;
        }
        
        boolean enabled = systemConfigFindService.findAccessRestrictionStatusConfig(tenantSeq).isEnabled();
        if (!enabled) {
            return;
        }
        
        List<String> allowedIpCidrs = accessAllowedIpService.findAllIpCidrs(tenantSeq, RoleType.USER);

        // USER: IP + Country 체크 (테넌트 어드민이 사용자 접근 IP/국가 화이트리스트 관리)
        List<String> allowedCountryCodes = accessAllowedCountryService.findAllCountryCodes(tenantSeq);
        if (allowedIpCidrs.isEmpty() && allowedCountryCodes.isEmpty()) {
            return;
        }
        Country country = ipLocationService.getCountry(clientIp);
        boolean hasCountryCode = country != null && country.getIsoCode() != null;
        // IP 체크: 허용 IP 목록이 있으면 먼저 확인. 일치하면 국가 관계없이 통과 (GeoIP 오판 대응)
        if (!allowedIpCidrs.isEmpty()) {
            boolean ipAllowed = allowedIpCidrs.stream().anyMatch(cidr -> isIpInCidr(clientIp, cidr));
            if (ipAllowed) {
                return;
            }
            // IP 목록에 없을 때: 국가 목록도 없거나 국가 판별 불가(사설 IP 등)이면 차단
            if (allowedCountryCodes.isEmpty() || !hasCountryCode) {
                throw ApiRuntimeException.type(AccessErrorCode.BLOCKED_IP);
            }
        }
        
        // 국가 체크: IP 목록이 없거나 IP 목록 불일치 후 넘어온 경우
        // 사설 IP(국가 없음) + 국가 목록만 있는 경우 → hasCountryCode=false → 스킵 → 통과
        if (!allowedCountryCodes.isEmpty() && hasCountryCode) {
            String countryCode = country.getIsoCode();
            log.info("[IpAccessControlService] ip : {}, countryName : {}, countryCode : {}", LogSafe.of(clientIp), country.getName(), countryCode);
            if (!allowedCountryCodes.contains(countryCode)) {
                throw ApiRuntimeException.type(AccessErrorCode.BLOCKED_COUNTRY);
            }
        }
    }

    private void checkIpOnly(String clientIp, List<String> allowedIpCidrs) {
        if (allowedIpCidrs.isEmpty()) {
            return;
        }
        boolean ipAllowed = allowedIpCidrs.stream().anyMatch(cidr -> isIpInCidr(clientIp, cidr));
        if (!ipAllowed) {
            throw ApiRuntimeException.type(AccessErrorCode.BLOCKED_IP);
        }
    }

    // 1. "192.192.1.10" vs "192.192.1.0/24" 검사한다고 가정하면 아래와 같은 흐름으로 IP 접근 허용 여부 계산함.
    private boolean isIpInCidr(String ip, String cidr) {
        String[] parts = cidr.split("/"); // ["192.192.1.0", "24"]
        String networkAddress = parts[0];
        int prefix = Integer.parseInt(parts[1]);
        try {
            // 2. IP → 32비트 정수 변환
            int ipInt = ipToInt(InetAddress.getByName(ip).getAddress()); // 192.192.1.10
            int networkInt = ipToInt(InetAddress.getByName(networkAddress).getAddress()); // 192.192.1.0
            // 3. prefix=24이면 마스크는 상위 24비트가 1
            // int mask
            // = 0xFFFFFFFF << (32 - 24)
            // = 0xFFFFFF00  (255.255.255.0)
            int mask = prefix == 0 ? 0 : (0xFFFFFFFF << (32 - prefix));
            // "192.192.1.10" vs "192.192.1.0/24"
            // 4. prefix가 24인 경우 마스크 AND → 상위 24비트(네트워크 부분)만 추출해 비교
            // (0xC0C0010A & 0xFFFFFF00) == 0xC0C00100  // 192.192.1.10  → true  (허용)
            // (0xC0C001FF & 0xFFFFFF00) == 0xC0C00100  // 192.192.1.255 → true  (허용)
            // (0xC0C00200 & 0xFFFFFF00) == 0xC0C00100  // 192.192.2.0   → false (차단)
            // 그외 예시 "192.192.5.77" vs "192.192.0.0/16"
            // 마스크 AND → 상위 16비트(앞 2 옥텟)만 비교
            // (0xC0C0054D & 0xFFFF0000) == 0xC0C00000  // 192.192.5.77   → true  (허용)
            // (0xC0C0FFFF & 0xFFFF0000) == 0xC0C00000  // 192.192.255.255→ true  (허용)
            // (0xC0C10000 & 0xFFFF0000) == 0xC0C00000  // 192.193.0.0    → false (차단)
            // 그외 예시 "10.0.0.1" vs "10.0.0.1/32"
            // 마스크 AND → 32비트 전체 비교 → 완전 일치만 허용
            // (0x0A000001 & 0xFFFFFFFF) == 0x0A000001  // 10.0.0.1 → true  (허용)
            // (0x0A000002 & 0xFFFFFFFF) == 0x0A000001  // 10.0.0.2 → false (차단)
            return (ipInt & mask) == (networkInt & mask);
        } catch (UnknownHostException e) {
            return false;
        }
    }
    private int ipToInt(byte[] bytes) {
        return (bytes[0] & 0xFF) << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
    }

}
