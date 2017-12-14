package org.opencv.samples.colorblobdetect;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.imgproc.Imgproc;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnTouchListener;
import android.view.SurfaceView;

public class VLCDetectionActivity extends Activity implements OnTouchListener, CvCameraViewListener2 {
    private static final String  TAG              = "OCVSample::Activity";

    private boolean              mIsColorSelected = false;
    private Mat                  mRgba;
    private Scalar               mBlobColorRgba;
    private Scalar               mBlobColorHsv;
    private VLCDetector mDetector;
    private Mat                  mSpectrum;
    private int                  mHeight;
    private int                  mWidth;
    private Mat                 mTextFramesImage;
    private Mat                 mTextFramesImageRotated;
    private Size                 SPECTRUM_SIZE;
    private Scalar               CONTOUR_COLOR;

    private CameraBridgeViewBase mOpenCvCameraView;

    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                    mOpenCvCameraView.setOnTouchListener(VLCDetectionActivity.this);
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public VLCDetectionActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.color_blob_detection_surface_view);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.color_blob_detection_activity_surface_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        mHeight = height;
        mWidth = width;
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mTextFramesImage = new Mat(mWidth, mHeight, CvType.CV_8UC4);
        mTextFramesImageRotated = new Mat(mHeight, mWidth, CvType.CV_8UC4);
        mDetector = new VLCDetector();
        mSpectrum = new Mat();
        mBlobColorRgba = new Scalar(255);
        mBlobColorHsv = new Scalar(255);
        SPECTRUM_SIZE = new Size(200, 64);
        CONTOUR_COLOR = new Scalar(255,0,0,255);
    }

    public void onCameraViewStopped() {
        mRgba.release();
    }

    public boolean onTouch(View v, MotionEvent event) {

        mDetector.mDetectionIsRunning =  !mDetector.mDetectionIsRunning;

        int cols = mRgba.cols();
        int rows = mRgba.rows();

        int xOffset = (mOpenCvCameraView.getWidth() - cols) / 2;
        int yOffset = (mOpenCvCameraView.getHeight() - rows) / 2;

        int x = (int)event.getX() - xOffset;
        int y = (int)event.getY() - yOffset;

        Log.i(TAG, "Touch image coordinates: (" + x + ", " + y + ")");

        if ((x < 0) || (y < 0) || (x > cols) || (y > rows)) return false;

        Rect touchedRect = new Rect();

        touchedRect.x = (x>4) ? x-4 : 0;
        touchedRect.y = (y>4) ? y-4 : 0;

        touchedRect.width = (x+4 < cols) ? x + 4 - touchedRect.x : cols - touchedRect.x;
        touchedRect.height = (y+4 < rows) ? y + 4 - touchedRect.y : rows - touchedRect.y;

        Mat touchedRegionRgba = mRgba.submat(touchedRect);

        Mat touchedRegionHsv = new Mat();
        Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv, Imgproc.COLOR_RGB2HSV_FULL);

        // Calculate average color of touched region
        mBlobColorHsv = Core.sumElems(touchedRegionHsv);
        int pointCount = touchedRect.width*touchedRect.height;
        for (int i = 0; i < mBlobColorHsv.val.length; i++)
            mBlobColorHsv.val[i] /= pointCount;

        mBlobColorRgba = converScalarHsv2Rgba(mBlobColorHsv);

        Log.i(TAG, "Touched rgba color: (" + mBlobColorRgba.val[0] + ", " + mBlobColorRgba.val[1] +
                ", " + mBlobColorRgba.val[2] + ", " + mBlobColorRgba.val[3] + ")");

        mDetector.setHsvColor(mBlobColorHsv);

        Imgproc.resize(mDetector.getSpectrum(), mSpectrum, SPECTRUM_SIZE);

        mIsColorSelected = true;

        touchedRegionRgba.release();
        touchedRegionHsv.release();

        return false; // don't need subsequent touch events
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();

        if (mIsColorSelected) {

            mDetector.process(mRgba);

            //plot the correlation
            int maxx = (int)mDetector.mCorr.size().width;
            byte[] cyanPix = new byte[4];
            cyanPix[0] = (byte)0;
            cyanPix[1] = (byte)255;
            cyanPix[2] = (byte)255;
            cyanPix[3] = (byte)255;
            Core.MinMaxLocResult res = Core.minMaxLoc(mDetector.mCorr);
            double corval;
            double q = mRgba.size().width/(res.maxVal-res.minVal);
            double offset = 1200;
            q = q/-8;

            for(int x = 0; x < maxx; x++) {
                corval = mDetector.mCorr.get(0, x)[0];
                corval = (corval*q)+offset;

                mRgba.put(x, (int)corval+1, cyanPix);
                mRgba.put(x, (int)corval+2, cyanPix);
                mRgba.put(x, (int)corval, cyanPix);
                mRgba.put(x, (int)corval+3, cyanPix);
            }

            //plot the detected signal
            byte[] redPix = new byte[4];
            redPix[0] = (byte)255;
            redPix[1] = (byte)0;
            redPix[2] = (byte)0;
            redPix[3] = (byte)255;
            q = -45;
            offset = 1300;

            for(int x = 0; x < maxx; x++) {
                corval = mDetector.detected[x];
                corval = (corval*q)+offset;

                mRgba.put(x, (int)corval, redPix);
                mRgba.put(x, (int)corval+1, redPix);
                mRgba.put(x, (int)corval+2, redPix);
                mRgba.put(x, (int)corval+3, redPix);
            }
            if(mDetector.mFrames != null) {
                mTextFramesImage = new Mat(mWidth, mHeight, CvType.CV_8UC4, Scalar.all(0));
                for (int i = 0; i < mDetector.mFrames.size(); i++) {

                    Imgproc.putText(mTextFramesImage,
                            String.format("Frame%d = 0x%04X (%d)", i + 1, mDetector.mFrames.get(i), mDetector.mFrames.get(i)),
                            new Point(30, 365+70*i),
                            Core.FONT_HERSHEY_PLAIN,
                            4.5,
                            new Scalar(0, 255, 0),
                            10);
                }
                Core.rotate(mTextFramesImage, mTextFramesImageRotated, 0);
                Core.add(mTextFramesImageRotated, mRgba, mRgba);
            }

            Core.flip(mRgba, mRgba, -1);
        }

        return mRgba;
    }

    private Scalar converScalarHsv2Rgba(Scalar hsvColor) {
        Mat pointMatRgba = new Mat();
        Mat pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4);

        return new Scalar(pointMatRgba.get(0, 0));
    }
}
