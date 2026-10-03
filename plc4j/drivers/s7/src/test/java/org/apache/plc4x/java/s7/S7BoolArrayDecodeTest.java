/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.plc4x.java.s7;

import org.apache.plc4x.java.api.value.PlcValue;
import org.apache.plc4x.java.s7.configuration.S7Configuration;
import org.apache.plc4x.java.s7.readwrite.ControllerType;
import org.apache.plc4x.java.s7.tag.S7Tag;
import org.apache.plc4x.java.spi.buffers.api.exceptions.BufferException;
import org.apache.plc4x.java.utils.auditlog.api.AuditLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class S7BoolArrayDecodeTest {

    @ParameterizedTest
    @ValueSource(ints = {2, 7, 8, 9, 15, 16, 17})
    void decodesPackedBitsAcrossByteBoundaries(int count) throws Exception {
        byte[] packed = new byte[(count + 7) / 8];
        // A non-symmetric pattern distinguishes bit order and byte boundaries.
        for (int i = 0; i < count; i++) {
            if (i % 3 == 0 || i % 5 == 1) {
                packed[i / 8] |= (byte) (1 << (i % 8));
            }
        }
        PlcValue value = decode("%DB42:214.0[0.." + (count - 1) + "]:BOOL", packed);
        assertTrue(value.isList());
        assertEquals(count, value.getList().size());
        for (int i = 0; i < count; i++) {
            assertEquals(i % 3 == 0 || i % 5 == 1, value.getIndex(i).getBoolean(),
                "bit " + i);
        }
    }

    @Test
    void ignoresUnusedHighBitsOfTheLastByte() throws Exception {
        PlcValue value = decode("%DB42:214.0[0..8]:BOOL", new byte[]{0, (byte) 0xfe});
        assertEquals(9, value.getList().size());
        for (PlcValue bit : value.getList()) {
            assertFalse(bit.getBoolean());
        }
    }

    @Test
    void keepsScalarBoolDecoding() throws Exception {
        assertTrue(decode("%DB42:214.0:BOOL", new byte[]{1}).getBoolean());
        assertFalse(decode("%DB42:214.0:BOOL", new byte[]{0}).getBoolean());
    }

    @Test
    void rejectsTruncatedPackedArray() throws Exception {
        InvocationTargetException error = assertThrows(InvocationTargetException.class,
            () -> decode("%DB42:214.0[0..8]:BOOL", new byte[]{1}));
        assertInstanceOf(BufferException.class, error.getCause());
    }

    private static PlcValue decode(String address, byte[] payload) throws Exception {
        S7Configuration configuration = new S7Configuration();
        configuration.setControllerType(ControllerType.S7_1500);
        S7CotpConnection connection = new S7CotpConnection(configuration,
            new S7ScriptedConnectionHarness.ScriptedS7Transport(), AuditLog.builder().build());
        Method decoder = S7CotpConnection.class.getDeclaredMethod("parsePlcValue", S7Tag.class, byte[].class);
        decoder.setAccessible(true);
        return (PlcValue) decoder.invoke(connection, S7Tag.of(address), payload);
    }
}
