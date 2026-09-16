# 포트원 V1 · 토스페이먼츠 테스트 결제

현재 연동은 시연용 카드 결제입니다. V1 브라우저 SDK와 V1 REST API를 사용하며, 서버는 `tosspayments` / `iamporttest_3` / KRW / card 결제만 수락합니다. 운영 채널이나 다른 MID는 지원하지 않습니다.

## 로컬 설정

사용자가 확인한 테스트 채널의 고객사 식별코드와 채널 키는 Git에서 제외된 `src/main/resources/application-local.properties`에 설정했습니다. 다음 두 값을 같은 파일에 직접 추가하거나 환경변수 `PORTONE_API_KEY`, `PORTONE_API_SECRET`으로 설정하세요. 채팅이나 커밋에 넣지 마세요.

```properties
portone.api-key=포트원_V1_REST_API_Key
portone.api-secret=포트원_V1_REST_API_Secret
```

팀원은 `application-local.properties.example`의 PortOne 항목을 복사하고 본인의 테스트 채널 정보를 입력합니다. 공유 설정은 기본 비활성화 상태입니다. `enabled`, `test-channel-confirmed`, 고객사 식별코드, 채널 키와 API 인증정보가 모두 있어야 결제가 활성화됩니다.

서버를 재시작한 뒤 미래 일정 신청 → 관리자 승인 및 총액 설정 → 결제 화면에서 연락처 입력 → 테스트 결제 순으로 확인합니다. 설정이 미완료면 결제 버튼이 비활성화됩니다. 브라우저에서 서버 키를 내려받지 않으며 카드 번호는 사이트에서 직접 받지 않습니다.

테스트 MID 확인은 서버의 결제 결과 검증입니다. **결제창을 열기 전 포트원 콘솔의 해당 채널이 토스페이먼츠 신모듈·테스트 모드·MID iamporttest_3인지 확인해야 합니다.** 서버가 승인 후 MID 불일치를 발견하는 것으로 실제 승인을 사전에 막을 수는 없습니다. 운영 채널로 설정을 바꾸지 마세요. 사용자가 제공한 콘솔 설정 외에 해당 계정의 채널을 API로 사전 감사한 것은 아닙니다.

## 웹훅

포트원 콘솔에서 결제알림(Webhook)을 **V1 / 테스트 / application/json**으로 설정하고 공개 HTTPS 주소 `https://서비스주소/api/portone/webhook`을 등록합니다. `localhost`는 포트원에서 접근할 수 없으므로 테스트 서버 또는 공개 HTTPS 터널 주소가 필요합니다. 주소 등록이나 터널 공개는 이번 코드 작업에서 자동 수행하지 않습니다.

브라우저 콜백과 모바일 복귀는 로그인한 사용자의 CSRF 보호된 검증 API를 호출합니다. 웹훅만 CSRF 예외이며, 전달된 status/amount를 신뢰하지 않고 서버 비밀키로 포트원 결제를 다시 조회합니다. 중복 알림은 동일한 예약 확정 이벤트를 재발행하지 않습니다. JSON 형식만 지원합니다.

브라우저를 닫거나 통신 오류가 난 경우 웹훅이 결과를 반영합니다. 모바일 결과 화면 및 PC 결제 화면에는 결과 재확인 동작이 있습니다. 웹훅 연결을 완료하기 전에는 브라우저 종료 후 자동 반영을 보장할 수 없습니다.

## 데이터와 상태

- `reservation_payment`에 고유한 `merchant_uid`, `imp_uid`가 추가됩니다. 현재 Hibernate update 설정으로 적용됩니다.
- 서버가 저장한 결제 ID와 승인 금액을 사용합니다. 결제창 재시도는 같은 주문번호를 사용하여 동일 예약의 이중 결제를 방지합니다.
- 결제 준비 후에는 신청을 수정할 수 없습니다. 날짜·시간·인원을 변경하려면 취소 후 새로 신청해야 합니다. 준비 이전에는 기존 수정/재검토 흐름을 유지합니다.
- 포트원에서 paid 상태, 주문번호, imp_uid, 정확한 금액, 통화, PG, 테스트 MID, 카드 결제, 취소 금액 및 승인 시각을 검증한 후 확정합니다.
- 기한 안에 승인됐으나 웹훅이 늦게 온 경우 정상 반영합니다. 이미 취소됐거나 기한 후 결제 또는 차단된 장소인 경우 테스트 결제를 전액 취소하고 신청을 종료합니다.
- 기존 환불 가능 시점에 사용자가 취소하면 포트원 취소 API를 호출하고 재조회로 확인한 뒤 환불 완료로 표시합니다. 취소/조회 통신 실패는 DB상 환불 완료로 처리하지 않습니다. 기한 후 취소는 기존 환불 불가 정책을 유지합니다.
- 기존 모의 결제 데이터(imp_uid 없음)는 기존 내부 취소 흐름을 유지하며 외부 환불 API를 호출하지 않습니다.
- 부분 환불 및 운영 결제는 지원하지 않습니다. 통신 타임아웃 뒤에는 재조회/재시도로 결과를 확인해야 합니다. 별도 정기 대사 작업은 포함하지 않습니다.
- 시연 규모에서는 예약 쓰기 잠금 안에서 외부 조회/취소를 수행합니다. 실제 운영 전에는 비동기 처리 및 대사 작업을 도입해야 합니다.

## 검증

```powershell
.\mvnw.cmd "-Dtest=Reservation*Test,PortOne*Test,AdminSecurityTest" test
node --check src/main/resources/static/js/reservation/portone-payment.js
```

HTTP 모의 서버로 인증·결제 조회·취소 계약을 검증합니다. 실제 포트원 자격정보나 카드로 결제를 실행하는 테스트가 아닙니다.

공식 참고:
시연 화면은 일반 결제 문구와 결제 완료 화면을 사용하지만, 테스트 채널 설정과 서버의 테스트 MID 검증은 유지합니다. PG 자체 결제창의 테스트 표시는 변경하지 않습니다. 관리자 신청 검토의 장소 정보에는 좌표가 있을 때 OpenStreetMap 지도를 표시합니다.

- https://developers.portone.io/opi/ko/integration/pg/v1/newtoss/readme
- https://help.portone.io/content/tosspayments
- https://developers.portone.io/opi/ko/integration/cancel/v1/basic
