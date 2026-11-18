# DriveFront

<br>

## Git Commit 가이드


### Commit 단위

- Jira Task단위로 Commit 요청드립니다.
- 하루 작업 후, 전체 Commit등은 지양 부탁드립니다.
  <br>

### Commit 메시지

- 기본 형식
    - (TFDRIVE-이슈번호) 커밋 종류: 내용

    ```
    (TFDRIVE-이슈번호) setting : 개발 환경 초기 세팅
    (TFDRIVE-이슈번호) feat : 새로운 기능 추가 ex) 사용자 카드 UI 퍼블리싱 완료, 사용자 단일 조회 API 연동
    (TFDRIVE-이슈번호) design : UI를 위한 스타일링 ex) CSS 스타일 수정, 반응형 대응, HTML 구조 정리 등
    (TFDRIVE-이슈번호) fix : 버그 수정
    (TFDRIVE-이슈번호) refactor 코드 리팩터링
    (TFDRIVE-이슈번호) chore : 그 외 수정 ex) 오타, 주석, 세미콜론(;) 추가 등
    (TFDRIVE-이슈번호) test : 테스트코드 관련
    (TFDRIVE-이슈번호) docs : 문서 추가, 수정, 삭제

    * feat과 다른 것을 같이 커밋하시는 경우 feat으로 처리 부탁드립니다.
    ```

- 예시

    ```
    (TFDRIVE-85) feat : 이미지 미리보기 (In-app preview) 구현
    (TFDRIVE-85) feat : 이미지 미리보기 다이얼로그 구현 및 기존 새 탭 미리보기 기능 in-app 방식으로 전환
    (TFDRIVE-85) feat : Hover 액션 메뉴에서 preview 동작 시 리소스 정보 전달 로직 추가
    (TFDRIVE-85) refactor:  다운로드 후 URL.revokeObjectURL 호출로 메모리 해제 처리

    (TFDRIVE-86) design : 폼 입력 스타일 개선 및 포커스 색상 추가
    (TFDRIVE-86) feat : 마이페이지 UI 퍼블리싱
    (TFDRIVE-86) refactor : 중복된 UI 클래스 제거 및 구조 정리

    예외로 merge 충돌로 인한 커밋은 아래와 같이 작성
    chore : merge complete
    ```


## TODO: On-premise Drive의 Cloud Office 자동 연결 (보류)

2026-11-10 결정: Cloud Office 자동 연결은 아직 구현하지 않습니다. 관리자 Office 설정의
공용 입력 컴포넌트에서는 Cloud Office 선택 항목을 렌더링하지 않습니다. On-premise TFO
연결과 Office 연동하지 않기는 기존 방식으로 유지합니다.

추후 작업 범위:
- Developers 로그인·연결 승인·API Key 선택과 서버 간 전달.
- 외부에서 접근 가능한 Drive만 지원하고, 실제 Cloud Office 실행 경로에서 HTTP Storage 접근 검증.
- 테넌트별 연결 기록과 고정 Storage ID를 사용한 단계별 재시도. installationId는 필수로 도입하지 않음.
- 기존 활성 설정과 준비 중 설정 분리, 연결 실패 시 기존 문서 저장 보호.
- Developers의 외부 HTTP Storage 지원과 Drive 문서 접근 정보 전달 계약 보완.
- API Key 권한·폐기, 주소 접근 보안, 중복 처리, 파일 권한, 문서 열기·편집·자동 저장·과금 검증.

위 항목의 구현 및 검증이 완료된 뒤 Cloud Office 선택 항목을 다시 노출합니다.
현재 Developers 관리형 My Drive의 프록시 연동과 운영 설정은 이 UI 변경 대상이 아닙니다.
