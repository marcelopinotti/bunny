package com.marcelodev.userservice.controller;

import com.marcelodev.userservice.config.ApiExceptionHandler;
import com.marcelodev.userservice.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerValidationTest {

    private final UserService userService = mock(UserService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new UserController(userService))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void invalidBodyReturns400WithFieldErrors() throws Exception {
        var longName = "a".repeat(101);
        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + longName + "\",\"email\":\"nao-e-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists());

        verify(userService, never()).save(any());
    }
}
