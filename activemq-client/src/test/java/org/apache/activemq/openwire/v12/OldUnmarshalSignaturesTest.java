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
package org.apache.activemq.openwire.v12;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.DataInput;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;

import org.apache.activemq.command.PartialCommand;
import org.apache.activemq.openwire.BooleanStream;
import org.apache.activemq.openwire.DataStreamMarshaller;
import org.apache.activemq.openwire.OpenWireFormat;
import org.apache.activemq.util.ByteSequence;
import org.junit.Test;

public class OldUnmarshalSignaturesTest {

    private static final int DATA_LENGTH = 16;
    private static final byte DATA_BYTE = 0x5A;

    static class OldSignaturePartialCommandMarshaller extends PartialCommandMarshaller {
        @Override
        @SuppressWarnings("deprecation")
        public void tightUnmarshal(OpenWireFormat wireFormat, Object o, DataInput dataIn, BooleanStream bs) throws IOException {
            PartialCommand info = (PartialCommand) o;
            info.setCommandId(dataIn.readInt());
            info.setData(tightUnmarshalByteArray(dataIn, bs));
        }

        @Override
        @SuppressWarnings("deprecation")
        public void looseUnmarshal(OpenWireFormat wireFormat, Object o, DataInput dataIn) throws IOException {
            PartialCommand info = (PartialCommand) o;
            info.setCommandId(dataIn.readInt());
            info.setData(looseUnmarshalByteArray(dataIn));
        }
    }

    @Test
    public void testTightOldSignatureChecksBufferAgainstFrame() throws Exception {
        assertBufferCheckedAgainstFrame(true);
    }

    @Test
    public void testLooseOldSignatureChecksBufferAgainstFrame() throws Exception {
        assertBufferCheckedAgainstFrame(false);
    }

    @Test
    public void testOldSignatureReadsValidFrame() throws Exception {
        OpenWireFormat format = formatWithOldSignatureMarshaller(true);
        PartialCommand read = (PartialCommand) format.unmarshal(format.marshal(partialCommand()));
        assertTrue(Arrays.equals(partialCommand().getData(), read.getData()));
    }

    private void assertBufferCheckedAgainstFrame(boolean tight) throws Exception {
        OpenWireFormat format = formatWithOldSignatureMarshaller(tight);
        ByteSequence frame = format.marshal(partialCommand());
        overwriteDataLength(frame, 100000);
        try {
            format.unmarshal(frame);
            fail("Should have refused a buffer larger than its frame");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("larger than frame size"));
        }
    }

    private static PartialCommand partialCommand() {
        PartialCommand command = new PartialCommand();
        command.setCommandId(1);
        byte[] data = new byte[DATA_LENGTH];
        Arrays.fill(data, DATA_BYTE);
        command.setData(data);
        return command;
    }

    private static OpenWireFormat formatWithOldSignatureMarshaller(boolean tight) throws Exception {
        OpenWireFormat format = new OpenWireFormat(12);
        format.setTightEncodingEnabled(tight);
        Field field = OpenWireFormat.class.getDeclaredField("dataMarshallers");
        field.setAccessible(true);
        DataStreamMarshaller[] marshallers = (DataStreamMarshaller[]) field.get(format);
        marshallers[PartialCommand.DATA_STRUCTURE_TYPE & 0xFF] = new OldSignaturePartialCommandMarshaller();
        return format;
    }

    // The data length is the int written just before the payload.
    private static void overwriteDataLength(ByteSequence frame, int length) {
        byte[] bytes = frame.getData();
        int end = frame.getOffset() + frame.getLength();
        for (int i = frame.getOffset() + 4; i + DATA_LENGTH <= end; i++) {
            boolean payload = true;
            for (int j = 0; j < DATA_LENGTH && payload; j++) {
                payload = bytes[i + j] == DATA_BYTE;
            }
            if (payload) {
                bytes[i - 4] = (byte) (length >>> 24);
                bytes[i - 3] = (byte) (length >>> 16);
                bytes[i - 2] = (byte) (length >>> 8);
                bytes[i - 1] = (byte) length;
                return;
            }
        }
        throw new AssertionError("payload not found in the frame");
    }
}
