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
                ElevatedButton(
                  onPressed: () {
                    print("before button pressed  $index");

                    setState(() {
                      //index++;
                      loadMesh(index);
                      print("after button pressed  $index");
                    });
                  }
                  ,
                  style: ElevatedButton.styleFrom(
                      elevation: 12.0,
                      textStyle: const TextStyle(color: Colors.white)),
                  child: const Text("Change Filter"),
                ),
              ] ),
        ));
  }


/*  void _onArCoreViewCreated(ArCoreFaceController controller) {
    arCoreFaceController = controller;

    loadMesh(index);

    if



    //arCoreFaceController?.deleteObject();



  }*/

  void _onArCoreViewCreated(ArCoreFaceController controller) {
    setState(() {  arCoreFaceController = controller;
    //loadMesh(index);
    print("index before: $index");

    Future.delayed(const Duration(seconds: 3)).then((value) {
      ++index;
      print("index after: $index");
      loadMesh(index);

    });

      //arCoreFaceController?.deleteObject();

    });
  }






  loadMesh(int x) async {
    final ByteData textureBytes =
    await rootBundle.load('assets/fox_face_mesh_texture.png');

    arCoreFaceController?.loadMesh(
        textureBytes: textureBytes.buffer.asUint8List(),
        index: x);
  }

  @override
  void dispose() {
    arCoreFaceController?.dispose();
    super.dispose();
  }
}
