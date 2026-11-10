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
package org.apache.activemq.transport.tcp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.net.Socket;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.apache.activemq.openwire.OpenWireFormat;
import org.junit.Test;

public class SslTransportVerifyHostNameTest {

    private SslTransport createTransport() throws Exception {
        SSLSocketFactory factory = SSLContext.getDefault().getSocketFactory();
        return new SslTransport(new OpenWireFormat(), factory, new URI("ssl://localhost:61616"), null, false);
    }

    private String endpointIdentification(SslTransport transport) throws Exception {
        Socket socket = transport.socket;
        transport.initialiseSocket(socket);
        return ((SSLSocket) socket).getSSLParameters().getEndpointIdentificationAlgorithm();
    }

    @Test
    public void testClientVerifiesHostNameByDefault() throws Exception {
        assertEquals("HTTPS", endpointIdentification(createTransport()));
    }

    @Test
    public void testClientOptOutWithSocketOption() throws Exception {
        SslTransport transport = createTransport();
        Map<String, Object> options = new HashMap<>();
        options.put("verifyHostName", "false");
        transport.setSocketOptions(options);
        assertNull(endpointIdentification(transport));
    }

    @Test
    public void testServerSideSetterDisablesVerification() throws Exception {
        SslTransport transport = createTransport();
        transport.setVerifyHostName(false);
        assertNull(endpointIdentification(transport));
    }
}
