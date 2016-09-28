package com.example.pascal.texterkennung;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.googlecode.tesseract.android.TessBaseAPI;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int PHOTO_REQUEST_CODE = 1;
    private TessBaseAPI tessBaseAPI;
    private TextView textView;
    private Uri outputFileUri;
    private static final String lang = "deu";
    private String result = "empty";

    private static final String DATA_PATH = Environment.getExternalStorageDirectory().toString() + "/Texterkennung/";
    private static final String TESSDATA = "tessdata";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button captureImg = (Button) findViewById(R.id.button);
        captureImg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startCameraActivity();
            }
        });
        textView = (TextView) findViewById(R.id.textViewResult);
    }


    /**
     * Take a Picture
     *
     * @throws Exception
     */
    private void startCameraActivity(){
        try{
            String IMGS_PATH = Environment.getExternalStorageDirectory().toString() + "/Texterkennung/imgs";
            prepareDirectory(IMGS_PATH);

            String img_path = IMGS_PATH + "/ocr.jpg";

            outputFileUri = Uri.fromFile(new File(img_path));

            final Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);

            if(takePictureIntent.resolveActivity(getPackageManager()) != null){
                startActivityForResult(takePictureIntent, PHOTO_REQUEST_CODE);
            }

        }catch (Exception e){
            Log.e(TAG, e.getMessage());
        }
    }

    /**
     * Prepare directory on external storage
     *
     * @param path
     * @throws Exception
     */
    private void prepareDirectory(String path){
        File dir = new File(path);
        if(!dir.exists()){
            if(!dir.mkdirs()){
                Log.e(TAG, "ERROR: Creation of directory. " + path + " failed, check does Android Manifest have permission to write to external storage.");
            }
        } else{
            Log.i(TAG, "Created directory " + path);
        }
    }

    /**
     * exploit results
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        //Foto is ready
        if(requestCode == PHOTO_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            CropImage.activity(outputFileUri)
                    .setGuidelines(CropImageView.Guidelines.ON)
                    .start(this);
        } else if(requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            if(resultCode == RESULT_OK) {
                Uri resultUri = result.getUri();
                prepareTesseract();
                startOCR(resultUri);
            } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE){
                Exception error = result.getError();
            }
        } else {
            Toast.makeText(this, "ERROR: Image was not obtained", Toast.LENGTH_SHORT).show();
        }
    }

    private void prepareTesseract() {
        try {
            prepareDirectory(DATA_PATH + TESSDATA);
        } catch(Exception e) {
            e.printStackTrace();
        }
        copyTessDataFiles(TESSDATA);
    }

    /**
     * Copy tessdata files (located on assets/tessdata) to destination directory
     *
     * @param path
     */
    private void copyTessDataFiles(String path) {
        try {
            String fileList[] = getAssets().list(path);

            for(String fileName : fileList){

                //open file within the assets folder
                //if it ist not already there copy it to the sdcard
                String pathToDataFile = DATA_PATH + path + "/" + fileName;
                if(!(new File(pathToDataFile)).exists()){

                    InputStream in = getAssets().open(path + "/" + fileName);

                    OutputStream out = new FileOutputStream(pathToDataFile);

                    //Transfer bytes from in to out
                    byte[] buf = new byte[1024];
                    int len;

                    while((len = in.read(buf)) > 0){
                        out.write(buf, 0, len);
                    }
                    in.close();
                    out.close();

                    Log.d(TAG, "Copied " + fileName + " to tessdata");
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Unable to copy files to tessdata " + e.toString());
        }
    }

    /**
     *
     * @param imgUri
     */
    private void startOCR(Uri imgUri) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 4;
            Bitmap bitmap = BitmapFactory.decodeFile(imgUri.getPath(), options);

            result = extractText(bitmap);

            //Text ist in Result
            editText(result);   //Text in Alertfenster

        } catch(Exception e){
            Log.e(TAG, e.getMessage());
        }
    }

    private String extractText(Bitmap bitmap){
        try {
            tessBaseAPI = new TessBaseAPI();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            if (tessBaseAPI == null) {
                Log.e(TAG, "TessBaseAPI is null. TessFactory not returning tess object.");
            }
        }

        tessBaseAPI.init(DATA_PATH, lang);

        //       //EXTRA SETTINGS
        //        //For example if we only want to detect numbers
        //        tessBaseApi.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "1234567890");
        //
        //        //blackList Example
        //        tessBaseApi.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, "!@#$%^&*()_+=-qwertyuiop[]}{POIU" +
        //                "YTRWQasdASDfghFGHjklJKLl;L:'\"\\|~`xcvXCVbnmBNM,./<>?");

        Log.d(TAG, "Training file loaded");
        tessBaseAPI.setImage(bitmap);
        String extractedText = "empty result";
        try {
            extractedText = tessBaseAPI.getUTF8Text();
        } catch(Exception e) {
            Log.e(TAG, "Error in recognizing text");
        }
        tessBaseAPI.end();
        return extractedText;
    }

    private void editText (String result){
        AlertDialog.Builder alert = new AlertDialog.Builder(this);

        alert.setTitle("Text anpassen");

        //Set an EditText view to geht user input
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setLines(8);
        input.setMinLines(6);
        input.setMaxLines(10);
        input.setGravity(Gravity.TOP|Gravity.LEFT);
        input.setSingleLine(false);
        input.setMovementMethod(new ScrollingMovementMethod());
        input.setText(result);
        alert.setView(input);

        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String tmp = input.getText().toString();
                textView.setText(tmp);
            }
        });
        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //Canceled.
            }
        });
        alert.show();
    }
}
