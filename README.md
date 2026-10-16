# KNOU Auto Player - JAVA

![](https://i.imgur.com/fDiGa4j.png)

방통대 형성평가 자동 재생기

같이 방통대 다니는 친구들에게 파이썬 설치방법을 알려주기 귀찮아서  
Aiden Lee님의 [knou-auto-player](https://github.com/leegeunhyeok/knou-auto-player)을  
자바 Swing UI를 사용해 한번에 실행할 수 있도록 포팅 시도

Release로는 Launch4J로 Executable을 만들어 배포함

- 과목 당 일일 수강 한도 초과 시 다음 과목으로 넘기기
- 수강 과목 선택 추가
- 연습문제 풀이 추가 (정답 여부와 상관 없이 랜덤한 선택지를 찍도록 작동)
- 자동 로그인 추가 (아래 참고)
- macOS 빌드 추가 (아래 참고)

### 자동 로그인

`자동 로그인`을 체크하고 Start를 누르면, 로그인에 성공했을 때 아이디/비밀번호와 실행 옵션을 저장하고 다음 실행 시 바로 시작합니다.

- 비밀번호는 OS 자격 증명 저장소에 저장됩니다. (Windows: 자격 증명 관리자, macOS: 키체인)
- 체크를 해제하면 저장된 정보가 바로 삭제됩니다.
- 저장된 아이디/비밀번호로 로그인에 실패하면 저장된 정보를 삭제하고 자동 로그인을 해제합니다. (직접 입력해서 실패한 경우는 유지)
- 과목 선택 팝업에서 OK로 확정한 과목은 기억해 두었다가 다음 실행 때 기본으로 체크됩니다.
- `이전 선택으로 바로 시작(팝업 생략)`을 체크하면 기억된 과목으로 팝업 없이 바로 시작합니다. (기억된 과목이 없거나 현재 강의 목록과 맞지 않으면 팝업을 띄움)
- macOS에서는 처음 실행할 때 키체인 접근 허용 창이 뜰 수 있습니다. `항상 허용`을 누르면 됩니다.

### macOS

macOS용 `.dmg`는 Java 런타임이 포함되어 있어 JDK 설치 없이 실행됩니다. (Chrome은 설치되어 있어야 함)

__빌드__
- GitHub Actions의 `Build macOS` 워크플로를 수동 실행하거나 `v*` 태그를 push하면 Apple Silicon(arm64) / Intel(x64)용 `.dmg`가 Artifacts로 올라옵니다.
- Mac에서 직접 빌드: `./gradlew packageMac` → `build/jpackage/dist/`

__실행__

서명되지 않은 앱이라 처음 실행 시 "확인되지 않은 개발자" 경고로 열리지 않습니다.
`시스템 설정 > 개인정보 보호 및 보안`에서 `그래도 열기`를 누르거나, 터미널에서 아래 명령을 실행하세요.

```bash
xattr -dr com.apple.quarantine "/Applications/KNOU Auto Player.app"
```

### Required

- JDK 21 버전 : [JDK Download Page - Oracle](https://www.oracle.com/java/technologies/downloads/)
- JDK 설치 후 환경변수 설정

__Windows Batch 파일로 환경 설정__
 ```bash  
# 아래 내용을 메모장으로 작성 후 .bat파일로 저장하여 실행
@echo off
setx JAVA_HOME "C:\YOUR\JAVA\PATH" /M
setx PATH "%PATH%;%JAVA_HOME%\bin" /M
pause  
```  

__직접 환경 변수 수정__
> Win + Pause 키 (고급 시스템 설정) -> 환경변수 이동
> 시스템 변수에 JAVA_HOME 추가
> ![](https://i.imgur.com/oknSW7r.png)


### 원본 프로젝트
- **원본 프로젝트:** [knou-auto-player](https://github.com/leegeunhyeok/knou-auto-player)
- **원작자:** [이근혁(Aiden Lee)](https://www.linkedin.com/in/dev-ghlee)
- **라이선스:** MIT License
