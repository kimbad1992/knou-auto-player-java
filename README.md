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
