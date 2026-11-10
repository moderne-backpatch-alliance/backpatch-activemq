/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.util;

import static org.apache.activemq.util.MarshallingSupport.unmarshalPrimitiveMap;
import static org.apache.activemq.util.MarshallingSupport.unmarshalPrimitiveList;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UTFDataFormatException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.DeflaterOutputStream;

import org.fusesource.hawtbuf.UTF8Buffer;
import org.junit.Test;

public class MarshallingSupportTest {

    /**
     * Test method for
     * {@link org.apache.activemq.util.MarshallingSupport#propertiesToString(java.util.Properties)}.
     *
     * @throws Exception
     */
    @Test
    public void testPropertiesToString() throws Exception {
        Properties props = new Properties();
        for (int i = 0; i < 10; i++) {
            String key = "key" + i;
            String value = "value" + i;
            props.put(key, value);
        }
        String str = MarshallingSupport.propertiesToString(props);
        Properties props2 = MarshallingSupport.stringToProperties(str);
        assertEquals(props, props2);
    }

    @Test
    public void testUnmarshalPrimitiveMap() throws Exception {
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        DataOutputStream dataOut = new DataOutputStream(bytesOut);

        Map<String, Object> testMap = new HashMap<>();
        testMap.put("key1", "value1");
        // nested map with nested collection, should require depth of 2 being allowed
        Map<String, Object> nested = new HashMap<>();
        nested.put("string", "string");
        nested.put("nested", Arrays.<Object>asList("one"));
        testMap.put("key2", nested);

        MarshallingSupport.marshalPrimitiveMap(testMap, dataOut);
        DataInputStream dataIn = new DataInputStream(new ByteArrayInputStream(bytesOut.toByteArray()));

        //allowed
        assertEquals(2, unmarshalPrimitiveMap(dataIn, 100, 1024, 10).size());

        // too many properties
        dataIn.reset();
        assertException(() -> unmarshalPrimitiveMap(dataIn, 1, 1024, 10),
                "map is larger than the allowed size");

        // buffers too large
        dataIn.reset();
        assertException(() -> unmarshalPrimitiveMap(dataIn, 100, 4, 10),
                "Max buffer size: 4 exceeded, size: 6");

        // max depth violated
        dataIn.reset();
        assertException(() -> unmarshalPrimitiveMap(dataIn, 100, 1024, 1),
                "Max unmarshaling depth: 1 exceeded, depth: 2");
    }

    @Test
    public void testUnmarshalPrimitiveList() throws Exception {
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        DataOutputStream dataOut = new DataOutputStream(bytesOut);

        List<Object> testList = new ArrayList<>();
        testList.add("value");
        // nested list with nested collection, should require depth of 2 being allowed
        testList.add(Arrays.asList(Arrays.asList("one")));
        Map<String, Object> nested = new HashMap<>();
        nested.put("one", "two");
        nested.put("three", "four");
        testList.add(nested);

        MarshallingSupport.marshalPrimitiveList(testList, dataOut);
        DataInputStream dataIn = new DataInputStream(new ByteArrayInputStream(bytesOut.toByteArray()));

        //allowed
        assertEquals(3, unmarshalPrimitiveList(dataIn, 100, 1024, 2).size());

        // Max property size applies to maps, so this will check the nested map in the list
        // and should fail because the map has 2 properties
        dataIn.reset();
        assertException(() -> unmarshalPrimitiveList(dataIn, 1, 1024, 2),
                "map is larger than the allowed size");

        // buffers too large
        dataIn.reset();
        assertException(() -> unmarshalPrimitiveList(dataIn, 100, 2, 2),
                "Max buffer size: 2 exceeded, size: 3");

        // max depth violated
        dataIn.reset();
        assertException(() -> unmarshalPrimitiveList(dataIn, 100, 1024, 1),
                "Max unmarshaling depth: 1 exceeded, depth: 2");
    }

