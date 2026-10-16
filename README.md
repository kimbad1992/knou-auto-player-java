# KNOU Auto Player - JAVA

![](https://i.imgur.com/fDiGa4j.png)

방통대 형성평가 자동 재생기

같이 방통대 다니는 친구들에게 파이썬 설치방법을 알려주기 귀찮아서  
Aiden Lee님의 [knou-auto-player](https://github.com/leegeunhyeok/knou-auto-player)을  
자바 Swing UI를 사용해 한번에 실행할 수 있도록 포팅 시도

Release에는 Java 런타임이 포함된 설치 파일(Windows `.msi`, macOS `.dmg`)을 올리므로 JDK 설치 없이 실행됩니다. (Chrome은 설치되어 있어야 함)

- 과목 당 일일 수강 한도 초과 시 다음 과목으로 넘기기
- 수강 과목 선택 추가
- 연습문제 풀이 추가 (정답 여부와 상관 없이 랜덤한 선택지를 찍도록 작동)
- 자동 로그인 추가 (아래 참고)
- Windows / macOS 설치 파일 빌드 추가 (아래 참고)

### 자동 로그인

`자동 로그인`을 체크하고 `시작`을 누르면, 로그인에 성공했을 때 아이디/비밀번호와 실행 옵션을 저장하고 다음 실행 시 바로 시작합니다.

- 비밀번호는 OS 자격 증명 저장소에 저장됩니다. (Windows: 자격 증명 관리자, macOS: 키체인)
- 체크를 해제하면 저장된 정보가 바로 삭제됩니다.
- 저장된 아이디/비밀번호로 로그인에 실패하면 저장된 정보를 삭제하고 자동 로그인을 해제합니다. (직접 입력해서 실패한 경우는 유지)
- 과목 선택 창에서 `시작`으로 확정한 과목은 기억해 두었다가 다음 실행 때 기본으로 체크됩니다.
- `이전 선택으로 바로 시작`을 체크하면 기억된 과목으로 선택 창 없이 바로 시작합니다. (기억된 과목이 없거나 현재 강의 목록과 맞지 않으면 선택 창을 띄움)
- macOS에서는 처음 실행할 때 키체인 접근 허용 창이 뜰 수 있습니다. `항상 허용`을 누르면 됩니다.

### Windows

[Releases](../../releases)에서 `KNOU Auto Player-x.x.x.msi`를 내려받아 실행하면 설치됩니다. 설치 후 시작 메뉴와 바탕화면 바로가기로 실행하세요.

서명되지 않은 설치 파일이라 처음 실행 시 Windows SmartScreen 경고가 뜹니다. `추가 정보 > 실행`을 누르면 됩니다.

### macOS

[Releases](../../releases)에서 `.dmg`를 내려받아 앱을 `Applications`로 끌어다 놓으면 설치됩니다. (Apple Silicon은 arm64, Intel Mac은 x64)

서명되지 않은 앱이라 처음 실행 시 "확인되지 않은 개발자" 경고로 열리지 않습니다.
`시스템 설정 > 개인정보 보호 및 보안`에서 `그래도 열기`를 누르거나, 터미널에서 아래 명령을 실행하세요.

```bash
xattr -dr com.apple.quarantine "/Applications/KNOU Auto Player.app"
```

### 소스 빌드

직접 빌드할 때만 JDK 21이 필요합니다. 설치 파일로 실행하는 경우에는 필요 없습니다.

__GitHub Actions__
- `Build Release` 워크플로를 수동 실행하거나 `v*` 태그를 push하면 Windows(x64) `.msi`, macOS(arm64/x64) `.dmg`가 Artifacts로 올라옵니다.

__로컬 빌드__ (jpackage는 크로스 빌드가 안 되므로 각 OS에서 실행)
- Windows: [WiX Toolset 3.x](https://github.com/wixtoolset/wix3/releases) 설치 후 `gradlew packageWindows` → `build\jpackage\dist\`
- macOS: `./gradlew packageMac` → `build/jpackage/dist/`
- fat jar만 필요하면 `./gradlew shadowJar` → `build/libs/`

__JDK 21 설치__
- [JDK Download Page - Oracle](https://www.oracle.com/java/technologies/downloads/)
- 설치 후 환경변수 설정

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
