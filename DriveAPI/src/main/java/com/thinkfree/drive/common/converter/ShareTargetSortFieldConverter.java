package com.thinkfree.drive.common.converter;

import com.thinkfree.drive.share.target.enums.ShareTargetSortField;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class ShareTargetSortFieldConverter implements Converter<String, ShareTargetSortField> {
    @Override
    public ShareTargetSortField convert(String source) {
        return ShareTargetSortField.from(source);
    }
}
