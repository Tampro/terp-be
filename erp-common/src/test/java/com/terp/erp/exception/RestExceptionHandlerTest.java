package com.terp.erp.exception;

import jakarta.validation.constraints.Max;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Client mistakes must come back as 4xx in the API envelope, never as COMMON_002 / 500. */
class RestExceptionHandlerTest {
    enum EColor { RED }

    @RestController
    static class Probe {
        @GetMapping("/probe")
        String probe(@RequestParam @Max(12) int month, @RequestParam(required = false) EColor color) {
            return "ok";
        }

        @PostMapping(value = "/probe", consumes = MediaType.APPLICATION_JSON_VALUE)
        String body(@RequestBody String body) {
            return "ok";
        }
    }

    private final MockMvc mvc;

    RestExceptionHandlerTest() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new Probe())
                .setControllerAdvice(new RestExceptionHandler(new StaticMessageSource()))
                .setValidator(validator)
                .build();
    }

    @Test
    void validCallPasses() throws Exception {
        mvc.perform(get("/probe").param("month", "11")).andExpect(status().isOk());
    }

    @Test
    void missingParameterIs400() throws Exception {
        mvc.perform(get("/probe")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    void wrongTypeOrUnknownEnumIs400() throws Exception {
        mvc.perform(get("/probe").param("month", "abc")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
        mvc.perform(get("/probe").param("month", "1").param("color", "BAD")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    void constraintOnParameterIs400() throws Exception {
        mvc.perform(get("/probe").param("month", "13")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    void wrongMethodIs405AndWrongContentTypeIs415() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/probe"))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("COMMON_001"));
        mvc.perform(post("/probe").contentType(com.terp.erp.common.Nulls.nonNull(MediaType.TEXT_PLAIN)).content("x"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("COMMON_001"));
    }
}
