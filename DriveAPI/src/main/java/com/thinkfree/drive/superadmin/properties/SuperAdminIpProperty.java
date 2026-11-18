package com.thinkfree.drive.superadmin.properties;

import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

@Validated
@ConfigurationProperties("drive.super-admin")
public record SuperAdminIpProperty
(
        List<@Pattern(
                regexp = "^((25[0-5]|2[0-4]\\d|[01]?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d?\\d)(/(3[0-2]|[12]?\\d))?$",
                message = "drive.super-admin.allowed-ips 항목은 유효한 IPv4 또는 CIDR 형식이어야 합니다"
        ) String> allowedIps
)
{
        public SuperAdminIpProperty {
            if (allowedIps != null) {
                    allowedIps = allowedIps.stream().map(String::trim).toList();
                    allowedIps.forEach(SuperAdminIpProperty::validateNetworkAddress);
            }
        }

        private static void validateNetworkAddress(String cidr) {
            String[] parts = cidr.split("/");
            if (parts.length == 1) {
                    return;
            }
            int prefix = Integer.parseInt(parts[1]);
            if (prefix == 0) {
                    throw new IllegalArgumentException(
                            cidr + ": 0.0.0.0/0(전체 허용)은 SuperAdmin 화이트리스트로 허용되지 않습니다");
            }
            try {
                byte[] addr = InetAddress.getByName(parts[0]).getAddress();
                long ipInt = ((long) (addr[0] & 0xFF) << 24)
                                | ((long) (addr[1] & 0xFF) << 16)
                                | ((long) (addr[2] & 0xFF) << 8)
                                | (addr[3] & 0xFF);
                long mask = (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
                if ((ipInt & mask) != ipInt) {
                        throw new IllegalArgumentException(
                                cidr + ": 네트워크 주소가 아닙니다 (host bit이 0이어야 함)");
                }
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("잘못된 IP: " + cidr, e);
            }
        }
}
