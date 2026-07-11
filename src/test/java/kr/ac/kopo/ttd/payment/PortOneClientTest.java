package kr.ac.kopo.ttd.payment;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class PortOneClientTest {

    private static final long PRICE = 9900L;

    private PortOneClient clientRespondingWith(String json, MockRestServiceServer[] holder) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(method(POST)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        holder[0] = server;
        return new PortOneClient(builder, "secret", "store-1", "channel-1", false);
    }

    @Test
    void 승인_금액이_요청_금액과_같으면_성공이다() {
        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        PortOneClient client = clientRespondingWith(
                "{\"status\":\"PAID\",\"paidAt\":\"2026-07-10T18:00:00+09:00\",\"amount\":{\"total\":9900}}", holder);

        PortOnePaymentResult result = client.payWithBillingKey("pay-1", "bk", PRICE, "주문");

        assertThat(result.success()).isTrue();
        holder[0].verify();
    }

    @Test
    void 승인_금액이_요청_금액과_다르면_실패로_처리한다() {
        // 할인/변조된 금액으로 PAID가 와도 성공으로 인정하지 않는다
        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        PortOneClient client = clientRespondingWith(
                "{\"status\":\"PAID\",\"amount\":{\"total\":5000}}", holder);

        PortOnePaymentResult result = client.payWithBillingKey("pay-1", "bk", PRICE, "주문");

        assertThat(result.success()).isFalse();
        assertThat(result.failReason()).contains("AMOUNT_MISMATCH");
    }

    @Test
    void 금액_필드가_없으면_승인을_거부한다() {
        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        PortOneClient client = clientRespondingWith("{\"status\":\"PAID\"}", holder);

        PortOnePaymentResult result = client.payWithBillingKey("pay-1", "bk", PRICE, "주문");

        assertThat(result.success()).isFalse();
        assertThat(result.failReason()).isEqualTo("AMOUNT_MISSING");
    }

    @Test
    void mock_모드는_데모_빌링키만_PG_실호출_없이_성공_처리한다() {
        // mockEnabled=true + 데모 빌링키(demo-*)면 RestClient를 건드리지 않아야 한다(설계서 S-13).
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        // 어떤 요청도 기대하지 않음 — 호출되면 verify에서 실패한다.
        PortOneClient client = new PortOneClient(builder, "secret", "store-1", "channel-1", true);

        PortOnePaymentResult result =
                client.payWithBillingKey("pay-1", "demo-f117-billing-abc", PRICE, "주문");

        assertThat(result.success()).isTrue();
        server.verify(); // 실호출이 없었음을 확인
    }

    @Test
    void mock_모드여도_실제_빌링키는_PG로_결제를_보낸다() {
        // 데모 키가 아니면 mockEnabled여도 실제 PortOne 결제 경로를 탄다(내부 승인 대상은 데모 키뿐).
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(method(POST)).andRespond(withSuccess(
                "{\"status\":\"PAID\",\"amount\":{\"total\":9900}}", MediaType.APPLICATION_JSON));
        PortOneClient client = new PortOneClient(builder, "secret", "store-1", "channel-1", true);

        PortOnePaymentResult result =
                client.payWithBillingKey("pay-1", "real-billing-key", PRICE, "주문");

        assertThat(result.success()).isTrue();
        server.verify(); // 실제로 PG 호출이 일어났음을 확인
    }
}
