package sensor;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class BluetoothService {
    private final String TAG = "BluetoothService";
    private final String GAME_UUID = "4e67630f-f88e-4016-8203-822a23442311";
    private Context mContext;

    private BluetoothSocket mmSocket;
    private BluetoothDevice mmDevice;

    private final BluetoothAdapter bluetoothAdapter;

    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private ProgressDialog mProgressDialog;

    public BluetoothService(Context context) {
        this.mContext = context;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public void startClient(BluetoothDevice device) {
        Log.d(TAG, "start client");
        mProgressDialog = ProgressDialog.show(mContext,"Connecting Bluetooth"
                ,"Please Wait...",true);
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
    }

    private class ConnectThread extends Thread {

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            mmDevice = device;
        }
        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            bluetoothAdapter.cancelDiscovery();
            boolean run = true;
            BluetoothSocket tmp = null;
            while (run) {
                run = false;
                try {
                    // Get a BluetoothSocket to connect with the given BluetoothDevice.
                    // MY_UUID is the app's UUID string, also used in the server code.
                    tmp = mmDevice.createRfcommSocketToServiceRecord(UUID.fromString(GAME_UUID));
                } catch (IOException e) {
                    Log.e(TAG, "Socket's create() method failed", e);
                }
                mmSocket = tmp;
                try {
                    // Connect to the remote device through the socket. This call blocks
                    // until it succeeds or throws an exception.
                    mmSocket.connect();
                } catch (IOException connectException) {
                    run = true;
                    // Unable to connect; close the socket and return.
                    try {
                        mmSocket.close();
                    } catch (IOException closeException) {
                        Log.e(TAG, "Could not close the client socket", closeException);
                    }
                }
                try {
                    sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            connect(mmSocket, mmDevice);
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }
    }

    private void connect(BluetoothSocket mmSocket, BluetoothDevice mmDevice) {
        Log.d(TAG, "connected, create connected thread");
        mConnectedThread = new ConnectedThread(mmSocket);
        mConnectedThread.start();
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private OutputStream mmOutStream;
        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "ConnectedThread: Starting.");

            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            //dismiss the progressdialog when connection is established
            try{
                mProgressDialog.dismiss();
            }catch (NullPointerException e){
                e.printStackTrace();
            }


            try {
                tmpIn = mmSocket.getInputStream();
                tmpOut = mmSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream

            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                // Read from the InputStream
                try {
                    bytes = mmInStream.read(buffer);
                    String incomingMessage = new String(buffer, 0, bytes);
                    Log.d(TAG, "InputStream: " + incomingMessage);
                } catch (IOException e) {
                    Log.e(TAG, "write: Error reading Input Stream. " + e.getMessage() );
                    break;
                }
            }
        }
        //Call this from the main activity to send data to the remote device
        public void write(byte[] bytes) {
            String text = new String(bytes, Charset.defaultCharset());
            Log.d(TAG, "write: Writing to output stream: " + text);
            //BufferedOutputStream bos = new BufferedOutputStream(mmOutStream);
            try {
                mmOutStream.write(bytes);
                //mmOutStream.flush();
                //mmOutStream.close();
                //bos.write(bytes);
                //bos.flush();
            } catch (IOException e) {
                Log.e(TAG, "write: Error writing to output stream. " + e.getMessage() );
            }
        }
    }
    public void write(String str) {
        mConnectedThread.write(str.getBytes(StandardCharsets.UTF_8));
        Log.d(TAG, "send data |" + str + "|");
    }
}
