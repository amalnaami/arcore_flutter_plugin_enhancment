package com.difrancescogianmarco.arcore_flutter_plugin;



import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.CamcorderProfile;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import com.google.ar.sceneform.SceneView;
import java.io.File;
import java.io.IOException;

/**
 * Video Recorder class handles recording the contents of a SceneView. It uses MediaRecorder to
 * encode the video. The quality settings can be set explicitly or simply use the CamcorderProfile
 * class to select a predefined set of parameters.
 */
public class VideoRecorder {
    private static final String TAG = "VideoRecorder";
    private static final int DEFAULT_BITRATE = 10000000;
    private static final int DEFAULT_FRAMERATE = 30;

    // recordingVideoFlag is true when the media recorder is capturing video.
    private boolean recordingVideoFlag;

    private MediaRecorder mediaRecorder;

    private Size videoSize;
    private Context context;

    private SceneView sceneView;
    private int videoCodec;
    private File videoDirectory;
    private String videoBaseName;
    private File videoPath;
    private int bitRate = DEFAULT_BITRATE;
    private int frameRate = DEFAULT_FRAMERATE;
    private Surface encoderSurface;

    private static final int[] FALLBACK_QUALITY_LEVELS = {
            CamcorderProfile.QUALITY_HIGH,
            CamcorderProfile.QUALITY_2160P,
            CamcorderProfile.QUALITY_1080P,
            CamcorderProfile.QUALITY_720P,
            CamcorderProfile.QUALITY_480P
    };

    public VideoRecorder() {
        recordingVideoFlag = false;
    }

    public File getVideoPath() {
        return videoPath;
    }

    public void setContext(Context context){this.context = context;}

    public void setBitRate(int bitRate) {
        this.bitRate = bitRate;
    }

    public void setFrameRate(int frameRate) {
        this.frameRate = frameRate;
    }

    public void setSceneView(SceneView sceneView) {
        this.sceneView = sceneView;
    }

    /**
     * Toggles the state of video recording.
     *
     * @return true if recording is now active.
     */
    public boolean onToggleRecord() {
        if (recordingVideoFlag) {
            stopRecordingVideo();
        } else {
            startRecordingVideo();
        }
        return recordingVideoFlag;
    }

    private void startRecordingVideo() {
        if (mediaRecorder == null) {
            mediaRecorder = new MediaRecorder();
        }

        try {
            buildFilename();
            setUpMediaRecorder();
        } catch (IOException e) {
            Log.e(TAG, "Exception setting up recorder", e);
            return;
        }

        // Set up Surface for the MediaRecorder
        encoderSurface = mediaRecorder.getSurface();

        sceneView.startMirroringToSurface(
                encoderSurface, 0, 0, videoSize.getWidth(), videoSize.getHeight());

        recordingVideoFlag = true;
    }

    private void buildFilename() {
        if (videoDirectory == null) {
            videoDirectory =
                    context.getCacheDir();
/*                    new File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                                    + "/Sceneform");*/
        }
        if (videoBaseName == null || videoBaseName.isEmpty()) {
            videoBaseName = "Sample";
        }
        videoPath =
                new File(
                        videoDirectory, videoBaseName + Long.toHexString(System.currentTimeMillis()) + ".mp4");
        File dir = videoPath.getParentFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    private void stopRecordingVideo() {
        // UI
        recordingVideoFlag = false;

        if (encoderSurface != null) {
            sceneView.stopMirroringToSurface(encoderSurface);
            encoderSurface = null;
        }
        // Stop recording
        mediaRecorder.stop();
        mediaRecorder.reset();
    }

    private void setUpMediaRecorder() throws IOException {

        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

        mediaRecorder.setOutputFile(videoPath.getAbsolutePath());
        mediaRecorder.setVideoEncodingBitRate(bitRate);
        mediaRecorder.setVideoFrameRate(frameRate);
        mediaRecorder.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
        mediaRecorder.setVideoEncoder(videoCodec);
        
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        mediaRecorder.prepare();

        try {
            mediaRecorder.start();
        } catch (IllegalStateException e) {
            Log.e(TAG, "Exception starting capture: " + e.getMessage(), e);
        }
    }

    public void setVideoSize(int width, int height) {
        videoSize = new Size(width, height);
    }

    public void setVideoQuality(int quality, int orientation) {
        CamcorderProfile profile = null;
        if (CamcorderProfile.hasProfile(quality)) {
            profile = CamcorderProfile.get(quality);
        }
        if (profile == null) {
            // Select a quality  that is available on this device.
            for (int level : FALLBACK_QUALITY_LEVELS) {
                if (CamcorderProfile.hasProfile(level)) {
                    profile = CamcorderProfile.get(level);
                    break;
                }
            }
        }
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
        } else {
            setVideoSize(profile.videoFrameHeight, profile.videoFrameWidth);
        }
        setVideoCodec(profile.videoCodec);
        setBitRate(profile.videoBitRate);
        setFrameRate(profile.videoFrameRate);
    }

