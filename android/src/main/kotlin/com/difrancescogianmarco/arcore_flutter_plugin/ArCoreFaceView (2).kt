package com.difrancescogianmarco.arcore_flutter_plugin

//import com.google.ar.sceneform.ux.ArFragment.OnViewCreatedListener
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.CamcorderProfile
import android.media.Image
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import android.widget.Toast
import com.difrancescogianmarco.arcore_flutter_plugin.utils.ArCoreUtils
import com.google.ar.core.*
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException
import com.google.ar.sceneform.*
import com.google.ar.sceneform.animation.ModelAnimator
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.Texture
import com.google.ar.sceneform.ux.AugmentedFaceNode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.pow


//import com.google_mlkit_common.InputImageConverter

class ArCoreFaceView(activity:Activity,context: Context, messenger: BinaryMessenger, id: Int, debug: Boolean) : BaseArCoreView(activity, context, messenger, id, debug) {

    private val TAG: String = ArCoreFaceView::class.java.name
    private var faceRegionsRenderable: ModelRenderable? = null
    private var dummyAnimationModel: ModelRenderable? = null
    private var faceMeshTexture: Texture? = null
    private val faceNodeMap = HashMap<AugmentedFace, AugmentedFaceNode>()
    private var faceSceneUpdateListener: Scene.OnUpdateListener
    private var videoRecorder = VideoRecording()
    private var flashEnabled = false
    //private var path : File? = null
    private var imggdirectory : File? = null
    private var imagePath : String? = " "
    private val imggdir =  context.getCacheDir()
    private var modelAnimator : ModelAnimator? = null
    private var  i = 0;
    val skeletonNode = SkeletonNode()
    private lateinit var mlKitFaceDetector: FaceDetector
    private val frameInterval: Long = 500
    val session = arSceneView?.session
    private var mUserRequestedInstall = true
    private var mlKitThread = HandlerThread("mlKitThread")
    private lateinit var mlKitHandler: Handler
    private var IsThreadInitialised = false
    private var IsDetectorInitialized = false
    private var heartRenderable: ModelRenderable? = null
    var frameCounter = 0
    private var heartAnimationFlag = false
    var filterString: String? = null
    var errorMessage: String? = null

