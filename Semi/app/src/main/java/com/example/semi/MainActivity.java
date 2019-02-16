package com.example.semi;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.location.Location;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.semi.env.BorderedText;
import com.example.semi.env.Logger;
import com.example.semi.tracking.MultiBoxTracker;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.firestore.FirebaseFirestore;
//import org.tensorflow.lite.demo.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Vector;


public class MainActivity extends AppCompatActivity implements OnImageAvailableListener {

    private static final String TAG = "MainActivity";
    public static final int CAMERA_PERMISSION_REQUEST_CODE = 1997;
    public static final int CAMERA_REQUEST_CODE = 1998;
    private static final int MY_REQUEST_GET_LOCATION = 1;

    //New Imports Start
    private Handler handler;
    private HandlerThread handlerThread;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;

    public static String timestampImage;
    private static final Logger LOGGER = new Logger();

    private static final int TF_OD_API_INPUT_SIZE = 480;
    private static final boolean TF_OD_API_IS_QUANTIZED = true;
    private static final String TF_OD_API_MODEL_FILE = "finals.tflite";
    private static final String TF_OD_API_LABELS_FILE = "labels.txt";

    private static final float TEXT_SIZE_DIP = 10;


    private BorderedText borderedText;
    private MultiBoxTracker tracker;

    private Classifier detector;

    private Integer sensorOrientation;

    private long timestamp = 0;

    private long lastProcessingTimeMs;

    private enum DetectorMode {
        TF_OD_API;
    }
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.6f;
    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    //New Imports End


    //Import from Test3

    private static Bitmap modelImage;

    //End

    //GPS

    private FusedLocationProviderClient mFusedLocationClient;

    private static Double latitude;
    private static Double longitude;

    //End


    //Firebase Database Start

    FirebaseFirestore db;
    DatabaseReference data;

    //Firebase Database End
    ImageView image_view;
    private static Bitmap scaledBitmap;
    public Button button;
    public static Uri fetchUri;
    private static Uri uri;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = FirebaseFirestore.getInstance();


