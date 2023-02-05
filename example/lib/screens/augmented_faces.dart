import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:arcore_flutter_plugin/arcore_flutter_plugin.dart';
import 'package:flutter/services.dart';


class AugmentedFacesScreen extends StatefulWidget {
  const AugmentedFacesScreen({Key? key}) : super(key: key);

  @override
  _AugmentedFacesScreenState createState() => _AugmentedFacesScreenState();
}

class _AugmentedFacesScreenState extends State<AugmentedFacesScreen> {
  ArCoreFaceController? arCoreFaceController;
  int index = 0;
  String?  path;


  @override
  Widget build(BuildContext context) {
    return MaterialApp(
        home: Scaffold(
          appBar: AppBar(
            title: const Text('Augmented Faces'),
          ),
          body:Stack(
              alignment: Alignment.bottomCenter,
              children: [
                ArCoreFaceView(
                  onArCoreViewCreated: _onArCoreViewCreated,
                  enableAugmentedFaces: true,
                ),
                Align(
                  alignment: Alignment.bottomLeft,
                  child: ElevatedButton(
                  onPressed: () async {
                    print("path before is: $path");
                    final newpath =  await getVideoPath();
                   setState(() {
                     path =  newpath;
                   });
                    print("path after is: $path");
                    // Navigator.push(context, MaterialPageRoute(builder: (context) {
                    //   return VideoShow(
                    //     clipPath: newpath!,
                    //   );
                    // }));
                  },
                  style: ElevatedButton.styleFrom(
                      elevation: 8.0,
                      textStyle: const TextStyle(color: Colors.white)),
                  child: const Text("get path"),
                  ),
                ),
                Align(
                  alignment: Alignment.bottomRight,
                  child: ElevatedButton(
                  onPressed: () {
                    //arCoreFaceController?.init();
                    loadMesh('sunglasses.sfb', null);
                  }
                  ,
                  style: ElevatedButton.styleFrom(
                      elevation: 2.0,
                      textStyle: const TextStyle(color: Colors.white)),
                  child: const Text("Filter 3"),
                  ),
                ),
                Align(
                  alignment: Alignment.bottomCenter,
                  child: ElevatedButton(
                    onPressed: () {
                      record();
                    }
                    ,
                    style: ElevatedButton.styleFrom(
                        elevation: 2.0,
                        textStyle: const TextStyle(color: Colors.white)),
                    child: const Text("record"),
                  ),
                ),
              ] ),
        ));
  }

  void _onArCoreViewCreated(ArCoreFaceController controller) {
    arCoreFaceController = controller;
    loadMesh('wolf6.sfb', 'assets/fox_face_mesh_texture.png');
  }

  loadMesh(String filter, String? texture) async {
    if(texture == null){
      arCoreFaceController?.loadMesh(
          textureBytes: null,
          skin3DModelFilename: filter);
    }
    else {
      final ByteData textureBytes =
      await rootBundle.load(texture);

      arCoreFaceController?.loadMesh(
          textureBytes: textureBytes.buffer.asUint8List(),
          skin3DModelFilename: filter);
    }
  }

  record() async{
    arCoreFaceController?.record();
  }

  Future<String?> getVideoPath() async {
    final path = (arCoreFaceController?.getVideoPath());
    //print("path here is $path");
    return path;
  }

  @override
  void dispose() {
    arCoreFaceController?.dispose();
    super.dispose();
  }

  @override
  void initState() {
    super.initState();
  }
}
