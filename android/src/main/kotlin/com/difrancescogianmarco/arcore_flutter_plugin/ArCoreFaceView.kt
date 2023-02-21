package com.difrancescogianmarco.arcore_flutter_plugin



import android.app.Activity

import android.content.ContentUris
import android.provider.DocumentsContract
import android.database.Cursor
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
    //private var path : File? = null
    private var imggdirectory : File? = null
    private var imagePath : String? = " "
    private val imggdir =  File(context.getCacheDir().getAbsolutePath())
    //private var newRecorder: Boolean = true

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
        //this.sceneView.getWidth()
        //this.sceneView.getHeight()

        //Toast.makeText(activity, "Width:"+arSceneView!!.getWidth() , Toast.LENGTH_SHORT).show()
        //Toast.makeText(activity, "Height:"+arSceneView!!.getHeight() , Toast.LENGTH_SHORT).show()
        //videoRecorder!!.setVideoSize(arSceneView!!.getHeight(), arSceneView!!.getWidth())

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
            //Toast.makeText(activity, "taking picture"+System.currentTimeMillis(), Toast.LENGTH_SHORT).show()
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
//                        saveBitmapToDisk(bitmap)
                        saveBitmap(bitmap)
                    } catch (e: IOException) {
                        e.printStackTrace();
                    }
                }
                handlerThread.quitSafely()
            }, Handler(handlerThread.getLooper()))

        } catch (e: Throwable) {
            // Several error may come out with file handling or DOM
            //e.printStackTrace()
        }
        result.success(null)
    }
    @Throws(IOException::class)
    fun saveBitmap(bitmap: Bitmap): Uri {

/*        if (imggdirectory == null) {

            imggdirectory =
                File(context.getCacheDir() + "/Sceneform")
        }
        val path = File(imggdirectory, "pic" + System.currentTimeMillis() + ".jpg"
        )

        val dir: File = path.getParentFile()
        if (!dir.exists()) {
            dir.mkdirs()
        }*/

        if (!imggdir.exists()) {
            imggdir.mkdirs()
        }

        val imggpath = File(
            imggdir,
            "img"
                    //+ System.currentTimeMillis() + ".jpeg"
        )

        Toast.makeText(activity, "saving picture: " + imggpath.getAbsolutePath(), Toast.LENGTH_LONG).show()


        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.TITLE, "AR picture" )
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
        }
        //imagePath = imggpath.getAbsolutePath()


        val resolver = context.contentResolver
        var uri: Uri? = null




        try {
            uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Failed to create new MediaStore record.")

            resolver.openOutputStream(uri)?.use {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 50, it))
                    throw IOException("Failed to save bitmap.")
            } ?: throw IOException("Failed to open output stream.")
            imagePath = getRealPathFromURI(context, uri)

            return uri

        } catch (e: IOException) {

            uri?.let { orphanUri ->
                // Don't leave an orphan entry in the MediaStore
                resolver.delete(orphanUri, null, null)
            }

            throw e
        }
    }

    fun getRealPathFromURI(context: Context, uri: Uri): String? {
        when {
            // DocumentProvider
            DocumentsContract.isDocumentUri(context, uri) -> {
                when {
                    // ExternalStorageProvider
                    isExternalStorageDocument(uri) -> {
                        val docId = DocumentsContract.getDocumentId(uri)
                        val split = docId.split(":").toTypedArray()
                        val type = split[0]
                        // This is for checking Main Memory
                        return if ("primary".equals(type, ignoreCase = true)) {
                            if (split.size > 1) {
                                Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                            } else {
                                Environment.getExternalStorageDirectory().toString() + "/"
                            }
                            // This is for checking SD Card
                        } else {
                            "storage" + "/" + docId.replace(":", "/")
                        }
                    }
                    isDownloadsDocument(uri) -> {
                        val fileName = getFilePath(context, uri)
                        if (fileName != null) {
                            return Environment.getExternalStorageDirectory().toString() + "/Download/" + fileName
                        }
                        var id = DocumentsContract.getDocumentId(uri)
                        if (id.startsWith("raw:")) {
                            id = id.replaceFirst("raw:".toRegex(), "")
                            val file = File(id)
                            if (file.exists()) return id
                        }
                        val contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), java.lang.Long.valueOf(id))
                        return getDataColumn(context, contentUri, null, null)
                    }
                    isMediaDocument(uri) -> {
                        val docId = DocumentsContract.getDocumentId(uri)
                        val split = docId.split(":").toTypedArray()
                        val type = split[0]
                        var contentUri: Uri? = null
                        when (type) {
                            "image" -> {
                                contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            }
                            "video" -> {
                                contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            }
                            "audio" -> {
                                contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                            }
                        }
                        val selection = "_id=?"
                        val selectionArgs = arrayOf(split[1])
                        return getDataColumn(context, contentUri, selection, selectionArgs)
                    }
                }
            }
            "content".equals(uri.scheme, ignoreCase = true) -> {
                // Return the remote address
                return if (isGooglePhotosUri(uri)) uri.lastPathSegment else getDataColumn(context, uri, null, null)
            }
            "file".equals(uri.scheme, ignoreCase = true) -> {
                return uri.path
            }
        }
        return null
    }

    fun getDataColumn(context: Context, uri: Uri?, selection: String?,
                      selectionArgs: Array<String>?): String? {
        var cursor: Cursor? = null
        val column = "_data"
        val projection = arrayOf(
            column
        )
        try {
            if (uri == null) return null
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs,
                null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(index)
            }
        } finally {
            cursor?.close()
        }
        return null
    }


    fun getFilePath(context: Context, uri: Uri?): String? {
        var cursor: Cursor? = null
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME
        )
        try {
            if (uri == null) return null
            cursor = context.contentResolver.query(uri, projection, null, null,
                null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                return cursor.getString(index)
            }
        } finally {
            cursor?.close()
        }
        return null
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    fun isGooglePhotosUri(uri: Uri): Boolean {
        return "com.google.android.apps.photos.content" == uri.authority
    }

//  @Throws(IOException::class)
//    fun saveBitmapToDisk(bitmap: Bitmap):String {
//
////        val now = LocalDateTime.now()
////        now.format(DateTimeFormatter.ofPattern("M/d/y H:m:ss"))
//        val now = "pic"
//        // android/data/com.hswo.mvc_2021.hswo_mvc_2021_flutter_ar/files/
//        // activity.applicationContext.getFilesDir().toString() //doesnt work!!
//        // Environment.getExternalStorageDirectory()
//        //context.getCacheDir().toString()
//        val mPath: String =  Environment.getExternalStorageDirectory().toString()+ "/DCIM/" + now + System.currentTimeMillis()  + ".jpg"
//        //Toast.makeText(activity, "saving pic" +mPath, Toast.LENGTH_SHORT).show()
//        val mediaFile = File(mPath)
//        debugLog(mediaFile.toString())
//        //Log.i("path","fileoutputstream opened")
//        //Log.i("path",mPath)
//        val fileOutputStream = FileOutputStream(mediaFile)
//        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream)
//        fileOutputStream.flush()
//        fileOutputStream.close()
////        Log.i("path","fileoutputstream closed")
//        imagePath = mPath
//        //Toast.makeText(activity, "path" +imagePath, Toast.LENGTH_SHORT).show()
//        return mPath as String
//    }

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