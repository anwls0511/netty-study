# Netty Day 4 보강: Decoder edge case 테스트

> 오늘은 정상 흐름뿐 아니라 Decoder가 애매한 입력을 만났을 때 어떻게 동작해야 하는지 테스트를 보강했다.

## 오늘의 목표

Day 4에서는 `LengthFieldBasedFrameDecoder`의 기본 동작을 실험했다. 이번 보강에서는 실제 서버에서 마주칠 수 있는 경계 상황을 추가로 확인했다.

| 테스트 대상 | 보강한 케이스 |
| --- | --- |
| `LineBasedFrameDecoder` | `\r\n` 구분자 처리, 최대 frame 길이 초과 |
| `LengthFieldBasedFrameDecoder` | 길이 헤더가 일부만 도착한 경우, 길이 값이 최대 frame 크기를 넘는 경우 |
| `DeviceStatusJsonDecoder` | 앞뒤 공백 제거, 알 수 없는 JSON 필드 |

## 왜 edge case 테스트가 필요한가

Decoder는 정상 메시지만 처리하는 코드가 아니다. TCP 서버 앞단에서 들어오는 데이터를 가장 먼저 만나는 계층이라서, 잘린 메시지나 너무 긴 메시지, 예상하지 못한 JSON을 처리해야 한다.

예를 들어 줄바꿈 기반 프로토콜에서는 이런 질문을 해야 한다.

```text
\n뿐 아니라 \r\n도 처리되는가?
줄바꿈 없이 너무 긴 데이터가 들어오면 어떻게 되는가?
```

길이 헤더 기반 프로토콜에서는 이런 질문이 필요하다.

```text
4바이트 길이 헤더 중 2바이트만 먼저 오면 기다리는가?
헤더의 길이 값이 너무 크면 바로 막는가?
```

## LineBasedFrameDecoder 보강

CRLF는 Windows나 일부 텍스트 프로토콜에서 자주 볼 수 있는 줄 구분자다.

```java
channel.writeInbound(Unpooled.copiedBuffer("PING\r\n", StandardCharsets.UTF_8));

assertEquals("PING", channel.readInbound());
```

이 테스트는 `LineBasedFrameDecoder`가 `\r\n`을 메시지 구분자로 처리하고, 다음 Handler에는 실제 본문인 `PING`만 넘기는지 확인한다.

너무 긴 메시지에 대한 테스트도 추가했다.

```java
assertThrows(TooLongFrameException.class, () ->
        channel.writeInbound(Unpooled.copiedBuffer("too-long-message\n", StandardCharsets.UTF_8))
);
```

최대 길이를 제한하지 않으면 잘못된 클라이언트가 아주 긴 데이터를 보내서 서버 메모리를 압박할 수 있다.

## LengthFieldBasedFrameDecoder 보강

길이 헤더는 4바이트로 약속했기 때문에, 2바이트만 들어왔을 때는 아직 길이 값을 해석할 수 없다.

```java
channel.writeInbound(Unpooled.copiedBuffer(frame, 0, 2));

assertNull(channel.readInbound());
```

나머지 바이트가 들어오면 그때 frame을 완성한다.

```java
channel.writeInbound(Unpooled.copiedBuffer(frame, 2, frame.length - 2));
```

반대로 헤더가 말하는 길이가 너무 크면 예외가 발생해야 한다.

```java
byte[] headerOnly = ByteBuffer.allocate(4)
        .putInt(2048)
        .array();

assertThrows(TooLongFrameException.class, () ->
        channel.writeInbound(Unpooled.wrappedBuffer(headerOnly))
);
```

## JSON Decoder 보강

`DeviceStatusJsonDecoder`는 메시지 앞뒤 공백을 제거한 뒤 JSON을 파싱한다.

```java
String trimmed = msg.trim();
```

그래서 앞뒤에 공백과 줄바꿈이 있어도 정상 JSON이면 `DeviceStatus`로 변환되어야 한다.

반대로 현재 `ObjectMapper` 설정에서는 알 수 없는 필드가 있으면 예외가 발생한다.

```java
{"deviceId":"device-1","temperature":25.1,"humidity":40.2,"timestamp":1717830000,"battery":90}
```

이 동작은 장단점이 있다. 예상하지 못한 필드를 빠르게 발견할 수 있지만, 클라이언트가 새 필드를 추가했을 때 서버가 깨질 수도 있다. 실제 서비스에서는 이 정책을 명확히 정해야 한다.

## 오늘 정리한 점

Decoder 테스트는 정상 메시지를 객체로 바꾸는지만 확인하면 부족하다. 다음 기준도 함께 봐야 한다.

```text
1. 아직 메시지가 완성되지 않았을 때 기다리는가?
2. 너무 큰 메시지를 막는가?
3. 구분자나 헤더를 제거하고 본문만 넘기는가?
4. 예상하지 못한 JSON을 허용할지 거부할지 정책이 있는가?
```

이런 edge case를 테스트해두면 Decoder의 책임이 더 명확해지고, 나중에 프로토콜을 바꿀 때도 어떤 동작이 유지되어야 하는지 확인하기 쉽다.