    init {
        val orientation: Int = context.getResources().getConfiguration().orientation
        videoRecorder!!.setSceneView(arSceneView)
        videoRecorder!!.setContext(context)
        videoRecorder!!.setVideoQuality(CamcorderProfile.QUALITY_1080P, orientation)
        //videoRecorder!!.setVideoSize(arSceneView!!.getWidth(), arSceneView!!.getHeight())

        faceSceneUpdateListener = Scene.OnUpdateListener { frameTime ->
            run {
/*                //                if (faceRegionsRenderable == null || faceMeshTexture == null) {
                if (faceMeshTexture == null) {
                    return@OnUpdateListener
                }*/
                val faceList = arSceneView?.session?.getAllTrackables(AugmentedFace::class.java)
                //videoRecorder!!.setVideoSize(arSceneView!!.getWidth(), arSceneView!!.getHeight())

                faceList?.let {
                    // Make new AugmentedFaceNodes for any new faces.
                    for (face in faceList) {
                        if (!faceNodeMap.containsKey(face)) {
                            val faceNode = AugmentedFaceNode(face)
                            faceNode.setParent(arSceneView?.scene)
                            skeletonNode.setParent(faceNode)
                            faceNode.faceRegionsRenderable = faceRegionsRenderable
                            faceNode.faceMeshTexture = faceMeshTexture
                            faceNodeMap[face] = faceNode

                            // change assets on runtime
                        } else if(faceNodeMap[face]?.faceRegionsRenderable != faceRegionsRenderable  ||  faceNodeMap[face]?.faceMeshTexture != faceMeshTexture ){
                            faceNodeMap[face]?.faceRegionsRenderable = faceRegionsRenderable
                            faceNodeMap[face]?.faceMeshTexture = faceMeshTexture
/*                            if(faceRegionsRenderable != null && dummyAnimationModel?.getAnimationDataCount() != 0)
                            {
                                //Toast.makeText(activity, "Calling animateModel func: "+ (faceRegionsRenderable != null && dummyAnimationModel?.getAnimationDataCount() != 0) , Toast.LENGTH_SHORT).show()
                                animateModel(faceRegionsRenderable)
                            }*/
                        }
                        /*if(filterString == "EYE_TONGUE.sfb")
                            heartRenderable = faceRegionsRenderable*/
                        frameCounter++
                        while(faceRegionsRenderable == heartRenderable && heartRenderable != null && frameCounter >= 10) {

                            if (!IsThreadInitialised) {
                                mlKitThread.start()
                                mlKitHandler = Handler(mlKitThread.looper)
                                IsThreadInitialised = true
                            }

                            onUpdateFrame(frameTime)

                            frameCounter = 1

                            //arCoreCameraThread.quitSafely()
                        }
                        heartAnimationFlag = false
                    }

                    // Remove any AugmentedFaceNodes associated with an AugmentedFace that stopped tracking.
                    val iter = faceNodeMap.iterator()
                    while (iter.hasNext()) {
                        val entry = iter.next()
                        val face = entry.key
                        if (face.trackingState == TrackingState.STOPPED) {
                            val faceNode = entry.value
                            faceNode.setParent(null)
                            iter.remove()
                        }
                    }
                }
            }
        }
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        if(isSupportedDevice){
            debugLog(call.method +"called on supported device")
            when (call.method) {
                "init" -> {
                    arScenViewInit(call, result)
                }
                "loadMesh" -> {
                    val map = call.arguments as HashMap<*, *>
                    val textureBytes = map["textureBytes"] as? ByteArray
                    val skin3DModelFilename = map["skin3DModelFilename"] as? String
                    loadMesh(textureBytes, skin3DModelFilename)
                }
                "record" -> {
                    record()
                }
                "getVideoPath" -> {
                    //val path = videoRecorder!!.getVideoPath().getAbsolutePath()
                    result.success(videoRecorder!!.getVideoPath().getAbsolutePath())
                    //methodChannel.invokeMethod("getVideoPath", path.toString())
                }
                "toggleFlashlight" -> {
                    toggleFlash()
                }
                "takePicture" -> {
                    //takeScreenshot(call, result)
                    captureArViewAndGetImagePath()
                }
                "getImagePath" -> {
                    result.success(imagePath)
                }
                "getErrorMessage" -> {
                    result.success(errorMessage)
                }
                "dispose" -> {
                    debugLog( " updateMaterials")
                    dispose()
                }
                else -> {
                    result.notImplemented()
                }
            }
        }else{
            debugLog("Impossible call " + call.method + " method on unsupported device")
            result.error("Unsupported Device","",null)
        }
    }

    fun loadMesh(textureBytes: ByteArray?, skin3DModelFilename: String?) {

        if (skin3DModelFilename != null) {
            // Load the face regions renderable.
            // This is a skinned model that renders 3D objects mapped to the regions of the augmented face.
            ModelRenderable.builder()
                .setSource(activity, Uri.parse(skin3DModelFilename))
                .build()
                .thenAccept { modelRenderable ->
                    //AnchorNode anchorNode = new AnchorNode();
                    faceRegionsRenderable = modelRenderable
                    dummyAnimationModel = modelRenderable
                    //skeletonNode.renderable = modelRenderable
                    modelRenderable.isShadowCaster = false
                    modelRenderable.isShadowReceiver = false
                }

        }
        else{
            faceRegionsRenderable = null
        }

        filterString = skin3DModelFilename
        /*if(skin3DModelFilename == "HeartAnimated.sfb" || skin3DModelFilename == "walking.sfb") {
            heartRenderable = faceRegionsRenderable
        }*/

        // Load the face mesh texture.
        if(textureBytes != null) {
            Texture.builder()
                //.setSource(activity, Uri.parse("fox_face_mesh_texture.png"))
                .setSource(BitmapFactory.decodeByteArray(textureBytes, 0, textureBytes!!.size))
                .build()
                .thenAccept { texture -> faceMeshTexture = texture }
        }
        else{
            faceMeshTexture = null
        }
    }

