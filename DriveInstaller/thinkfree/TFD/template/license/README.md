# 라이선스 폴더

이 폴더에 두 파일을 넣습니다. 둘 다 커밋하지 않습니다(`.gitignore`).

| 파일 | 내용 |
|---|---|
| `license.tfl` | 고객 Drive 라이선스 파일(이름을 이렇게 바꿔서 넣음) |
| `thinkfree.public.key` | 라이선스 서명 검증용 공개키 |

- docker-compose 가 이 폴더를 drive 컨테이너의 `/root/weboffice/license` 로 마운트합니다(읽기 전용).
- 라이선스 라이브러리는 `~/weboffice/license/thinkfree.public.key`(또는 `$WEBOFFICE_HOME/license/`)에서 공개키를 읽습니다. 공개키가 없으면 라이선스 등록·조회가 실패합니다.
- 앱은 기동할 때 `DRIVE_INITIAL_LICENSE_FILE`(기본 `/root/weboffice/license/license.tfl`)을 읽어, 대상 테넌트에 라이선스가 **없을 때만** 업로드와 같은 검증을 거쳐 등록합니다. 이미 있으면 그대로 둡니다.
- 파일이 없으면 등록을 건너뜁니다. 그 경우 관리자 화면에서 업로드하면 됩니다.
- 설치한 뒤 라이선스를 바꿀 때는 이 파일을 교체하지 말고 관리자 화면에서 업로드합니다. 이미 등록된 DB 에는 파일 교체가 반영되지 않습니다.
