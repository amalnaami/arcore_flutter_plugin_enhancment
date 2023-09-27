import 'dart:typed_data';

import 'package:flutter/services.dart';

import '../arcore_flutter_plugin.dart';

class ArCoreFaceController {

/*  static checkArCoreAvailability() async {
    final bool arcoreAvailable = await MethodChannel(UTILS_CHANNEL_NAME)
        .invokeMethod('checkArCoreApkAvailability');
    return arcoreAvailable;
  }

  static checkIsArCoreInstalled() async {
    final bool arcoreInstalled = await MethodChannel(UTILS_CHANNEL_NAME)
        .invokeMethod('checkIfARCoreServicesInstalled');
    return arcoreInstalled;
  }*/
  
  ArCoreFaceController(
      {int? id, this.enableAugmentedFaces, this.debug = false}) {
    _channel = MethodChannel('arcore_flutter_plugin_$id');
    _channel.setMethodCallHandler(_handleMethodCalls);
    init();
  }

  final bool? enableAugmentedFaces;
  final bool debug;
  late MethodChannel _channel;
  late StringResultHandler onError;
  //String path = '';

  init() async {
    try {
      await _channel.invokeMethod<void>('init', {
        'enableAugmentedFaces': enableAugmentedFaces,
      });
    } on PlatformException catch (ex) {
      print(ex.message);
    }
  }

  Future<dynamic> _handleMethodCalls(MethodCall call) async {
    if (debug) {
      print('_platformCallHandler call ${call.method} ${call.arguments}');
    }
    switch (call.method) {
      case 'onError':
        onError(call.arguments);
        break;
/*      case 'getVideoPath':
        path = call.arguments as String;
        break;*/
      default:
        if (debug) {
          print('Unknown method ${call.method}');
        }
    }
    return Future.value();
  }

/*  Future<void> loadMesh(
      {required Uint8List textureBytes, required int index}) {
    return _channel.invokeMethod('loadMesh', {
      'textureBytes': textureBytes,
      'index': index
    });
  }*/

  Future<void> loadMesh(
      {required Uint8List? textureBytes, required String? skin3DModelFilename}) {
    return _channel.invokeMethod('loadMesh', {
      'textureBytes': textureBytes,
      'skin3DModelFilename': skin3DModelFilename
    });
  }



  Future<void> record(){
    return _channel.invokeMethod('record');
  }

  Future<String> getVideoPath() async {
    final String videoPath = await _channel.invokeMethod('getVideoPath');
    //print("path is: $path");
    return videoPath;
  }

  Future<String?> getImagePath() async {
    final String? imgPath = await _channel.invokeMethod('getImagePath');
    //print("path is: $path");
    print("path is: $imgPath");
    return imgPath;
  }

  Future<void> toggleFlashlight(){
    return _channel.invokeMethod('toggleFlashlight');
  }

  Future<void> takePictureFront(){
    return _channel.invokeMethod('takePicture');
  }
  

  Future<bool?> checkArCoreApkAvailability() async {
    final bool arcoreAvailable = await MethodChannel(UTILS_CHANNEL_NAME)
        .invokeMethod('checkArCoreApkAvailability');
    return arcoreAvailable;
  }
  
  Future<bool?> checkIfARCoreServicesInstalled() async {
    final bool arcoreInstalled = await MethodChannel(UTILS_CHANNEL_NAME)
        .invokeMethod('checkIfARCoreServicesInstalled');
    return arcoreInstalled;
  }

  Future<String?> getErrorMessage() async{
    final String? errorMessage = await _channel.invokeMethod('getErrorMessage');
    return errorMessage;
  }
  





  void dispose() {
    _channel.invokeMethod<void>('dispose');
  }
}
