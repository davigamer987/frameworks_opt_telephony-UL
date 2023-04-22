/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony.satellite;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.os.AsyncResult;
import android.os.Looper;
import android.os.Message;
import android.telephony.satellite.SatelliteDatagram;
import android.telephony.satellite.SatelliteManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.satellite.metrics.ControllerMetricsStats;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.LinkedBlockingQueue;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DatagramDispatcherTest extends TelephonyTest {
    private static final String TAG = "DatagramDispatcherTest";
    private static final int SUB_ID = 0;
    private static final int DATAGRAM_TYPE1 = SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE;
    private static final int DATAGRAM_TYPE2 = SatelliteManager.DATAGRAM_TYPE_LOCATION_SHARING;
    private static final String TEST_MESSAGE = "This is a test datagram message";

    private DatagramDispatcher mDatagramDispatcherUT;

    @Mock private DatagramController mMockDatagramController;
    @Mock private SatelliteModemInterface mMockSatelliteModemInterface;
    @Mock private ControllerMetricsStats mMockControllerMetricsStats;

    /** Variables required to send datagram in the unit tests. */
    LinkedBlockingQueue<Integer> mResultListener;
    SatelliteDatagram mDatagram;
    InOrder mInOrder;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        MockitoAnnotations.initMocks(this);
        logd(TAG + " Setup!");

        replaceInstance(DatagramController.class, "sInstance", null,
                mMockDatagramController);
        replaceInstance(SatelliteModemInterface.class, "sInstance", null,
                mMockSatelliteModemInterface);
        replaceInstance(ControllerMetricsStats.class, "sInstance", null,
                mMockControllerMetricsStats);

        mDatagramDispatcherUT = DatagramDispatcher.make(mContext, Looper.myLooper(),
                mMockDatagramController);

        mResultListener = new LinkedBlockingQueue<>(1);
        mDatagram = new SatelliteDatagram(TEST_MESSAGE.getBytes());
        mInOrder = inOrder(mMockDatagramController);
    }

    @After
    public void tearDown() throws Exception {
        logd(TAG + " tearDown");
        mDatagramDispatcherUT.destroy();
        mDatagramDispatcherUT = null;
        mResultListener = null;
        mDatagram = null;
        mInOrder = null;
        super.tearDown();
    }

    @Test
    public void testSendSatelliteDatagram_usingSatelliteModemInterface_success() throws  Exception {
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[3];

            mDatagramDispatcherUT.obtainMessage(2 /*EVENT_SEND_SATELLITE_DATAGRAM_DONE*/,
                    new AsyncResult(message.obj, null, null))
                    .sendToTarget();
            return null;
        }).when(mMockSatelliteModemInterface).sendSatelliteDatagram(any(SatelliteDatagram.class),
                anyBoolean(), anyBoolean(), any(Message.class));

        mDatagramDispatcherUT.sendSatelliteDatagram(SUB_ID, DATAGRAM_TYPE1, mDatagram,
                true, mResultListener::offer);

        processAllMessages();

        mInOrder.verify(mMockDatagramController)
                .updateSendStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING), eq(1),
                        eq(SatelliteManager.SATELLITE_ERROR_NONE));
        mInOrder.verify(mMockDatagramController)
                .updateSendStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS), eq(0),
                        eq(SatelliteManager.SATELLITE_ERROR_NONE));
        mInOrder.verify(mMockDatagramController)
                .updateSendStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE), eq(0),
                        eq(SatelliteManager.SATELLITE_ERROR_NONE));
        verifyNoMoreInteractions(mMockDatagramController);

        assertThat(mResultListener.peek()).isEqualTo(SatelliteManager.SATELLITE_ERROR_NONE);
    }

    @Test
    public void testSendSatelliteDatagram_usingSatelliteModemInterface_failure() throws  Exception {
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[3];

            mDatagramDispatcherUT.obtainMessage(2 /*EVENT_SEND_SATELLITE_DATAGRAM_DONE*/,
                            new AsyncResult(message.obj, null,
                                    new SatelliteManager.SatelliteException(
                                            SatelliteManager.SATELLITE_SERVICE_ERROR)))
                    .sendToTarget();
            return null;
        }).when(mMockSatelliteModemInterface).sendSatelliteDatagram(any(SatelliteDatagram.class),
                anyBoolean(), anyBoolean(), any(Message.class));

        mDatagramDispatcherUT.sendSatelliteDatagram(SUB_ID, DATAGRAM_TYPE2, mDatagram,
                true, mResultListener::offer);

        processAllMessages();

        mInOrder.verify(mMockDatagramController)
                .updateSendStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING), eq(1),
                        eq(SatelliteManager.SATELLITE_ERROR_NONE));
        mInOrder.verify(mMockDatagramController)
                .updateSendStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED), eq(0),
                        eq(SatelliteManager.SATELLITE_SERVICE_ERROR));
        mInOrder.verify(mMockDatagramController)
                .updateSendStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE), eq(0),
                        eq(SatelliteManager.SATELLITE_ERROR_NONE));
        verifyNoMoreInteractions(mMockDatagramController);

        assertThat(mResultListener.peek()).isEqualTo(SatelliteManager.SATELLITE_SERVICE_ERROR);
    }

    @Test
    public void testSendSatelliteDatagram_usingCommandsInterface_phoneNull() throws Exception {
        doReturn(false).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        replaceInstance(PhoneFactory.class, "sPhones", null, new Phone[] {null});

        mDatagramDispatcherUT.sendSatelliteDatagram(SUB_ID, DATAGRAM_TYPE1, mDatagram,
                true, mResultListener::offer);

        processAllMessages();

        mInOrder.verify(mMockDatagramController)
                .updateSendStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING), eq(1),
                        eq(SatelliteManager.SATELLITE_ERROR_NONE));
        mInOrder.verify(mMockDatagramController)
                .updateSendStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED), eq(0),
                        eq(SatelliteManager.SATELLITE_INVALID_TELEPHONY_STATE));
        mInOrder.verify(mMockDatagramController)
                .updateSendStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE), eq(0),
                        eq(SatelliteManager.SATELLITE_ERROR_NONE));
        verifyNoMoreInteractions(mMockDatagramController);

        assertThat(mResultListener.peek())
                .isEqualTo(SatelliteManager.SATELLITE_INVALID_TELEPHONY_STATE);
    }

    @Test
    public void testSendSatelliteDatagram_usingCommandsInterface_success() throws  Exception {
        doReturn(false).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        replaceInstance(PhoneFactory.class, "sPhones", null, new Phone[] {mPhone});
        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[0];

            mDatagramDispatcherUT.obtainMessage(2 /*EVENT_SEND_SATELLITE_DATAGRAM_DONE*/,
                            new AsyncResult(message.obj, null, null))
                    .sendToTarget();
            return null;
        }).when(mPhone).sendSatelliteDatagram(any(Message.class), any(SatelliteDatagram.class),
                anyBoolean());

        mDatagramDispatcherUT.sendSatelliteDatagram(SUB_ID, DATAGRAM_TYPE2, mDatagram,
                true, mResultListener::offer);

        processAllMessages();

        mInOrder.verify(mMockDatagramController)
                .updateSendStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING), eq(1),
                        eq(SatelliteManager.SATELLITE_ERROR_NONE));
        mInOrder.verify(mMockDatagramController)
                .updateSendStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS), eq(0),
                        eq(SatelliteManager.SATELLITE_ERROR_NONE));
        mInOrder.verify(mMockDatagramController)
                .updateSendStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE), eq(0),
                        eq(SatelliteManager.SATELLITE_ERROR_NONE));
        verifyNoMoreInteractions(mMockDatagramController);

        assertThat(mResultListener.peek()).isEqualTo(SatelliteManager.SATELLITE_ERROR_NONE);
    }

    @Test
    public void testSendSatelliteDatagram_usingCommandsInterface_failure() throws  Exception {
        doReturn(false).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        replaceInstance(PhoneFactory.class, "sPhones", null, new Phone[] {mPhone});
        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[0];

            mDatagramDispatcherUT.obtainMessage(2 /*EVENT_SEND_SATELLITE_DATAGRAM_DONE*/,
                            new AsyncResult(message.obj, null,
                                    new SatelliteManager.SatelliteException(
                                            SatelliteManager.SATELLITE_SERVICE_ERROR)))
                    .sendToTarget();
            return null;
        }).when(mPhone).sendSatelliteDatagram(any(Message.class), any(SatelliteDatagram.class),
                anyBoolean());

        mDatagramDispatcherUT.sendSatelliteDatagram(SUB_ID, DATAGRAM_TYPE1, mDatagram,
                true, mResultListener::offer);

        processAllMessages();

        mInOrder.verify(mMockDatagramController)
                .updateSendStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING), eq(1),
                        eq(SatelliteManager.SATELLITE_ERROR_NONE));
        mInOrder.verify(mMockDatagramController)
                .updateSendStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED), eq(0),
                        eq(SatelliteManager.SATELLITE_SERVICE_ERROR));
        mInOrder.verify(mMockDatagramController)
                .updateSendStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE), eq(0),
                        eq(SatelliteManager.SATELLITE_ERROR_NONE));
        verifyNoMoreInteractions(mMockDatagramController);

        assertThat(mResultListener.peek()).isEqualTo(SatelliteManager.SATELLITE_SERVICE_ERROR);
    }
}