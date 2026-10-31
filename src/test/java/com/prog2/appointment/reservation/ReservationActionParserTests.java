package com.prog2.appointment.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.prog2.appointment.reservation.domain.enums.ReservationActionType;
import com.prog2.appointment.reservation.domain.model.ReservationDelivery;
import com.prog2.appointment.reservation.infrastructure.messaging.ReservationActionParser;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Valida mensajes externos y su identidad estable, sin broker ni deserialización polimórfica. */
class ReservationActionParserTests {
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    private final ReservationActionParser parser =
            new ReservationActionParser(json, ReservationTestSupport.GROUP);
    private final ReservationDelivery.Origin origin =
            new ReservationDelivery.Origin("actions", 0, 3, "process-1");

    private ObjectNode event() {
        return (ObjectNode)
                json.readTree(
                        """
                        {"schemaVersion":1,"eventId":"event-1","eventType":"AdditionalInformationRequested",
                        "reservationProcessId":"process-1","holdId":"hold-1","requiredField":"PHONE_NUMBER",
                        "expiresAt":"2026-11-02T11:05:00Z","occurredAt":"2026-11-02T11:00:01Z"}
                        """);
    }

    private ReservationDelivery parse(ObjectNode node) {
        return parser.parse(origin, json.writeValueAsString(node));
    }

    @Test
    void requestKeepsTheEventIdAndCompatibleFieldsDoNotChangeItsIdentity() {
        var source = event();
        var first = parse(source);
        source.putObject("futureField").put("anything", true);
        var repeat = parse(source);
        assertThat(first.action().type()).isEqualTo(ReservationActionType.PHONE_REQUESTED);
        assertThat(first.action().correlation().eventId()).isEqualTo("event-1");
        assertThat(repeat.fingerprint()).isEqualTo(first.fingerprint());
        assertThat(repeat.rejection()).isNull();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "eventId",
                "reservationProcessId",
                "holdId",
                "requiredField",
                "expiresAt",
                "occurredAt",
                "schemaVersion"
            })
    void missingRequiredDataBecomesADurableRejection(String field) {
        var source = event();
        source.remove(field);
        var delivery = parse(source);
        assertThat(delivery.action()).isNull();
        assertThat(delivery.rejection()).isEqualTo("INVALID_ACTION");
        assertThat(delivery.payload()).contains("eventType");
    }

    @ParameterizedTest
    @ValueSource(strings = {"schemaVersion", "groupId", "reservationProcessId", "requiredField"})
    void incompatibleVersionGroupKeyOrFieldCannotChangeTheProcess(String field) {
        var source = event();
        if (field.equals("schemaVersion")) {
            source.put(field, 2);
        } else {
            source.put(field, "other");
        }
        assertThat(parse(source).rejection()).isEqualTo("INVALID_ACTION");
    }

    @Test
    void unknownTypesAreRetainedAndDifferentFactsCannotAliasTheSameEventId() {
        var source = event();
        source.put("eventType", "FutureAction");
        var first = parse(source);
        assertThat(first.action().type()).isEqualTo(ReservationActionType.OTHER);
        source.put("eventType", "AnotherFutureAction");
        assertThat(parse(source).fingerprint()).isNotEqualTo(first.fingerprint());
        source.put("eventType", "FutureAction").put("occurredAt", "2026-11-02T11:00:02Z");
        assertThat(parse(source).fingerprint()).isNotEqualTo(first.fingerprint());
    }

    @Test
    void confirmedRequiresReservationSelectionStatusAndRequestCorrelation() {
        var source = event();
        source.put("eventType", "AppointmentConfirmed")
                .put("groupId", ReservationTestSupport.GROUP)
                .put("requestEventId", "request-1")
                .put("reservationId", 17)
                .put("professionalId", 15)
                .put("date", "2026-11-02")
                .put("startTime", "09:00")
                .put("status", "CONFIRMED");
        var first = parse(source);
        assertThat(first.action().reservationId()).isEqualTo(17);
        source.put("startTime", "09:00:00");
        assertThat(parse(source).fingerprint()).isEqualTo(first.fingerprint());
        source.remove("requestEventId");
        assertThat(parse(source).action()).isNull();
    }

    @Test
    void malformedAndOversizedBodiesAreRetainedWithBoundedPayload() {
        assertThat(parser.parse(origin, null).rejection()).isEqualTo("INVALID_ACTION");
        assertThat(parser.parse(origin, "{bad-json").action()).isNull();
        var oversized = parser.parse(origin, "x".repeat(1_048_577));
        assertThat(oversized.rejection()).isEqualTo("PAYLOAD_TOO_LARGE");
        assertThat(oversized.payload()).hasSize(65536);
        assertThat(oversized.toString()).doesNotContain("x".repeat(20));
    }
}
