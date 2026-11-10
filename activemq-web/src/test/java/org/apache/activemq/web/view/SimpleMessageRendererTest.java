/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.web.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.activemq.util.IntrospectionSupport;
import org.junit.Test;

public class SimpleMessageRendererTest {

    private String render(SimpleMessageRenderer renderer, HttpServletResponse response) throws Exception {
        StringWriter out = new StringWriter();
        Queue queue = mock(Queue.class);
        when(queue.toString()).thenReturn("queue'<a>&\"");
        Message message = mock(Message.class);
        when(message.getJMSMessageID()).thenReturn("id'<a>&\"");
        QueueBrowser browser = mock(QueueBrowser.class);
        when(browser.getQueue()).thenReturn(queue);
        when(browser.getMessageSelector()).thenReturn("JMSType = 'a<b>'");
        when(browser.getEnumeration()).thenReturn(Collections.enumeration(Collections.singletonList(message)));
        when(response.getWriter()).thenReturn(new PrintWriter(out, true));
        renderer.renderMessages(mock(HttpServletRequest.class), response, browser);
        return out.toString();
    }

    @Test
    public void testHeaderAndMessageIdAreEscaped() throws Exception {
        String output = render(new SimpleMessageRenderer(), mock(HttpServletResponse.class));
        assertTrue(output, output.contains("queue='queue&apos;&lt;a&gt;&amp;&quot;'"));
        assertTrue(output, output.contains("selector='JMSType = &apos;a&lt;b&gt;&apos;'"));
        assertTrue(output, output.contains("<message id='id&apos;&lt;a&gt;&amp;&quot;'/>"));
        assertFalse(output, output.contains("<a>"));
    }

    @Test
    public void testContentTypeCannotBeChangedByProperties() throws Exception {
        SimpleMessageRenderer renderer = new SimpleMessageRenderer();
        Map<String, String> properties = new HashMap<>();
        properties.put("contentType", "text/html");
        IntrospectionSupport.setProperties(renderer, properties);
        assertEquals("text/xml", renderer.getContentType());

        HttpServletResponse response = mock(HttpServletResponse.class);
        render(renderer, response);
        verify(response).setContentType("text/xml");
    }

    @Test
    public void testEntryContentTypeCannotBeChangedByProperties() throws Exception {
        RssMessageRenderer renderer = new RssMessageRenderer();
        Map<String, String> properties = new HashMap<>();
        properties.put("entryContentType", "text/html");
        IntrospectionSupport.setProperties(renderer, properties);
        assertEquals("text/plain", renderer.getEntryContentType());
    }
}
