package com.thinkfree.drive.application.admin.csv;

import com.thinkfree.drive.csv.annotation.CsvField;
import com.thinkfree.drive.csv.annotation.CsvHeader;
import com.thinkfree.drive.csv.annotation.CsvModel;
import com.thinkfree.drive.csv.annotation.CsvRequiredTogether;
import com.thinkfree.drive.csv.validation.constraint.CountryCodeConstraint;
import com.thinkfree.drive.csv.validation.constraint.EmailConstraint;
import com.thinkfree.drive.csv.validation.constraint.PasswordConstraint;
import com.thinkfree.drive.csv.validation.normalizer.CountryCodeNormalizer;
import com.thinkfree.drive.csv.validation.normalizer.PhoneNumberNormalizer;
import lombok.Getter;

@CsvModel
@CsvRequiredTogether(fields = {"countryCode", "phoneNumber"})
@Getter
public class AdminUserCsvImportRow{
    @CsvHeader(key = "UserID", index = 0)
    @CsvField(max=254, unique = true, constraints = EmailConstraint.class)
    private String userId;
    @CsvHeader(key = "Password", index = 1)
    @CsvField(min=8, max=64, constraints = PasswordConstraint.class)
    private String pw;
    @CsvHeader(key = "Name", index = 2)
    @CsvField(max=20)
    private String userName;
    @CsvHeader(key = "StorageLimit(GB)", index = 3)
    @CsvField(max=10)
    private Integer totalQuotaByte;
    @CsvHeader(key = "CountryCode", index = 4)
    @CsvField(max=3, required = false, normalizers = CountryCodeNormalizer.class, constraints = CountryCodeConstraint.class)
    private String countryCode;
    @CsvHeader(key = "PhoneNumber", index = 5)
    @CsvField(max=20, required = false, normalizers = PhoneNumberNormalizer.class)
    private String phoneNumber;
}
