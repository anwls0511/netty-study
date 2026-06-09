# Netty Day 2: TCP 메시지 경계 실험

> 오늘은 TCP에서 메시지가 쪼개지거나 붙어서 들어오는 상황을 직접 실험했다.

## 목차

- [1. 오늘의 목표](#1-오늘의-목표)
- [2. 왜 실험이 필요한가](#2-왜-실험이-필요한가)
- [3. 실험 케이스](#3-실험-케이스)
- [4. 클라이언트 코드](#4-클라이언트-코드)
- [5. Decoder 테스트](#5-decoder-테스트)
- [6. 정리](#6-정리)

## 1. 오늘의 목표

Day 1에서는 Netty Pipeline과 Decoder가 왜 필요한지 정리했다. Day 2에서는 그 내용을 코드로 확인해보는 것이 목표다.

이번 실험에서는 클라이언트가 메시지를 세 가지 방식으로 보낸다.

| 케이스 | 설명 | 기대 결과 |
| --- | --- | --- |
| 완성된 메시지 | JSON 한 개를 한 번에 전송 | 서버가 메시지 1개로 처리 |
| 쪼개진 메시지 | JSON 한 개를 두 번에 나눠 전송 | 줄바꿈이 올 때까지 기다렸다가 메시지 1개로 처리 |
| 붙어 있는 메시지 | JSON 두 개를 한 번에 전송 | 서버가 메시지 2개로 분리 처리 |

## 2. 왜 실험이 필요한가

TCP는 스트림 기반 프로토콜이라서 클라이언트의 `write()` 한 번이 서버의 `read()` 한 번과 같다는 보장이 없다.

예를 들어 클라이언트가 아래 메시지를 보냈다고 해도,

```json
{"deviceId":"device-2","temperature":26.3,"humidity":41.0,"timestamp":1717830001}
```

서버에는 이렇게 나눠 들어올 수 있다.

```text
{"deviceId":"device-2","temperature":26.3,
```

```text
"humidity":41.0,"timestamp":1717830001}
```

반대로 두 메시지가 한 번에 붙어서 들어올 수도 있다.

```text
{"deviceId":"device-3","temperature":27.0,"humidity":42.5,"timestamp":1717830002}
{"deviceId":"device-4","temperature":28.4,"humidity":43.1,"timestamp":1717830003}
```

그래서 서버와 클라이언트는 메시지 경계 규칙을 약속해야 한다. 이번 예제에서는 `\n`을 메시지 끝으로 정했다.

## 3. 실험 케이스

`BoundaryExperimentClient`는 서버에 연결한 뒤 세 가지 메시지를 보낸다.

```text
case 1: 완성된 메시지 1개
case 2: 하나의 메시지를 두 번에 나눠 전송
case 3: 두 개의 메시지를 한 번에 전송
```

서버의 `LineBasedFrameDecoder`는 줄바꿈이 오기 전까지 데이터를 모아두고, 줄바꿈을 만나면 하나의 프레임으로 다음 Handler에 넘긴다.

## 4. 클라이언트 코드

쪼개진 메시지 실험은 아래처럼 작성했다.

```java
String part1 = "{\"deviceId\":\"device-2\",\"temperature\":26.3,";
String part2 = "\"humidity\":41.0,\"timestamp\":1717830001}\n";

write(out, part1);
Thread.sleep(500);
write(out, part2);
```

첫 번째 `write()`에서는 줄바꿈이 없기 때문에 서버가 아직 메시지를 처리하면 안 된다. 두 번째 `write()`에서 줄바꿈이 들어오면 그때 메시지 하나로 처리된다.

붙어 있는 메시지 실험은 아래처럼 작성했다.

```java
String messages = """
        {"deviceId":"device-3","temperature":27.0,"humidity":42.5,"timestamp":1717830002}
        {"deviceId":"device-4","temperature":28.4,"humidity":43.1,"timestamp":1717830003}
        """;

write(out, messages);
```

서버는 이 데이터를 한 번에 읽더라도 줄바꿈 기준으로 두 개의 메시지로 나눠 처리한다.

## 5. Decoder 테스트

실제 소켓을 열지 않고도 Netty Handler를 테스트할 수 있다. Netty의 `EmbeddedChannel`을 사용하면 Pipeline에 Decoder를 넣고 inbound 데이터를 직접 밀어 넣을 수 있다.

쪼개진 메시지 테스트는 아래 흐름으로 작성했다.

```java
channel.writeInbound(Unpooled.copiedBuffer("{\"deviceId\":\"device-1\",", StandardCharsets.UTF_8));

assertNull(channel.readInbound());

channel.writeInbound(Unpooled.copiedBuffer("\"temperature\":25.1}\n", StandardCharsets.UTF_8));

assertEquals("{\"deviceId\":\"device-1\",\"temperature\":25.1}", channel.readInbound());
```

첫 번째 조각만 들어왔을 때는 아직 줄바꿈이 없기 때문에 `readInbound()` 결과가 없어야 한다. 두 번째 조각에서 줄바꿈이 들어오면 메시지 하나가 완성된다.

## 6. 정리

이번 실험으로 확인한 내용은 단순하다.

| 상황 | Decoder 동작 |
| --- | --- |
| 메시지가 반만 들어옴 | 줄바꿈이 올 때까지 기다린다. |
| 메시지가 여러 개 붙어 들어옴 | 줄바꿈 기준으로 여러 프레임을 만든다. |
| 줄바꿈이 없음 | 아직 메시지 하나가 완성되지 않았다고 본다. |

결국 핵심은 TCP가 메시지 경계를 보장하지 않기 때문에, 서버와 클라이언트가 경계 규칙을 정해야 한다는 점이다. 이번 예제에서는 줄바꿈을 사용했지만, 실제 서비스에서는 XML이나 바이너리 데이터에 따라 길이 기반 프로토콜을 선택할 수도 있다.
