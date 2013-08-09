package edu.uml.cs.isense.pendulum;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;

//import org.opencv.example.colorblobdetect.R;
import org.opencv.imgproc.Imgproc;

//import org.opencv.samples.colorblobdetect.ColorBlobDetectionActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

// iSENSE data upload
import org.json.JSONArray;
import edu.uml.cs.isense.comm.RestAPI;

public class ColorBlobDetectionActivity extends Activity implements OnTouchListener, CvCameraViewListener2 {
    
	private static final String  TAG              = "PendulumTracker::Activity";
	public static Context mContext;
	
    // iSENSE member variables
    // use development site
    Boolean useDevSite = true; 
	// iSENSE uploader
	RestAPI rapi;
	
	// iSENSE login
	private static String userName = "videoAnalytics";
	private static String password = "videoAnalytics";
	
	// create session name based upon first name and last initial user enters
    static String firstName = "";
    static String lastInitial = "";
    private final int ENTERNAME_REQUEST = -4;
    Boolean sessionNameEntered = false;
	
	//private static String experimentNumber = "586"; // production
	private static String experimentNumber = "598"; // dev
	private static String baseSessionUrl   = "http://isense.cs.uml.edu/newvis.php?sessions=";
	private static String baseSessionUrlDev   = "http://isensedev.cs.uml.edu/newvis.php?sessions=";
	private static String sessionUrl = "";
	private String dateString;
	
	// upload progress dialogue
	ProgressDialog dia;
	// JSON array for uploading pendulum position data,
	// accessed from ColorBlobDetectionView
	public static JSONArray mDataSet;
		
	// OpenCV
	
    private boolean              mIsColorSelected = false;
    private Mat                  mRgba;
    private Scalar               mBlobColorRgba;
    private Scalar               mBlobColorHsv;
    private ColorBlobDetector    mDetector;
    private Mat                  mSpectrum;
    private Size                 SPECTRUM_SIZE;
    private Scalar               CONTOUR_COLOR;
    private FpsMeter			 mFps;
    
