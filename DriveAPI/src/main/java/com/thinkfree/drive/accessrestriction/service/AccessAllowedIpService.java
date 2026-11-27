package com.thinkfree.drive.accessrestriction.service;

import com.thinkfree.drive.accessrestriction.cache.AccessPolicyCache;
import com.thinkfree.drive.access.exception.AccessErrorCode;
import com.thinkfree.drive.accessrestriction.entity.AccessAllowedIp;
import com.thinkfree.drive.accessrestriction.repository.AccessAllowedIpRepository;
import com.thinkfree.drive.accessrestriction.repository.AccessAllowedIpQueryRepository;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.user.enums.RoleType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccessAllowedIpService {
    private final AccessAllowedIpRepository accessAllowedIpRepository;
    private final AccessAllowedIpQueryRepository accessAllowedIpQueryRepository;
    private final AccessPolicyCache accessPolicyCache;

    @Transactional
    public void replaceAllowedIps(Integer tenantSeq, RoleType roleType, Integer loginUserSeq, List<String> ips) {
        List<AccessAllowedIp> entities = ips.stream()
                .map(cidr -> parseCidr(tenantSeq, roleType, cidr, loginUserSeq))
                .toList();

        accessPolicyCache.invalidate(tenantSeq);
        accessAllowedIpRepository.deleteByTenantAndRole(tenantSeq, roleType);
        if (!entities.isEmpty()) {
            accessAllowedIpRepository.insertAll(entities);
        }
    }

    @Transactional(readOnly = true)
    public List<String> findAllIpCidrs(Integer tenantSeq, RoleType roleType) {
        return accessAllowedIpQueryRepository.findAllByTenantAndRole(tenantSeq, roleType)
                .stream()
                .map(ip -> ip.getIpAddress() + "/" + ip.getCidrPrefix())
                .toList();
    }

    private int ipToInt(byte[] bytes) {
        return (bytes[0] & 0xFF) << 24
             | (bytes[1] & 0xFF) << 16
             | (bytes[2] & 0xFF) << 8
             | (bytes[3] & 0xFF);
    }

    private AccessAllowedIp parseCidr(Integer tenantSeq, RoleType roleType, String cidr, Integer loginUserSeq) {
        String ipAddress;
        int prefix;

        if (cidr.contains("/")) {
            String[] parts = cidr.split("/", 2);
            ipAddress = parts[0];
            try {
                prefix = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                throw ApiRuntimeException.type(AccessErrorCode.INVALID_IP_CIDR).with("ip", cidr);
            }
        } else {
            ipAddress = cidr;
            prefix = 32;
        }

        if (prefix < 0 || prefix > 32) {
            throw ApiRuntimeException.type(AccessErrorCode.INVALID_IP_CIDR).with("ip", cidr);
        }

        try {
            InetAddress address = InetAddress.getByName(ipAddress);
            if (!(address instanceof Inet4Address)) {
                throw ApiRuntimeException.type(AccessErrorCode.INVALID_IP_CIDR).with("ip", cidr);
            }

            // 호스트 비트가 0이어야 함 (예: 192.168.1.10/24는 거부, 192.168.1.0/24만 허용)
            // /0의 경우 mask=0이므로 0.0.0.0/0만 통과, /32는 mask=0xFFFFFFFF이므로 모든 단일 IP 통과
            byte[] bytes = address.getAddress();
            int networkInt = ipToInt(bytes);
            int mask = prefix == 0 ? 0 : (0xFFFFFFFF << (32 - prefix));
            if ((networkInt & mask) != networkInt) {
                throw ApiRuntimeException.type(AccessErrorCode.INVALID_IP_CIDR).with("ip", cidr);
            }
        } catch (UnknownHostException e) {
            throw ApiRuntimeException.type(AccessErrorCode.INVALID_IP_CIDR).with("ip", cidr);
        }

        return AccessAllowedIp.create(tenantSeq, roleType, ipAddress, prefix, loginUserSeq);
    }
    /** 테넌트 삭제 — 이 테넌트의 행 전부 */
    public void deleteByTenant(Integer tenantSeq) {
        accessPolicyCache.invalidate(tenantSeq);
        accessAllowedIpRepository.deleteByTenant(tenantSeq);
    }
}
