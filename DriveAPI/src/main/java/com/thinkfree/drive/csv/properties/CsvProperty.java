package com.thinkfree.drive.csv.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("csv.upload")
public record CsvProperty
(
    @DefaultValue("5MB")
    DataSize adminMaxSize
)
{
}


