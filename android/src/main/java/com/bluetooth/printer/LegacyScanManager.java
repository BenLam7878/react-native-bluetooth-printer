package com.bluetooth.printer;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.util.Log;
import com.facebook.react.bridge.*;
import org.json.JSONException;

import static com.facebook.react.bridge.UiThreadUtil.runOnUiThread;

public class LegacyScanManager extends ScanManager {

	public LegacyScanManager(ReactApplicationContext reactContext, BlueToothPrinterModule blueToothPrinterModule) {
		super(reactContext, blueToothPrinterModule);
	}

	@Override
	public void stopScan(Callback callback) {
		// update scanSessionId to prevent stopping next scan by running timeout thread
		scanSessionId.incrementAndGet();

		getBluetoothAdapter().stopLeScan(mLeScanCallback);
		callback.invoke();
	}

	private BluetoothAdapter.LeScanCallback mLeScanCallback =
			new BluetoothAdapter.LeScanCallback() {


				@Override
				public void onLeScan(final BluetoothDevice device, final int rssi,
									 final byte[] scanRecord) {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							Log.i(blueToothPrinterModule.LOG_TAG, "DiscoverPeripheral: " + device.getName());
							String address = device.getAddress();

							if (!blueToothPrinterModule.peripherals.containsKey(address)) {

								Peripheral peripheral = new Peripheral(device, rssi, scanRecord, reactContext);
								blueToothPrinterModule.peripherals.put(device.getAddress(), peripheral);

								try {
									Bundle bundle = BundleJSONConverter.convertToBundle(peripheral.asJSONObject());
									WritableMap map = Arguments.fromBundle(bundle);
									blueToothPrinterModule.sendEvent("BlueToothPrinterModuleDiscoverPeripheral", map);
								} catch (JSONException ignored) {

								}

							} else {
								// this isn't necessary
								Peripheral peripheral = blueToothPrinterModule.peripherals.get(address);
								peripheral.updateRssi(rssi);
							}
						}
					});
				}


			};

	@Override
	public void scan(ReadableArray serviceUUIDs, final int scanSeconds, Callback callback) {
		getBluetoothAdapter().startLeScan(mLeScanCallback);
		if (scanSeconds > 0) {
			Thread thread = new Thread() {
				private int currentScanSession = scanSessionId.incrementAndGet();

				@Override
				public void run() {

					try {
						Thread.sleep(scanSeconds * 1000);
					} catch (InterruptedException ignored) {
					}

					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							BluetoothAdapter btAdapter = getBluetoothAdapter();
							// check current scan session was not stopped
							if (scanSessionId.intValue() == currentScanSession) {
								if (btAdapter.getState() == BluetoothAdapter.STATE_ON) {
									btAdapter.stopLeScan(mLeScanCallback);
								}
								WritableMap map = Arguments.createMap();
								blueToothPrinterModule.sendEvent("BlueToothPrinterModuleStopScan", map);
							}
						}
					});

				}

			};
			thread.start();
		}
		callback.invoke();
	}
}
