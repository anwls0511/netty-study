# Netty 기반 TCP 메시지 처리 설계 기록

Netty를 공부하면서 가장 먼저 느낀 점은, 단순히 TCP 서버를 띄우는 것보다 "들어온 바이트를 어디까지 네트워크 처리로 보고, 어디서부터 비즈니스 로직으로 볼 것인가"를 나누는 게 중요하다는 점이었다.

이 문서는 실제 서비스 프로젝트에 바로 적용하기보다는, 공부용 `netty-study` 프로젝트에서 작은 TCP 서버를 만들며 정리한 내용이다. 예제는 장비가 온도, 습도, timestamp를 JSON으로 보내고 서버가 최신 상태를 저장하는 흐름으로 구성했다.

## 1. Netty를 사용한 이유

장비 상태 데이터처럼 짧은 메시지가 계속 들어오는 상황을 가정하면 HTTP 요청만으로 처리하는 것보다 TCP 연결을 유지하면서 데이터를 받는 구조가 더 자연스러울 수 있다. 장비가 주기적으로 서버에 데이터를 보내고, 서버는 이 데이터를 빠르게 읽어서 저장하면 된다.

Netty는 이런 TCP 서버를 만들 때 필요한 기능을 잘 제공한다. `ServerBootstrap`으로 서버를 띄우고, `EventLoopGroup`으로 연결 수락과 I/O 처리를 나눌 수 있다. 또 `ChannelPipeline`을 사용해서 바이트 처리, 문자열 변환, JSON 파싱, 비즈니스 처리 단계를 순서대로 구성할 수 있다.

이번 예제에서는 Netty를 사용해 9000번 포트에서 TCP 서버를 열고, 장비 상태 메시지를 받아 메모리에 저장하는 구조를 만들었다.

## 2. TCP는 메시지 경계가 없다는 점

TCP는 스트림 기반 프로토콜이다. 클라이언트가 메시지를 한 번 보냈다고 해서 서버에서도 정확히 한 번에 읽힌다는 보장이 없다.

예를 들어 클라이언트가 아래 두 메시지를 보냈다고 해도,

```text
{\"deviceId\":\"device-1\",\"temperature\":25.1,\"humidity\":40.2,\"timestamp\":1717830000}
{\"deviceId\":\"device-1\",\"temperature\":25.3,\"humidity\":40.5,\"timestamp\":1717830001}
```

서버 입장에서는 첫 번째 메시지가 반만 들어올 수도 있고, 두 메시지가 한 번에 붙어서 들어올 수도 있다.

그래서 TCP 서버에서는 "어디까지가 하나의 메시지인가"를 애플리케이션 프로토콜에서 정해야 한다. 이번 공부용 예제에서는 가장 단순하게 줄바꿈(`\\n`)을 메시지 경계로 사용했다. 즉, 클라이언트는 JSON 하나를 보낸 뒤 반드시 줄바꿈을 붙여야 한다.

## 3. Decoder를 둔 이유

Decoder를 둔 이유는 Handler가 TCP의 바이트 조각 문제까지 신경 쓰지 않게 하기 위해서다.

이번 예제의 Pipeline은 먼저 `LineBasedFrameDecoder`로 줄바꿈 기준의 프레임을 만든다. 그 다음 `StringDecoder`가 바이트를 문자열로 바꾸고, `DeviceStatusJsonDecoder`가 JSON 문자열을 `DeviceStatus` 객체로 변환한다.

```text
ByteBuf
  -> LineBasedFrameDecoder
  -> StringDecoder
  -> DeviceStatusJsonDecoder
  -> DeviceStatusHandler
```

이렇게 나누면 `DeviceStatusHandler`는 "완성된 장비 상태 객체가 들어왔다"고 보고 처리할 수 있다. Handler 안에서 직접 바이트를 합치거나 JSON을 파싱하는 코드를 섞지 않아도 된다.

## 4. ChannelPipeline 구조

현재 예제의 `ChannelPipeline`은 `NettyTcpServer`에서 구성한다.

```java
pipeline.addLast(new LineBasedFrameDecoder(1024));
pipeline.addLast(new StringDecoder(StandardCharsets.UTF_8));
pipeline.addLast(new StringEncoder(StandardCharsets.UTF_8));
pipeline.addLast(new DeviceStatusJsonDecoder());
pipeline.addLast(new DeviceStatusHandler(deviceStatusService));
```

각 Handler의 역할은 다음과 같다.

- `LineBasedFrameDecoder`: 줄바꿈 기준으로 하나의 메시지를 자른다.
- `StringDecoder`: `ByteBuf`를 UTF-8 문자열로 변환한다.
- `StringEncoder`: 서버가 응답 문자열을 보낼 수 있게 한다.
- `DeviceStatusJsonDecoder`: JSON 문자열을 `DeviceStatus` 객체로 변환한다.
- `DeviceStatusHandler`: 변환된 객체를 받아 서비스 계층에 저장을 요청한다.

Pipeline을 이렇게 두면 네트워크 처리 단계가 눈에 잘 보인다. 그리고 문제가 생겼을 때 어느 단계에서 문제가 났는지도 비교적 찾기 쉽다.

