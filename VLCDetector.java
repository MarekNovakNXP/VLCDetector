package org.opencv.samples.colorblobdetect;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

public class VLCDetector {
    // Lower and Upper bounds for range checking in HSV color space
    private Scalar mLowerBound = new Scalar(0);
    private Scalar mUpperBound = new Scalar(0);
    // Minimum contour area in percent for contours filtering
    private static double mMinContourArea = 0.1;
    // Color radius for range checking in HSV color space
    private Scalar mColorRadius = new Scalar(25,50,50,0);
    private Mat mSpectrum = new Mat();
    private List<MatOfPoint> mContours = new ArrayList<MatOfPoint>();

    // Cache
    Mat mGrayMat = new Mat();
    Mat mCorr = new Mat();
    int [] detected;
    Mat detectedMat;
    double mMeasuredBitPeriod;
    ArrayList<Integer> mFrames;
    Boolean mDetectionIsRunning = true;
    int mFrameTotalCntr = 0;
    int mFrameBadCntr = 0;

    public void setColorRadius(Scalar radius) {
        mColorRadius = radius;
    }

    public void setHsvColor(Scalar hsvColor) {
        double minH = (hsvColor.val[0] >= mColorRadius.val[0]) ? hsvColor.val[0]-mColorRadius.val[0] : 0;
        double maxH = (hsvColor.val[0]+mColorRadius.val[0] <= 255) ? hsvColor.val[0]+mColorRadius.val[0] : 255;

        mLowerBound.val[0] = minH;
        mUpperBound.val[0] = maxH;

        mLowerBound.val[1] = hsvColor.val[1] - mColorRadius.val[1];
        mUpperBound.val[1] = hsvColor.val[1] + mColorRadius.val[1];

        mLowerBound.val[2] = hsvColor.val[2] - mColorRadius.val[2];
        mUpperBound.val[2] = hsvColor.val[2] + mColorRadius.val[2];

        mLowerBound.val[3] = 0;
        mUpperBound.val[3] = 255;

        Mat spectrumHsv = new Mat(1, (int)(maxH-minH), CvType.CV_8UC3);

        for (int j = 0; j < maxH-minH; j++) {
            byte[] tmp = {(byte)(minH+j), (byte)255, (byte)255};
            spectrumHsv.put(0, j, tmp);
        }

        Imgproc.cvtColor(spectrumHsv, mSpectrum, Imgproc.COLOR_HSV2RGB_FULL, 4);
    }

    public Mat getSpectrum() {
        return mSpectrum;
    }

    public void setMinContourArea(double area) {
        mMinContourArea = area;
    }

