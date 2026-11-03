package com.prog2.appointment.openapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

import com.prog2.appointment.security.JwtTestTokens;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = OpenApiTestConfiguration.class, properties = "API_DOCS_ENABLED=false")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("openapi-test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OpenApiDisabledHttpTests {
    private final MockMvc mvc;

    OpenApiDisabledHttpTests(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void documentationIsNotExposedByDefaultEvenToAnAuthenticatedUser() throws Exception {
        String user = JwtTestTokens.signed(JwtTestTokens.claims());
        for (String path : new String[]{"/v3/api-docs", "/v3/api-docs.yaml",
                "/v3/api-docs/swagger-config", "/swagger-ui.html", "/swagger-ui/index.html",
                "/swagger-ui/swagger-ui-bundle.js"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + user))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void yamlDisablesDocumentationByDefaultAndOneOverrideControlsAllThreeSettings() throws Exception {
        var environment = new MockEnvironment();
        new YamlPropertySourceLoader()
                .load("defaults", new ClassPathResource("application.yaml"))
                .forEach(source -> environment.getPropertySources().addLast(source));
        String[] settings = {"app.api-docs.enabled", "springdoc.api-docs.enabled",
                "springdoc.swagger-ui.enabled"};
        for (String setting : settings) {
            assertThat(environment.getProperty(setting, Boolean.class)).isFalse();
        }
        environment.setProperty("API_DOCS_ENABLED", "true");
        for (String setting : settings) {
            assertThat(environment.getProperty(setting, Boolean.class)).isTrue();
        }
    }
}
