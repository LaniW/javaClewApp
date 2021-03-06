package com.example.clewapplication;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Camera;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;

import java.util.ArrayList;
import java.util.Objects;

//use git push in terminal to commit changes for now on
public class SingleUseRouteActivity extends FragmentActivity implements TextToSpeech.OnInitListener {

    private static final String TAG = SingleUseRouteActivity.class.getSimpleName();

    @SuppressLint("StaticFieldLeak")
    private static ArFragment arFragment;

    private Session session;
    private ModelRenderable modelRenderable;
    private boolean b = true;
    private boolean buttonStart = false;
    private boolean buttonStop = false;
    private boolean bPath = true;
    private boolean bVoice = false;
    private Node newCrumb = new Node();
    private final ArrayList<Node> coordinatesList = new ArrayList<>();
    private ArrayList<Node> longList = new ArrayList<>();
    private static TextToSpeech tts = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_single_use_route);

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.fragment);
        assert arFragment != null;
        arFragment.getArSceneView().getScene().addOnUpdateListener(this::onUpdateFrame);
        setupModel(); //Rendering Crumbs [SAFE DELETE]

        tts = new TextToSpeech(this, this);
    }

    @Override
    protected void onDestroy() {
        if (session != null) {
            Log.e(TAG, "Inside Session Destroyed");
            session.close();
            session = null;
        }

        super.onDestroy();
    }

    private void setupModel() {
        ModelRenderable.builder().setSource(this, R.raw.sphere2).build().thenAccept(renderable -> modelRenderable = renderable).exceptionally(throwable -> {
            Toast.makeText(SingleUseRouteActivity.this, "Model can't be loaded", Toast.LENGTH_SHORT).show();
            return null;
        });
    }

    private void onUpdateFrame(FrameTime frameTime) {

        Frame frame = arFragment.getArSceneView().getArFrame();

        if (frame == null) {
            return;
        }

        if ((frame.getCamera().getTrackingState() == TrackingState.TRACKING) && buttonStart) {
            path(bPath);
        } else if ((frame.getCamera().getTrackingState() == TrackingState.TRACKING) && buttonStop && bVoice){
            recursivePath();
            boolean ab;
            for(int i = longList.size() - 1; i > 0; i--){
                ab = directionToVoice(longList.get(i));
                if(ab){
                    i--;
                }
            }
        }
    }

    public void setTrue(View view) {
        buttonStart = true;
        buttonStop = false;
        bPath = true;
    }

    public void setFalse(View view) {
        buttonStart = false;
        buttonStop = true;
        bPath = false;
        bVoice = true;
    }

    public void path(boolean bPath) {

        Frame frame = arFragment.getArSceneView().getArFrame();

        assert frame != null;
        Pose pos = frame.getCamera().getPose().compose(Pose.makeTranslation(0, 0, 0));
        Anchor anchor = Objects.requireNonNull(arFragment.getArSceneView().getSession()).createAnchor(pos);
        AnchorNode anchorNode = new AnchorNode(anchor);
        anchorNode.setParent(arFragment.getArSceneView().getScene());

        Node crumb = new Node();
        crumb.setParent(anchorNode);

        double distanceValue = Math.sqrt((crumb.getWorldPosition().x - newCrumb.getWorldPosition().x) * (crumb.getWorldPosition().x - newCrumb.getWorldPosition().x) + (crumb.getWorldPosition().y - newCrumb.getWorldPosition().y) * (crumb.getWorldPosition().y - newCrumb.getWorldPosition().y) + (crumb.getWorldPosition().z - newCrumb.getWorldPosition().z) * (crumb.getWorldPosition().z - newCrumb.getWorldPosition().z));

        if (bPath) {
            if (b || distanceValue >= 0.2) {
                crumb.setRenderable(modelRenderable);
                newCrumb = crumb;

                coordinatesList.add(crumb);
                b = false;
            }
        }
    }

    public static float distanceToLine(Node aCrumb, Node bCrumb, Node cCrumb) {
        Vector3 point1 = aCrumb.getWorldPosition();
        Vector3 point2 = bCrumb.getWorldPosition();
        Vector3 difference = Vector3.subtract(point1, point2);
        Vector3 farPoint = cCrumb.getWorldPosition();
        Vector3 unitVector = difference.normalized();
        Vector3 a = Vector3.subtract(farPoint, point1);
        float magnitudeA = a.length();
        float aDotUnit = Vector3.dot(a, unitVector);
        return (float) (Math.sqrt((magnitudeA) * (magnitudeA) - (aDotUnit) * (aDotUnit)));
    }

    private static void rdp(ArrayList<Node> arr, int s, int e, float threshold, ArrayList<Node> substituteArr) {
        float fmax = 0;
        int index = 0;

        final Node startNode = arr.get(s);
        final Node endNode = arr.get(e - 1);
        for (int i = s + 1; i < e - 1; i++) {
            final Node inBetween = arr.get(i);
            final float d = distanceToLine(startNode, endNode, inBetween);
            if (d > fmax) {
                index = i;
                fmax = d;
            }
        }
        //If max distance is greater than threshold, recursively simplify
        if (fmax > threshold) {
            rdp(arr, s, index + 1, threshold, substituteArr);
            rdp(arr, index, e, threshold, substituteArr);
            substituteArr.remove(substituteArr.size() - 1);
        } else {
            if ((e - 1 - s) > 0) {
                substituteArr.add(arr.get(s));
                substituteArr.add(arr.get(e - 1));
            } else {
                substituteArr.add(arr.get(s));
            }
        }
    }

    public void recursivePath(){
        ArrayList<Node> lineWaypoints = new ArrayList<>();

        //simplifies the paths (only creates points in line segments, not actual waypoints)
        rdp(coordinatesList, 0, coordinatesList.size(), 0.5f, lineWaypoints);

        //the notable points (points distinguished from the line segments (waypoints))
        ArrayList<Node> pathWaypoints = new ArrayList<>();
        for (Node n4 : coordinatesList) {
            if (!lineWaypoints.contains(n4)) {
                pathWaypoints.add(n4);
            }
        }

        longList = pathWaypoints;

        //SAFE DELETE (sets all nodes to a single parent node but also renders it)
        /*
        Frame frame2 = arFragment.getArSceneView().getArFrame();
        assert frame2 != null;
        Pose pos = frame2.getCamera().getPose().compose(Pose.makeTranslation(0, 0, 0));
        Anchor anchor2 = Objects.requireNonNull(arFragment.getArSceneView().getSession()).createAnchor(pos);
        AnchorNode anchorNode2 = new AnchorNode(anchor2);
        anchorNode2.setParent(arFragment.getArSceneView().getScene());
        for (Node nnn : pathWaypoints) {
            nnn.setParent(anchorNode2);
            nnn.setRenderable(modelRenderable);
        }
         */
    }

    //Takes in the next waypoint you are trying to get to
    //assume the user holds the phone upright
    public static boolean directionToVoice(Node point) {

        boolean checkOff = false;
        Frame frame2 = arFragment.getArSceneView().getArFrame();
        Camera arCamera = arFragment.getArSceneView().getScene().getCamera();
        assert frame2 != null;
        Pose cameraPose = frame2.getCamera().getPose().compose(Pose.makeTranslation(0, 0, 0));
        Anchor anchor = Objects.requireNonNull(arFragment.getArSceneView().getSession()).createAnchor(cameraPose);
        AnchorNode anchorNode = new AnchorNode(anchor);
        anchorNode.setParent(arFragment.getArSceneView().getScene());
        Node trackCrumb = new Node();
        trackCrumb.setParent(anchorNode);

        assert point.getParent() != null;
        double distanceValue = Math.sqrt((trackCrumb.getWorldPosition().x - point.getLocalPosition().x) * (trackCrumb.getWorldPosition().x - point.getLocalPosition().x) + (trackCrumb.getWorldPosition().y - point.getLocalPosition().y) * (trackCrumb.getWorldPosition().y - point.getLocalPosition().y) + (trackCrumb.getWorldPosition().z - point.getLocalPosition().z) * (trackCrumb.getWorldPosition().z - point.getLocalPosition().z));

        System.out.println("ABC: " + distanceValue);
        if (distanceValue <= 0.3) {
            speakOut("Next point");
            checkOff = true;
        }

        //Difference vector
        Vector3 relativePoint = arCamera.worldToLocalPoint(point.getWorldPosition());
        Vector3 cameraLocal = arCamera.worldToLocalPoint(arCamera.getWorldPosition());
        Vector3 difference = Vector3.subtract(relativePoint, cameraLocal);

        //-Z axis vector
        Vector3 cameraPos = arCamera.getWorldPosition();
        Vector3 cameraForward = Vector3.add(cameraPos, arCamera.getForward().normalized());
        Vector3 frontFaceZ = Vector3.subtract(cameraForward, cameraPos);
        frontFaceZ = new Vector3(frontFaceZ.x, 0, frontFaceZ.z).normalized();

        //distance (in meters)
        float distance = (float) (Math.sqrt(Math.pow((frontFaceZ.x - difference.x), 2) +
                Math.pow((frontFaceZ.y - difference.y), 2) +
                Math.pow((frontFaceZ.z - difference.z), 2)));

        //horizontal angle (in radians)
        float horizontalAngle = (float) (Math.atan2((frontFaceZ.y), (frontFaceZ.x)) -
                Math.atan2((difference.y), (difference.x)));

        //vertical angle (in radians)
        float verticalAngle = (float) (
                Math.acos(frontFaceZ.z / (
                        Math.sqrt(
                                Math.pow(frontFaceZ.x, 2) +
                                        Math.pow(frontFaceZ.y, 2) +
                                        Math.pow(frontFaceZ.z, 2)))) -
                        Math.acos((difference.z / (
                                Math.sqrt(
                                        Math.pow(difference.x, 2) +
                                                Math.pow(difference.y, 2) +
                                                Math.pow(difference.z, 2))))));
        return checkOff;
    }

    @Override
    public void onInit(int i) {

    }

    private static void speakOut(String text) {
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "");
    }
}