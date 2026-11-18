package com.group.restaurant;

import com.group.restaurant.controllers.GuestAccessController;
import com.group.restaurant.exceptions.GlobalExceptionHandler;
import com.group.restaurant.services.guest.GuestAdmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.asm.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Reproduce IDE compilation that omits Java's optional MethodParameters metadata. */
class GuestControllerParameterBindingTest {
    @Test void tableLookupWorksWithoutCompilerParameterNames() throws Exception {
        var admission = mock(GuestAdmissionService.class);
        when(admission.info(1L, "QR-B1-T5")).thenReturn(new GuestAdmissionService.TableInfo(
                5L, 1L, "Table 5", "Branch", "Restaurant", false, false));
        var mvc = MockMvcBuilders.standaloneSetup(controllerWithoutParameterNames(admission))
                .addPlaceholderValue("api.prefix", "/api/v1")
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/v1/guest/table").param("branchId", "1").param("qrCode", "QR-B1-T5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.tableId").value(5));
    }

    @Test void unknownQrReturnsNotFoundWithoutCompilerParameterNames() throws Exception {
        var admission = mock(GuestAdmissionService.class);
        when(admission.info(1L, "unknown")).thenThrow(new ResponseStatusException(NOT_FOUND, "Unknown QR"));
        var mvc = MockMvcBuilders.standaloneSetup(controllerWithoutParameterNames(admission))
                .addPlaceholderValue("api.prefix", "/api/v1")
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/v1/guest/table").param("branchId", "1").param("qrCode", "unknown"))
                .andExpect(status().isNotFound());
    }

    @Test void missingResourceDoesNotBecomeInternalServerError() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new MissingResourceController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/missing-api")).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    @org.springframework.web.bind.annotation.RestController
    static class MissingResourceController {
        @org.springframework.web.bind.annotation.GetMapping("/missing-api")
        public void missing() throws org.springframework.web.servlet.resource.NoResourceFoundException {
            throw new org.springframework.web.servlet.resource.NoResourceFoundException(
                    org.springframework.http.HttpMethod.GET, "missing-api");
        }
    }

    private Object controllerWithoutParameterNames(GuestAdmissionService admission) throws Exception {
        byte[] original;
        try (var input = GuestAccessController.class.getResourceAsStream("GuestAccessController.class")) {
            original = input.readAllBytes();
        }
        var writer = new ClassWriter(0);
        new ClassReader(original).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override public void visitParameter(String name, int access) { /* intentionally omitted */ }
                };
            }
        }, 0);
        byte[] withoutNames = writer.toByteArray();
        var loader = new ClassLoader(GuestAccessController.class.getClassLoader()) {
            Class<?> defineController() {
                return defineClass(GuestAccessController.class.getName(), withoutNames, 0, withoutNames.length);
            }
        };
        return loader.defineController().getConstructor(GuestAdmissionService.class).newInstance(admission);
    }
}
