package com.bluetooth.printer;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Base64;
import android.util.Log;
import com.facebook.react.bridge.*;
import com.facebook.react.modules.core.RCTNativeAppEventEmitter;
import org.json.JSONException;
import java.io.IOException;
import java.io.OutputStream;

import android.bluetooth.BluetoothSocket;
import java.util.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Canvas;
import android.widget.Toast;

import static android.app.Activity.RESULT_OK;
import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static com.facebook.react.bridge.UiThreadUtil.runOnUiThread;


class BlueToothPrinterModule extends ReactContextBaseJavaModule implements ActivityEventListener {

	public static final String LOG_TAG = "logs";
	private static final int ENABLE_REQUEST = 539;

	public static final String PRINT_TYPE_TEXT = "TEXT";
	public static final String PRINT_TYPE_PHOTO = "PHOTO";

    public static final byte[] RESET = {0x1b, 0x40};//复位打印机
    public static final byte[] CUT_PAPER = {0x1d,0x56,0x00};//切纸
    /**
     *   文字部分操作指令
     */
    public static final byte[] ALIGN_LEFT = {0x1b, 0x61, 0x00};//左对齐
    public static final byte[] ALIGN_CENTER = {0x1b, 0x61, 0x01};//居中
    public static final byte[] ALIGN_RIGHT = {0x1b, 0x61, 0x02};//右对齐
    public static final byte[] BOLD = {0x1b, 0x45, 0x01};//加粗
    public static final byte[] BOLD_CANCEL = {0x1b, 0x45, 0x00};//取消加粗
    /**
     *   图片部分操作指令
     */
//    public static final byte[] ALIGN_LEFT_PHOTO = {0x1d,0x4c,0x10,0x00};//左对齐 图片

	private BluetoothAdapter bluetoothAdapter;
	private Context context;
	private ReactContext reactContext;
	private Callback enableBluetoothCallback;
	private ScanManager scanManager;

	// key is the MAC Address
	public Map<String, Peripheral> peripherals = new LinkedHashMap<>();
	// scan session id


	public BlueToothPrinterModule(ReactApplicationContext reactContext) {
		super(reactContext);
		context = reactContext;
		this.reactContext = reactContext;
		reactContext.addActivityEventListener(this);
		if (Build.VERSION.SDK_INT >= LOLLIPOP) {
			scanManager = new LollipopScanManager(reactContext, this);
		} else {
			scanManager = new LegacyScanManager(reactContext, this);
		}
		Log.d(LOG_TAG, "BlueToothPrinterModule created");
	}

	@Override
	public String getName() {
		return "BlueToothPrinterModule";
	}

	private BluetoothAdapter getBluetoothAdapter() {
		if (bluetoothAdapter == null) {
			BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
			bluetoothAdapter = manager.getAdapter();
		}
		return bluetoothAdapter;
	}

	public void sendEvent(String eventName,
						   @Nullable WritableMap params) {
		getReactApplicationContext()
				.getJSModule(RCTNativeAppEventEmitter.class)
				.emit(eventName, params);
	}

	@ReactMethod
	public void start(ReadableMap options, Callback callback) {
		Log.d(LOG_TAG, "start");
		if (getBluetoothAdapter() == null) {
			Log.d(LOG_TAG, "No bluetooth support");
			callback.invoke("No bluetooth support");
			return;
		}
		IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
		context.registerReceiver(mReceiver, filter);
		callback.invoke();
		Log.d(LOG_TAG, "BlueToothPrinterModule initialized");
	}

	@ReactMethod
	public void enableBluetooth(Callback callback) {
		if (getBluetoothAdapter() == null) {
			Log.d(LOG_TAG, "No bluetooth support");
			callback.invoke("No bluetooth support");
			return;
		}
		if (!getBluetoothAdapter().isEnabled()) {
			enableBluetoothCallback = callback;
			Intent intentEnable = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			if (getCurrentActivity() == null)
				callback.invoke("Current activity not available");
			else
				getCurrentActivity().startActivityForResult(intentEnable, ENABLE_REQUEST);
		} else
			callback.invoke();
	}

