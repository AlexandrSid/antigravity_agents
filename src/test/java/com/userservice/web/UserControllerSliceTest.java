package com.userservice.web;

import com.userservice.domain.DuplicateEmailException;
import com.userservice.domain.DuplicatePhoneException;
import com.userservice.domain.InvalidParentRelationshipException;
import com.userservice.domain.InvalidPhoneException;
import com.userservice.domain.ParentNotFoundException;
import com.userservice.domain.UserNotFoundException;
import com.userservice.dto.UserResponse;
import com.userservice.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(ApiExceptionHandler.class)
@AutoConfigureMockMvc
class UserControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiExceptionHandler apiExceptionHandler;

    @MockBean
    private UserService userService;

    @BeforeEach
    void stubUserService() {
        UserResponse persisted = sampleUser();
        when(userService.create(any())).thenReturn(persisted);
        when(userService.getById(anyLong())).thenReturn(persisted);
        when(userService.update(anyLong(), any())).thenReturn(persisted);
        when(userService.getByEmail(anyString())).thenReturn(persisted);
        when(userService.getDirectFamily(anyLong())).thenReturn(List.of(persisted, child()));
        doNothing().when(userService).softDelete(anyLong());
    }

    @Test
    void postUsers_validBody_returns201LocationAndPersistedDto() throws Exception {
        ResultActions actions = performSafely(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody()));
        rethrowIfNotImplemented(actions);
        actions.andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/users/42"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.childrenIds").isArray());
    }

    @Test
    void getUserById_returns200Dto() throws Exception {
        ResultActions actions = performSafely(get("/api/v1/users/42"));
        rethrowIfNotImplemented(actions);
        actions.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.email").value("ada@example.com"));
    }

    @Test
    void putUser_validReplacement_returns200Dto() throws Exception {
        ResultActions actions = performSafely(put("/api/v1/users/42")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody()));
        rethrowIfNotImplemented(actions);
        actions.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.email").value("ada@example.com"));
    }

    @Test
    void deleteUser_returns204AndEmptyBody() throws Exception {
        ResultActions actions = performSafely(delete("/api/v1/users/42"));
        rethrowIfNotImplemented(actions);
        actions.andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void getUserByEmail_passesQueryAndReturns200Dto() throws Exception {
        ResultActions actions = performSafely(get("/api/v1/users/by-email").param("email", "Ada@Example.com"));
        rethrowIfNotImplemented(actions);
        actions.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42));
        verify(userService).getByEmail("Ada@Example.com");
    }

    @Test
    void getDirectFamily_returnsTopLevelJsonArray() throws Exception {
        ResultActions actions = performSafely(get("/api/v1/users/42/family"));
        rethrowIfNotImplemented(actions);
        actions.andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(42))
                .andExpect(jsonPath("$[1].id").value(43));
    }

    @Test
    void postOrPut_blankNamesInvalidEmailOrFutureBirthDate_returns400ProblemDetails() throws Exception {
        apiExceptionHandler.handleMethodArgumentNotValid(validationException());
        String body = """
                {"firstName":" ","lastName":"","birthDate":"%s","email":"not-an-email"}
                """.formatted(LocalDate.now().plusDays(1));
        assertValidationProblem(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body), "/api/v1/users");
        assertValidationProblem(put("/api/v1/users/42").contentType(MediaType.APPLICATION_JSON).content(body), "/api/v1/users/42");
        verify(userService, never()).create(any());
        verify(userService, never()).update(anyLong(), any());
    }

    @Test
    void postOrPut_oversizedAddressFields_returns400WithInvalidParams() throws Exception {
        apiExceptionHandler.handleMethodArgumentNotValid(validationException());
        String oversized = "x".repeat(151);
        String body = """
                {
                  "firstName":"Ada",
                  "lastName":"Lovelace",
                  "birthDate":"1990-01-01",
                  "email":"ada@example.com",
                  "address":{"country":"%s","city":"%s","street":"%s","building":"%s","apartment":"%s","postalCode":"%s"}
                }
                """.formatted("c".repeat(101), "i".repeat(101), oversized, "b".repeat(51), "a".repeat(51), "p".repeat(21));
        assertValidationProblem(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body), "/api/v1/users");
        assertValidationProblem(put("/api/v1/users/42").contentType(MediaType.APPLICATION_JSON).content(body), "/api/v1/users/42");
        verify(userService, never()).create(any());
        verify(userService, never()).update(anyLong(), any());
    }

    @Test
    void postOrPut_invalidPhoneAfterNormalization_returns400ProblemDetails() throws Exception {
        when(userService.create(any())).thenThrow(new InvalidPhoneException("invalid phone"));
        when(userService.update(anyLong(), any())).thenThrow(new InvalidPhoneException("invalid phone"));
        String body = """
                {"firstName":"Ada","lastName":"Lovelace","birthDate":"1990-01-01","email":"ada@example.com","phoneNumber":"call-me"}
                """;
        assertValidationProblem(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body), "/api/v1/users");
        assertValidationProblem(put("/api/v1/users/42").contentType(MediaType.APPLICATION_JSON).content(body), "/api/v1/users/42");
    }

    @Test
    void response_nullOptionals_areOmittedAndChildrenIdsIsArray() throws Exception {
        when(userService.getById(42L)).thenReturn(new UserResponse(
                42L, "Ada", "Lovelace", LocalDate.of(1990, 1, 1), "ada@example.com",
                null, null, null, null, List.of()
        ));
        ResultActions actions = performSafely(get("/api/v1/users/42"));
        rethrowIfNotImplemented(actions);
        actions.andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.address").doesNotExist())
                .andExpect(jsonPath("$.fatherId").doesNotExist())
                .andExpect(jsonPath("$.motherId").doesNotExist())
                .andExpect(jsonPath("$.childrenIds").isArray())
                .andExpect(jsonPath("$.childrenIds.length()").value(0));
    }

    @Test
    void userNotFound_returns404UserNotFoundProblem() throws Exception {
        when(userService.getById(42L)).thenThrow(new UserNotFoundException("missing"));
        assertProblem(get("/api/v1/users/42"), 404, "urn:user-service:error:user-not-found", "User Not Found", "/api/v1/users/42");
    }

    @Test
    void parentNotFound_returns404ParentUserNotFoundProblem() throws Exception {
        when(userService.create(any())).thenThrow(new ParentNotFoundException("missing parent"));
        assertProblem(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(validBody()),
                404, "urn:user-service:error:parent-not-found", "Parent User Not Found", "/api/v1/users");
    }

    @Test
    void duplicateEmail_returns409EmailConflictProblem() throws Exception {
        when(userService.create(any())).thenThrow(new DuplicateEmailException("duplicate email"));
        assertProblem(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(validBody()),
                409, "urn:user-service:error:duplicate-email", "Email Conflict", "/api/v1/users");
    }

    @Test
    void duplicatePhone_returns409PhoneConflictProblem() throws Exception {
        when(userService.update(anyLong(), any())).thenThrow(new DuplicatePhoneException("duplicate phone"));
        assertProblem(put("/api/v1/users/42").contentType(MediaType.APPLICATION_JSON).content(validBody()),
                409, "urn:user-service:error:duplicate-phone", "Phone Number Conflict", "/api/v1/users/42");
    }

    @Test
    void invalidParentRelationship_returns400InvalidParentProblem() throws Exception {
        doThrow(new InvalidParentRelationshipException("cycle")).when(userService).update(anyLong(), any());
        assertProblem(put("/api/v1/users/42").contentType(MediaType.APPLICATION_JSON).content(validBody()),
                400, "urn:user-service:error:invalid-parent-relation", "Invalid Parent Relationship", "/api/v1/users/42");
    }

    @Test
    void beanValidationFailure_returns400WithInvalidParamsNameAndReason() throws Exception {
        String body = """
                {"firstName":"","lastName":"Lovelace","birthDate":"1990-01-01","email":"not-an-email"}
                """;
        apiExceptionHandler.handleMethodArgumentNotValid(validationException());
        ResultActions actions = performSafely(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body));
        rethrowIfNotImplemented(actions);
        actions.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:user-service:error:validation-failed"))
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/api/v1/users"))
                .andExpect(jsonPath("$.invalidParams").isArray())
                .andExpect(jsonPath("$.invalidParams[0].name").isNotEmpty())
                .andExpect(jsonPath("$.invalidParams[0].reason").isNotEmpty());
        verify(userService, never()).create(any());
    }

    @Test
    void malformedJsonOrInvalidDate_returns400MalformedJsonProblem() throws Exception {
        apiExceptionHandler.handleMalformedJson(new HttpMessageNotReadableException("bad json", new HttpInputMessage() {
            @Override
            public java.io.InputStream getBody() {
                return new java.io.ByteArrayInputStream(new byte[0]);
            }

            @Override
            public org.springframework.http.HttpHeaders getHeaders() {
                return new org.springframework.http.HttpHeaders();
            }
        }));
        assertProblem(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("{"),
                400, "urn:user-service:error:malformed-json", "Malformed JSON Payload", "/api/v1/users");
        assertProblem(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"birthDate\":\"not-a-date\",\"email\":\"ada@example.com\"}"),
                400, "urn:user-service:error:malformed-json", "Malformed JSON Payload", "/api/v1/users");
        verify(userService, never()).create(any());
    }

    private void assertValidationProblem(org.springframework.test.web.servlet.RequestBuilder request, String instance) throws Exception {
        assertProblem(request, 400, "urn:user-service:error:validation-failed", "Validation Failed", instance);
    }

    private void assertProblem(org.springframework.test.web.servlet.RequestBuilder request, int status, String type, String title, String instance) throws Exception {
        ResultActions actions = performSafely(request);
        rethrowIfNotImplemented(actions);
        actions.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(type))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value(instance));
    }

    private ResultActions performSafely(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        try {
            return mockMvc.perform(request);
        } catch (Exception exception) {
            UnsupportedOperationException unimplemented = findNotImplemented(exception);
            if (unimplemented != null) {
                throw unimplemented;
            }
            throw exception;
        }
    }

    private static void rethrowIfNotImplemented(ResultActions actions) throws Exception {
        MvcResult result;
        try {
            result = actions.andReturn();
        } catch (Exception exception) {
            UnsupportedOperationException unimplemented = findNotImplemented(exception);
            if (unimplemented != null) {
                throw unimplemented;
            }
            throw exception;
        }
        UnsupportedOperationException unimplemented = findNotImplemented(result.getResolvedException());
        if (unimplemented != null) {
            throw unimplemented;
        }
    }

    private static UnsupportedOperationException findNotImplemented(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof UnsupportedOperationException unsupported
                    && "Not implemented".equals(unsupported.getMessage())) {
                return unsupported;
            }
            if (current instanceof jakarta.servlet.ServletException servlet && servlet.getRootCause() != null
                    && servlet.getRootCause() != current) {
                current = servlet.getRootCause();
                continue;
            }
            current = current.getCause();
        }
        return null;
    }

    private static MethodArgumentNotValidException validationException() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "firstName", "must not be blank"));
        MethodParameter parameter = new MethodParameter(
                UserController.class.getMethod("create", com.userservice.dto.UserCreateRequest.class), 0);
        return new MethodArgumentNotValidException(parameter, bindingResult);
    }

    private static UserResponse sampleUser() {
        return new UserResponse(
                42L, "Ada", "Lovelace", LocalDate.of(1990, 1, 1), "ada@example.com",
                null, null, null, null, List.of()
        );
    }

    private static UserResponse child() {
        return new UserResponse(
                43L, "Child", "Lovelace", LocalDate.of(2010, 1, 1), "child@example.com",
                null, null, 42L, null, List.of()
        );
    }

    private static String validBody() {
        return """
                {"firstName":"Ada","lastName":"Lovelace","birthDate":"1990-01-01","email":"Ada@Example.com"}
                """;
    }
}
