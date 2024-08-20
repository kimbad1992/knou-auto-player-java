# KNOU Auto Player - JAVA

방통대 형성평가 자동 재생기

같이 방통대 다니는 친구들에게 파이썬 설치방법이 알려주기 귀찮아서
Aiden Lee님의 [knou-auto-player](https://github.com/leegeunhyeok/knou-auto-player)을
자바 Swing UI를 사용해 한번에 실행할 수 있도록 포팅 시도

Release로는 Launch4J로 Executable을 만들어 배포함

### Required

- JDK 21 버전 : [JDK Download Page - Oracle](https://www.oracle.com/java/technologies/downloads/)
- JDK 설치 후 환경변수 설정
 
```bash
@echo off
setx JAVA_HOME "C:\YOUR\JAVA\PATH" /M
setx PATH "%PATH%;%JAVA_HOME%\bin" /M
pause
```



### 원본 프로젝트
- **원본 프로젝트:** [knou-auto-player](https://github.com/leegeunhyeok/knou-auto-player)
- **원작자:** [이근혁(Aiden Lee)](https://www.linkedin.com/in/dev-ghlee)
- **라이선스:** MIT License