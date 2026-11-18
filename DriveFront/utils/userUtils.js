import api from './api';

// 빈 값인 경우
export const emptyCheck = (value, t) => {
    if (value === '') {
        throw new Error(t(`common.validate.emptyCheck`));
    }
};

// 사용자 ID - 이메일 형식 확인
export const validateEmailPattern = (userId, t) => {
    const emailPattern = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
    if (!emailPattern.test(userId)) {
        throw new Error(t(`common.validate.emailPattern`));
    }
};

// 사용자 ID - 중복 이메일 확인
export const validateDuplicate = async (userId, t) => {
    const res = await api.Get(`users/exists?userId=${encodeURIComponent(userId)}`, {});
    if (res?.errorCode === 'USER_004') {
        throw new Error(t(`common.validate.duplicateUserId`));
    }
};

// 프로필 이미지 - 서버 ProfileImageValidator 와 같은 기준(5MB 이하, PNG·JPG·GIF·WEBP)
export const PROFILE_IMAGE_MAX_BYTES = 5 * 1024 * 1024;
export const PROFILE_IMAGE_ACCEPT = 'image/png,image/jpeg,image/gif,image/webp';
const PROFILE_IMAGE_TYPES = ['image/png', 'image/jpeg', 'image/jpg', 'image/gif', 'image/webp'];

export const validateProfileImage = (file, t) => {
    if (file.size > PROFILE_IMAGE_MAX_BYTES) {
        throw new Error(t('admin.branding.upload.errorFileSizeExceeded'));
    }
    if (!PROFILE_IMAGE_TYPES.includes(file.type?.toLowerCase())) {
        throw new Error(t('admin.branding.upload.errorInvalidFormat'));
    }
};

// 비밀번호 - 대문자, 소문자, 숫자, 특수문자(ASCII 기호 전체) 각각 1자 이상 포함, 최소 8자 최대 64자
export const validatePassword = (password, t) => {
    const passwordPattern = /^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^\w\s]).{8,64}$/;
    if (!passwordPattern.test(password)) {
        throw new Error(t(`common.validate.password`));
    }
};

// 사용자 이름 - 1자 이상
export const validateUserName = (userName, t) => {
    if (userName.length <= 1) {
        throw new Error(t(`common.validate.minLengthUserName`));
    }
};

// 입력 value  앞뒤 공백 검증
export const validateNoEdgeSpaces = (value, t) => {
    if (value.trim() !== value) {
        throw new Error(t(`common.validate.noEdgeSpaces`));
    }
};

// 입력값 앞뒤 공백 제거
export const trimEdgeSpaces = value => {
    return typeof value === 'string' ? value.trim() : value;
};

// 시간대별 user 인사말
export const getGreetingKeyByHour = () => {
    const hour = new Date().getHours(); // 로컬 시간 기준

    if (hour >= 5 && hour < 10) {
        return 'UserSummary.greetingMorning'; // 05:00 ~ 9:59
    } else if (hour >= 10 && hour < 18) {
        return 'UserSummary.greetingAfternoon'; // 10:00 ~ 17:59
    } else {
        return 'UserSummary.greetingEvening'; // 18:00 ~ 04:59
    }
};

// 전화번호 검사 (국가 번호와 전화번호 모두 있거나 모두 없어야 함)
export const validatePhoneAndCountry = (phoneNumber, countryCode, t) => {
    // console.log('phoneNumber', phoneNumber);
    // console.log('countryCode', countryCode);
    // 둘 다 없는 경우 통과
    if (!phoneNumber && !countryCode) {
        return;
    }

    if (!phoneNumber || !countryCode) {
        throw new Error(t('common.validate.phoneAndCountry'));
    }
};
