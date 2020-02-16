/*  Copyright (C) 2016-2019 Andreas Shimokawa, Carsten Pfeiffer, Daniele
    Gobbetti, Jean-François Greffier, Koen Zandberg, Vadim Kaushan

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package nodomain.freeyourgadget.gadgetbridge.service.devices.riotwatch;

import android.net.Uri;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.devices.riotwatch.RiotWatchCoordinator;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser;
import nodomain.freeyourgadget.gadgetbridge.model.Alarm;
import nodomain.freeyourgadget.gadgetbridge.model.CalendarEventSpec;
import nodomain.freeyourgadget.gadgetbridge.model.CallSpec;
import nodomain.freeyourgadget.gadgetbridge.model.CannedMessagesSpec;
import nodomain.freeyourgadget.gadgetbridge.model.MusicSpec;
import nodomain.freeyourgadget.gadgetbridge.model.MusicStateSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec;
import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec;
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLEDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.btle.BtLEAction;
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattService;
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattCharacteristic;
import nodomain.freeyourgadget.gadgetbridge.service.btle.Transaction;
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.btle.actions.ReadAction;
import nodomain.freeyourgadget.gadgetbridge.service.btle.actions.SetDeviceStateAction;
import nodomain.freeyourgadget.gadgetbridge.service.btle.ServerTransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

public class RiotWatchSupport extends AbstractBTLEDeviceSupport {
    private static final Logger LOG = LoggerFactory.getLogger(RiotWatchSupport.class);

    public BluetoothGattCharacteristic ctrlCharacteristic = null;
    public BluetoothGattCharacteristic heartrateCharacteristic = null;
    public BluetoothGattCharacteristic batteryCharacteristic = null;

    public static final UUID RIOTWATCH_UUID = UUID.fromString("9851dc0a-b04a-1399-5646-3b38788cb1c5");

    private final GBDeviceEventVersionInfo versionCmd = new GBDeviceEventVersionInfo();
    private final GBDeviceEventBatteryInfo batteryCmd = new GBDeviceEventBatteryInfo();

//discovered supported service: Generic Access: 00001800-0000-1000-8000-00805f9b34fb
//characteristic: Device Name: 00002a00-0000-1000-8000-00805f9b34fb
//characteristic: Appearance: 00002a01-0000-1000-8000-00805f9b34fb

//discovered supported service: Generic Attribute: 00001801-0000-1000-8000-00805f9b34fb
//characteristic: Service Changed: 00002a05-0000-1000-8000-00805f9b34fb

//discovered unsupported service: Unknown Service: 9851dc0a-b04a-1399-5646-3b38788cb1c5

//discovered supported service: Heart Rate: 0000180d-0000-1000-8000-00805f9b34fb
//characteristic: Heart Rate Measurement: 00002a37-0000-1000-8000-00805f9b34fb
//characteristic: Body Sensor Location: 00002a38-0000-1000-8000-00805f9b34fb
//discovered unsupported service: Device Information: 0000180a-0000-1000-8000-00805f9b34fb

//discovered supported service: Battery Service: 0000180f-0000-1000-8000-00805f9b34fb
//characteristic: Battery Level: 00002a19-0000-1000-8000-00805f9b34fb


    public RiotWatchSupport() {
        super(LOG);
        addSupportedService(GattService.UUID_SERVICE_GENERIC_ACCESS);
        addSupportedService(GattService.UUID_SERVICE_GENERIC_ATTRIBUTE);
        addSupportedService(GattService.UUID_SERVICE_BATTERY_SERVICE);
        addSupportedService(GattService.UUID_SERVICE_CURRENT_TIME);
        addSupportedService(GattService.UUID_SERVICE_HEART_RATE);
        addSupportedService(GattService.UUID_SERVICE_DEVICE_INFORMATION);
        addSupportedService(RIOTWATCH_UUID);

        BluetoothGattService RiotWatchGATTCTSService = new BluetoothGattService(GattService.UUID_SERVICE_CURRENT_TIME,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        BluetoothGattCharacteristic bluetoothGATTCtsTimeCharacteristic = new BluetoothGattCharacteristic(GattCharacteristic.UUID_CHARACTERISTIC_CURRENT_TIME,
                BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);
        bluetoothGATTCtsTimeCharacteristic.setValue(new byte[0]);

        BluetoothGattCharacteristic bluetoothGATTCtsLocalTimeInfoCharacteristic = new BluetoothGattCharacteristic(GattCharacteristic.UUID_CHARACTERISTIC_LOCAL_TIME_INFORMATION,
                BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);
        bluetoothGATTCtsTimeCharacteristic.setValue(new byte[0]);

        BluetoothGattDescriptor bluetoothGattDescriptor = new BluetoothGattDescriptor(GattCharacteristic.UUID_CHARACTERISTIC_CLIENT_CHARACTERISTIC_CONFIG,
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        bluetoothGattDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);

        bluetoothGATTCtsTimeCharacteristic.addDescriptor(bluetoothGattDescriptor);

        RiotWatchGATTCTSService.addCharacteristic(bluetoothGATTCtsTimeCharacteristic);
        RiotWatchGATTCTSService.addCharacteristic(bluetoothGATTCtsLocalTimeInfoCharacteristic);

        LOG.info("Adding Current Time Service to the GATT server");
        addSupportedServerService(RiotWatchGATTCTSService);
    }

    @Override
    public boolean useAutoConnect() {
        return true;
    }

    @Override
    protected TransactionBuilder initializeDevice(TransactionBuilder builder) {
        LOG.info("Initializing Riot Watch");

        gbDevice.setState(GBDevice.State.INITIALIZING);
        gbDevice.sendDeviceUpdateIntent(getContext());

        batteryCharacteristic = getCharacteristic(GattCharacteristic.UUID_CHARACTERISTIC_BATTERY_LEVEL);
        heartrateCharacteristic = getCharacteristic(GattCharacteristic.UUID_CHARACTERISTIC_HEART_RATE_MEASUREMENT);

        builder.notify(heartrateCharacteristic, true);
        builder.notify(batteryCharacteristic, true);

        gbDevice.setState(GBDevice.State.INITIALIZED);
        gbDevice.sendDeviceUpdateIntent(getContext());

        LOG.info("Initialization Riot Watch Done");

        return builder;
    }

    @Override
    public boolean onCharacteristicChanged(BluetoothGatt gatt,
                                           BluetoothGattCharacteristic characteristic) {
        if (super.onCharacteristicChanged(gatt, characteristic)) {
            return true;
        }
        byte[] data = characteristic.getValue();
        if (data.length == 0) {
            return true;
        }

        UUID characteristicUUID = characteristic.getUuid();

        if (GattCharacteristic.UUID_CHARACTERISTIC_HEART_RATE_MEASUREMENT.equals(characteristicUUID)) {
            //TODO handle heart data
            LOG.info("Current heart rate: " + data[0]);
            return true;
        } else if (GattCharacteristic.UUID_CHARACTERISTIC_BATTERY_LEVEL.equals(characteristicUUID)) {
            handleBatteryInfo(characteristic.getValue(), BluetoothGatt.GATT_SUCCESS);
            return true;
        } else {
            LOG.warn("Unknown UUID notification update received: " + characteristicUUID);
            return true;
        }
    }

    @Override
    public boolean onCharacteristicRead(BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicRead(gatt, characteristic, status);

        UUID characteristicUUID = characteristic.getUuid();

        if (GattCharacteristic.UUID_CHARACTERISTIC_BATTERY_LEVEL.equals(characteristicUUID)) {
            handleBatteryInfo(characteristic.getValue(), status);
            return true;
        } else {
            LOG.warn("Unknown gatt read request" + characteristicUUID);
        }
        return true;
    }

    private void handleBatteryInfo(byte[] value, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            batteryCmd.level = value[0];
            LOG.info("New battery level measurement: " + value[0] + "%");
            handleGBDeviceEvent(batteryCmd);
        }
    }

    @Override
    public void onNotification(NotificationSpec notificationSpec) {
        LOG.error("Unable to send RIOTWatch notification");
    }

    @Override
    public void onDeleteNotification(int id) {
    }

    @Override
    public void onSetTime() {
        LOG.error("Unable to send current time");
    }

    @Override
    public void onSetAlarms(ArrayList<? extends Alarm> alarms) {

    }

    @Override
    public void onSetCannedMessages(CannedMessagesSpec cannedMessagesSpec) {

    }

    @Override
    public void onSetMusicState(MusicStateSpec stateSpec) {

    }

    @Override
    public void onSetMusicInfo(MusicSpec musicSpec) {

    }

    @Override
    public void onEnableRealtimeSteps(boolean enable) {

    }

    @Override
    public void onInstallApp(Uri uri) {

    }

    @Override
    public void onAppInfoReq() {

    }

    @Override
    public void onAppStart(UUID uuid, boolean start) {

    }

    @Override
    public void onAppDelete(UUID uuid) {

    }

    @Override
    public void onAppConfiguration(UUID appUuid, String config, Integer id) {

    }

    @Override
    public void onAppReorder(UUID[] uuids) {

    }

    public void onSetCallState(CallSpec callSpec) {

    }

    @Override
    public void onFetchRecordedData(int dataTypes) {
    }

    @Override
    public void onReset(int flags) {

    }

    @Override
    public void onHeartRateTest() {
        try {
            TransactionBuilder t = performInitialized("heartRateTest");
            BluetoothGattCharacteristic hrm = new BluetoothGattCharacteristic(GattCharacteristic.UUID_CHARACTERISTIC_HEART_RATE_MEASUREMENT, 0, 0);
            t.add(new ReadAction(hrm));
            t.queue(getQueue());
        }catch (Exception e){
            LOG.warn("Error starting heart rate measurement: "+e.getMessage());
            GB.toast(getContext(), "Error starting heart rate measurement: " + e.getLocalizedMessage(), Toast.LENGTH_LONG, GB.ERROR);
        }
    }

    @Override
    public void onEnableRealtimeHeartRateMeasurement(boolean enable) {

    }

    @Override
    public void onFindDevice(boolean start) {

    }

    @Override
    public void onSetConstantVibration(int integer) {

    }

    @Override
    public void onScreenshotReq() {

    }

    @Override
    public void onEnableHeartRateSleepSupport(boolean enable) {

    }

    @Override
    public void onSetHeartRateMeasurementInterval(int seconds) {

    }

    @Override
    public void onAddCalendarEvent(CalendarEventSpec calendarEventSpec) {

    }

    @Override
    public void onDeleteCalendarEvent(byte type, long id) {

    }

    @Override
    public void onSendConfiguration(String config) {

    }

    @Override
    public void onReadConfiguration(String config) {

    }

    @Override
    public void onTestNewFunction() {

    }

    @Override
    public void onSendWeather(WeatherSpec weatherSpec) {

    }

    private byte[] formatCurrentTime() {
        Calendar c = GregorianCalendar.getInstance();
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int dayofmonth = c.get(Calendar.DAY_OF_MONTH);
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int minute = c.get(Calendar.MINUTE);
        int second = c.get(Calendar.SECOND);
        int millis = c.get(Calendar.MILLISECOND);

        return new byte[]{
                (byte) (year % 256),
                (byte) ((year / 256) & 0xff),
                (byte) (month + 1),
                (byte) (dayofmonth),
                (byte) (hour),
                (byte) (minute),
                (byte) (second),
                (byte) ((millis * 256) / 1000),
        };
    }

    private void handleTimeRequest(BluetoothDevice device, int requestId, int offset) {
        try {
            ServerTransactionBuilder builder = performServer("sendCurrentTime");
            builder.writeServerResponse(device, requestId, 0, offset, formatCurrentTime());
            builder.queue(getQueue());
        } catch (IOException e) {
            LOG.warn("Sending current time failed: " + e.getMessage());
        }
    }

    @Override
    public boolean onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
        UUID characteristicUUID = characteristic.getUuid();
        if (characteristicUUID.equals(GattCharacteristic.UUID_CHARACTERISTIC_CURRENT_TIME)) {
            LOG.info("will send response to read request for time from device: " + device.getAddress());
            handleTimeRequest(device, requestId, offset);
            return true;
        } else if (characteristicUUID.equals(GattCharacteristic.UUID_CHARACTERISTIC_LOCAL_TIME_INFORMATION)) {
            LOG.info("will send response to read request for local time info from device: " + device.getAddress());
            return true;
        }
        LOG.warn(device.getName() + "sent unrecognized read request" + characteristic.getUuid());
        return false;

    }
}
