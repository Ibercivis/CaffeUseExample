package com.ibercivis.caffeuseexample;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import org.apache.http.Header;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/*
 * MainActivity
 * -------------------------
 *
 * Author: Eduardo Lostal
 *
 */
public class MainActivity extends AppCompatActivity {

    // UI elements
    TextView classification_received_text;
    Button request_classification_button;
    ImageView imgView;

    // Server Connection
    RequestParams params = new RequestParams();
    Uri imageUri;
    private static final int RESULT_LOAD_IMG = 1;
    private static final int TAKE_NEW_IMG = 2;
    private static final int NUM_RESULTS = 5;
    AlertDialog alertDialog;

    // Image Capture and Management
    File myFile;
    String imgPath, fileName;
    private static final String JPEG_FILE_PREFIX = "IMG_";
    private static final String JPEG_FILE_SUFFIX = ".jpg";
    private AlbumStorageDirFactory mAlbumStorageDirFactory = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get the reference for the UI elements that are modified somehow in runtime
        classification_received_text = (TextView)findViewById(R.id.classificationReceivedText_id);
        request_classification_button = (Button) findViewById(R.id.request_classification_button_id);
        imgView = (ImageView) findViewById(R.id.imageClassifiedView_id);
        // Disable the button to make the http request until there is a image ready to be sent
        request_classification_button.setEnabled(false);

