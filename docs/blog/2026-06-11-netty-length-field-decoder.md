# Netty Day 4: LengthFieldBasedFrameDecoder 실험

> 오늘은 줄바꿈 대신 메시지 앞의 길이 헤더를 기준으로 TCP 메시지를 구분하는 방식을 테스트했다.

## 오늘의 목표

Day 1~3에서는 줄바꿈(`\n`)을 메시지 경계로 사용하는 방식을 다뤘다. 이 방식은 이해하기 쉽지만, 본문에 줄바꿈이 들어갈 수 있는 XML이나 긴 JSON을 다룰 때는 조심해야 한다.

Day 4에서는 아래 구조를 사용한다.

```text
[4 bytes body length][body bytes]
```

예를 들어 body가 42바이트라면 TCP로 보내는 frame은 개념적으로 이렇게 생긴다.

```text
[00 00 00 2A][42 bytes JSON body]
```

## 왜 길이 헤더를 쓰는가

TCP는 메시지 경계를 보장하지 않는다. 그래서 서버와 클라이언트가 메시지 경계 규칙을 정해야 한다.

줄바꿈 방식은 이런 약속이다.

```text
JSON 하나가 끝나면 \n을 붙인다.
```

길이 헤더 방식은 이런 약속이다.

```text
앞 4바이트는 body 길이다.
그 뒤의 body 길이만큼 읽으면 메시지 하나다.
```

길이 헤더 방식은 body 안에 줄바꿈이 있어도 메시지 경계를 안정적으로 구분할 수 있다는 장점이 있다.

## 테스트한 Pipeline

이번 테스트에서는 실제 서버를 바꾸지 않고, `EmbeddedChannel`로 길이 기반 Pipeline을 따로 구성했다.

```java
new EmbeddedChannel(
        new LengthFieldBasedFrameDecoder(
                1024,
                0,
                4,
                0,
                4
        ),
        new StringDecoder(StandardCharsets.UTF_8),
        new DeviceStatusJsonDecoder(),
        new DeviceStatusHandler(service)
);
```

각 옵션의 의미는 다음과 같다.

| 옵션 | 값 | 의미 |
| --- | --- | --- |
| `maxFrameLength` | `1024` | 허용할 최대 frame 크기 |
| `lengthFieldOffset` | `0` | 길이 필드가 frame 맨 앞에서 시작 |
| `lengthFieldLength` | `4` | 길이 필드 크기가 4바이트 |
| `lengthAdjustment` | `0` | 길이 값은 body 길이만 의미 |
| `initialBytesToStrip` | `4` | 다음 Handler에는 길이 헤더를 제거하고 body만 전달 |

## 조각난 frame 테스트

길이 헤더 기반에서도 TCP 데이터는 중간에 쪼개질 수 있다.

```java
channel.writeInbound(Unpooled.copiedBuffer(frame, 0, 12));

assertNull(service.findByDeviceId("device-1"));
assertNull(channel.readOutbound());
```

앞부분만 들어왔을 때는 body 길이를 만족하지 못했기 때문에 다음 Handler로 넘어가면 안 된다. 그래서 Service에도 저장된 값이 없어야 한다.

나머지 데이터가 들어오면 그때 frame이 완성된다.

```java
channel.writeInbound(Unpooled.copiedBuffer(frame, 12, frame.length - 12));
```

## 붙어서 들어온 frame 테스트

길이 기반 frame도 여러 개가 한 번에 붙어서 들어올 수 있다.

```java
channel.writeInbound(Unpooled.wrappedBuffer(first, second));
```

이 경우 `LengthFieldBasedFrameDecoder`는 첫 frame의 길이를 읽고 body를 자른 뒤, 남은 바이트에서 다시 다음 frame의 길이를 읽는다. 그래서 두 메시지가 각각 저장되어야 한다.

## 오늘 정리한 점

| 방식 | 메시지 경계 기준 | 장점 | 주의할 점 |
| --- | --- | --- | --- |
| 줄바꿈 기반 | `\n` | 이해하기 쉽고 테스트하기 쉽다. | body에 줄바꿈이 들어가면 위험할 수 있다. |
| 길이 헤더 기반 | header의 body length | body 내용과 상관없이 안정적으로 자를 수 있다. | 클라이언트와 서버가 헤더 규칙을 정확히 맞춰야 한다. |

이번 실험으로 `LengthFieldBasedFrameDecoder`는 길이 헤더를 자동으로 만들어주는 도구가 아니라, 이미 들어온 frame에서 길이 필드를 읽고 body를 잘라주는 Decoder라는 점을 확인했다.

즉, 클라이언트는 반드시 약속된 형식으로 `[길이][본문]`을 만들어 보내야 한다.
