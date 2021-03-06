package com.bluetooth.printer;


import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;
import com.facebook.react.bridge.*;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

import static com.facebook.react.bridge.UiThreadUtil.runOnUiThread;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class LollipopScanManager extends ScanManager {

	public LollipopScanManager(ReactApplicationContext reactContext, BlueToothPrinterModule blueToothPrinterModule) {
		super(reactContext, blueToothPrinterModule);
	}

	@Override
	public void stopScan(Callback callback) {
		// update scanSessionId to prevent stopping next scan by running timeout thread
		scanSessionId.incrementAndGet();

		getBluetoothAdapter().getBluetoothLeScanner().stopScan(mScanCallback);
		callback.invoke();
	}

	@Override
	public void scan(ReadableArray serviceUUIDs, final int scanSeconds, Callback callback) {
		ScanSettings settings = new ScanSettings.Builder().build();
		List<ScanFilter> filters = new ArrayList<>();

		if (serviceUUIDs.size() > 0) {
			for(int i = 0; i < serviceUUIDs.size(); i++){
				ScanFilter.Builder builder = new ScanFilter.Builder();
				builder.setServiceUuid(new ParcelUuid(UUIDHelper.uuidFromString(serviceUUIDs.getString(i))));
				filters.add(builder.build());
				Log.d(blueToothPrinterModule.LOG_TAG, "Filter service: " + serviceUUIDs.getString(i));
			}
		}

		getBluetoothAdapter().getBluetoothLeScanner().startScan(filters, settings, mScanCallback);
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
								if(btAdapter.getState() == BluetoothAdapter.STATE_ON) {
									btAdapter.getBluetoothLeScanner().stopScan(mScanCallback);
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

	private ScanCallback mScanCallback = new ScanCallback() {
		@Override
		public void onScanResult(final int callbackType, final ScanResult result) {

			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					Log.i(blueToothPrinterModule.LOG_TAG, "DiscoverPeripheral: " + result.getDevice().getName());
					String address = result.getDevice().getAddress();

					if (!blueToothPrinterModule.peripherals.containsKey(address)) {

						Peripheral peripheral = new Peripheral(result.getDevice(), result.getRssi(), result.getScanRecord().getBytes(), reactContext);
						blueToothPrinterModule.peripherals.put(address, peripheral);

						try {
							Bundle bundle = BundleJSONConverter.convertToBundle(peripheral.asJSONObject());
							WritableMap map = Arguments.fromBundle(bundle);
							blueToothPrinterModule.sendEvent("BlueToothPrinterModuleDiscoverPeripheral", map);
						} catch (JSONException ignored) {

						}

					} else {
						// this isn't necessary
						Peripheral peripheral = blueToothPrinterModule.peripherals.get(address);
						peripheral.updateRssi(result.getRssi());
					}
				}
			});
		}

		@Override
		public void onBatchScanResults(final List<ScanResult> results) {
		}

		@Override
		public void onScanFailed(final int errorCode) {
		}
	};
}
