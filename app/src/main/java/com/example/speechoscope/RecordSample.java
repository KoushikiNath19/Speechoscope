package com.example.speechoscope;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class RecordSample extends Activity {
    private static final int RECORDER_BPP = 16;
    private static final String AUDIO_RECORDER_FILE_EXT_WAV = ".wav";
    private static final String AUDIO_RECORDER_FOLDER = "AudioRecorder";
    private static final String AUDIO_RECORDER_TEMP_FILE = "record_temp.raw";
    private static final int RECORDER_SAMPLERATE = 44100;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_STEREO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    public static final int RequestPermissionCode=1;

    private AudioRecord recorder = null;
    private int bufferSize = 0;
    private Thread recordingThread = null;
    private boolean isRecording = false;

    private StorageReference mstorage;
    private ProgressDialog progressDialog;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_sample);

        setButtonHandlers();
        enableButtons(false);
        Button bsubmit=(Button)findViewById(R.id.button2);
        bsubmit.setEnabled(false);
        Button bplay=(Button)findViewById(R.id.button);
        bplay.setEnabled(false);

        mstorage= FirebaseStorage.getInstance().getReference();
        bufferSize = AudioRecord.getMinBufferSize(8000,
                AudioFormat.CHANNEL_CONFIGURATION_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        progressDialog=new ProgressDialog(this);
    }

    private void setButtonHandlers() {
        ((Button)findViewById(R.id.btnStart)).setOnClickListener(btnClick);
        ((Button)findViewById(R.id.btnStop)).setOnClickListener(btnClick);
        ((Button)findViewById(R.id.button)).setOnClickListener(btnClick);
        ((Button)findViewById(R.id.button2)).setOnClickListener(btnClick);

    }

    private void enableButton(int id,boolean isEnable){
        ((Button)findViewById(id)).setEnabled(isEnable);
    }

    private void enableButtons(boolean isRecording) {
        enableButton(R.id.btnStart,!isRecording);
        enableButton(R.id.btnStop,isRecording);
    }
    String storefile=null;
    String folder=null;
    private String getFilename(){
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath,AUDIO_RECORDER_FOLDER);

        if(!file.exists()){
            file.mkdirs();
        }
        folder=file.getAbsolutePath();
        storefile=file.getAbsolutePath() + "/" + System.currentTimeMillis() + AUDIO_RECORDER_FILE_EXT_WAV;
        return storefile;
    }

    private String getTempFilename(){
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath,AUDIO_RECORDER_FOLDER);

        if(!file.exists()){
            file.mkdirs();
        }

        File tempFile = new File(filepath,AUDIO_RECORDER_TEMP_FILE);

        if(tempFile.exists()){
            tempFile.delete();
        }
        return (file.getAbsolutePath() + "/" + AUDIO_RECORDER_TEMP_FILE);
    }

    private void startRecording(){


        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLERATE, RECORDER_CHANNELS,RECORDER_AUDIO_ENCODING, bufferSize);

        int i = recorder.getState();
        if(i==1)
            recorder.startRecording();

        isRecording = true;

        recordingThread = new Thread(new Runnable() {

            @Override
            public void run() {
                writeAudioDataToFile();
            }
        },"AudioRecorder Thread");

        recordingThread.start();
    }

    private void writeAudioDataToFile(){
        byte data[] = new byte[bufferSize];
        String filename = getTempFilename();
        FileOutputStream os = null;

        try {
            os = new FileOutputStream(filename);
        } catch (FileNotFoundException e) {
// TODO Auto-generated catch block
            e.printStackTrace();
        }

        int read = 0;

        if(null != os){
            while(isRecording){
                read = recorder.read(data, 0, bufferSize);

                if(AudioRecord.ERROR_INVALID_OPERATION != read){
                    try {
                        os.write(data);
                    }catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            try {
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void stopRecording(){



        if(null != recorder){
            isRecording = false;

            int i = recorder.getState();
            if(i==1)
                recorder.stop();
            recorder.release();

            recorder = null;
            recordingThread = null;
        }
        copyWaveFile(getTempFilename(),getFilename());
        deleteTempFile();
    }

    private void deleteTempFile() {
        File file = new File(getTempFilename());

        file.delete();
    }

    private void copyWaveFile(String inFilename,String outFilename){
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        long totalDataLen = totalAudioLen + 36;
        long longSampleRate = RECORDER_SAMPLERATE;
        int channels = 2;
        long byteRate = RECORDER_BPP * RECORDER_SAMPLERATE * channels/8;

        byte[] data = new byte[bufferSize];

        try {
            in = new FileInputStream(inFilename);
            out = new FileOutputStream(outFilename);
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;

            AppLog.logString("File size: " + totalDataLen);

            WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
                    longSampleRate, channels, byteRate);

            while(in.read(data) != -1){
                out.write(data);
            }

            in.close();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void WriteWaveFileHeader(
            FileOutputStream out, long totalAudioLen,
            long totalDataLen, long longSampleRate, int channels,
            long byteRate) throws IOException {

        byte[] header = new byte[44];

        header[0] = 'R'; // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f'; // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16; // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1; // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * 16 / 8); // block align
        header[33] = 0;
        header[34] = RECORDER_BPP; // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        out.write(header, 0, 44);
    }

    private View.OnClickListener btnClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btnStart: {
                    AppLog.logString("Start Recording");
                    Toast.makeText(getApplicationContext(),"RECORDING....", Toast.LENGTH_SHORT);
                    Button bstart=(Button)findViewById(R.id.btnStart);
                    bstart.setEnabled(false);
                    Button bstop=(Button)findViewById(R.id.btnStop);
                    bstop.setEnabled(true);
                    Button bsubmit=(Button)findViewById(R.id.button2);
                    bsubmit.setEnabled(false);
                    Button bplay=(Button)findViewById(R.id.button);
                    bplay.setEnabled(false);
                    // enableButtons(true);
                    if(checkPermission())
                    {
                        startRecording();
                    }
                    else
                    {
                        requestPermission();
                    }

                    break;
                }
                case R.id.btnStop: {
                    AppLog.logString("Start Recording");
                    Button bstart=(Button)findViewById(R.id.btnStart);
                    bstart.setEnabled(false);
                    Button bstop=(Button)findViewById(R.id.btnStop);
                    bstop.setEnabled(false);
                    Button bsubmit=(Button)findViewById(R.id.button2);
                    bsubmit.setEnabled(true);
                    Button bplay=(Button)findViewById(R.id.button);
                    bplay.setEnabled(true);

                    // enableButtons(false);
                    stopRecording();


                    break;
                }
                case R.id.button: {
                    AppLog.logString("Start Recording");
                    playonclick();
                    break;
                }
                case R.id.button2: {
                    AppLog.logString("Start Recording");
                    onsubmit();
                    break;
                }
                default:Toast.makeText(getApplicationContext(),"DEFAULT......", Toast.LENGTH_SHORT);
            }
        }
    };

    public void playonclick()
    {
        Toast.makeText(getApplicationContext(),"PLAYING....", Toast.LENGTH_SHORT);
        MediaPlayer mediaPlayer=new MediaPlayer();
        Button bstart=(Button)findViewById(R.id.btnStart);
        bstart.setEnabled(false);
        Button bstop=(Button)findViewById(R.id.btnStop);
        bstop.setEnabled(false);
        Button bsubmit=(Button)findViewById(R.id.button2);
        bsubmit.setEnabled(true);
        Button bplay=(Button)findViewById(R.id.button);
        bplay.setEnabled(false);

        try
        {
            mediaPlayer.setDataSource(storefile);
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.start();
                }
            });
            mediaPlayer.prepare();
        }catch(Exception e){}

    }
    public void onsubmit()
    {
        Toast.makeText(getApplicationContext(),"ON SUBMIT....", Toast.LENGTH_SHORT);
        String Unique=sendData();
        uploadAudio(Unique);
        File file=new File(storefile);
        file.delete();
        File folder1=new File(folder);
        folder1.delete();
        Button bstart=(Button)findViewById(R.id.btnStart);
        bstart.setEnabled(true);
        Button bstop=(Button)findViewById(R.id.btnStop);
        bstop.setEnabled(false);
        Button bsubmit=(Button)findViewById(R.id.button2);
        bsubmit.setEnabled(false);
        Button bplay=(Button)findViewById(R.id.button);
        bplay.setEnabled(false);
    }
    int flag=0;
    private void uploadAudio(String dbfolder) {
        progressDialog.setMessage("Uploading.....");
        progressDialog.show();
        StorageReference filepath = mstorage.child(dbfolder).child("audio.wav");
        Uri uri=Uri.fromFile(new File(storefile));
        filepath.putFile(uri).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            //int flag=0;
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                flag=1;
                progressDialog.dismiss();
            }
        });
        if(flag==0)
        {
            Toast.makeText(getApplicationContext(),"OPERATION FAILED", Toast.LENGTH_SHORT);

        }


    }
    private String sendData()
    {
        String result=null;
        Bundle extra=getIntent().getExtras();
        if(extra!=null)
        {
            FirebaseFirestore db=FirebaseFirestore.getInstance();
            CollectionReference uss=db.collection("users");
            String name=extra.getString("name");
            String age=extra.getString("age");
            String email=extra.getString("email");
            String password=extra.getString("password");
            final Map<String, Object> user = new HashMap<>();
            user.put("name", name);
            user.put("age", age);
            user.put("email", email);
            user.put("password", password);
            Toast.makeText(RecordSample.this, "New Account Created!!", Toast.LENGTH_SHORT).show();
            DocumentReference id = db.collection("users").document();
            result=name+"_"+id.getId();
            user.put("docname", id.getId());
            id.set(user).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(RecordSample.this, "FAILURE....", Toast.LENGTH_SHORT).show();
                }
            });
        }
        return result;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(RecordSample.this, new
                String[]{WRITE_EXTERNAL_STORAGE, RECORD_AUDIO}, RequestPermissionCode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case RequestPermissionCode:
                if (grantResults.length> 0) {
                    boolean StoragePermission = grantResults[0] ==
                            PackageManager.PERMISSION_GRANTED;
                    boolean RecordPermission = grantResults[1] ==
                            PackageManager.PERMISSION_GRANTED;

                    if (StoragePermission && RecordPermission) {
                        Toast.makeText(RecordSample.this, "Permission Granted",
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(RecordSample.this,"Permission Denied",Toast.LENGTH_LONG).show();
                    }
                }
                break;
        }
    }

    public boolean checkPermission() {
        int result = ContextCompat.checkSelfPermission(getApplicationContext(),
                WRITE_EXTERNAL_STORAGE);
        int result1 = ContextCompat.checkSelfPermission(getApplicationContext(),
                RECORD_AUDIO);
        return result == PackageManager.PERMISSION_GRANTED &&
                result1 == PackageManager.PERMISSION_GRANTED;
    }
}