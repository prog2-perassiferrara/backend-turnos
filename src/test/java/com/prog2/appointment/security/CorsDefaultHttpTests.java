package com.prog2.appointment.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import com.prog2.appointment.infrastructure.security.config.SecurityConfiguration;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Contexto sin orígenes configurados: CORS cerrado no impide clientes nativos con bearer. */
@WebMvcTest(controllers = SecurityHttpTests.ProbeController.class)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({SecurityConfiguration.class, SecurityHttpTests.Fixtures.class, SecurityHttpTests.ProbeController.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CorsDefaultHttpTests {
    private final MockMvc mvc;

    CorsDefaultHttpTests(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void closesWebOriginsByDefaultWhileAcceptingNativeBearerRequests() throws Exception {
        mvc.perform(options(SecurityHttpTests.IDENTITY).header(HttpHeaders.ORIGIN, "https://app.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        String token = JwtTestTokens.signed(JwtTestTokens.claims());
        mvc.perform(get(SecurityHttpTests.IDENTITY).header(HttpHeaders.ORIGIN, "https://app.test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get(SecurityHttpTests.IDENTITY).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subject").value("42"));
    }
}
