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
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.io.File
import java.io.IOException
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import com.difrancescogianmarco.arcore_flutter_plugin.utils.ArCoreUtils
import com.google.ar.core.AugmentedFace
import com.google.ar.core.Config
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.Texture
import com.google.ar.sceneform.ux.AugmentedFaceNode
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel


class ArCoreFaceView(
    activity: Activity,
    context: Context,
    messenger: BinaryMessenger,
    id: Int,
    debug: Boolean
) : BaseArCoreView(activity, context, messenger, id, debug) {

    private val TAG: String = ArCoreFaceView::class.java.name
    private var faceRegionsRenderable: ModelRenderable? = null
    private var faceMeshTexture: Texture? = null
    private val faceNodeMap = HashMap<AugmentedFace, AugmentedFaceNode>()
    private var faceSceneUpdateListener: Scene.OnUpdateListener
    private var videoRecorder =
        VideoRecording()
    private var flashEnabled = false
    private var imagePath: String? = " "

    init {
        val orientation: Int = context.getResources().getConfiguration().orientation
        videoRecorder.setSceneView(arSceneView)
        videoRecorder.setContext(context)
        videoRecorder.setVideoQuality(CamcorderProfile.QUALITY_1080P, orientation)

        faceSceneUpdateListener = Scene.OnUpdateListener { frameTime ->
            run {
                val faceList = arSceneView?.session?.getAllTrackables(AugmentedFace::class.java)

                faceList?.let {
                    for (face in faceList) {
                        if (!faceNodeMap.containsKey(face)) {
                            val faceNode = AugmentedFaceNode(face)
                            faceNode.setParent(arSceneView?.scene)
                            faceNode.faceRegionsRenderable = faceRegionsRenderable
                            faceNode.faceMeshTexture = faceMeshTexture
                            faceNodeMap[face] = faceNode

                        } else if (faceNodeMap[face]?.faceRegionsRenderable != faceRegionsRenderable || faceNodeMap[face]?.faceMeshTexture != faceMeshTexture) {
                            faceNodeMap[face]?.faceRegionsRenderable = faceRegionsRenderable
                            faceNodeMap[face]?.faceMeshTexture = faceMeshTexture
                        }
                    }

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
        if (isSupportedDevice) {
            debugLog(call.method + "called on supported device")
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
                    result.success(videoRecorder.getVideoPath().getAbsolutePath())
                }

                "toggleFlashlight" -> {
                    toggleFlash()
                }

                "takePicture" -> {
                    captureArViewAndGetImagePath()
                }

                "getImagePath" -> {
                    result.success(imagePath)
                }

                "dispose" -> {
                    debugLog(" updateMaterials")
                    dispose()
                }

                else -> {
                    result.notImplemented()
                }
            }
        } else {
            debugLog("Impossible call " + call.method + " method on unsupported device")
            result.error("Unsupported Device", "", null)
        }
    }

    fun loadMesh(textureBytes: ByteArray?, skin3DModelFilename: String?) {
        if (skin3DModelFilename != null) {
            ModelRenderable.builder()
                .setSource(activity, Uri.parse(skin3DModelFilename))
                .build()
                .thenAccept { modelRenderable ->
                    faceRegionsRenderable = modelRenderable
                    modelRenderable.isShadowCaster = false
                    modelRenderable.isShadowReceiver = false
                }
        } else {
            faceRegionsRenderable = null
        }

        if (textureBytes != null) {
            Texture.builder()
                .setSource(BitmapFactory.decodeByteArray(textureBytes, 0, textureBytes.size))
                .build()
                .thenAccept { texture -> faceMeshTexture = texture }
        } else {
            faceMeshTexture = null
        }
    }

    fun record() {
        val recording = videoRecorder.onToggleRecord()
        if (!recording) {
            val videoPath = videoRecorder.getVideoPath().getAbsolutePath()
            val values = ContentValues()
            values.put(MediaStore.Video.Media.TITLE, "Sceneform Video")
            values.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            values.put(MediaStore.Video.Media.DATA, videoPath)
            context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        }
    }

    private fun captureArViewAndGetImagePath() {
        var bitmap: Bitmap? = null
        if (arSceneView!!.getHeight() % 2 == 0) {
            bitmap = Bitmap.createBitmap(
                arSceneView!!.getWidth(), arSceneView!!.getHeight(),
                Bitmap.Config.ARGB_8888
            )
        } else {
            bitmap = Bitmap.createBitmap(
                arSceneView!!.getWidth(), arSceneView!!.getHeight() + 1,
                Bitmap.Config.ARGB_8888
            )
        }


        val handlerThread = HandlerThread("PixelCopier")
        handlerThread.start()

        PixelCopy.request(arSceneView!!, bitmap, { copyResult ->
            if (copyResult === PixelCopy.SUCCESS) {
                try {
                    val imageFile = saveImageToCache(bitmap)
                    imagePath = imageFile.absolutePath
                } catch (e: IOException) {
                    e.printStackTrace();
                }
            }
            handlerThread.quitSafely()
        }, Handler(handlerThread.getLooper()))
    }

    // Save the bitmap image to the cache directory
    private fun saveImageToCache(bitmap: Bitmap): File {
        val cacheDir = context.cacheDir

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "AR_Image_$timestamp.jpg"

        val imageFile = File(cacheDir, fileName)

        FileOutputStream(imageFile).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            outputStream.flush()
        }

        return imageFile
    }

    fun toggleFlash() {
        if (hasFlash()) {
            if (flashEnabled) {
                disableFlash()
            } else {
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
            cameraManager.setTorchMode(cameraId, mode)
        } catch (e: CameraAccessException) {
            flashEnabled = false
            e.printStackTrace()
        }
    }


    fun arScenViewInit(call: MethodCall, result: MethodChannel.Result) {
        val enableAugmentedFaces: Boolean? = call.argument("enableAugmentedFaces")
        if (enableAugmentedFaces != null && enableAugmentedFaces) {
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

            if (!ArCoreUtils.hasCameraPermission(activity)) {
                ArCoreUtils.requestCameraPermission(activity, RC_PERMISSIONS)
            }

            if (!ArCoreUtils.hasAudioPermission(activity)) {
                ArCoreUtils.requestAudioPermission(activity)
            }

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