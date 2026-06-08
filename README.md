# netty-study

Netty TCP 서버를 공부하기 위한 작은 실습 프로젝트입니다.

목표는 실제 서비스 코드를 크게 만들기 전에 TCP 메시지 경계, Decoder, ChannelPipeline, Handler와 Service 분리를 작은 예제로 이해하는 것입니다.

## 실행

```bash
./gradlew run
```

Windows에서는 다음 명령을 사용할 수 있습니다.

```bash
gradlew.bat run
```

서버는 기본적으로 `9000` 포트에서 실행됩니다.

## 메시지 예시

한 줄에 하나의 JSON 메시지를 보냅니다.

```json
{"deviceId":"device-1","temperature":25.1,"humidity":40.2,"timestamp":1717830000}
```

줄바꿈을 메시지 경계로 사용하기 때문에 마지막에 `\n`을 붙여야 합니다.

## 문서

- [Netty 기반 TCP 메시지 처리 설계 기록](docs/blog/2026-06-08-netty-design.md)