## 5. Handler와 Service를 분리한 이유

`DeviceStatusHandler`는 Netty의 `SimpleChannelInboundHandler<DeviceStatus>`를 상속한다. 이 클래스는 Netty 이벤트를 처리하는 계층이다.

반면 `DeviceStatusService`는 장비 상태 저장이라는 애플리케이션 로직을 담당한다. 현재는 공부용이라 `ConcurrentHashMap`에 최신 상태만 저장한다.

Handler와 Service를 분리한 이유는 책임을 나누기 위해서다. Handler는 네트워크 이벤트를 받고 응답을 쓰는 일에 집중한다. Service는 저장 방식과 조회 방식을 담당한다.

이렇게 해두면 나중에 저장소를 메모리에서 Redis나 DB로 바꾸더라도 Handler 코드는 크게 바꾸지 않아도 된다. 또 Netty 없이 Service만 따로 테스트할 수도 있다.

## 6. 현재 프로젝트의 메시지 처리 흐름

현재 `netty-study` 예제의 메시지 처리 흐름은 다음과 같다.

```text
Device TCP Client
  -> NettyTcpServer:9000
  -> LineBasedFrameDecoder
  -> StringDecoder
  -> DeviceStatusJsonDecoder
  -> DeviceStatusHandler
  -> DeviceStatusService.save()
  -> ConcurrentHashMap 저장
  -> OK 응답
```

클라이언트가 보내는 메시지는 한 줄 JSON이다.

```json
{\"deviceId\":\"device-1\",\"temperature\":25.1,\"humidity\":40.2,\"timestamp\":1717830000}
```

서버는 이 메시지를 `DeviceStatus` record로 변환하고, `deviceId`를 key로 최신 상태를 저장한다. 저장이 끝나면 클라이언트에 `OK`를 응답한다.

## 7. 개선할 점

현재 예제는 공부용이라 일부러 단순하게 만들었다. 그래서 실제 서비스에 쓰려면 개선할 부분이 많다.

첫 번째는 메시지 프로토콜이다. 줄바꿈 기반 프로토콜은 이해하기 쉽지만, 메시지 본문에 줄바꿈이 들어갈 수 있거나 바이너리 데이터를 다뤄야 한다면 적합하지 않을 수 있다. 그런 경우에는 `LengthFieldBasedFrameDecoder`처럼 길이 필드 기반 프로토콜을 검토할 수 있다.

두 번째는 검증 로직이다. 현재는 JSON이 `DeviceStatus`로 변환되면 바로 저장한다. 실제로는 `deviceId`가 비어 있는지, 온도나 습도 값이 말이 되는 범위인지, timestamp가 정상인지 확인해야 한다.

세 번째는 저장소다. 지금은 `ConcurrentHashMap`에 최신 상태만 저장한다. 공부용으로는 충분하지만, 서버가 재시작되면 데이터가 사라진다. 실제 프로젝트에서는 Redis, RDB, Kafka 같은 저장 또는 메시징 구조를 붙일 수 있다.

네 번째는 테스트다. TCP 메시지 경계 문제를 확인하려면 Decoder 테스트가 중요하다. 메시지가 여러 조각으로 나뉘어 들어와도 하나의 `DeviceStatus`로 복원되는지 확인해야 한다.

다섯 번째는 서버 종료 처리다. 지금은 서버가 실행되고 채널이 닫힐 때까지 대기한다. 실제 애플리케이션에서는 Spring Boot 생명주기나 종료 hook과 연결해서 graceful shutdown을 더 명확히 처리하는 편이 좋다.

## 8. 면접에서 설명할 수 있는 포인트

면접에서는 "Netty를 써봤다"보다 어떤 문제를 해결하려고 Netty 구조를 잡았는지를 설명하는 게 더 좋다고 생각한다.

첫 번째로 TCP가 스트림 기반이라는 점을 설명할 수 있다. TCP는 메시지 경계를 보장하지 않기 때문에, 서버에서 Decoder를 두고 애플리케이션 메시지 단위로 복원해야 한다.

두 번째로 Pipeline 구조를 설명할 수 있다. Netty는 들어온 데이터를 Pipeline에 등록된 Handler들이 순서대로 처리한다. 이번 예제에서는 프레임 분리, 문자열 변환, JSON 변환, 서비스 호출을 각각 다른 단계로 나눴다.

세 번째로 Handler와 Service를 분리한 이유를 설명할 수 있다. Handler는 네트워크 이벤트를 처리하고, Service는 비즈니스 로직을 처리한다. 이렇게 나누면 테스트가 쉬워지고 저장 방식이 바뀌어도 네트워크 계층 변경을 줄일 수 있다.

마지막으로 현재 예제의 한계도 같이 말할 수 있다. 지금은 줄바꿈 기반 JSON 메시지와 메모리 저장소를 사용한 공부용 예제이고, 실제 서비스로 확장하려면 메시지 검증, 영속 저장소, 예외 처리, Decoder 테스트가 더 필요하다.

이 정도로 정리하면 Netty의 핵심 개념을 과장하지 않고, 직접 만든 작은 예제 기준으로 설명할 수 있다.
