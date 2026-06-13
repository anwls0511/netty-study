# Netty Day 5: Decoder 검증 정책 보강

> 오늘은 JSON을 객체로 바꾸는 것에서 한 단계 더 나아가, 메시지 값이 유효한지도 Decoder에서 확인하도록 보강했다.

## 오늘의 목표

이전까지의 Decoder 테스트는 주로 메시지 경계와 JSON 파싱에 집중했다.

```text
TCP 조각이 합쳐지는가?
여러 메시지가 분리되는가?
JSON 문자열이 DeviceStatus 객체로 바뀌는가?
```

Day 5에서는 질문을 하나 더 추가했다.

```text
객체로 변환되었더라도 이 값을 실제 메시지로 인정해도 되는가?
```

## 왜 검증이 필요한가

JSON 형식이 맞다고 해서 항상 유효한 메시지는 아니다.

예를 들어 아래 메시지는 JSON 문법은 맞지만 장비 ID가 없다.

```json
{"temperature":25.1,"humidity":40.2,"timestamp":1717830000}
```

아래 메시지도 JSON 문법은 맞지만 습도 값이 비정상적이다.

```json
{"deviceId":"device-1","temperature":25.1,"humidity":101.0,"timestamp":1717830000}
```

그래서 `DeviceStatusJsonDecoder`에서 파싱 후 최소한의 검증을 추가했다.

## 추가한 검증 규칙

| 필드 | 규칙 |
| --- | --- |
| `deviceId` | `null`이거나 blank이면 안 된다. |
| `humidity` | 0 이상 100 이하만 허용한다. |
| `timestamp` | 0보다 커야 한다. |

현재 코드는 아래처럼 동작한다.

```java
DeviceStatus status = objectMapper.readValue(trimmed, DeviceStatus.class);
validate(status);

out.add(status);
```

검증 로직은 다음과 같다.

```java
if (status.deviceId() == null || status.deviceId().isBlank()) {
    throw new IllegalArgumentException("deviceId is required");
}
```

## 테스트한 케이스

이번에 추가한 테스트는 다음과 같다.

| 케이스 | 기대 결과 |
| --- | --- |
| `deviceId` 누락 | `DecoderException` 발생 |
| `deviceId` blank | `DecoderException` 발생 |
| `humidity` 101 | `DecoderException` 발생 |
| `timestamp` 누락 | `DecoderException` 발생 |

테스트 예시는 아래와 같다.

```java
assertThrows(DecoderException.class, () -> channel.writeInbound("""
        {"deviceId":" ","temperature":25.1,"humidity":40.2,"timestamp":1717830000}
        """));
```

## Decoder에서 어디까지 검증해야 할까

모든 비즈니스 검증을 Decoder에 넣는 것은 좋지 않다.

예를 들어 "이 장비 ID가 실제 등록된 장비인가?" 같은 검증은 Service나 도메인 계층에서 처리하는 편이 맞다.

하지만 아래처럼 메시지 자체가 성립하기 위한 최소 조건은 Decoder에서 걸러도 괜찮다고 봤다.

```text
필수 필드가 있는가?
값의 기본 범위가 말이 되는가?
타임스탬프가 존재하는가?
```

이렇게 하면 Handler는 "최소한 정상 형태의 DeviceStatus가 들어왔다"고 보고 저장 로직에 집중할 수 있다.

## 오늘 정리한 점

Decoder는 단순히 문자열을 객체로 바꾸는 역할만 할 수도 있지만, 프로토콜 관점에서는 메시지로 인정할 수 있는 최소 조건을 확인하는 역할도 할 수 있다.

다만 검증 위치는 항상 기준이 필요하다.

```text
메시지 형식과 필수 값 검증 -> Decoder
도메인 규칙과 저장 정책 검증 -> Service
```

이 기준을 정해두면 Handler가 너무 많은 책임을 가지지 않게 만들 수 있다.
