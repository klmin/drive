package com.thinkfree.drive.upload.session.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UploadPartPlan — 파트 크기·수·기대 크기")
class UploadPartPlanTest {

    private static final long MIB = 1024L * 1024;
    private static final long MIN = 16 * MIB;

    @Test
    @DisplayName("16MB 로 나눠떨어지면 마지막 파트도 16MB")
    void exactMultiple() {
        var plan = UploadPartPlan.of(64 * MIB, MIN);
        assertThat(plan.partSize()).isEqualTo(MIN);
        assertThat(plan.partCount()).isEqualTo(4);
        assertThat(plan.expectedPartSize(4)).isEqualTo(MIN);
    }

    @Test
    @DisplayName("나머지는 마지막 파트 — 범위 밖 번호는 -1")
    void remainder() {
        var plan = UploadPartPlan.of(33 * MIB + 5, MIN);
        assertThat(plan.partCount()).isEqualTo(3);
        assertThat(plan.expectedPartSize(1)).isEqualTo(MIN);
        assertThat(plan.expectedPartSize(2)).isEqualTo(MIN);
        assertThat(plan.expectedPartSize(3)).isEqualTo(MIB + 5);
        assertThat(plan.expectedPartSize(0)).isEqualTo(-1);
        assertThat(plan.expectedPartSize(4)).isEqualTo(-1);
    }

    @Test
    @DisplayName("16MB 로 160GB 까지 — 넘으면 파트 크기를 키워 10,000 파트 안쪽")
    void largeFileGrowsPartSize() {
        var atLimit = UploadPartPlan.of(MIN * 10_000, MIN);
        assertThat(atLimit.partSize()).isEqualTo(MIN);
        assertThat(atLimit.partCount()).isEqualTo(10_000);

        var over = UploadPartPlan.of(MIN * 10_000 + 1, MIN);
        assertThat(over.partSize()).isGreaterThan(MIN);
        assertThat(over.partCount()).isLessThanOrEqualTo(10_000);

        long fiveTiB = 5L * 1024 * 1024 * MIB;
        var max = UploadPartPlan.of(fiveTiB, MIN);
        assertThat(max.partCount()).isLessThanOrEqualTo(10_000);
        assertThat(max.partSize() * (max.partCount() - 1) + max.expectedPartSize(max.partCount())).isEqualTo(fiveTiB);
    }

    @Test
    @DisplayName("1바이트·0바이트")
    void tiny() {
        var one = UploadPartPlan.of(1, MIN);
        assertThat(one.partCount()).isEqualTo(1);
        assertThat(one.expectedPartSize(1)).isEqualTo(1);

        var zero = UploadPartPlan.of(0, MIN);
        assertThat(zero.partCount()).isEqualTo(1);
        assertThat(zero.expectedPartSize(1)).isZero();
    }

    @Test
    @DisplayName("음수 크기·S3 최소(5MiB) 미만 파트 크기는 거부")
    void invalid() {
        assertThatThrownBy(() -> UploadPartPlan.of(-1, MIN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UploadPartPlan.of(10, MIB)).isInstanceOf(IllegalArgumentException.class);
    }
}