    fun Frame.tryAcquireCameraImage(): Image? =
        acquireCameraImage()

    private fun copyPixelFromView(views: SurfaceView, callback: (Bitmap) -> Unit) {
        var bitmap = Bitmap.createBitmap(
            views!!.width,
            views!!.height,
            Bitmap.Config.ARGB_8888
        )
        // val view = arFragment?.arSceneView
        PixelCopy.request(views, bitmap, { copyResult ->
            if (copyResult == PixelCopy.SUCCESS) {
                Log.i(TAG, "Copying ArFragment view.")
                callback(bitmap)
                Log.i(TAG, "Copied ArFragment view.")

            } else {
                Log.e(TAG, "Failed to copy ArFragment view.")
            }
        }, mlKitHandler)
    }

    private fun onUpdateFrame(frameTime: FrameTime?){
        arSceneView!!.arFrame ?: return
        copyPixelFromView(arSceneView!!){ bitmap ->
            val targetBitmap = Bitmap.createBitmap(bitmap)
            processImageWithMLKit(targetBitmap)
        }
    }

    fun processImageWithMLKit(bitmap: Bitmap?){
        val inputImage = InputImage.fromBitmap(bitmap!!, 0)
        if(!IsDetectorInitialized) {
            createMLKitFaceDetector()
            IsDetectorInitialized = true
        }
        mlKitFaceDetector.process(inputImage)
            .addOnSuccessListener{faces ->
                for(face in faces){
                    Log.i(TAG, "Inside ML kit detecting...")
                    detectMouthOpen(face)
                }
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
            }
        Log.i(TAG, "Finished Processing Image")
    }

    fun createMLKitFaceDetector(){
        val faceDetectorOptions = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
        //lateinit var mlKitFaceDetector: FaceDetector
        Log.i(TAG, "Created Detector")
        mlKitFaceDetector = FaceDetection.getClient(faceDetectorOptions)
    }

    fun calcualteSideLength(coordinate1: PointF, coordinate2: PointF): Double {
        val xDiff = Math.abs(coordinate1.x - coordinate2.x)
        val yDiff = Math.abs(coordinate1.y - coordinate2.y)
        return Math.hypot(xDiff.toDouble(), yDiff.toDouble())
    }