    // Reflection, so this test also compiles and fails properly without the limits
    @SuppressWarnings("unchecked")
    private static Map<String, Object> unmarshalPrimitiveMap(DataInputStream in, int maxPropertySize, int maxBufferSize, int maxDepth) throws Exception {
        return (Map<String, Object>) invokeLimited("unmarshalPrimitiveMap", in, maxPropertySize, maxBufferSize, maxDepth);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> unmarshalPrimitiveList(DataInputStream in, int maxPropertySize, int maxBufferSize, int maxDepth) throws Exception {
        return (List<Object>) invokeLimited("unmarshalPrimitiveList", in, maxPropertySize, maxBufferSize, maxDepth);
    }

    private static Object invokeLimited(String name, DataInputStream in, int maxPropertySize, int maxBufferSize, int maxDepth) throws Exception {
        Method method = MarshallingSupport.class.getMethod(name, DataInputStream.class, int.class, int.class, int.class);
        try {
            return method.invoke(null, in, maxPropertySize, maxBufferSize, maxDepth);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw e;
        }
    }

    @Test
    public void testPrimitiveValuesRoundTrip() throws Exception {
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        DataOutputStream dataOut = new DataOutputStream(bytesOut);
        String largeString = repeat("x", Short.MAX_VALUE / 4);

        MarshallingSupport.marshalPrimitive(dataOut, null);
        MarshallingSupport.marshalPrimitive(dataOut, true);
        MarshallingSupport.marshalPrimitive(dataOut, (byte) -12);
        MarshallingSupport.marshalPrimitive(dataOut, '\u03a9');
        MarshallingSupport.marshalPrimitive(dataOut, (short) 1234);
        MarshallingSupport.marshalPrimitive(dataOut, -123456);
        MarshallingSupport.marshalPrimitive(dataOut, 1234567890123L);
        MarshallingSupport.marshalPrimitive(dataOut, 1.25f);
        MarshallingSupport.marshalPrimitive(dataOut, -2.5d);
        MarshallingSupport.marshalPrimitive(dataOut, new byte[] {1, 2, 3});
        MarshallingSupport.marshalPrimitive(dataOut, "short string");
        MarshallingSupport.marshalPrimitive(dataOut, new UTF8Buffer("buffer string"));
        MarshallingSupport.marshalPrimitive(dataOut, largeString);

        DataInputStream dataIn = new DataInputStream(new ByteArrayInputStream(bytesOut.toByteArray()));
        assertNull(MarshallingSupport.unmarshalPrimitive(dataIn));
        assertEquals(Boolean.TRUE, MarshallingSupport.unmarshalPrimitive(dataIn));
        assertEquals(Byte.valueOf((byte) -12), MarshallingSupport.unmarshalPrimitive(dataIn));
        assertEquals(Character.valueOf('\u03a9'), MarshallingSupport.unmarshalPrimitive(dataIn));
        assertEquals(Short.valueOf((short) 1234), MarshallingSupport.unmarshalPrimitive(dataIn));
        assertEquals(Integer.valueOf(-123456), MarshallingSupport.unmarshalPrimitive(dataIn));
        assertEquals(Long.valueOf(1234567890123L), MarshallingSupport.unmarshalPrimitive(dataIn));
        assertEquals(Float.valueOf(1.25f), MarshallingSupport.unmarshalPrimitive(dataIn));
        assertEquals(Double.valueOf(-2.5d), MarshallingSupport.unmarshalPrimitive(dataIn));
        assertArrayEquals(new byte[] {1, 2, 3}, (byte[]) MarshallingSupport.unmarshalPrimitive(dataIn));
        assertEquals("short string", MarshallingSupport.unmarshalPrimitive(dataIn).toString());
        assertEquals("buffer string", MarshallingSupport.unmarshalPrimitive(dataIn).toString());
        assertEquals(largeString, MarshallingSupport.unmarshalPrimitive(dataIn).toString());
        assertEquals(0, dataIn.available());
    }

    @Test
    public void testCountUTFBytesAtEncodingBoundaries() {
        String[] values = {"", "\u0000", "\u0001", "\u007f", "\u0080", "\u07ff",
                "\u0800", "\uffff", "\ud83d\ude03", "ascii\u0000\u007f\u0080\u07ff\u0800\ud83d\ude03"};
        long[] lengths = {0, 2, 1, 1, 2, 2, 3, 3, 6, 21};
        for (int i = 0; i < values.length; i++) {
            assertEquals("Encoded length for case " + i, lengths[i], MarshallingSupport.countUTFBytes(values[i]));
        }
    }

    @Test
    public void testWriteUTFBytesToBufferMatchesModifiedUTFAtOffset() throws Exception {
        String value = "A\u0000\u007f\u0080\u07ff\u0800\uffff\ud83d\ude03";
        byte[] expected = modifiedUTFBytes(value);
        byte[] buffer = new byte[expected.length + 4];
        Arrays.fill(buffer, (byte) 0x55);

        assertEquals(expected.length + 2,
                MarshallingSupport.writeUTFBytesToBuffer(value, expected.length, buffer, 2));
        assertArrayEquals(expected, Arrays.copyOfRange(buffer, 2, 2 + expected.length));
        assertArrayEquals(new byte[] {0x55, 0x55}, Arrays.copyOfRange(buffer, 0, 2));
        assertArrayEquals(new byte[] {0x55, 0x55}, Arrays.copyOfRange(buffer, buffer.length - 2, buffer.length));
        assertEquals(2, MarshallingSupport.writeUTFBytesToBuffer("", 0, buffer, 2));
    }

    @Test
    public void testConvertUTF8WithBufMatchesJavaDecoderAtOffset() throws Exception {
        String value = "A\u0000\u007f\u0080\u07ff\u0800\uffff\ud83d\ude03";
        byte[] encoded = modifiedUTFBytes(value);
        byte[] buffer = new byte[encoded.length + 2];
        buffer[0] = (byte) 0xff;
        buffer[buffer.length - 1] = (byte) 0xff;
        System.arraycopy(encoded, 0, buffer, 1, encoded.length);

        assertEquals(value, MarshallingSupport.convertUTF8WithBuf(buffer, new char[value.length()], 1, encoded.length));
        assertEquals("", MarshallingSupport.convertUTF8WithBuf(buffer, new char[0], 1, 0));
    }

    @Test
    public void testConvertUTF8WithBufRejectsMalformedSequences() {
        byte[][] malformed = {
                {(byte) 0x80}, {(byte) 0xff}, {(byte) 0xc2}, {(byte) 0xc2, 0x41},
                {(byte) 0xe1}, {(byte) 0xe1, (byte) 0x80},
                {(byte) 0xe1, 0x41, (byte) 0x80}, {(byte) 0xe1, (byte) 0x80, 0x41},
                {(byte) 0xf0, (byte) 0x9f, (byte) 0x98, (byte) 0x83}
        };
        for (byte[] bytes : malformed) {
            assertThrows(UTFDataFormatException.class,
                    () -> MarshallingSupport.convertUTF8WithBuf(bytes, new char[bytes.length], 0, bytes.length));
        }
    }

    @Test
    public void testWriteUTF8UsesIntLengthAndModifiedUTFEncoding() throws Exception {
        String value = "A\u0000\u0080\u0800\ud83d\ude03";
        byte[] expected = modifiedUTFBytes(value);
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        DataOutputStream dataOut = new DataOutputStream(bytesOut);
        MarshallingSupport.writeUTF8(dataOut, value);
        MarshallingSupport.writeUTF8(dataOut, "");
        MarshallingSupport.writeUTF8(dataOut, null);

        DataInputStream dataIn = input(bytesOut.toByteArray());
        assertEquals(expected.length, dataIn.readInt());
        byte[] actual = new byte[expected.length];
        dataIn.readFully(actual);
        assertArrayEquals(expected, actual);
        assertEquals(0, dataIn.readInt());
        assertEquals(-1, dataIn.readInt());
        assertEquals(0, dataIn.available());
    }

    @Test
    public void testReadUTF8ReadsNullEmptyAndLongStringsWithoutConsumingNextValue() throws Exception {
        String longString = repeat("\u0800", 22000);
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        DataOutputStream dataOut = new DataOutputStream(bytesOut);
        dataOut.writeInt(-1);
        dataOut.writeInt(0);
        MarshallingSupport.writeUTF8(dataOut, longString);
        dataOut.writeByte(42);

        DataInputStream dataIn = input(bytesOut.toByteArray());
        assertNull(MarshallingSupport.readUTF8(dataIn));
        assertEquals("", MarshallingSupport.readUTF8(dataIn));
        assertEquals(longString, MarshallingSupport.readUTF8(dataIn));
        assertEquals(42, dataIn.readUnsignedByte());
    }

    @Test
    public void testReadUTF8EnforcesEncodedByteLimitAndRejectsTruncation() throws Exception {
        byte[] encoded = modifiedUTFBytes("\u0800");
        assertEquals("\u0800", MarshallingSupport.readUTF8(input(lengthPrefixed(encoded, false)), 3));
        assertThrows(MarshallingSupport.ActiveMQUnmarshalEOFException.class,
                () -> MarshallingSupport.readUTF8(input(lengthPrefixed(encoded, false)), 2));
        assertThrows(MarshallingSupport.ActiveMQUnmarshalEOFException.class,
                () -> MarshallingSupport.readUTF8(input(new byte[] {0, 0, 0, 3, 65})));
        assertThrows(EOFException.class, () -> MarshallingSupport.readUTF8(input(new byte[] {0, 0, 0})));
        assertThrows(UTFDataFormatException.class,
                () -> MarshallingSupport.readUTF8(input(lengthPrefixed(new byte[] {(byte) 0xc2, 65}, false))));
    }

    @Test
    public void testReadShortUTF8UsesUnsignedLengthAndEnforcesLimits() throws Exception {
        String value = repeat("x", 65535);
        byte[] encoded = modifiedUTFBytes(value);
        assertEquals(value, MarshallingSupport.readShortUTF8(input(lengthPrefixed(encoded, true)), 65535));
        assertEquals("", MarshallingSupport.readShortUTF8(input(new byte[] {0, 0}), 0));
        assertThrows(MarshallingSupport.ActiveMQUnmarshalEOFException.class,
                () -> MarshallingSupport.readShortUTF8(input(lengthPrefixed(encoded, true)), 65534));
        assertThrows(MarshallingSupport.ActiveMQUnmarshalEOFException.class,
                () -> MarshallingSupport.readShortUTF8(input(new byte[] {0, 2, 65}), 2));
        assertThrows(EOFException.class, () -> MarshallingSupport.readShortUTF8(input(new byte[] {0}), 10));
        assertThrows(UTFDataFormatException.class,
                () -> MarshallingSupport.readShortUTF8(input(lengthPrefixed(new byte[] {(byte) 0xc2}, true)), 1));
    }

    @Test
    public void testReadUTFRetainsEncodedBytesAndRespectsLength() throws Exception {
        byte[] encoded = modifiedUTFBytes("A\u0000\u0080\u0800");
        byte[] withTrailer = Arrays.copyOf(encoded, encoded.length + 1);
        withTrailer[encoded.length] = 42;
        DataInputStream dataIn = input(withTrailer);
        UTF8Buffer result = MarshallingSupport.readUTF(dataIn, encoded.length, encoded.length);
        assertArrayEquals(encoded, Arrays.copyOfRange(result.data, result.offset, result.offset + result.length));
        assertEquals(42, dataIn.readUnsignedByte());
        assertEquals("", MarshallingSupport.readUTF(input(new byte[0]), 0, 0).toString());
        assertThrows(MarshallingSupport.ActiveMQUnmarshalEOFException.class,
                () -> MarshallingSupport.readUTF(input(encoded), encoded.length - 1, encoded.length));
        assertThrows(MarshallingSupport.ActiveMQUnmarshalEOFException.class,
                () -> MarshallingSupport.readUTF(input(encoded), encoded.length + 1, encoded.length + 1));
    }

    @Test
    public void testMarshalStringAtShortAndBigStringBoundary() throws Exception {
        int boundary = Short.MAX_VALUE / 4;
        String[] values = {"", repeat("\u0800", boundary - 1), repeat("\u0800", boundary)};
        for (String value : values) {
            ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
            MarshallingSupport.marshalString(new DataOutputStream(bytesOut), value);
            DataInputStream dataIn = input(bytesOut.toByteArray());
            if (value.length() < boundary) {
                assertEquals(MarshallingSupport.STRING_TYPE, dataIn.readByte());
                assertEquals(value, dataIn.readUTF());
            } else {
                assertEquals(MarshallingSupport.BIG_STRING_TYPE, dataIn.readByte());
                assertEquals(value, MarshallingSupport.readUTF8(dataIn));
            }
            assertEquals(0, dataIn.available());
        }
    }

    private static DataInputStream input(byte[] bytes) {
        return new DataInputStream(new ByteArrayInputStream(bytes));
    }

    private static byte[] modifiedUTFBytes(String value) throws IOException {
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        new DataOutputStream(bytesOut).writeUTF(value);
        byte[] encoded = bytesOut.toByteArray();
        return Arrays.copyOfRange(encoded, 2, encoded.length);
    }

    private static byte[] lengthPrefixed(byte[] bytes, boolean shortLength) throws IOException {
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        DataOutputStream dataOut = new DataOutputStream(bytesOut);
        if (shortLength) {
            dataOut.writeShort(bytes.length);
        } else {
            dataOut.writeInt(bytes.length);
        }
        dataOut.write(bytes);
        return bytesOut.toByteArray();
    }

    @Test
    public void testPropertiesAndTruncateBoundaries() throws Exception {
        assertEquals("", MarshallingSupport.propertiesToString(null));
        assertEquals(new Properties(), MarshallingSupport.stringToProperties(null));
        assertEquals(new Properties(), MarshallingSupport.stringToProperties(""));
        assertEquals(repeat("x", 63), MarshallingSupport.truncate64(repeat("x", 63)));
        String longText = repeat("x", 64);
        assertEquals(repeat("x", 45) + "..." + repeat("x", 12), MarshallingSupport.truncate64(longText));
    }

    private static String repeat(String text, int count) {
        StringBuilder result = new StringBuilder(text.length() * count);
        for (int i = 0; i < count; i++) {
            result.append(text);
        }
        return result.toString();
    }

    private void assertException(ThrowableRunnable<Exception> runnable, String error) throws Exception {
        try {
            // Trigger unmarshaling the properties. This should throw an error because
            // the buffer is too large
            runnable.run();
            fail("should have IO exception");
        } catch (IOException e) {
            System.out.println(e.getMessage());
            assertTrue(e.getMessage().contains(error));
        }
    }

    private interface ThrowableRunnable<T extends Throwable> {
        void run() throws T;
    }
}
