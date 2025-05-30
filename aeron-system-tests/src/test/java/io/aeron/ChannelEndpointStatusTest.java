/*
 * Copyright 2014-2025 Real Logic Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.aeron;

import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import io.aeron.driver.exceptions.InvalidChannelException;
import io.aeron.exceptions.AeronException;
import io.aeron.exceptions.ChannelEndpointException;
import io.aeron.exceptions.RegistrationException;
import io.aeron.logbuffer.LogBufferDescriptor;
import io.aeron.protocol.DataHeaderFlyweight;
import io.aeron.status.ChannelEndpointStatus;
import io.aeron.test.InterruptAfter;
import io.aeron.test.InterruptingTestCallback;
import io.aeron.test.SystemTestWatcher;
import io.aeron.test.Tests;
import io.aeron.test.driver.TestMediaDriver;
import org.agrona.CloseHelper;
import org.agrona.ErrorHandler;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(InterruptingTestCallback.class)
@InterruptAfter(20)
class ChannelEndpointStatusTest
{
    private static final String URI = "aeron:udp?endpoint=localhost:23456";
    private static final String URI_NO_CONFLICT = "aeron:udp?endpoint=localhost:23457";
    private static final String URI_WITH_INTERFACE_PORT =
        "aeron:udp?endpoint=localhost:23456|interface=localhost:24567";

    private static final int STREAM_ID = 1001;
    private static final ThreadingMode THREADING_MODE = ThreadingMode.DEDICATED;

    private static final int TERM_BUFFER_LENGTH = LogBufferDescriptor.TERM_MIN_LENGTH;
    private static final int NUM_MESSAGES_PER_TERM = 64;
    private static final int MESSAGE_LENGTH =
        (TERM_BUFFER_LENGTH / NUM_MESSAGES_PER_TERM) - DataHeaderFlyweight.HEADER_LENGTH;

    private Aeron clientA;
    private Aeron clientB;
    private Aeron clientC;
    private TestMediaDriver driverA;
    private TestMediaDriver driverB;

    private final UnsafeBuffer buffer = new UnsafeBuffer(new byte[MESSAGE_LENGTH]);

    private final ErrorHandler errorHandlerClientA = mock(ErrorHandler.class);
    private final ErrorHandler errorHandlerClientB = mock(ErrorHandler.class);
    private final ErrorHandler errorHandlerClientC = mock(ErrorHandler.class);

    private final AtomicReference<Throwable> testException = new AtomicReference<>();
    private final ErrorHandler driverErrorHandler =
        (ex) ->
        {
            if (ex instanceof AeronException && ex.getMessage().contains("Address already in use"))
            {
                return;
            }
            else if (ex instanceof InvalidChannelException)
            {
                return;
            }

            testException.set(ex);
        };

    @RegisterExtension
    final SystemTestWatcher testWatcher = new SystemTestWatcher();

    @BeforeEach
    void before(@TempDir final File tempDir)
    {
        final String baseDirA = new File(tempDir, "A").getAbsolutePath();
        final String baseDirB = new File(tempDir, "B").getAbsolutePath();

        buffer.putInt(0, 1);

        final MediaDriver.Context driverAContext = new MediaDriver.Context()
            .publicationTermBufferLength(TERM_BUFFER_LENGTH)
            .aeronDirectoryName(baseDirA)
            .errorHandler(driverErrorHandler)
            .threadingMode(THREADING_MODE);

        final MediaDriver.Context driverBContext = new MediaDriver.Context()
            .publicationTermBufferLength(TERM_BUFFER_LENGTH)
            .aeronDirectoryName(baseDirB)
            .errorHandler(driverErrorHandler)
            .threadingMode(THREADING_MODE);

        driverA = TestMediaDriver.launch(driverAContext, testWatcher);
        testWatcher.dataCollector().add(driverA.context().aeronDirectory());
        driverB = TestMediaDriver.launch(driverBContext, testWatcher);
        testWatcher.dataCollector().add(driverB.context().aeronDirectory());

        testWatcher.ignoreErrorsMatching((s) -> true);

        clientA = Aeron.connect(
            new Aeron.Context()
                .aeronDirectoryName(driverAContext.aeronDirectoryName())
                .errorHandler(errorHandlerClientA));

        clientB = Aeron.connect(
            new Aeron.Context()
                .aeronDirectoryName(driverBContext.aeronDirectoryName())
                .errorHandler(errorHandlerClientB));

        clientC = Aeron.connect(
            new Aeron.Context()
                .aeronDirectoryName(driverBContext.aeronDirectoryName())
                .errorHandler(errorHandlerClientC));
    }

    @AfterEach
    void after()
    {
        CloseHelper.closeAll(clientC, clientB, clientA, driverB, driverA);
    }

    @Test
    void shouldErrorBadUri()
    {
        assertThrows(RegistrationException.class, () -> clientA.addSubscription("bad uri", STREAM_ID));
    }

    @Test
    void shouldBeAbleToQueryChannelStatusForSubscription()
    {
        final Subscription subscription = clientA.addSubscription(URI, STREAM_ID);

        while (subscription.channelStatus() == ChannelEndpointStatus.INITIALIZING)
        {
            Tests.yield();
        }

        assertThat(subscription.channelStatus(), is(ChannelEndpointStatus.ACTIVE));
        assertNull(testException.get());
    }

    @Test
    void shouldBeAbleToQueryChannelStatusForPublication()
    {
        final Publication publication = clientA.addPublication(URI, STREAM_ID);

        while (publication.channelStatus() == ChannelEndpointStatus.INITIALIZING)
        {
            Tests.yield();
        }

        assertThat(publication.channelStatus(), is(ChannelEndpointStatus.ACTIVE));
        assertNull(testException.get());
    }

    @Test
    void shouldCatchErrorOnAddressAlreadyInUseForSubscriptions()
    {
        TestMediaDriver.notSupportedOnCMediaDriver("C Driver raises error on conductor");
        final Subscription subscriptionA = clientA.addSubscription(URI, STREAM_ID);

        while (subscriptionA.channelStatus() == ChannelEndpointStatus.INITIALIZING)
        {
            Tests.yield();
        }

        assertThat(subscriptionA.channelStatus(), is(ChannelEndpointStatus.ACTIVE));

        final Subscription subscriptionB = clientB.addSubscription(URI, STREAM_ID);

        final ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(errorHandlerClientB, timeout(5000L)).onError(captor.capture());

        assertThat(captor.getValue(), instanceOf(ChannelEndpointException.class));

        final ChannelEndpointException channelEndpointException = (ChannelEndpointException)captor.getValue();
        final long status = clientB.countersReader().getCounterValue(channelEndpointException.statusIndicatorId());

        assertThat(status, is(ChannelEndpointStatus.ERRORED));
        assertThat(subscriptionB.channelStatusId(), is(channelEndpointException.statusIndicatorId()));
        assertThat(subscriptionA.channelStatus(), is(ChannelEndpointStatus.ACTIVE));
        assertNull(testException.get());
    }

    @Test
    void shouldCatchErrorOnAddressAlreadyInUseForPublications()
    {
        TestMediaDriver.notSupportedOnCMediaDriver("C Driver raises error on conductor");
        final Publication publicationA = clientA.addPublication(URI_WITH_INTERFACE_PORT, STREAM_ID);

        while (publicationA.channelStatus() == ChannelEndpointStatus.INITIALIZING)
        {
            Tests.yield();
        }

        assertThat(publicationA.channelStatus(), is(ChannelEndpointStatus.ACTIVE));

        final Publication publicationB = clientB.addPublication(URI_WITH_INTERFACE_PORT, STREAM_ID);

        final ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(errorHandlerClientB, timeout(5000L)).onError(captor.capture());

        assertThat(captor.getValue(), instanceOf(ChannelEndpointException.class));

        final ChannelEndpointException channelEndpointException = (ChannelEndpointException)captor.getValue();
        final long status = clientB.countersReader().getCounterValue(channelEndpointException.statusIndicatorId());

        assertThat(status, is(ChannelEndpointStatus.ERRORED));
        assertThat(publicationB.channelStatusId(), is(channelEndpointException.statusIndicatorId()));
        assertThat(publicationB.channelStatus(), is(ChannelEndpointStatus.ERRORED));
        assertThat(publicationA.channelStatus(), is(ChannelEndpointStatus.ACTIVE));
        assertNull(testException.get());
    }

    @Test
    void shouldNotErrorOnAddressAlreadyInUseOnActiveChannelEndpointForSubscriptions()
    {
        TestMediaDriver.notSupportedOnCMediaDriver("C Driver raises error on conductor");
        final Subscription subscriptionA = clientA.addSubscription(URI, STREAM_ID);

        while (subscriptionA.channelStatus() == ChannelEndpointStatus.INITIALIZING)
        {
            Tests.yield();
        }

        final Subscription subscriptionB = clientB.addSubscription(URI_NO_CONFLICT, STREAM_ID);
        final Subscription subscriptionC = clientC.addSubscription(URI, STREAM_ID);

        while (subscriptionB.channelStatus() == ChannelEndpointStatus.INITIALIZING ||
            subscriptionC.channelStatus() == ChannelEndpointStatus.INITIALIZING)
        {
            Tests.yield();
        }

        verify(errorHandlerClientC, timeout(5000L)).onError(any(ChannelEndpointException.class));

        assertThat(subscriptionC.channelStatus(), is(ChannelEndpointStatus.ERRORED));

        assertThat(subscriptionA.channelStatus(), is(ChannelEndpointStatus.ACTIVE));
        assertThat(subscriptionB.channelStatus(), is(ChannelEndpointStatus.ACTIVE));
        assertNull(testException.get());
    }
}
