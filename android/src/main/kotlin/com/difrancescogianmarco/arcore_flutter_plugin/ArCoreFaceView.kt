package com.difrancescogianmarco.arcore_flutter_plugin



import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.media.CamcorderProfile
import android.net.Uri
import android.provider.MediaStore
import android.graphics.Bitmap
import android.view.PixelCopy
import android.os.Handler
import android.os.HandlerThread
import android.content.ContextWrapper
import java.io.FileOutputStream
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import android.os.Environment
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.widget.Toast
import com.difrancescogianmarco.arcore_flutter_plugin.VideoRecorder
import com.difrancescogianmarco.arcore_flutter_plugin.utils.ArCoreUtils
import com.google.ar.core.AugmentedFace
import com.google.ar.core.Config
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException
import com.google.ar.sceneform.*
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.Texture
import com.google.ar.sceneform.ux.AugmentedFaceNode
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel


class ArCoreFaceView(activity:Activity,context: Context, messenger: BinaryMessenger, id: Int, debug: Boolean) : BaseArCoreView(activity, context, messenger, id, debug) {

    private val TAG: String = ArCoreFaceView::class.java.name
    private var faceRegionsRenderable: ModelRenderable? = null
    private var faceMeshTexture: Texture? = null
    private val faceNodeMap = HashMap<AugmentedFace, AugmentedFaceNode>()
    private var faceSceneUpdateListener: Scene.OnUpdateListener
    private var videoRecorder = VideoRecorder()
    private var flashEnabled = false
    private var imagePath : String = " "
    //private var newRecorder: Boolean = true

    init {
        val orientation: Int = context.getResources().getConfiguration().orientation
        videoRecorder!!.setVideoQuality(CamcorderProfile.QUALITY_HIGH, orientation)
        videoRecorder!!.setSceneView(arSceneView)
        videoRecorder!!.setContext(context)
        //videoRecorder!!.setVideoSize(arSceneView!!.getWidth(), arSceneView!!.getHeight())

        faceSceneUpdateListener = Scene.OnUpdateListener { frameTime ->
            run {
/*                //                if (faceRegionsRenderable == null || faceMeshTexture == null) {
                if (faceMeshTexture == null) {
                    return@OnUpdateListener
                }*/
                val faceList = arSceneView?.session?.getAllTrackables(AugmentedFace::class.java)

                faceList?.let {
                    // Make new AugmentedFaceNodes for any new faces.
                    for (face in faceList) {
                        if (!faceNodeMap.containsKey(face)) {
                            val faceNode = AugmentedFaceNode(face)
                            faceNode.setParent(arSceneView?.scene)
                            faceNode.faceRegionsRenderable = faceRegionsRenderable
                            faceNode.faceMeshTexture = faceMeshTexture
                            faceNodeMap[face] = faceNode

                            // change assets on runtime
                        } else if(faceNodeMap[face]?.faceRegionsRenderable != faceRegionsRenderable  ||  faceNodeMap[face]?.faceMeshTexture != faceMeshTexture ){
                            faceNodeMap[face]?.faceRegionsRenderable = faceRegionsRenderable
                            faceNodeMap[face]?.faceMeshTexture = faceMeshTexture
                        }
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
                    takeScreenshot(call, result)
                }
                "getImagePath" -> {
                    result.success(imagePath)
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
                    faceRegionsRenderable = modelRenderable
                    modelRenderable.isShadowCaster = false
                    modelRenderable.isShadowReceiver = false
                }
        }
        else{
            faceRegionsRenderable = null
        }

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
    
    fun record(){
/*        if(newRecorder) {
            videoRecorder = VideoRecorder()

            val orientation: Int = context.getResources().getConfiguration().orientation
            videoRecorder!!.setVideoQuality(CamcorderProfile.QUALITY_2160P, orientation)
            videoRecorder!!.setSceneView(arSceneView)
            videoRecorder!!.setContext(context)
            newRecorder = false

        }*/
        val recording = videoRecorder!!.onToggleRecord()
        if (recording) {
            Toast.makeText(activity, "Started Recording", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(activity, "Recording Stopped", Toast.LENGTH_SHORT).show()
            val videoPath = videoRecorder!!.getVideoPath().getAbsolutePath()
            //path = videoPath
            Toast.makeText(activity, "Video saved: $videoPath", Toast.LENGTH_SHORT).show()
            //Log.d(VideoRecorder.TAG, "Video saved: $videoPath")
            val values = ContentValues()
            values.put(MediaStore.Video.Media.TITLE, "Sceneform Video")
            values.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            values.put(MediaStore.Video.Media.DATA, videoPath)
            context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            //newRecorder = true
        }
    }

    private fun takeScreenshot(call: MethodCall, result: MethodChannel.Result) {
        try {
            // create bitmap screen capture

            // Create a bitmap the size of the scene view.
            Toast.makeText(activity, "taking picture"+System.currentTimeMillis(), Toast.LENGTH_SHORT).show()
            val bitmap: Bitmap = Bitmap.createBitmap(arSceneView!!.getWidth(), arSceneView!!.getHeight(),
                Bitmap.Config.ARGB_8888)

            // Create a handler thread to offload the processing of the image.
            val handlerThread = HandlerThread("PixelCopier")
            handlerThread.start()
            // Make the request to copy.
            // Make the request to copy.
            PixelCopy.request(arSceneView!!, bitmap, { copyResult ->
                if (copyResult === PixelCopy.SUCCESS) {
                    try {
                        saveBitmapToDisk(bitmap)
                    } catch (e: IOException) {
                        e.printStackTrace();
                    }
                }
                handlerThread.quitSafely()
            }, Handler(handlerThread.getLooper()))

        } catch (e: Throwable) {
            // Several error may come out with file handling or DOM
            e.printStackTrace()
        }
        result.success(null)
    }

    @Throws(IOException::class)
    fun saveBitmapToDisk(bitmap: Bitmap):String {

//        val now = LocalDateTime.now()
//        now.format(DateTimeFormatter.ofPattern("M/d/y H:m:ss"))
        val now = "pic"
        // android/data/com.hswo.mvc_2021.hswo_mvc_2021_flutter_ar/files/
        // activity.applicationContext.getFilesDir().toString() //doesnt work!!
        // Environment.getExternalStorageDirectory()
        val mPath: String =  context.cacheDir.toString() + now + System.currentTimeMillis()  + ".jpg"
        //Toast.makeText(activity, "saving pic" +mPath, Toast.LENGTH_SHORT).show()
        val mediaFile = File(mPath)
        debugLog(mediaFile.toString())
        //Log.i("path","fileoutputstream opened")
        //Log.i("path",mPath)
//        val fileOutputStream = FileOutputStream(mediaFile)
//        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream)
//        fileOutputStream.flush()
//        fileOutputStream.close()
//        Log.i("path","fileoutputstream closed")
        imagePath = mPath
        //Toast.makeText(activity, "path" +imagePath, Toast.LENGTH_SHORT).show()
        return mPath as String
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
            Toast.makeText(activity, "Id:"+cameraId, Toast.LENGTH_SHORT).show()

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
            if (!ArCoreUtils.hasWritePermission(activity)) {
                ArCoreUtils.requestWritePermission(activity)
            }
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
                ArCoreUtils.handleSessionException(activity, e)
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