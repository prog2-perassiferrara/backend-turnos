package com.prog2.appointment.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.prog2.appointment.reservation.domain.enums.ReservationActionType;
import com.prog2.appointment.reservation.domain.model.ReservationAction;
import java.util.Optional;
import com.prog2.appointment.reservation.infrastructure.messaging.ReservationActionParser;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Valida mensajes externos y su identidad estable, sin broker ni deserialización polimórfica. */
class ReservationActionParserTests {
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    private final ReservationActionParser parser =
            new ReservationActionParser(json, ReservationTestSupport.GROUP);

    private ObjectNode event() {
        return (ObjectNode)
                json.readTree(
                        """
                        {"schemaVersion":1,"eventId":"event-1","eventType":"AdditionalInformationRequested",
                        "reservationProcessId":"process-1","holdId":"hold-1","requiredField":"PHONE_NUMBER",
                        "expiresAt":"2026-11-02T11:05:00Z","occurredAt":"2026-11-02T11:00:01Z"}
                        """);
    }

    private Optional<ReservationAction> parse(ObjectNode node) {
        return parser.parse("process-1", json.writeValueAsString(node));
    }

    @Test
    void requestKeepsTheEventIdAndCompatibleFieldsDoNotChangeItsIdentity() {
        var source = event();
        var first = parse(source);
        source.putObject("futureField").put("anything", true);
        var repeat = parse(source);
        assertThat(first.orElseThrow().type()).isEqualTo(ReservationActionType.PHONE_REQUESTED);
        assertThat(first.orElseThrow().correlation().eventId()).isEqualTo("event-1");
        assertThat(repeat).isEqualTo(first);
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
    void missingRequiredDataIsRejectedWithoutAnAction(String field) {
        var source = event();
        source.remove(field);
        var delivery = parse(source);
        assertThat(delivery).isEmpty();
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
        assertThat(parse(source)).isEmpty();
    }

    @Test
    void unknownTypesRemainUnsupportedActionsWithoutPayloadAuditing() {
        var source = event();
        source.put("eventType", "FutureAction");
        assertThat(parse(source).orElseThrow().type()).isEqualTo(ReservationActionType.OTHER);
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
        assertThat(first.orElseThrow().reservationId()).isEqualTo(17);
        source.put("startTime", "09:00:00");
        assertThat(parse(source)).isEqualTo(first);
        source.remove("requestEventId");
        assertThat(parse(source)).isEmpty();
    }

    @Test
    void malformedAndOversizedBodiesDoNotBecomeActions() {
        assertThat(parser.parse("process-1", null)).isEmpty();
        assertThat(parser.parse("process-1", "{bad-json")).isEmpty();
        assertThat(parser.parse("process-1", "x".repeat(1_048_577))).isEmpty();
    }

    @Test
    void rejectionRequiresItsCorrelationAndAllowsCompatibleFields() {
        var source = event();
        source.put("eventType", "AdditionalInformationRejected")
                .put("groupId", ReservationTestSupport.GROUP)
                .put("requestEventId", "request-1")
                .put("reason", "INVALID_PHONE_NUMBER");
        var rejected = parse(source);
        assertThat(rejected.orElseThrow().type()).isEqualTo(ReservationActionType.PHONE_REJECTED);
        source.putObject("newCompatibleField").put("value", true);
        assertThat(parse(source)).isEqualTo(rejected);
        source.remove("groupId");
        assertThat(parse(source)).isEmpty();
    }
}
