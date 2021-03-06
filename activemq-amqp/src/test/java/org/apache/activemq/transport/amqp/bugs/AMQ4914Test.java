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
package org.apache.activemq.transport.amqp.bugs;

import org.apache.activemq.transport.amqp.AmqpTestSupport;
import org.apache.qpid.amqp_1_0.jms.impl.ConnectionFactoryImpl;
import org.apache.qpid.amqp_1_0.jms.impl.QueueImpl;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import static org.junit.Assert.*;

public class AMQ4914Test extends AmqpTestSupport {
    @Rule
    public TestName testName = new TestName();

    protected static final Logger LOG = LoggerFactory.getLogger(AMQ4914Test.class);
    private final static String QUEUE_NAME="queue://ENTMQ476TestQueue";

    /**
     *
     * @param sizeInBytes
     * @return
     */
    private String createLargeString(int sizeInBytes) {
        byte[] base = {1, 2, 3, 4, 5, 6, 7, 8, 9, 0};
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < sizeInBytes; i++) {
            builder.append(base[i % base.length]);
        }

        LOG.debug("Created string with size : " + builder.toString().getBytes().length + " bytes");
        return builder.toString();
    }

    @Test(timeout = 2 * 60 * 1000)
    public void testSendSmallerMessages() throws JMSException {
        for (int i = 512; i <= (16 * 1024); i += 512) {
            doTestSendLargeMessage(i);
        }
    }

    @Ignore("AMQ-4914")
    @Test(timeout = 2 * 60 * 1000)
    public void testSendLargeMessages() throws JMSException {
        //for (int i = 32000; i < (32 *1024); i++) {
            doTestSendLargeMessage(32604);       // Fails at 32614; or 32604 with my changes to AmqpProtocolBuffer
        //}
    }

    public void doTestSendLargeMessage(int expectedSize) throws JMSException{
        LOG.info("doTestSendLargeMessage called with expectedSize " + expectedSize);
        String payload = createLargeString(expectedSize);
        assertEquals(expectedSize, payload.getBytes().length);

        Connection connection = createAMQPConnection(port, false);

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        QueueImpl queue = new QueueImpl(QUEUE_NAME);
        MessageProducer producer = session.createProducer(queue);
        TextMessage message = session.createTextMessage();
        message.setText(payload);
        producer.send(message);
        LOG.debug("Returned from send");

        MessageConsumer consumer = session.createConsumer(queue);
        connection.start();
        LOG.debug("Calling receive");
        Message receivedMessage = consumer.receive();
        assertNotNull(receivedMessage);
        assertTrue(receivedMessage instanceof TextMessage);
        TextMessage receivedTextMessage = (TextMessage) receivedMessage;
        String receivedText = receivedTextMessage.getText();
        assertEquals(expectedSize, receivedText.getBytes().length);
        assertEquals(payload, receivedText);
        connection.close();
    }

    private Connection createAMQPConnection(int testPort, boolean useSSL) throws JMSException {
        LOG.debug("In createConnection using port {} ssl? {}", testPort, useSSL);
        final ConnectionFactoryImpl connectionFactory = new ConnectionFactoryImpl("localhost", testPort, "admin", "password", null, useSSL);
        final Connection connection = connectionFactory.createConnection();
        connection.setExceptionListener(new ExceptionListener() {
            @Override
            public void onException(JMSException exception) {
                exception.printStackTrace();
            }
        });
        connection.start();
        return connection;
    }
}
