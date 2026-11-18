package com.thinkfree.drive.userlog.service;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.record.Country;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.InetAddress;

@Service
public class IpLocationService {

    private final DatabaseReader dbReader;

    public IpLocationService() throws Exception {
        InputStream dbStream = getClass().getClassLoader().getResourceAsStream("geoip/GeoLite2-Country.mmdb");
        if (dbStream == null) {
            throw new IllegalStateException("GeoIP DB file not found in classpath!");
        }
        this.dbReader = new DatabaseReader.Builder(dbStream).build();
    }

    public String getCountryFromIp(String ip) {
        try {
            InetAddress ipAddress = InetAddress.getByName(ip);
            CountryResponse response = dbReader.country(ipAddress);

            String country = response.getCountry().getName();        // e.g., "South Korea"
            String countryCode = response.getCountry().getIsoCode(); // e.g., "KR"

            return country + " (" + countryCode + ")";
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    public Country getCountry(String ip) {
        try {
            InetAddress ipAddress = InetAddress.getByName(ip);
            CountryResponse countryResponse = dbReader.country(ipAddress);
            return countryResponse.getCountry();
        } catch (Exception e) {
            return null;
        }
    }
}