    public void process(Mat rgbaImage) {

        if(!mDetectionIsRunning)
        {
            return;
        }

        int signalLength = (int)rgbaImage.size().height;
        Mat grayVect = new Mat(1, signalLength, 4);
        //transform to grayscale
        Imgproc.cvtColor(rgbaImage, mGrayMat, Imgproc.COLOR_RGB2GRAY);

        Mat transposedGrayMat = mGrayMat.t();

        //sum to get 1D signal
        Core.reduce(transposedGrayMat, grayVect, 0, Core.REDUCE_SUM, 4);


        int bitPeriod = 14;
        int preambleLength = 5;
        int dataLength = 16;
        double samplingLevel = 0.80;
        int framePeriod = 295; //810~289; //(preambleLength+dataLength)*bitPeriod
        double err = 0.03;
        int maxPeriodError = 2;



        Mat s0 = grayVect.submat(0, 1, 0, (int)(signalLength*2/13));
        Mat s1 = grayVect.submat(0, 1, (int)(signalLength*6/13), (int)(signalLength*7/13));
        Mat s2 = grayVect.submat(0, 1, (int)(signalLength*11/13), (int)(signalLength-1));

        Mat[] ROIs = {s0, s1, s2};
        int[] ROIOffsets = {0, (int)(signalLength*6/13),(int)(signalLength*11/13)};
        float[] Xs = new float[ROIs.length];
        float[] Ys = new float[ROIs.length];
        Mat A = new Mat(ROIs.length, ROIs.length, 5);
        Mat B = new Mat(ROIs.length, 1, 5);
        Mat C = new Mat(ROIs.length, 1, 5);

        //define linear equations matrices
        for(int i = 0; i < ROIs.length; i++) {
            //get points to determine quadratic equation
            Core.MinMaxLocResult res = Core.minMaxLoc(ROIs[i]);
            Xs[i] = (float)res.maxLoc.x + ROIOffsets[i]; //get index
            Ys[i] = (float)res.maxVal; //get value

            A.put(i, 0, Xs[i] * Xs[i]);
            A.put(i, 1, Xs[i]);
            A.put(i, 2, 1);

            B.put(i, 0, Ys[i]);
        }

        //get quadratic euation
        Core.solve(A, B, C);

        detected = new int[(int)signalLength];
        detectedMat = new Mat(1, signalLength, 4);

        double C0 = C.get(0,0)[0];
        double C1 = C.get(1,0)[0];
        double C2 = C.get(2,0)[0];

        //detect and store in detected vetor
        for(int i = 0; i < signalLength; i++)
        {
            double tmp = C0*i*i + C1*i+ C2;
            tmp *= samplingLevel;
            if(grayVect.get(0,i)[0] > tmp){
                detected[i] = 1;
                detectedMat.put(0, i, 1);
            }
            else
            {
                detected[i] = -1;
                detectedMat.put(0, i, -1);
            }
        }



        // sampled preamble sequence
        Mat sampledPreamble = new Mat(1, bitPeriod*preambleLength, 1);
        int j = 0;
        for(int bits = 0; bits < preambleLength; bits++) {
            if (bits % 2 == 0) {
                for (int i = 0; i < bitPeriod; i++) {
                    sampledPreamble.put(0, j, -2);
                    j++;
                }
            } else {
                for (int i = 0; i < bitPeriod; i++) {
                    sampledPreamble.put(0, j, 2);
                    j++;
                }
            }
        }

        // correlate with sampled preamble sequence
        Imgproc.filter2D(detectedMat, mCorr, -1, sampledPreamble); //detectedMat grayVect


        ArrayList<Integer> mins = new ArrayList<Integer>();
        ArrayList<Integer> maxs = new ArrayList<Integer>();
        ArrayList<Integer> maxValues = new ArrayList<Integer>();

        int prevDiff =(int)(mCorr.get(0, 0)[0] -  mCorr.get(0, 1)[0]);
        int i=1;
        while(i<signalLength-1){
            int currDiff = 0;
            int zeroCount = 0;
            while(currDiff == 0 && i<signalLength-1){
                zeroCount++;
                i++;
                currDiff = (int)(mCorr.get(0, i-1)[0] -  mCorr.get(0, i)[0]);
            }

            int signCurrDiff = Integer.signum(currDiff);
            int signPrevDiff = Integer.signum(prevDiff);
            if( signPrevDiff != signCurrDiff && signCurrDiff != 0){ //signSubDiff==0, the case when prev while ended bcoz of last elem
                int index = i-1-(zeroCount)/2;
                if(signPrevDiff == 1){
                    mins.add( index );
                }else{
                    maxs.add( index );
                    maxValues.add((int)mCorr.get(0,index)[0]);
                }
            }
            prevDiff = currDiff;
        }



        mMeasuredBitPeriod = bitPeriod;
        int maxFreqSameLevel = detected.length/framePeriod + 1;
        int trials =2*(detected.length/framePeriod); //maxValues.size();
        ArrayList<ArrayList<Integer>> preamblesList = new ArrayList<ArrayList<Integer>>();
        ArrayList<Integer> maxsSorted = new ArrayList<Integer>(maxValues);
        Collections.sort(maxsSorted);


        for(int skip = 0; skip < trials; skip++)
        {
            ArrayList<Integer> preambles = new ArrayList<Integer>();
            ArrayList<Integer> maxstemp = new ArrayList<Integer>(maxsSorted);

            if(maxstemp.size() < trials)
            {
                Log.e("Error", "No preamble found!");
                return;
            }


            int thismax = maxstemp.get(maxstemp.size()-skip-1);
            int thisindex = maxValues.indexOf(thismax);

            //drop last maximum
            if(thisindex == (maxValues.size()-1))
            {
                continue;
            }

            //drop first maximum
            if(thisindex == 0)
            {
                continue;
            }

            if(Collections.frequency(maxsSorted, thismax) > maxFreqSameLevel)
            {
                continue;
            }

            //this local maximum must be bigger than neighbor local maxima
            if((thismax < maxValues.get(thisindex-1)) || (thismax < maxValues.get(thisindex+1)))
            {
                continue;
            }

            //the position of surrounding maxima must be close to bit period

            if(Math.abs(Math.abs(maxs.get(thisindex) - maxs.get(thisindex-1)) - 2*bitPeriod)  > maxPeriodError)
            {
                continue;
            }

            if(Math.abs(Math.abs(maxs.get(thisindex) - maxs.get(thisindex+1)) - 2*bitPeriod)  > maxPeriodError)
            {
                continue;
            }

            double T = Math.abs(maxs.get(thisindex-1) - maxs.get(thisindex+1))/4.0;
            mMeasuredBitPeriod = (T + mMeasuredBitPeriod)/2;


            thisindex = maxs.get(thisindex);
            //drop last and first maxima, due to mutuality
            for(int idx = 1; idx < (maxs.size()-1); idx++) {

                if(Collections.frequency(maxValues, maxValues.get(idx)) > maxFreqSameLevel)
                {
                    continue;
                }

                double tmp = (1.0 * (Math.abs(thisindex - maxs.get(idx)) % framePeriod)) / framePeriod;
                if((tmp < err) || (tmp > (1-err))) {
                    preambles.add(maxs.get(idx));
                }
            }
            preamblesList.add(preambles);
        }

        ArrayList<Integer> preamblesNum = new ArrayList<Integer>();
        for(int idx = 0; idx < preamblesList.size(); idx++)
        {
            ArrayList<Integer> tmp = preamblesList.get(idx);
            preamblesNum.add(tmp.size());
        }

        int maxPreamblesNum;
        try {
            // pick the maximum number of preambles
            maxPreamblesNum = Collections.max(preamblesNum);
        }
        catch(Exception e)
        {
            Log.e("Fatal detection error", "No preambles found!");
            return;
        }

        // this should be detected with different shifts - if not, we are not locked
        if (Collections.frequency(preamblesNum, maxPreamblesNum)!= maxPreamblesNum)
        {
            Log.e("Fatal detection error", "Failed to find preambles");
            return;
        }
        else
        {
            //get SOF position of all detected frames
            int correctPreamblesIdx = preamblesNum.indexOf(maxPreamblesNum);
            // filter out uncomplete frames and get start-of-frames
            ArrayList<Integer> tmp = preamblesList.get(correctPreamblesIdx);
            ArrayList<Integer>SOFs = new ArrayList<Integer>();

            for(int p = 0; p < tmp.size(); p++) {
                if ((tmp.get(p) + framePeriod) < signalLength) {
                    // offset sampling points to middle and skip preamble
                    SOFs.add(tmp.get(p) + (int)(((preambleLength / 2.0)+0.5) * bitPeriod));
                }
            }

            if(SOFs.size() <= 1)
            {
                return;
            }

            //create sorted list of received frames
            ArrayList<Integer>frameList = new ArrayList<Integer>();
            for(int sof = 0; sof < SOFs.size(); sof++)
            {
                int frame = 0;
                for(int bits = 0; bits < dataLength; bits++) {
                    if (detected[SOFs.get(sof) + ((int)(bits * bitPeriod))] == 1)
                        frame |= (1 << bits);
                }

                frameList.add(frame);

            }

            mFrames = frameList;
        }


    }

    public List<MatOfPoint> getContours() {
        return mContours;
    }
}