        // Create a dialog to be displayed while the request is done to the server and get the
        // results back
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);

        // Set dialog title and message
        alertDialogBuilder.setTitle("Image Classification");
        alertDialogBuilder.setMessage("Contacting the server and uploading the image, it may take" +
                " some time. Please, wait.")
                .setCancelable(false);

        // Create alert dialog to be ready whenever must be displayed
        alertDialog = alertDialogBuilder.create();

        // Check the current version for the SDK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
            mAlbumStorageDirFactory = new FroyoAlbumDirFactory();
        } else {
            mAlbumStorageDirFactory = new BaseAlbumDirFactory();
        }

        // Check that the camera is available in order to enable (or not) the button to take new
        // images with the camera
        Button take_image_button = (Button) findViewById(R.id.take_image_button_id);
        take_image_button.setEnabled(false);
        if (isIntentAvailable(this, MediaStore.ACTION_IMAGE_CAPTURE)) {
            take_image_button.setEnabled(true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Start the intent to choose a stored image in the device
     *
     * @param view Current view object, not null
     *
     */
    public void loadImagefromGallery(View view) {
        // Create intent to Open Image applications like Gallery, Google Photos
        Intent galleryIntent = new Intent(Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

        // Start the Intent
        startActivityForResult(galleryIntent, RESULT_LOAD_IMG);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        // Initialize the uri for the chosen or taken image to null
        Uri selectedImageUri = null;

        // Select the origin of the uri, if a stored image or a new one
        switch (requestCode) {
            case RESULT_LOAD_IMG:
                // An already existing image
                if (resultCode == Activity.RESULT_OK) {
                    selectedImageUri = data.getData();
                }
                break;
            case TAKE_NEW_IMG:
                // A new image
                if (resultCode == RESULT_OK) {
                    // Use imageUri here to access the image with the uri saved at intent launch
                    selectedImageUri = imageUri;
                } else {
                    Toast.makeText(this, "Picture was not taken", Toast.LENGTH_SHORT).show();
                }
                break;
        }

        if(selectedImageUri != null){
            // With the proper uri, prepare the params for the http request and set some UI stuff
            try {
                // MEDIA GALLERY
                String[] filePathColumn = {MediaStore.Images.Media.DATA};

                // Get the cursor
                Cursor cursor = getContentResolver().query(selectedImageUri,
                        filePathColumn, null, null, null);

                // Move to first row
                cursor.moveToFirst();

                // Get the correct path of the file
                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                imgPath = cursor.getString(columnIndex);
                cursor.close();

                // Set the selected image in the image view
                imgView.setImageBitmap(BitmapFactory
                        .decodeFile(imgPath));

                // Get the Image's file name
                String fileNameSegments[] = imgPath.split("/");
                fileName = fileNameSegments[fileNameSegments.length - 1];

                // Put file name in Async Http Post Param
                params.put("filename", fileName);

                // Initialize the text of the results to be displayed as empty
                classification_received_text.setText("");

                // Since an image is already selected and, thus, ready, enable the button to make
                // the request to the server
                request_classification_button.setEnabled(true);

            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), "Internal error",
                        Toast.LENGTH_LONG).show();
                Log.e(e.getClass().getName(), e.getMessage(), e);
            }
        }
    }

    /**
     * Get the directory for the album
     *
     * @return a directory for an individual photo album
     *
     */
    private File getAlbumDir() {
        File storageDir = null;

        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            // Create a directory for an individual photo album if necessary
            storageDir = mAlbumStorageDirFactory.getAlbumStorageDir(getString(R.string.album_name));

            if (storageDir != null) {
                if (! storageDir.mkdirs()) {
                    if (! storageDir.exists()){
                        Log.d("CameraSample", "failed to create directory");
                        return null;
                    }
                }
            }

        } else {
            Log.v(getString(R.string.app_name), "External storage is not mounted READ/WRITE.");
        }

        return storageDir;
    }

    /**
     * Create the image file
     *
     * @return the image file
     *
     */
    private File createImageFile() throws IOException {

        // Create a unique name file
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = JPEG_FILE_PREFIX + timeStamp + "_";

        // Get the album directory
        File albumF = getAlbumDir();

        // Create and return the image file
        return File.createTempFile(imageFileName, JPEG_FILE_SUFFIX, albumF);
    }

    /**
     * Create the image file and set up its path
     *
     * @return the image file
     *
     */
    private File setUpPhotoFile() throws IOException {

        File f = createImageFile();
        imgPath = f.getAbsolutePath();

        return f;
    }

    /**
     * Do the set up and start the intent to take a new image
     *
     * @param view Current view object, not null
     *
     */
    public void takeNewImage(View view) {

        // Create the intent to take a new picture
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        File f;

        try {
            // Do the set up and get the uri
            f = setUpPhotoFile();
            imgPath = f.getAbsolutePath();
            imageUri = Uri.fromFile(f);

            // Prepare the contents to be stored in the uri and the intent
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.TITLE, f.getName());
            values.put(MediaStore.Images.Media.DESCRIPTION,"Image captured by camera");

            imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        } catch (IOException e) {
            e.printStackTrace();
            imgPath = null;
        }

        // Start the intent
        startActivityForResult(takePictureIntent, TAKE_NEW_IMG);
    }

    /**
     * Method called when button for classifying the image is pressed. Do the set up in the app
     * (text view, dialog, etc) and make the request
     *
     * @param view Current view object, not null
     *
     */
    public void uploadImage(View view) {

        // Show alert dialog to let the user know that request is being processed
        alertDialog.show();

        // Set the result text view to empty
        classification_received_text.setText("");

        // Check that the image path is not empty
        if (imgPath != null && !imgPath.isEmpty()) {
            // Convert image to String using Base64
            encodeImageToString();
        } else {
            Toast.makeText(
                    getApplicationContext(),
                    "You must select an image or take a new one before you try to upload it",
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * AsyncTask - To convert Image to String
     *
     * Encode the image converting it to string using Base64
     *
     */
    public void encodeImageToString() {

        new AsyncTask<Void, Void, String>() {

            protected void onPreExecute() {
            }

            @Override
            protected String doInBackground(Void... params) {

                myFile = new File(imgPath);
                return "";
            }

            @Override
            protected void onPostExecute(String msg) {

                // Put converted Image string into Async Http Post param
                try {
                    params.put("imagefile", myFile);
                } catch(FileNotFoundException e) {}

                // Trigger Image upload
                makeHTTPCall();
            }
        }.execute(null, null, null);
    }

    /**
     * Make Http call to upload Image to the server
     *
     */
    public void makeHTTPCall() {

        // Create the client
        AsyncHttpClient client = new AsyncHttpClient();

        // Set the time out
        client.setTimeout(40*1000);

        // Make the request
        client.post("http://alfa.ibercivis.es:5000/api/v1.0/classify_upload",
                params, new AsyncHttpResponseHandler() {

                    // When the response returned by REST has Http
                    // response code '200'
                    @Override
                    public void onSuccess(int statusCode, Header[] headers, byte[] response) {

                        // Hide dialog
                        alertDialog.hide();

                        // Get the JSON object from the response
                        JSONObject myObject = null;
                        try {
                            myObject = new JSONObject(new String(response));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                        // Deserialize JSON object
                        JSONArray jArr = null;
                        JSONArray jArrInner = null;
                        JSONArray jArrInner2 = null;
                        try {
                            jArr = myObject.getJSONArray("result");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                        // Go through the array with the result (boolean), predictions, bets and
                        // time
                        for (int i = 0; i < jArr.length(); i++) {
                            if (i == 1) {
                                // Contain the array with the predictions

                                // Use Html to achieve the mix of bold and normal fonts within
                                // the same text view
                                classification_received_text.append(Html.fromHtml
                                        ("<b>Predictions</b><br>"));

                                try {
                                    jArrInner = jArr.getJSONArray(i);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }

                                for (int j = 0; j < NUM_RESULTS; j++) {
                                    try {
                                        jArrInner2 = jArrInner.getJSONArray(j);
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }

                                    try {
                                        // Add the prediction
                                        classification_received_text.append(Html.fromHtml
                                                ("<b>" + jArrInner2
                                                        .getString(0) + "</b>" + ": " + jArrInner2.getString(1)));
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }

                                    // Give format to the result
                                    if (j != NUM_RESULTS - 1)
                                        classification_received_text.append(Html.fromHtml
                                                (", "));

                                }

                                classification_received_text.append("\n");
                            }

                            if (i == 2) {
                                // Contain the array with the bets

                                // Use Html to achieve the mix of bold and normal fonts within
                                // the same text view
                                classification_received_text.append(Html.fromHtml
                                        ("<b>Bets</b><br>"));

                                try {
                                    jArrInner = jArr.getJSONArray(i);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }

                                for (int j = 0; j < NUM_RESULTS; j++) {
                                    try {
                                        jArrInner2 = jArrInner.getJSONArray(j);
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }

                                    try {
                                        // Add the bet
                                        classification_received_text.append(Html.fromHtml
                                                ("<b>" + jArrInner2
                                                        .getString(0) + "</b>" + ": " + jArrInner2.getString(1)));
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }

                                    // Give format to the result
                                    if (j != NUM_RESULTS - 1)
                                        classification_received_text.append(Html.fromHtml
                                                (", "));

                                }

                                classification_received_text.append("\n");
                            }
                        }
                    }

                    // When the response returned by REST has Http
                    // response code other than '200' such as '404',
                    // '500' or '403' etc
                    @Override
                    public void onFailure(int statusCode, Header[] headers, byte[] errorResponse, Throwable e) {
                        e.printStackTrace();

                        // Hide dialog
                        alertDialog.hide();

                        // When Http response code is '0'
                        if (statusCode == 0) {
                            Toast.makeText(getApplicationContext(),
                                    "Request Time Out",
                                    Toast.LENGTH_LONG).show();
                        }
                        // When Http response code is '404'
                        else if (statusCode == 404) {
                            Toast.makeText(getApplicationContext(),
                                    "Requested resource not found",
                                    Toast.LENGTH_LONG).show();
                        }
                        // When Http response code is '500'
                        else if (statusCode == 500) {
                            Toast.makeText(getApplicationContext(),
                                    "Something went wrong at server end",
                                    Toast.LENGTH_LONG).show();
                        }
                        // When Http response code other than 404, 500
                        else {
                            Toast.makeText(
                                    getApplicationContext(),
                                    "Error Occured \n Most Common Error: \n1. Device not connected to Internet\n2. Web App is not deployed in App server\n3. App server is not running\n HTTP Status code : "
                                            + statusCode, Toast
                                            .LENGTH_LONG)
                                    .show();
                        }
                    }
                });
    }

    /**
     * Indicates whether the specified action can be used as an intent. This
     * method queries the package manager for installed packages that can
     * respond to an intent with the specified action. If no suitable package is
     * found, this method returns false.
     * http://android-developers.blogspot.com/2009/01/can-i-use-this-intent.html
     *
     * @param context The application's environment.
     * @param action The Intent action to check for availability.
     *
     * @return True if an Intent with the specified action can be sent and
     *         responded to, false otherwise.
     */
    public static boolean isIntentAvailable(Context context, String action) {
        final PackageManager packageManager = context.getPackageManager();
        final Intent intent = new Intent(action);
        List<ResolveInfo> list =
                packageManager.queryIntentActivities(intent,
                        PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        // Dismiss the progress bar when application is closed
    }

}
