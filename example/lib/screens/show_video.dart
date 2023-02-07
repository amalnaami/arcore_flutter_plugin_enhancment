import 'dart:io';

import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';

/// Video Show page
class VideoShow extends StatefulWidget {
  /// Constructor
  const VideoShow({Key? key, required this.clipPath});

  /// Video Path
  final String clipPath;

  @override
  State<VideoShow> createState() => _VideoShowState();
}

class _VideoShowState extends State<VideoShow> {
  late VideoPlayerController _videoController;
  late Future<void> myFuture;

  @override
  void initState() {
    super.initState();
    _videoController = VideoPlayerController.file(File(widget.clipPath));
    myFuture = _initializeVideo();
  }

  Future<void> _initializeVideo() async {
    await _videoController.initialize();
    await _videoController.play();
    await _videoController.setLooping(true);
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
        backgroundColor: Colors.blue,
        // appBar: AppBar(
        //   title: const Text('Show Video Faces'),
        // ),
        body: Container(
          color: Colors.amber,
          width: MediaQuery.of(context).size.width,
          height: MediaQuery.of(context).size.height,
          child: FutureBuilder(
              future: myFuture,
              builder: (context, snapshot) {
                return AspectRatio(
                    aspectRatio: _videoController.value.aspectRatio,
                    child: VideoPlayer(_videoController));
              }),
        ));
  }
}