    private CameraBridgeViewBase mOpenCvCameraView;

    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                    mOpenCvCameraView.setOnTouchListener(ColorBlobDetectionActivity.this);
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public ColorBlobDetectionActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        
        // set context (for starting new Intents,etc)
        mContext = this;
        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // set CBD activity content from xml layout file.
        // all Views, e.g. JavaCameraView will be inflated (e.g. created)
        setContentView(R.layout.color_blob_detection_surface_view);
        
        
        // think base class pointer: make mOpenCvCameraView become a JavaCameraView
        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.color_blob_detection_activity_surface_view);
        
        // set higher level camera parameters
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.enableFpsMeter();
        //mOpenCvCameraView.setMaxFrameSize(1280,720);
        mOpenCvCameraView.setMaxFrameSize(640, 480);
        //mOpenCvCameraView.setMaxFrameSize(320, 240);
        
        // iSENSE network connectivity stuff
        rapi = RestAPI.getInstance((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE), getApplicationContext());
        rapi.useDev(useDevSite);
        
             
    }

    @Override
    public void onPause()
    {
        super.onPause();
      //  if (mOpenCvCameraView != null)
        //    mOpenCvCameraView.disableView();
    }

    // this is called any time an activity starts interacting with the user. OpenCV library
    // reloaded anytime activity is resumed (e.g. brought to forefront)
    @Override
    public void onResume()
    {
        super.onResume();
        
        Log.i(TAG, "Trying to load OpenCV library");
        if (!OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_5, this, mLoaderCallback))
        {
        	Log.e(TAG, "Cannot connect to OpenCV Manager");
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mDetector = new ColorBlobDetector();
        mSpectrum = new Mat();
        mBlobColorRgba = new Scalar(255);
        mBlobColorHsv = new Scalar(255);
        SPECTRUM_SIZE = new Size(200, 64);
        CONTOUR_COLOR = new Scalar(255,0,0,255);
        
        mFps = new FpsMeter();
        mFps.init();
    }

    public void onCameraViewStopped() {
        mRgba.release();
    }

    public boolean onTouch(View v, MotionEvent event) {
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

    // invoked when camera frame delivered
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        
    	mRgba = inputFrame.rgba();

        if (mIsColorSelected) {
            mDetector.process(mRgba);
            List<MatOfPoint> contours = mDetector.getContours();
            Log.e(TAG, "Contours count: " + contours.size());
            Imgproc.drawContours(mRgba, contours, -1, CONTOUR_COLOR);

            Mat colorLabel = mRgba.submat(4, 68, 4, 68);
            colorLabel.setTo(mBlobColorRgba);

            Mat spectrumLabel = mRgba.submat(4, 4 + mSpectrum.rows(), 70, 70 + mSpectrum.cols());
            mSpectrum.copyTo(spectrumLabel);
        }

        mFps.measure();
        
        Core.putText(mRgba, mFps.strfps, new Point(200, 200), 
 				3/* CV_FONT_HERSHEY_COMPLEX */, 1, new Scalar(255, 0, 0, 255), 2);
        
        return mRgba;
    }

    private Scalar converScalarHsv2Rgba(Scalar hsvColor) {
        Mat pointMatRgba = new Mat();
        Mat pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4);

        return new Scalar(pointMatRgba.get(0, 0));
    }

    // ------ iSENSE upload/ActionBar/menu stuff-------
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.layout.menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
 	   
 	   switch(item.getItemId())
 	   {	   
 	   // STOP experiment and data collection and
 	   // UPLOAD data
 	   case R.id.menu_upload:
 			
 		   // login to iSENSE if not already
 		   if(rapi.isConnectedToInternet())
 		   {
 			   Log.i(TAG, "Connected to the 'net.");
 			   Boolean status;
 			   // attempt to upload data if logged in
 			   if(!rapi.isLoggedIn())
 			   {
 				   status = rapi.login(userName, password);
 				   
 				   if(!status)
 				   {
 			   		   Toast.makeText(this, "Unable to log into iSENSE. Invalid user id? Try again.",
 		    					Toast.LENGTH_LONG).show();  
 			   		   return false;
 				   }
 			   }
 			   
 			   if(rapi.isLoggedIn())
 			   {
 				   // upload data backgrounded in a thread
 				   // onActivity		   
 				   if(firstName.length() > 0 || lastInitial.length() > 0)
 				   {
// 					   if(mView.dataCollectionEnabled())
// 					   {
 						   new uploadTask().execute();
// 					   }
// 					   else
// 					   {
// 						   Toast.makeText(ColorBlobDetectionActivity.this, "You must start data collection before uploading to iSENSE!", Toast.LENGTH_LONG).show();
// 					   }
 				   }
 				   else
 				   {
 			   		   Toast.makeText(this, "You must first start data collection to create session name.",
 		    					Toast.LENGTH_LONG).show();
 			   		   
 			   		   return false;
 				   }
 					  
 			   }
 	
 		   }
 	   	   else
 	       {
 	   		   Toast.makeText(this, "You are not connected to the Intertubes. Check connectivity and try again.",
 	    					Toast.LENGTH_LONG).show();
 	   		   return false;
 	       }
 		   
 		   return true;
 		   
 	   // START experiment and data collection
 	   case R.id.menu_start:   

 		   // create session name with user first name and last initial
 		   // if we are logged in
 		   if(firstName.length() == 0 || lastInitial.length() == 0)
 		   {
 	 		    //	Boolean dontPromptMeTwice = true;
 	 			startActivityForResult(
 	     	   			new Intent(mContext, LoginActivity.class),
 	     	   			ENTERNAME_REQUEST);
 		   }
 		   
 		   
 		   AlertDialog.Builder startBuilder = new AlertDialog.Builder(this); // 'this' is an Activity - can add an ID to this like CRP
 		   // chain together various setter methods to set the dialog characteristics
 		   startBuilder.setMessage("Pull pendulum to edge of screen and hit 'OK' to start collecting data.")
 	          .setTitle("Instructions:")
 	          .setPositiveButton("OK", new DialogInterface.OnClickListener() {
               // @Override
                public void onClick(DialogInterface dialog, int id) {
             	   // grab position of target and pass it along
             	   // If this were Cancel for setNegativeButton() , just do nothin'!
         		   
         		   // clear existing data in JSON array (for upload to iSENSE)
         		   mDataSet = new JSONArray();
         		  
         		   // start data collection
 //        		   mView.startDataCollection(mDataSet);
                }
               
                
            });
 		   
 		   // get the AlertDialog from create()
 		   AlertDialog startDialog = startBuilder.create();
 		   startDialog.show(); // make me appear!
 		   		   
 		   return true;
 		   
 	   case R.id.menu_exit:
 		   // Exit app neatly
 		   this.finish();
 		   return true;
 		   
 	   case R.id.menu_instructions:   

 		   String strInstruct = "Center at-rest pendulum in center of image. Select 'Start data collection button' to start. Pull pendulum back to left or right edge of image and release when selecting 'OK'. Select 'Stop and upload to iSENSE' to stop. ";
 	
 		   AlertDialog.Builder builder = new AlertDialog.Builder(this); // 'this' is an Activity - can add an ID to this like CRP
 		   // chain together various setter methods to set the dialog characteristics
 		   builder.setMessage(strInstruct)
 	          .setTitle("Instructions:")
 	          .setPositiveButton("OK", new DialogInterface.OnClickListener() {
               // @Override
                public void onClick(DialogInterface dialog, int id) {
             	   // grab position of target and pass it along
             	   // If this were Cancel for setNegativeButton() , just do nothin'!
             	   
                }
               
                
            });
 		   
 		   // get the AlertDialog from create()
 		   AlertDialog dialog = builder.create();
 		   dialog.show(); // make me appear!
 		   
 		   return true;
 	   }
 	   return true;
    }
	private Runnable uploader = new Runnable() {
		
		//@Override
		public void run() {

			// stop data collection for upload to iSENSE
			mDataSet = new JSONArray();
		//	mDataSet = mView.stopDataCollection();
			
			// ----- HACKY TEST DATA ----
			addTestPoint(mDataSet);
			// ---- END HACKY TEST DATA ----
							
			// Create location-less session (for now)
			int sessionId = -1;
			SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy, HH:mm:ss");
		    Date dt = new Date();
		    dateString = sdf.format(dt);
		    
			String nameOfSession = firstName + " " +  lastInitial + ". - " + dateString;
			//String nameOfSession = "underpantsGnomes";
			 
			sessionId = rapi.createSession(experimentNumber, 
											nameOfSession + " (location not found)", 
											"Automated Submission Through Android App", 
											"", "", "");
			if(useDevSite)
			{
				sessionUrl = baseSessionUrlDev + sessionId; 
				Log.i(TAG, sessionUrl);
			}
			else
				sessionUrl = baseSessionUrl + sessionId;
				
			Log.i(TAG, "Putting session data...");
			rapi.putSessionData(sessionId, experimentNumber, mDataSet);
	
		}
		
	};
	
    // Task for uploading data to iSENSE
	public class uploadTask extends AsyncTask <Void, Integer, Void> {
    
	    @Override protected void onPreExecute() {
	     	
	        dia = new ProgressDialog(ColorBlobDetectionActivity.this);
	        dia.setProgressStyle(ProgressDialog.STYLE_SPINNER);
	        dia.setMessage("Please wait while your data is uploaded to iSENSE...");
	        dia.setCancelable(false);
	        dia.show();             
	    }

	    @Override protected Void doInBackground(Void... voids) {

	        uploader.run();
	        publishProgress(100);
	        return null;        
	    }

	    @Override  protected void onPostExecute(Void voids) {
	        
	    	dia.setMessage("Done");
	        dia.cancel();        
	        Toast.makeText(ColorBlobDetectionActivity.this, "Data upload successful!", Toast.LENGTH_SHORT).show();
	    }
	}	
	

	// HACKY test data
	// ---- HACKY TEST DATA ----
	void addTestPoint(JSONArray dataSet)
	{
		JSONArray dataJSON = new JSONArray();
	    Calendar c = Calendar.getInstance();
	    long currentTime =  (long) (c.getTimeInMillis() /*- 14400000*/);
	   
		/* Convert floating point to String to send data via HTML 
		/* Posn-x    */  dataJSON.put(-1);
		
		/* Posn-y    */  dataJSON.put(-1);
	
		/* Time       */ dataJSON.put(currentTime); 
										                 
		dataSet.put(dataJSON);

		Log.i(TAG, "--------------- ADDING DATA POINT ---------------");
	}
	// ---- END HACKY TEST DATA -----
}