    public void setVideoCodec(int videoCodec) {
        this.videoCodec = videoCodec;
    }

    public boolean isRecording() {
        return recordingVideoFlag;
    }

/*    public void toggleRecording(View unusedView) {

        if (!checkPermission()) {
            Log.e(TAG, "Video recording requires the EXTERNAL_STORAGE permission");
            Toast.makeText(
                            this,
                            "Video recording requires the EXTERNAL_STORAGE permission",
                            Toast.LENGTH_LONG)
                    .show();
            requestPermission();
        }
        boolean recording = this.onToggleRecord();
        if (recording) {
            Toast.makeText(this, "Started Recording", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Recording Stopped", Toast.LENGTH_SHORT).show();
            String videoPath = this.getVideoPath().getAbsolutePath();
            Toast.makeText(this, "Video saved: " + videoPath, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Video saved: " + videoPath);
            // Send  notification of updated content.
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.TITLE, "Sceneform Video");
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.DATA, videoPath);
            getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        }
    }*/

/*    public void requestPermission(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            // Android is 11 or above
            try {
                Log.d(TAG, "requestPermission: try");
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", this.getPackageName(),null);
                intent.setData(uri);
                startActivityForResult(intent, 1);
            }
            catch (Exception e){
                Log.d(TAG, "requestPermission: Catch", e);
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                storageActivityResultLauncher.launch(intent);
            }

        }
        else{
            // Android is below 11
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE},
                    100
            );

        }
    }

    public ActivityResultLauncher<Intent> storageActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                public void onActivityResult(ActivityResult result) {
                    Log.d(TAG, "onActivityResult: ");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // Android is 11 or above
                        if (Environment.isExternalStorageManager()) {
                            //Manage External Storage Permission is granted
                            Log.d(TAG, "onActivityResult: Manage External Storage Permission is granted");
                        }
                        else {
                            //Manage External Storage Permission is denied
                            Log.d(TAG, "onActivityResult: Manage External Storage Permission is denied ");
                            //Toast.makeText(this, "Manage External Storage permission is denied", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
    );

    public boolean checkPermission(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            // Android is 11 or above
            return Environment.isExternalStorageManager();
        }
        else{
            // Android is below 11
            int write = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            //int read = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);

            return write == PackageManager.PERMISSION_GRANTED; //&& read == PackageManager.PERMISSION_GRANTED;

        }
    }



    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == 100){
            if(grantResults.length > 0){
                //check each permission if granted or not
                boolean write = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                boolean read = grantResults[1] == PackageManager.PERMISSION_GRANTED;

                if(write && read){
                    //External Storage permissions granted
                    Log.d(TAG, "onRequestPermissionsResult: External Storage permissions granted");
                }
                else{
                    //External Storage permissions denied
                    Log.d(TAG, "onRequestPermissionsResult: External Storage permissions denied");
                    Toast.makeText(this,"External Storage permissions denied", Toast.LENGTH_SHORT).show();

                }
            }
        }
    }*/


}