	@ReactMethod
	public void scan(ReadableArray serviceUUIDs, final int scanSeconds, boolean allowDuplicates, Callback callback) {
		Log.d(LOG_TAG, "scan");
		if (getBluetoothAdapter() == null) {
			Log.d(LOG_TAG, "No bluetooth support");
			callback.invoke("No bluetooth support");
			return;
		}
		if (!getBluetoothAdapter().isEnabled())
			return;

		for (Iterator<Map.Entry<String, Peripheral>> iterator = peripherals.entrySet().iterator(); iterator.hasNext(); ) {
			Map.Entry<String, Peripheral> entry = iterator.next();
			if (!entry.getValue().isConnected()) {
				iterator.remove();
			}
		}

		scanManager.scan(serviceUUIDs, scanSeconds, callback);
	}

	@ReactMethod
	public void stopScan(Callback callback) {
		Log.d(LOG_TAG, "Stop scan");
		if (getBluetoothAdapter() == null) {
			Log.d(LOG_TAG, "No bluetooth support");
			callback.invoke("No bluetooth support");
			return;
		}
		if (!getBluetoothAdapter().isEnabled()) {
			callback.invoke("Bluetooth not enabled");
			return;
		}
		scanManager.stopScan(callback);
	}

	@ReactMethod
	public void connect(String peripheralUUID, Callback callback) {
		Log.d(LOG_TAG, "Connect to: " + peripheralUUID );

		Peripheral peripheral = peripherals.get(peripheralUUID);
		if (peripheral == null) {
			if (peripheralUUID != null) {
				peripheralUUID = peripheralUUID.toUpperCase();
			}
			if (BluetoothAdapter.checkBluetoothAddress(peripheralUUID)) {
				BluetoothDevice device = bluetoothAdapter.getRemoteDevice(peripheralUUID);
				peripheral = new Peripheral(device, reactContext);
				peripherals.put(peripheralUUID, peripheral);
			} else {
				callback.invoke("Invalid peripheral uuid");
				return;
			}
		}
		peripheral.connect(callback, getCurrentActivity());
	}

	@ReactMethod
	public void disconnect(String peripheralUUID, Callback callback) {
		Log.d(LOG_TAG, "Disconnect from: " + peripheralUUID);

		Peripheral peripheral = peripherals.get(peripheralUUID);
		if (peripheral != null){
			peripheral.disconnect();
			callback.invoke();
		} else
			callback.invoke("Peripheral not found");
	}

	@ReactMethod
	public void startNotification(String deviceUUID, String serviceUUID, String characteristicUUID, Callback callback) {
		Log.d(LOG_TAG, "startNotification");

		Peripheral peripheral = peripherals.get(deviceUUID);
		if (peripheral != null){
			peripheral.registerNotify(UUIDHelper.uuidFromString(serviceUUID), UUIDHelper.uuidFromString(characteristicUUID), callback);
		} else
			callback.invoke("Peripheral not found");
	}

	@ReactMethod
	public void stopNotification(String deviceUUID, String serviceUUID, String characteristicUUID, Callback callback) {
		Log.d(LOG_TAG, "stopNotification");

		Peripheral peripheral = peripherals.get(deviceUUID);
		if (peripheral != null){
			peripheral.removeNotify(UUIDHelper.uuidFromString(serviceUUID), UUIDHelper.uuidFromString(characteristicUUID), callback);
		} else
			callback.invoke("Peripheral not found");
	}

public static byte[] draw2PxPoint(Bitmap bmp) {
        //用来存储转换后的 bitmap 数据。为什么要再加1000，这是为了应对当图片高度无法
        //整除24时的情况。比如bitmap 分辨率为 240 * 250，占用 7500 byte，
        //但是实际上要存储11行数据，每一行需要 24 * 240 / 8 =720byte 的空间。再加上一些指令存储的开销，
        //所以多申请 1000byte 的空间是稳妥的，不然运行时会抛出数组访问越界的异常。
        int size = bmp.getWidth() * bmp.getHeight() / 8 + 1000;
        byte[] data = new byte[size];
        int k = 0;
        //设置行距为0的指令
        data[k++] = 0x1B;
        data[k++] = 0x33;
        data[k++] = 0x00;
        // 逐行打印
        for (int j = 0; j < bmp.getHeight() / 24f; j++) {
            //打印图片的指令
            data[k++] = 0x1B;
            data[k++] = 0x2A;
            data[k++] = 33;
            data[k++] = (byte) (bmp.getWidth() % 256); //nL
            data[k++] = (byte) (bmp.getWidth() / 256); //nH
            //对于每一行，逐列打印
            for (int i = 0; i < bmp.getWidth(); i++) {
                //每一列24个像素点，分为3个字节存储
                for (int m = 0; m < 3; m++) {
                    //每个字节表示8个像素点，0表示白色，1表示黑色
                    for (int n = 0; n < 8; n++) {
                        byte b = px2Byte(i, j * 24 + m * 8 + n, bmp);
                        data[k] += data[k] + b;
                    }
                    k++;
                }
            }
            data[k++] = 10;//换行
        }
        return data;
    }


/**
     * 灰度图片黑白化，黑色是1，白色是0
     *
     * @param x   横坐标
     * @param y   纵坐标
     * @param bit 位图
     * @return
     */
    public static byte px2Byte(int x, int y, Bitmap bit) {
        if (x < bit.getWidth() && y < bit.getHeight()) {
            byte b;
            int pixel = bit.getPixel(x, y);
            int red = (pixel & 0x00ff0000) >> 16; // 取高两位
            int green = (pixel & 0x0000ff00) >> 8; // 取中两位
            int blue = pixel & 0x000000ff; // 取低两位
            int gray = RGB2Gray(red, green, blue);
            if (gray < 128) {
                b = 1;
            } else {
                b = 0;
            }
            return b;
        }
        return 0;
    }

