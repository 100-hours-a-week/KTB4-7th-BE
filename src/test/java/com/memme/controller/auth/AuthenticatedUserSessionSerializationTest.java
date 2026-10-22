package com.memme.controller.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

class AuthenticatedUserSessionSerializationTest {

    @Test
    void jdk_직렬화로_세션_속성을_저장하고_복원한다() throws Exception {
        AuthenticatedUserSession expected = new AuthenticatedUserSession(17L, 28L);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(expected);
        }

        Object restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = input.readObject();
        }

        assertEquals(expected, restored);
    }
}
