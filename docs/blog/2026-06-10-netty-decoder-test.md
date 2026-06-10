# Netty Day 3: Decoder 테스트 보강

> 오늘은 Netty Pipeline을 눈으로만 이해하지 않고, `EmbeddedChannel`로 Decoder 동작을 테스트했다.

## 오늘의 목표

Day 2에서는 TCP 메시지가 쪼개지거나 붙어서 들어올 수 있다는 점을 실험했다. Day 3에서는 이 동작을 테스트 코드로 확인하는 것이 목표다.

이번에 보강한 테스트는 두 가지다.

| 테스트 | 확인하는 내용 |
| --- | --- |
| `DeviceStatusJsonDecoderTest` | JSON 문자열이 `DeviceStatus` 객체로 변환되는지 확인 |
| `DeviceStatusPipelineTest` | frame decoder, string decoder, json decoder, handler가 함께 동작하는지 확인 |

## 왜 EmbeddedChannel을 쓰는가

Netty 코드를 테스트한다고 해서 항상 실제 TCP 서버를 띄울 필요는 없다. `EmbeddedChannel`을 사용하면 메모리 안에서 Pipeline을 만들고 inbound 데이터를 직접 넣을 수 있다.

```java
EmbeddedChannel channel = new EmbeddedChannel(
        new LineBasedFrameDecoder(1024),
        new StringDecoder(StandardCharsets.UTF_8),
        new DeviceStatusJsonDecoder(),
        new DeviceStatusHandler(service)
);
```

이렇게 하면 실제 socket 연결 없이도 다음 흐름을 테스트할 수 있다.

```text
ByteBuf
  -> LineBasedFrameDecoder
  -> StringDecoder
  -> DeviceStatusJsonDecoder
  -> DeviceStatusHandler
```

## JSON Decoder 테스트

`DeviceStatusJsonDecoder`는 문자열 JSON을 `DeviceStatus` 객체로 바꾼다.

```java
channel.writeInbound("""
        {"deviceId":"device-1","temperature":25.1,"humidity":40.2,"timestamp":1717830000}
        """);

DeviceStatus status = channel.readInbound();

assertEquals("device-1", status.deviceId());
```

이 테스트는 Decoder가 단순 문자열을 도메인 객체로 바꾸는 책임만 검증한다. 네트워크 경계 처리나 저장 로직은 여기서 다루지 않는다.

## Pipeline 테스트

Pipeline 테스트에서는 메시지가 반만 들어왔을 때 저장되지 않는지 확인했다.

```java
channel.writeInbound(Unpooled.copiedBuffer(
        "{\"deviceId\":\"device-1\",\"temperature\":25.1,",
        StandardCharsets.UTF_8
));

assertNull(service.findByDeviceId("device-1"));
```

아직 줄바꿈이 없기 때문에 `LineBasedFrameDecoder`는 메시지를 다음 Handler로 넘기지 않는다. 그래서 Service에도 저장된 값이 없어야 한다.

나머지 메시지와 줄바꿈이 들어오면 그때 저장된다.

```java
channel.writeInbound(Unpooled.copiedBuffer(
        "\"humidity\":40.2,\"timestamp\":1717830000}\n",
        StandardCharsets.UTF_8
));
```

## 오늘 정리한 점

이번 테스트에서 확인한 핵심은 다음과 같다.

| 상황 | 기대 결과 |
| --- | --- |
| JSON 문자열만 테스트 | `DeviceStatus` 객체로 변환되어야 한다. |
| 빈 문자열 | 다음 Handler로 아무것도 넘기지 않는다. |
| 잘못된 JSON | Decoder 예외가 발생한다. |
| 줄바꿈이 없는 TCP 조각 | 아직 저장되지 않아야 한다. |
| 두 메시지가 붙어서 들어옴 | 두 개의 상태가 각각 저장되어야 한다. |

이렇게 테스트를 나누면 Decoder의 책임과 Pipeline 전체 흐름을 따로 확인할 수 있다. 실제 서버를 띄우기 전에 메시지 경계와 변환 흐름을 빠르게 검증할 수 있다는 점이 좋았다.