    /**
     * 图片灰度的转化
     */
    private static int RGB2Gray(int r, int g, int b) {
        int gray = (int) (0.29900 * r + 0.58700 * g + 0.11400 * b);  //灰度转化公式
        return gray;
    }

  /**
     * 对图片进行压缩（去除透明度）
     *
     * @param bitmapOrg
     */
    public static Bitmap compressPic(Bitmap bitmap, int newHeight, int newWidth) {
        // 获取这个图片的宽和高
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        // 指定调整后的宽度和高度
        Bitmap targetBmp = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888);
        Canvas targetCanvas = new Canvas(targetBmp);
        targetCanvas.drawColor(0xffffffff);
        targetCanvas.drawBitmap(bitmap, new Rect(0, 0, width, height), new Rect(0, 0, newWidth, newHeight), null);
        return targetBmp;
    }

	@ReactMethod
	@SuppressWarnings (value={"deprecation"})
	public void write(final ReadableMap options, String deviceUUID, String serviceUUID, String message, Integer maxByteSize, Callback callback) {
		Log.d(LOG_TAG, "Write to: " + deviceUUID);

		try{
			BluetoothDevice innerprinter_device = null;
			byte[] decoded = Base64.decode(message.getBytes(), Base64.DEFAULT);
			
			Set<BluetoothDevice> devices = bluetoothAdapter.getBondedDevices();
			for (BluetoothDevice device : devices) {
		      if (device.getAddress().equals(deviceUUID)) {
		        innerprinter_device = device;
		        break;
		      }
		    }
			
	        BluetoothSocket socket = innerprinter_device.createRfcommSocketToServiceRecord(UUIDHelper.uuidFromString(serviceUUID));
	        try{
	        	socket.connect();	
	        }catch(IOException e){
	        	callback.invoke("error:"+e.toString());
	        }
	        
	        try{
	        	if(options.hasKey("printType") && options.getString("printType").equals(PRINT_TYPE_PHOTO)){
	        		//打印图片
	        		if(!options.hasKey("width") || !options.hasKey("height")){
	                    throw new IllegalArgumentException("Invalid params: width or height must be large than 0");
	                }
	                int width = options.getInt("width");
	                int height = options.getInt("height");

	                OutputStream out = socket.getOutputStream();
	                out.write(this.RESET);
	                out.flush();

	                /**获取打印图片的数据**/
	                Bitmap map = compressPic(BitmapFactory.decodeByteArray(decoded, 0, decoded.length),height,width);//压缩后的图片

	                
	                if(options.hasKey("leftMargin")){
	                   byte[] margin = {0x1d,0x4c,(byte)options.getInt("leftMargin"),0x00};
	                   out.write(margin);
	                   out.flush();
	                }

	                //把图片转化为可打印的byte
	                byte[] send = draw2PxPoint(map);
	                out.write(send, 0, send.length);
	                out.flush();

	            	//换行指令
	                int nextLine = options.hasKey("nextLine")?options.getInt("nextLine"):0;
	                if(nextLine > 0){
	                	byte[] times = {0x1b,0x4a,(byte)nextLine};
	                	out.write(times);
	                    out.flush();
	                }

	                out.write(this.CUT_PAPER);
                    out.flush();
	                out.close();
	        	} else if(options.hasKey("printType") && options.getString("printType").equals(PRINT_TYPE_TEXT)){
	        		 OutputStream out = socket.getOutputStream();
	        		 out.write(this.RESET);
	        		 out.flush();
	        		 
	        		 //byte[] printWidth = {0x1d,0x57,0x01,0x01};//打印宽度
	        		 //out.write(printWidth);
	        		 //out.flush();
	        		 if(options.hasKey("alignCenter") && options.getBoolean("alignCenter")){
	                     out.write(this.ALIGN_CENTER);
	                     out.flush();
	                 }
	                 if(options.hasKey("alignLeft") && options.getBoolean("alignLeft")){
	                    out.write(this.ALIGN_LEFT);
	                    out.flush();
	                 }
	                 if(options.hasKey("alignRight") && options.getBoolean("alignRight")){
	                    out.write(this.ALIGN_RIGHT);
	                    out.flush();
	                 }
	                 if(options.hasKey("bold") && options.getBoolean("bold")){
	                 	out.write(this.BOLD);
	                 	out.flush();
	                 }
	        		//打印文字
	        		out.write(message.getBytes("UTF-8"));
	        		out.flush();
	        		
	        		//换行指令
	        		int nextLine = options.hasKey("nextLine")?options.getInt("nextLine"):0;
	                if(nextLine > 0){
	                   byte[] times = {0x1b,0x4a,(byte)nextLine};
	                   out.write(times);
	                   out.flush();
	                }

	                out.write(this.CUT_PAPER);
                    out.flush();
	                out.close();
	        	} else {
	        		throw new IllegalArgumentException("Invalid type to print.");
	        	}
	            socket.close();
	            callback.invoke("ok");
	        }catch(Exception e){
	         callback.invoke("error:"+e.toString()+", line: "+e.getStackTrace()[0].getLineNumber());
	        }
		}catch(Exception e){
			callback.invoke("error:"+e.getMessage().toString());
		}
	}

	@ReactMethod
	public void writeWithoutResponse(String deviceUUID, String serviceUUID, String characteristicUUID, String message, Integer maxByteSize, Integer queueSleepTime, Callback callback) {
		Log.d(LOG_TAG, "Write without response to: " + deviceUUID);

		Peripheral peripheral = peripherals.get(deviceUUID);
		if (peripheral != null){
			byte[] decoded = Base64.decode(message.getBytes(), Base64.DEFAULT);
			Log.d(LOG_TAG, "Message(" + decoded.length + "): " + bytesToHex(decoded));
			peripheral.write(UUIDHelper.uuidFromString(serviceUUID), UUIDHelper.uuidFromString(characteristicUUID), decoded, maxByteSize, queueSleepTime, callback, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
		} else
			callback.invoke("Peripheral not found");
	}

	@ReactMethod
	public void read(String deviceUUID, String serviceUUID, String characteristicUUID, Callback callback) {
		Log.d(LOG_TAG, "Read from: " + deviceUUID);
		Peripheral peripheral = peripherals.get(deviceUUID);
		if (peripheral != null){
			peripheral.read(UUIDHelper.uuidFromString(serviceUUID), UUIDHelper.uuidFromString(characteristicUUID), callback);
		} else
			callback.invoke("Peripheral not found", null);
	}

	@ReactMethod
	public void readRSSI(String deviceUUID,  Callback callback) {
		Log.d(LOG_TAG, "Read RSSI from: " + deviceUUID);
		Peripheral peripheral = peripherals.get(deviceUUID);
		if (peripheral != null){
			peripheral.readRSSI(callback);
		} else
			callback.invoke("Peripheral not found", null);
	}

	private BluetoothAdapter.LeScanCallback mLeScanCallback =
			new BluetoothAdapter.LeScanCallback() {


				@Override
				public void onLeScan(final BluetoothDevice device, final int rssi,
									 final byte[] scanRecord) {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							Log.i(LOG_TAG, "DiscoverPeripheral: " + device.getName());
							String address = device.getAddress();

							if (!peripherals.containsKey(address)) {

								Peripheral peripheral = new Peripheral(device, rssi, scanRecord, reactContext);
								peripherals.put(device.getAddress(), peripheral);

								try {
									Bundle bundle = BundleJSONConverter.convertToBundle(peripheral.asJSONObject());
									WritableMap map = Arguments.fromBundle(bundle);
									sendEvent("BlueToothPrinterModuleDiscoverPeripheral", map);
								} catch (JSONException ignored) {

								}

							} else {
								// this isn't necessary
								Peripheral peripheral = peripherals.get(address);
								peripheral.updateRssi(rssi);
							}
						}
					});
				}


			};

	@ReactMethod
	public void checkState(){
		Log.d(LOG_TAG, "checkState");

		BluetoothAdapter adapter = getBluetoothAdapter();
		String state = "off";
		if (adapter != null) {
			switch (adapter.getState()) {
				case BluetoothAdapter.STATE_ON:
					state = "on";
					break;
				case BluetoothAdapter.STATE_OFF:
					state = "off";
			}
		}

		WritableMap map = Arguments.createMap();
		map.putString("state", state);
		Log.d(LOG_TAG, "state:" + state);
		sendEvent("BlueToothPrinterModuleDidUpdateState", map);
	}

	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d(LOG_TAG, "onReceive");
			final String action = intent.getAction();

			String stringState = "";
			if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
				final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
						BluetoothAdapter.ERROR);
				switch (state) {
					case BluetoothAdapter.STATE_OFF:
						stringState = "off";
						break;
					case BluetoothAdapter.STATE_TURNING_OFF:
						stringState = "turning_off";
						break;
					case BluetoothAdapter.STATE_ON:
						stringState = "on";
						break;
					case BluetoothAdapter.STATE_TURNING_ON:
						stringState = "turning_on";
						break;
				}
			}

			WritableMap map = Arguments.createMap();
			map.putString("state", stringState);
			Log.d(LOG_TAG, "state: " + stringState);
			sendEvent("BlueToothPrinterModuleDidUpdateState", map);
		}
	};

	@ReactMethod
	public void getDiscoveredPeripherals(Callback callback) {
		Log.d(LOG_TAG, "Get discovered peripherals");
		WritableArray map = Arguments.createArray();
		for (Map.Entry<String, Peripheral> entry : peripherals.entrySet()) {
			Peripheral peripheral = entry.getValue();
			try {
				Bundle bundle = BundleJSONConverter.convertToBundle(peripheral.asJSONObject());
				WritableMap jsonBundle = Arguments.fromBundle(bundle);
				map.pushMap(jsonBundle);
			} catch (JSONException ignored) {
				callback.invoke("Peripheral json conversion error", null);
			}
		}
		callback.invoke(null, map);
	}

	@ReactMethod
	public void getConnectedPeripherals(ReadableArray serviceUUIDs, Callback callback) {
		Log.d(LOG_TAG, "Get connected peripherals");
		WritableArray map = Arguments.createArray();
		for (Map.Entry<String, Peripheral> entry : peripherals.entrySet()) {
			Peripheral peripheral = entry.getValue();
			Boolean accept = false;

			if (serviceUUIDs != null && serviceUUIDs.size() > 0) {
				for (int i = 0; i < serviceUUIDs.size(); i++) {
					accept = peripheral.hasService(UUIDHelper.uuidFromString(serviceUUIDs.getString(i)));
				}
			} else {
				accept = true;
			}

			if (peripheral.isConnected() && accept) {
				try {
					Bundle bundle = BundleJSONConverter.convertToBundle(peripheral.asJSONObject());
					WritableMap jsonBundle = Arguments.fromBundle(bundle);
					map.pushMap(jsonBundle);
				} catch (JSONException ignored) {
					callback.invoke("Peripheral json conversion error", null);
				}
			}
		}
		callback.invoke(null, map);
	}


	private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

	public static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	@Override
	public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
		Log.d(LOG_TAG, "onActivityResult");
		if (requestCode == ENABLE_REQUEST && enableBluetoothCallback != null) {
			if (resultCode == RESULT_OK) {
				enableBluetoothCallback.invoke();
			} else {
				enableBluetoothCallback.invoke("User refused to enable");
			}
			enableBluetoothCallback = null;
		}
	}

	@Override
	public void onNewIntent(Intent intent) {

	}

}
