# netty-study

Netty TCP 서버를 공부하기 위한 작은 실습 프로젝트입니다.

실제 서비스 프로젝트에 바로 붙이기 전에 TCP 메시지 경계, Decoder, ChannelPipeline, Handler와 Service 분리를 작게 실험하는 것을 목표로 합니다.

## 학습 목표

| 주제 | 설명 |
| --- | --- |
| TCP 메시지 경계 | TCP는 스트림 기반이라 한 번 보낸 메시지가 한 번에 읽힌다는 보장이 없다는 점을 확인합니다. |
| Decoder | 들어온 바이트를 애플리케이션 메시지 단위로 자르고 객체로 변환합니다. |
| ChannelPipeline | 네트워크 처리 단계를 순서대로 연결합니다. |
| Handler / Service 분리 | Netty 이벤트 처리와 비즈니스 로직을 나눕니다. |

## 프로젝트 구조

```text
src/main/java/com/mujin/study/netty
├─ NettyStudyApplication.java
├─ decoder
│  └─ DeviceStatusJsonDecoder.java
├─ device
│  └─ DeviceStatus.java
├─ handler
│  └─ DeviceStatusHandler.java
├─ server
│  └─ NettyTcpServer.java
└─ service
   └─ DeviceStatusService.java
```

## 메시지 처리 흐름

```text
Device TCP Client
  -> NettyTcpServer:9000
  -> LineBasedFrameDecoder
  -> StringDecoder
  -> DeviceStatusJsonDecoder
  -> DeviceStatusHandler
  -> DeviceStatusService
```

## 실행

Windows:

```powershell
.\gradlew.bat run
```

macOS / Linux:

```bash
./gradlew run
```

서버는 기본적으로 `9000` 포트에서 실행됩니다.

## Day 2 실험 클라이언트

서버를 먼저 실행한 뒤, 다른 터미널에서 아래 명령을 실행합니다.

```powershell
.\gradlew.bat runBoundaryClient
```

이 클라이언트는 다음 세 가지 상황을 전송합니다.

| 케이스 | 설명 |
| --- | --- |
| case 1 | 완성된 JSON 메시지 1개 전송 |
| case 2 | JSON 메시지 1개를 두 번에 나눠 전송 |
| case 3 | JSON 메시지 2개를 한 번에 붙여서 전송 |

## 메시지 예시

한 줄에 하나의 JSON 메시지를 보냅니다.

```json
{"deviceId":"device-1","temperature":25.1,"humidity":40.2,"timestamp":1717830000}
```

이 프로젝트는 줄바꿈을 메시지 경계로 사용합니다. 따라서 TCP 클라이언트에서 메시지를 보낼 때 마지막에 `\n`을 붙여야 합니다.

## 테스트

```powershell
.\gradlew.bat test
```

## 문서

- [Netty 기반 TCP 메시지 처리 설계 기록](docs/blog/2026-06-08-netty-design.md)
- [Netty Day 2: TCP 메시지 경계 실험](docs/blog/2026-06-09-netty-message-boundary.md)
- [Netty Day 3: Decoder 테스트 보강](docs/blog/2026-06-10-netty-decoder-test.md)
- [Netty Day 4: LengthFieldBasedFrameDecoder 실험](docs/blog/2026-06-11-netty-length-field-decoder.md)
- [Netty Day 4 보강: Decoder edge case 테스트](docs/blog/2026-06-12-netty-decoder-edge-cases.md)
- [Netty Day 5: Decoder 검증 정책 보강](docs/blog/2026-06-13-netty-decoder-validation.md)