    fun detectMouthOpen(face: Face){

        val mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)
        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)
        val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)

        val rightLeftDistance = calcualteSideLength(mouthRight!!.position, mouthLeft!!.position)
        val bottomLeftDistance = calcualteSideLength(mouthBottom!!.position, mouthLeft!!.position)
        val bottomRightDistance = calcualteSideLength(mouthBottom!!.position, mouthRight!!.position)

        val thetaRadians = Math.acos((rightLeftDistance.pow(2) + bottomLeftDistance.pow(2) - bottomRightDistance.pow(2)) / (2 * rightLeftDistance * bottomLeftDistance))
        //val betaRadians = Math.acos((rightLeftDistance.pow(2) + bottomRightDistance.pow(2) - bottomLeftDistance.pow(2)) / (2 * rightLeftDistance * bottomRightDistance))
        //val alphaRadians = Math.acos((bottomRightDistance.pow(2) + bottomLeftDistance.pow(2) - rightLeftDistance.pow(2)) / (2 * bottomRightDistance * bottomLeftDistance))
        val thetaDegrees = Math.toDegrees(thetaRadians)
        //val betaDegrees= Math.toDegrees(betaRadians)
        //val alphaDegrees = Math.toDegrees(alphaRadians)
        //Log.i(TAG, "Total Triangle angles: " + (thetaDegrees + betaDegrees + alphaDegrees) )
        if(mouthBottom != null ){
            if(thetaDegrees>28){
                if(!heartAnimationFlag ) {
                    animateModel(faceRegionsRenderable)
                    heartAnimationFlag = true
                }
                Log.i(TAG, "Mouth Open THETA: " + thetaDegrees)
                /*Log.i(TAG, "Mouth Open BETA: " + betaDegrees)
                Log.i(TAG, "Mouth Open ALPHA: " + alphaDegrees)*/
            } else{
                Log.i(TAG, "Mouth Closed THETA: " + thetaDegrees)
                /*Log.i(TAG, "Mouth Closed BETA: " + betaDegrees)
                Log.i(TAG, "Mouth Closed ALPHA: " + alphaDegrees)*/
            }
        }
    }

    fun detectBlinking(face: Face){
        val leftEyeOpenProbability = face.leftEyeOpenProbability
        val rightEyeOpenProbability = face.rightEyeOpenProbability

        if(leftEyeOpenProbability != null && rightEyeOpenProbability != null){
            if(leftEyeOpenProbability < 0.5 || rightEyeOpenProbability <0.5){
                if(!heartAnimationFlag ) {
                    animateModel(faceRegionsRenderable)
                    heartAnimationFlag = true
                }
                Log.i(TAG, "Blinking Detected ")
                Toast.makeText(activity, "Blinking Detected: " , Toast.LENGTH_SHORT).show()
            } else{
                Log.i(TAG, "Eye Open")
                Toast.makeText(activity, "Eye Open ", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun detectSmiling(face: Face){

        val smileProbability = face.smilingProbability

        if(smileProbability != null && smileProbability > 0.5){
            if(!heartAnimationFlag ) {
                animateModel(faceRegionsRenderable)
                heartAnimationFlag = true
            }/* else{
                heartAnimationFlag = false
            }*/
            Log.i(TAG, "Smiling Detected ")
            Toast.makeText(activity, "Smiling Detected ", Toast.LENGTH_SHORT).show()
        } else{
            Log.i(TAG, "No Smiling")
            Toast.makeText(activity, "No Smiling" , Toast.LENGTH_SHORT).show()
        }
    }

    fun animateModel(modelRenderable: ModelRenderable?){

        if(modelAnimator != null && modelAnimator!!.isRunning())
            return
        //Toast.makeText(activity, "Animation Count:"+modelRenderable!!.getAnimationDataCount() , Toast.LENGTH_SHORT).show()
        var animationCount = modelRenderable!!.getAnimationDataCount()
        //Toast.makeText(activity, "Animation Count:"+animationCount , Toast.LENGTH_SHORT).show()

        if(i == animationCount)
            i = 0
        var animationData = modelRenderable!!.getAnimationData(i)

        modelAnimator = ModelAnimator(animationData, modelRenderable)
        modelAnimator!!.start()
        //modelAnimator!!.setRepeatCount(-1)
        i++
    }
    
    fun record(){
        val recording = videoRecorder!!.onToggleRecord()
        if (!recording) {
            val videoPath = videoRecorder!!.getVideoPath().getAbsolutePath()
            val values = ContentValues()
            values.put(MediaStore.Video.Media.TITLE, "Sceneform Video")
            values.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            values.put(MediaStore.Video.Media.DATA, videoPath)
            context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        }
    }


    private fun captureArViewAndGetImagePath() {
        // Get the ARSceneView from your ARCore session

        // Create a bitmap to store the captured image
        //val bitmap = Bitmap.createBitmap(arSceneView!!.width, arSceneView!!.height, Bitmap.Config.ARGB_8888)
        var bitmap: Bitmap? = null
        if(arSceneView!!.getHeight() % 2 == 0) {
            bitmap = Bitmap.createBitmap(
                arSceneView!!.getWidth(), arSceneView!!.getHeight(),
                Bitmap.Config.ARGB_8888
            )
        } else{
            bitmap = Bitmap.createBitmap(
                arSceneView!!.getWidth(), arSceneView!!.getHeight() + 1,
                Bitmap.Config.ARGB_8888
            )
        }

        // Create a Canvas with the bitmap
        //val canvas = Canvas(bitmap)

        val handlerThread = HandlerThread("PixelCopier")
        handlerThread.start()

        // Use PixelCopy to capture the AR view
        PixelCopy.request(arSceneView!!, bitmap, { copyResult ->
            if (copyResult === PixelCopy.SUCCESS) {
                try {
                    //val resized: Bitmap = Bitmap.createScaledBitmap(bitmap, 1080, 1920, false)
                    val imageFile = saveImageToCache(bitmap)
                    imagePath = imageFile.absolutePath
                } catch (e: IOException) {
                    e.printStackTrace();
                }
            }
            handlerThread.quitSafely()
        }, Handler(handlerThread.getLooper()))
            null
    }

    // Save the bitmap image to the cache directory
    private fun saveImageToCache(bitmap: Bitmap): File {
        // Get the cache directory
        val cacheDir = context.cacheDir

        // Create a unique filename based on the current timestamp
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "AR_Image_$timestamp.jpg"

        // Create the image file
        val imageFile = File(cacheDir, fileName)

        // Save the bitmap to the image file
        FileOutputStream(imageFile).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            outputStream.flush()
        }

        // Return the image file
        return imageFile
    }


    
    fun toggleFlash(){
        if(hasFlash()){
            if(flashEnabled){
                disableFlash()
            }else{
                enableFlash()
            }
        }
    }
    
    fun enableFlash() {
        if (hasFlash()) {
            flashEnabled = true

            setTorchMode(flashEnabled)
        }
    }
    
    fun disableFlash() {
        if (hasFlash()) {
            flashEnabled = false

            setTorchMode(flashEnabled)
        }
    }
    
    private fun hasFlash(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
    }
    
    private fun getCameraManager(): CameraManager {
        return context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private fun setTorchMode(mode: Boolean) {
        try {
            val cameraManager = getCameraManager()
            val cameraId = cameraManager.cameraIdList[0]
            //Toast.makeText(activity, "Id:"+cameraId, Toast.LENGTH_SHORT).show()

            cameraManager.setTorchMode(cameraId, mode)
        } catch (e: CameraAccessException) {
            flashEnabled = false
            e.printStackTrace()
        }
    }

    fun arScenViewInit(call: MethodCall, result: MethodChannel.Result) {
        val enableAugmentedFaces: Boolean? = call.argument("enableAugmentedFaces")
        if (enableAugmentedFaces != null && enableAugmentedFaces) {
            // This is important to make sure that the camera stream renders first so that
            // the face mesh occlusion works correctly.
            arSceneView?.cameraStreamRenderPriority = Renderable.RENDER_PRIORITY_FIRST
            arSceneView?.scene?.addOnUpdateListener(faceSceneUpdateListener)
        }

        result.success(null)
    }

    override fun onResume() {
        if (arSceneView == null) {
            return
        }

        if (arSceneView?.session == null) {

            // request camera permission if not already requested
            if (!ArCoreUtils.hasCameraPermission(activity)) {
                ArCoreUtils.requestCameraPermission(activity, RC_PERMISSIONS)
            }
/*            if (!ArCoreUtils.hasWritePermission(activity)) {
                ArCoreUtils.requestWritePermission(activity)
            }*/
            if (!ArCoreUtils.hasAudioPermission(activity)) {
                ArCoreUtils.requestAudioPermission(activity)
            }

            // If the session wasn't created yet, don't resume rendering.
            // This can happen if ARCore needs to be updated or permissions are not granted yet.
            try {
                val session = ArCoreUtils.createArSession(activity, installRequested, true)
                if (session == null) {
                    installRequested = false
                    return
                } else {
                    val config = Config(session)
                    config.augmentedFaceMode = Config.AugmentedFaceMode.MESH3D
                    config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    session.configure(config)
                    arSceneView?.setupSession(session)
                }
            } catch (e: UnavailableException) {
                errorMessage = ArCoreUtils.handleSessionException(activity, e)
            }
        }

        try {
            arSceneView?.resume()
        } catch (ex: CameraNotAvailableException) {
            ArCoreUtils.displayError(activity, "Unable to get camera", ex)
            activity.finish()
            return
        }

    }

    override fun onDestroy() {
        arSceneView?.scene?.removeOnUpdateListener(faceSceneUpdateListener)
        super.onDestroy()
    }
}