package com.lyn.colorpickcamera;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;

/**
 * Created by @HelloDHLyn on 1/14/16.
 */
public class CameraActivity extends Activity {

    private Camera mCamera;
    private CameraPreview mPreview;
    private static FrameLayout preview;
    private static SurfaceView overlayView;
    private static SurfaceHolder holderTransparent;

    private static Boolean started = false;
    private Handler handler = new Handler();
    private static int interval = -1;
    private static long start_time = -1;
    private static int shots_number = -1;
    private static int taken_number = 0;

    private static int width;
    private static int height;

    private static TextView progressText;

    private String path;
    private File filePath;
    private String csvFile;
    private FileWriter csvWriter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/ciplab";

        filePath = new File(path);
        if (filePath.exists() == false) {
            if (filePath.mkdir() == false) {
                Log.e("ColorPickCamera", "Error making directory");
                return;
            }
        }

        csvFile = path + "/RGBImage.csv";
        try {
            csvWriter= new FileWriter(csvFile);
            csvWriter.append("timestamp,R,G,B");
            csvWriter.append('\n');
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Get parameter values
        Intent myIntent = getIntent();
        interval = myIntent.getIntExtra("Frequency", 3);
        shots_number = myIntent.getIntExtra("ShotNumber", 0);

        progressText = (TextView) findViewById(R.id.text_progress);
        overlayView = (SurfaceView)findViewById(R.id.overlay_preview);

        // Create an instance of Camera
        mCamera = getCameraInstance();

        // Create our Preview view and set it as the content of our activity.
        mPreview = new CameraPreview(this, mCamera);
        preview = (FrameLayout) findViewById(R.id.camera_preview);
        preview.addView(mPreview);

//        Box box = new Box(this);
//        addContentView(box, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));

//        holderTransparent = overlayView.getHolder();
//        holderTransparent.setFormat(PixelFormat.TRANSPARENT);
//        holderTransparent.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
//
        Camera.Parameters param = mCamera.getParameters();
        List<Camera.Size> sizeList = param.getSupportedPreviewSizes();
        width = sizeList.get(0).width;
        height = sizeList.get(0).height;
//
//        DrawFocusRect(width-50, height-50 , width+50 , height+50);

        Button button_focus = (Button) findViewById(R.id.btn_focus);
        button_focus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCamera.autoFocus(autoFocusCallback);
            }
        });

        Button button_shot = (Button) findViewById(R.id.btn_shot);
        button_shot.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                taken_number = 0;
                start_time = System.currentTimeMillis();
                start();
            }
        });
    }

    @Override
    public void onDestroy() {
        mCamera.release();
        try {
            csvWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(started) handler.removeCallbacks(runnable);
        super.onDestroy();
    }

    private void DrawFocusRect(float RectLeft, float RectTop, float RectRight, float RectBottom) {
        Canvas canvas = holderTransparent.lockCanvas();

        canvas.drawColor(0, PorterDuff.Mode.CLEAR);
        //border's properties
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.rgb(255,0,0));
        paint.setStrokeWidth(3);
        canvas.drawRect(RectLeft, RectTop, RectRight, RectBottom, paint);

        holderTransparent.unlockCanvasAndPost(canvas);
    }

    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            mCamera.takePicture(null, null, pictureCallback);

            if (started) {
                start();
            }
        }
    };

    public void stop() {
        started = false;
        handler.removeCallbacks(runnable);

        Toast toast = Toast.makeText(CameraActivity.this, "완료", Toast.LENGTH_SHORT);
        toast.show();

        finish();
    }

    public void start() {
        started = true;
        long delay = 0;
        if (taken_number > 0) {
            delay = start_time + 1000 * interval * (taken_number + 2) - System.currentTimeMillis();
        } else {
            delay = 1000 * interval;
        }

        Log.i("ColorPickCamera", Long.toString(delay));

        if (delay > 0) {
            handler.postDelayed(runnable, delay);
        } else {
            handler.post(runnable);
        }
    }

    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
            Log.e("ColorPickCamera", "카메라를 여는 중 오류가 발생했습니다: " + e.getMessage());
        }
        return c; // returns null if camera is unavailable
    }

    private Camera.AutoFocusCallback autoFocusCallback = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean success, Camera camera) {

        }
    };

    private Camera.PictureCallback pictureCallback = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            final byte[] imagedata = data;
            Thread thread = new Thread() {
                public void run() {
                    Calendar calendar = Calendar.getInstance();
                    String time = String.format("%04d%02d%02d-%02d:%02d:%02d",
                            calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1,
                            calendar.get(Calendar.DAY_OF_MONTH), calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE), calendar.get(Calendar.SECOND));
                    String fileName = time + ".jpg";

//                    File pictureFile = new File(path + "/" + fileName);
//                    if (pictureFile == null) {
//                        Log.e("ColorPickCamera", "Error creating media file, check storage permissions");
//                        return;
//                    }
//
//                    try {
//                        FileOutputStream fos = new FileOutputStream(pictureFile);
//                        fos.write(imagedata);
//                        fos.flush();
//                        fos.close();
//                    } catch (FileNotFoundException e) {
//                        Log.e("ColorPickCamera", "File not found: " + e.getMessage());
//                    } catch (IOException e) {
//                        Log.e("ColorPickCamera", "Error accessing file: " + e.getMessage());
//                    }

                    Bitmap bitmap = BitmapFactory.decodeByteArray(imagedata, 0, imagedata.length);

                    int redColors = 0;
                    int greenColors = 0;
                    int blueColors = 0;
                    int pixelCount = 0;
                    int size = 100;

                    int width = 5312;
                    int height = 2988;

                    for (int y = height / 2 - size; y < height / 2 + size; y++) {
                        for (int x = width / 2 - size; x < width / 2 + size; x++) {
                            int c = bitmap.getPixel(x, y);
                            pixelCount++;
                            redColors += Color.red(c);
                            greenColors += Color.green(c);
                            blueColors += Color.blue(c);
                        }
                    }
                    // calculate average of bitmap r,g,b values
                    float red = (float)redColors / (float)pixelCount;
                    float green = (float)greenColors / (float)pixelCount;
                    float blue = (float)blueColors / (float)pixelCount;

                    try {
                        csvWriter.append(time + "," + red + "," + green + "," + blue);
                        csvWriter.append('\n');

                        if (taken_number % 200 == 0) {
                            csvWriter.flush();
                        }

//                        pictureFile.delete();
                    } catch (FileNotFoundException e) {
                        Log.e("ColorPickCamera", "File not found: " + e.getMessage());
                    } catch (IOException e) {
                        Log.e("ColorPickCamera", "Error accessing file: " + e.getMessage());
                    }
                }
            };

            thread.start();

            taken_number++;
            progressText.setText(taken_number + " / " + shots_number);

            if (taken_number == shots_number) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                try {
                    csvWriter.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                stop();
            }
            else {
                mCamera.startPreview();
            }
        }
    };

}

class Box extends View {
    private Paint paint = new Paint();
    Box(Context context) {
        super(context);
    }

    @Override
    protected void onDraw(Canvas canvas) { // Override the onDraw() Method
        super.onDraw(canvas);

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.RED);
        paint.setStrokeWidth(3);

        //center
        int x0 = (canvas.getWidth()-400)/2;
        int y0 = canvas.getHeight()/2;
        int dx = 250;
        int dy = 250;
        //draw guide box
        canvas.drawRect(x0-dx, y0-dy, x0+dx, y0+dy, paint);
    }
}