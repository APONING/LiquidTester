package com.example.liquidtester;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Time;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class MainActivity extends AppCompatActivity implements SensorEventListener, View.OnClickListener{

    private static final String TAG = "res:";
    public String filePath;
    private AudioTracker mAudioTracker;
    private AudioRecorder mAudioRecorder;

    private TextView mText;
    private TextView mText2;
    private SensorManager mSensorManager;

    public int startFlag = 0;
    public int n = 0;
    public int n2 = 0;
    public StringBuffer databuffer = new StringBuffer();
    public StringBuffer databuffer2 = new StringBuffer();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // App主要涉及到的5个功能按钮
        // select file
        findViewById(R.id.selectFile).setOnClickListener(this);
        // play file
        findViewById(R.id.playFile).setOnClickListener(this);
        // upload file
        findViewById(R.id.uploadFile).setOnClickListener(this);
        // record
        findViewById(R.id.record).setOnClickListener(this);
        // stop record
        findViewById(R.id.stopRecord).setOnClickListener(this);

        mText = findViewById(R.id.sensorDataText1);
        mText2 = findViewById(R.id.sensorDataText2);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor mAccSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor mGyrSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mSensorManager.registerListener(this, mAccSensor, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this, mGyrSensor, SensorManager.SENSOR_DELAY_UI);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAudioTracker.release();
        mSensorManager.unregisterListener(this);
    }

    @SuppressLint("HandlerLeak")
    @Override
    public void onClick(View v){
        switch (v.getId()) {
            // 调用手机自带的文件管理器进行选择文件
            case R.id.selectFile: {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, 1);
            }
            break;

            // 播放文件
            case R.id.playFile:{
                mAudioTracker = new AudioTracker(this);
                mAudioTracker.createAudioTrack(filePath);
                mAudioTracker.start();
            }
            break;

            // 上传文件，具体看函数uploadFile
            case R.id.uploadFile: {
                uploadFile(filePath);
//                Thread uploadFileThread = new Thread(new Runnable() {
//                    @Override
//                    public void run() {
//                        uploadFile(filePath);
//                    }
//                });
//                uploadFileThread.start();
            }
            break;

            // 记录录音数据
            case R.id.record: {
                String recordFileName = getRecordFileName();
                Log.i(TAG, "recordFileName:" + recordFileName);
                mAudioRecorder = new AudioRecorder(this);
                mAudioRecorder.createDefaultAudio(recordFileName);
                mAudioRecorder.setRecordStreamListener(new AudioRecorder.RecordStreamListener() {

                    @Override
                    public void onRecording(byte[] bytes, int offset, int length) {
                        Log.i(TAG, String.format("onRecording: offset:{}, length:{}", offset, length));
                    }

                    @Override
                    public void finishRecord() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "录音完成", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
                mAudioRecorder.startRecord();
                Toast.makeText(this, "录音开始", Toast.LENGTH_SHORT).show();

            }
            break;

            // 停止录音
            case R.id.stopRecord:{
                mAudioRecorder.stopRecord();
            }
            default:
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 1) {
                Uri uri = data.getData();
                filePath = getFilePathForN(this, uri);
                Log.i(TAG, filePath);

                TextView tv = (TextView) findViewById(R.id.editTextTextPersonName1);
                tv.setText(filePath);
            }
        }

    }

    private static String getFilePathForN(Context context, Uri uri) {
        try {
            Cursor returnCursor = context.getContentResolver().query(uri, null, null, null, null);
            int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            returnCursor.moveToFirst();
            String name = (returnCursor.getString(nameIndex));
            File file = new File(context.getExternalFilesDir(null), name);
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            FileOutputStream outputStream = new FileOutputStream(file);
            int read = 0;
            int maxBufferSize = 1 * 1024 * 1024;
            int bytesAvailable = inputStream.available();
            int bufferSize = Math.min(bytesAvailable, maxBufferSize);
            final byte[] buffers = new byte[bufferSize];
            while ((read = inputStream.read(buffers)) != -1) {
                outputStream.write(buffers, 0, read);
            }
            returnCursor.close();
            inputStream.close();
            outputStream.close();
            return file.getPath();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void uploadFile(String uploadFilePath) {

        String end = "/r/n";
        String Hyphens = "--";
        String boundary = "*****";
        String fileName = uploadFilePath.substring(uploadFilePath.lastIndexOf(File.separator) + 1);
        // 上传服务器的地址
        String actionUrl = "http://101.200.61.68:10086/file";

        StringBuilder sb = new StringBuilder(actionUrl);
        sb.append("?filename=" + fileName);
        String newURL = sb.toString();

        try {
            URL url = new URL(newURL);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();

            /* 允许Input、Output，不使用Cache */
            con.setDoInput(true);
            con.setDoOutput(true);
            con.setUseCaches(false);

            /* 设定传送的method=POST */
            con.setRequestMethod("POST");

            /* setRequestProperty */
            con.setRequestProperty("Connection", "Keep-Alive");
            con.setRequestProperty("Charset", "UTF-8");
            con.setRequestProperty("Content-Type",
                    "multipart/form-data;boundary=" + boundary);

            /* 设定DataOutputStream */
            DataOutputStream ds = new DataOutputStream(con.getOutputStream());
            Log.i("3333", "setRequest");
            ds.writeBytes(Hyphens + boundary + end);
            ds.writeBytes("Content-Disposition: form-data; name=\"uploadfile\"; filename=\""
                        + fileName + "\"" + end);
            ds.writeBytes(end);



            /* 取得文件的FileInputStream */
            FileInputStream fStream = new FileInputStream(uploadFilePath);

            /* 设定每次写入1024bytes */
            int bufferSize = 1024;
            byte[] buffer = new byte[bufferSize];
            int length = -1;

            /* 从文件读取数据到缓冲区 */
            while ((length = fStream.read(buffer)) != -1)
            {
                /* 将数据写入DataOutputStream中 */
                ds.write(buffer, 0, length);
            }

            ds.writeBytes(end);
            ds.writeBytes(Hyphens + boundary + Hyphens + end);
            fStream.close();
            ds.flush();
            ds.close();


            /* 取得Response内容 */
            InputStream is = con.getInputStream();
            int ch;
            StringBuffer b = new StringBuffer();
            while ((ch = is.read()) != -1) {
                b.append((char) ch);
            }

            JSONObject retJSON = new JSONObject(b.toString());

            // 弹出提示框
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setMessage((String) retJSON.get("data"));
            builder.setPositiveButton("OK", null);
            builder.show();

        } catch (Exception e) {
            Toast.makeText(MainActivity.this, "上传失败" + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    // 将录音文件以录音时间命名
    public String getRecordFileName(){
        String fileName = "/storage/emulated/0/testwav/milk.wav";
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss");
        String timeNow = dtf.format(LocalDateTime.now());
        fileName = fileName.replace(".wav", "_" + timeNow + ".pcm");
        return fileName;
    }

    public float magnitude(float x, float y, float z){
        float res = 0;
        res = (float) Math.sqrt(x * x + y * y + z * z);
        return res;
    }

    // 记录传感器线性加速度计和陀螺仪的值
    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor;
        float acc_x = 0;
        float gyr_mag = 0;


        if (sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            float[] values = event.values;
            acc_x = values[0];
            StringBuffer buffer = new StringBuffer();
            buffer.append("ACC-X: ").append(values[0]).append("\n");
            buffer.append("ACC-Y: ").append(values[1]).append("\n");
            buffer.append("ACC-Z: ").append(values[2]).append("\n");
            buffer.append("ACC-NORM: ").append(magnitude(values[0], values[1], values[2])).append("\n");
            mText.setText(buffer);

            databuffer.append(values[0]).append("\n");
            n += 1;
            if (n == 120){
                Log.i("ACC-data", databuffer.toString());
            }

            if ((acc_x > 10 && acc_x < 20) && startFlag == 0){
                startFlag = 1;
            }
        }
        else if (sensor.getType() == Sensor.TYPE_GYROSCOPE){
            float[] values = event.values;
            gyr_mag = values[2];
            StringBuffer buffer = new StringBuffer();
            buffer.append("GYR-X: ").append(values[0]).append("\n");
            buffer.append("GYR-Y: ").append(values[1]).append("\n");
            buffer.append("GYR-Z: ").append(values[2]).append("\n");
            buffer.append("GYR-NORM: ").append(magnitude(values[0], values[1], values[2])).append("\n");
            mText2.setText(buffer);

            databuffer2.append(values[2]).append("\n");
            n2 += 1;
            if (n2 == 120){
                Log.i("GYR-data", databuffer2.toString());
            }

            if ((gyr_mag > 4 && gyr_mag < 8)&& startFlag == 1){
                startFlag = 2;
            }
        }

        if (startFlag == 2){
            Vibrator vibrator = (Vibrator) this.getSystemService(this.VIBRATOR_SERVICE);
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
            startFlag = 0;
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
