package com.thinkfree.drive.systemconfig.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 설정 키·값 — 읽기 전용 값(FindService 의 설정 해석용). getter 이름은 엔티티 {@code SystemConfig} 와 같다. */
@Getter
@AllArgsConstructor // 공개 전체 생성자는 JPQL 생성자 식용(SystemConfigJpaRepository#SELECT_VALUE)
public class SystemConfigValueData {

    private String settingKey;
    private String valueText;
}
