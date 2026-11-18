package com.thinkfree.drive.csv.constans;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.MediaType;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CsvConstants {
    public static final String FAILURE_REASON = "FailureReason";
    public static final MediaType TEXT_CSV_UTF8 = MediaType.parseMediaType("text/csv; charset=UTF-8");
    public static final String FAILURE_CSV_NAME = "admin-users-failures";
    public static final String CSV_EXTENSION = ".csv";
}
