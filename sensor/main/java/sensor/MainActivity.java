package sensor;

import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import com.example.sensor.R;

import java.util.ArrayList;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    /* Sensor */
    private SensorManager mSensorManager;
    private Sensor mAccSensor;
    private Sensor mGyroSensor;
    private Sensor mMagSensor;

    /* TextView */
    private TextView mBluetoothTv;
    private TextView mTowardsXTv;
    private TextView mTowardsYTv;
    //private TextView mMoveXTv;

    /* Sensor data */
    private final float[] mAccValues = new float[3];
    private final float[] mMagValues = new float[3];
    private final float[] mRMatrix = new float[9];
    private final float[] mPhoneAngleValues = new float[3];
    /* Phone status */
    //private boolean pForward = false;
    //private boolean pHorizontal = false;

    private float mLastAccX = Float.MAX_VALUE;

    /* Bluetooth */
    private BluetoothAdapter mBluetoothAdapter;
    private final String TARGET_DEVICE_NAME = "MSI";
    //private final String GAME_UUID = "4e67630f-f88e-4016-8203-822a23442311";
    private final String TAG = "BluetoothTest";
    private BroadcastReceiver mBroadcastReceiver;
    private BluetoothDevice mDevice;
    private BluetoothService mService;

    private Long timestamp = Long.MIN_VALUE;

    /* Test */
    private boolean record = false;
    private final ArrayList<Boolean> isH = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // init UI
        mBluetoothTv = (TextView) findViewById(R.id.bluetooth_status) ;

        mTowardsXTv = (TextView) findViewById(R.id.towards_x);
        mTowardsYTv = (TextView) findViewById(R.id.towards_y);
        //mMoveXTv = (TextView) findViewById(R.id.move_x);

        // init btn
        // init Sensors
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mAccSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGyroSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mMagSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mSensorManager.registerListener(this, mAccSensor, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, mGyroSensor, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, mMagSensor, SensorManager.SENSOR_DELAY_GAME);

        // init Bluetooth
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            // private static final String TAG = "main";
            startActivityForResult(enableBtIntent, 1);
        }

        /* Button */
        Button mRefresh = (Button) findViewById(R.id.refresh);
        mRefresh.setOnClickListener(v -> {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                // private static final String TAG = "main";
                startActivityForResult(enableBtIntent, 1);
            }
            mDevice = getPairedDevices();
        });

        Button mBuild = (Button) findViewById(R.id.build);
        mBuild.setOnClickListener(v -> {
            //init bluetooth service
            if (mDevice == null) {
                mDevice = getPairedDevices();
            }
            if (mDevice != null) {
                mService = new BluetoothService(MainActivity.this);
                mService.startClient(mDevice);
            }
        });

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    String name = device.getName();
                    if (name != null) {
                        Log.d(TAG, "find device: " + name);
                        mBluetoothTv.setText("find device" + name);
                    }
                    if (name != null && name.equals(TARGET_DEVICE_NAME)) {
                        Log.d(TAG, "found target, start connect");
                        mBluetoothTv.setText("found target");
                    }
                    mBluetoothAdapter.cancelDiscovery();
                }
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();
        mDevice = getPairedDevices();
        if (mDevice == null) {
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            registerReceiver(mBroadcastReceiver, filter);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSensorManager.unregisterListener(this, mAccSensor);
        mSensorManager.unregisterListener(this, mGyroSensor);
        mSensorManager.unregisterListener(this, mMagSensor);
        unregisterReceiver(mBroadcastReceiver);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        /* 使用加速度與地磁計算姿態角度 */
        SensorManager.getRotationMatrix(mRMatrix, null, mAccValues, mMagValues);
        SensorManager.getOrientation(mRMatrix, mPhoneAngleValues);
        double azimuth = Math.toDegrees(mPhoneAngleValues[0]);
        double pitch = Math.toDegrees(mPhoneAngleValues[1]);
        double roll = Math.toDegrees(mPhoneAngleValues[2]);

        /* 判斷手機朝前 */
        //pForward = pitch >= -45.0 && pitch < 45.0;

        if (record) {
            /* Game */
            Long BLOCK_TIME = 300000000L;
            if (sensorEvent.timestamp < timestamp + BLOCK_TIME) {
                isH.add((roll >= 45.0 && roll < 135.0) || (roll <= -45.0 && roll > -135.0));
            }
            else {
                int count = 0;
                Log.d(TAG, "size = " + isH.size());
                for (int i = 0; i < isH.size(); i++) {
                    if (isH.get(i)) {
                        count++;
                    }
                }
                Log.d(TAG, String.valueOf(count));
                if (count < isH.size() / 2) {
                    mService.write("RIGHT");
                }
                else {
                    mService.write("DOWN");
                }
                isH.clear();
                record = false;
            }
        }

        switch (sensorEvent.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                /* X 軸加速度差大於 20 時視為動作 */
                float accX = sensorEvent.values[0];
                if (!record && mLastAccX != Float.MAX_VALUE && (mLastAccX - accX) > 15.0f) {
                    record = true;
                    timestamp = sensorEvent.timestamp;
                }
                mLastAccX = accX;
                System.arraycopy(sensorEvent.values, 0, mAccValues, 0, mAccValues.length);
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                System.arraycopy(sensorEvent.values, 0, mMagValues, 0, mMagValues.length);
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    private BluetoothDevice getPairedDevices() {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices != null && pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                Log.d(TAG, device.getName() + " : " + device.getAddress());
                if (TextUtils.equals(TARGET_DEVICE_NAME, device.getName())) {
                    Log.d(TAG, "has paired target -> " + TARGET_DEVICE_NAME);
                    Log.d(TAG, device.toString());
                    mBluetoothTv.setText(device.getName());
                    return device;
                }
            }
        }
        else {
            mBluetoothTv.setText("藍芽狀態: 未連接");
        }
        return null;
    }
}