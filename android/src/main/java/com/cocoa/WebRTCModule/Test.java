package com.cocoa.WebRTCModule;

import org.webrtc.MediaStreamTrack;

import java.util.ArrayList;
import java.util.List;

public class Test {
    private static MediaStreamTrack audioTrack;
    private static MediaStreamTrack videoTrack;

    public static void addAudioTrack(MediaStreamTrack track) {
        audioTrack = track;
    }

    public static MediaStreamTrack getAudioTrack() {
        return audioTrack;
    }

    public static void addVideoTrack(MediaStreamTrack track) {
        videoTrack = track;
    }

    public static MediaStreamTrack getVideoTrack() {
        return videoTrack;
    }
}