        if(Build.VERSION.SDK_INT >= 23){
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_COARSE_LOCATION}, 2);
        }

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        image_view = findViewById(R.id.image_view);
        button = findViewById(R.id.button);
        button.setOnClickListener(view -> takePhoto());
    }
    public static Uri stringUri;

    //New Model Program Starts




    public void onPreviewSizeChosen(final int rotation) {
        System.out.println("In onPreviewSizeChosen");       //1
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        tracker = new MultiBoxTracker(this);
        //s-required

        int cropSize = TF_OD_API_INPUT_SIZE;


        try {
            System.out.println("Before Create");
            detector = TFLiteObjectDetectionAPIModel.create(getAssets(),          //goes to TFLiteObjectDetectionAPIModel
                    TF_OD_API_MODEL_FILE,
                    TF_OD_API_LABELS_FILE,
                    TF_OD_API_INPUT_SIZE,     //300
                    TF_OD_API_IS_QUANTIZED);

        } catch (final IOException e) {
            LOGGER.e("Exception initializing classifier!", e);
            Toast toast = Toast.makeText(getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }
        //fetching values from TFLiteObjectDetectionAPIModel
        //e-required


        sensorOrientation = rotation - getScreenOrientation();
        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

        try {
            modelImage = MediaStore.Images.Media.getBitmap(getApplicationContext().getContentResolver(), uri);

        } catch (IOException e) {
            e.printStackTrace();
        }

        //Send bitmap to rgbBitmap
        //Change this with Image
        croppedBitmap = Bitmap.createScaledBitmap(modelImage, 480, 480,true);

        System.out.println(croppedBitmap.getWidth());
        System.out.println(croppedBitmap.getHeight());

        processImage();

    }

    protected void processImage() {
        System.out.println("In processImage");
        ++timestamp;
        final long currTimestamp = timestamp;

        System.out.println("In run");
        LOGGER.i("Running detection on image " + currTimestamp);
        final long startTime = SystemClock.uptimeMillis();
        final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);        //CroppedBitmap is the bitmap for detection
        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;


        float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
        switch (MODE) {
            case TF_OD_API:
                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                break;
        }

        final List<Classifier.Recognition> mappedRecognitions =
                new LinkedList<Classifier.Recognition>();
        int countsam = 0;
        for (final Classifier.Recognition result : results) {
            final RectF location = result.getLocation();
            if (location != null && result.getConfidence() >= minimumConfidence) {

                result.setLocation(location);
                mappedRecognitions.add(result);
                String splitf = "/";
                String samResult = result.toString();
                String head[] = samResult.split(splitf);
                String prob[] = head[1].split("()");
                String probabilityFound = prob[2] + prob[3] + prob[4] + prob[5];
                String finalResult = head[0] + "/" + probabilityFound + "/" + timestampImage + countsam;
                saveData(finalResult);
                countsam++;

            }
        }
    }

    private void saveData(String finalResult) {

        String[] inference = finalResult.split("/");
        System.out.println(inference[0]);
        System.out.println(inference[1]);
        System.out.println(inference[2]);
        String GPS = Double.toString(latitude) +" "+ Double.toString(longitude);

        //System.out.println(finalResult);
        //System.out.println(timestamp);


        String key = inference[2];
        Map<String, Object> city = new HashMap<>();
        city.put("Class", inference[0]);
        city.put("Probability", inference[1]);
        city.put("GPS", GPS);

        db.collection("Data").document(key)
                .set(city)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "DocumentSnapshot successfully written!");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error writing document", e);
                    }
                });

    }


    protected int getScreenOrientation() {
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_90:
                return 90;
            default:
                return 0;
        }
    }






    //New Model Program Ends




    public void takePhoto(){

        fetchLocation();
        prepInvokeCamera();

    }

    private void fetchLocation() {

        if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){

            if(ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION)){

                new AlertDialog.Builder(this)
                        .setTitle("Required Location Permission")
                        .setMessage("You have to give this permission to access the feature")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                                MY_REQUEST_GET_LOCATION);
                    }
                }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .create()
                .show();

            }
            else{
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        MY_REQUEST_GET_LOCATION);
            }

        }
        else{

            mFusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            if(location != null){


                                latitude = location.getLatitude();
                                longitude = location.getLongitude();

                                String tempcoord = String.format("%.2f", latitude);
                                latitude = Double.parseDouble(tempcoord);
                                tempcoord = String.format("%.2f", longitude);
                                longitude = Double.parseDouble(tempcoord);

                                System.out.println("Lat: " + latitude + "Long: " + longitude);
                                int flag=1;
                            }

                        }
                    });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if(requestCode == CAMERA_PERMISSION_REQUEST_CODE){
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                invokeCamera();
            }
            else{
                Toast.makeText(this,getString(R.string.nocamerapermission), Toast.LENGTH_LONG).show();
            }
        }
        if(requestCode == MY_REQUEST_GET_LOCATION){
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                fetchLocation();
            }
            else{
                Toast.makeText(this,"No GPS Permission", Toast.LENGTH_LONG).show();
            }
        }

    }

    private void prepInvokeCamera() {
        //check permissions
        if(checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
            invokeCamera();
        }
        else{
            String[] permissionRequest = {Manifest.permission.CAMERA};
            requestPermissions(permissionRequest, CAMERA_PERMISSION_REQUEST_CODE);
        }
    }

    private void invokeCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File imagePath = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File picFile = new File(imagePath, getPictureName());

        uri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", picFile);

        // stringUri = uri.toString();
        InputStream pictureInputStream = null;
        try {

            pictureInputStream = getContentResolver().openInputStream(uri);
            Bitmap plantpicture = BitmapFactory.decodeStream(pictureInputStream);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        //resize the image right here before saving it
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
        cameraIntent.setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        //stringUri = picFile.getAbsolutePath();
        //System.out.println(stringUri + "String URI1");
        startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
    }

    private String getPictureName() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        Date now = new Date();
        timestampImage = sdf.format(now);
        String pictureName = "IMG" + timestampImage + ".jpg";
        return pictureName;

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(resultCode == RESULT_OK){
            if(requestCode == CAMERA_REQUEST_CODE){
                Toast.makeText(this, getString(R.string.picturesaved), Toast.LENGTH_LONG).show();



                System.out.println("Going to Inference");

                //runModelInference();
                onPreviewSizeChosen(90);
                //processImage();

            }
        }
    }



    @Override
    public void onImageAvailable(ImageReader reader) {

    }
}
