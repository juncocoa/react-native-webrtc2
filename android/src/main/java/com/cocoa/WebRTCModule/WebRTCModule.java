package com.cocoa.WebRTCModule;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import android.app.ProgressDialog;
import android.os.Build;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableArray; //--添加代码
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID; //--添加代码

import org.webrtc.*;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

@ReactModule(name = "WebRTCModule")
public class WebRTCModule extends ReactContextBaseJavaModule {
    private ReactApplicationContext context = null;
    private ProgressDialog progressDialog = null; //方向舵，进度对话框
    private Timer timer = new Timer("TimerTask1"); //延迟关闭
    static final String TAG = WebRTCModule.class.getCanonicalName();

    PeerConnectionFactory mFactory;
    //去掉关键词 private
    final SparseArray<PeerConnectionObserver> mPeerConnectionObservers;
    final Map<String, MediaStream> localStreams;

    /**
     * The implementation of {@code getUserMedia} extracted into a separate file
     * in order to reduce complexity and to (somewhat) separate concerns.
     */
    private GetUserMediaImpl getUserMediaImpl;

    public static class Options {
        private VideoEncoderFactory videoEncoderFactory = null;
        private VideoDecoderFactory videoDecoderFactory = null;
        private AudioDeviceModule audioDeviceModule = null;

        public Options() {}

        public void setAudioDeviceModule(AudioDeviceModule audioDeviceModule) {
            this.audioDeviceModule = audioDeviceModule;
        }

        public void setVideoDecoderFactory(VideoDecoderFactory videoDecoderFactory) {
            this.videoDecoderFactory = videoDecoderFactory;
        }

        public void setVideoEncoderFactory(VideoEncoderFactory videoEncoderFactory) {
            this.videoEncoderFactory = videoEncoderFactory;
        }
    }

    public WebRTCModule(ReactApplicationContext reactContext) {
        this(reactContext, null);
    }

    public WebRTCModule(ReactApplicationContext reactContext, Options options) {
        super(reactContext);
        context = reactContext;
        mPeerConnectionObservers = new SparseArray<>();
        localStreams = new HashMap<>();

        ThreadUtils.runOnExecutor(() -> initAsync(options));
    }

    /**
     * Invoked asynchronously to initialize this {@code WebRTCModule} instance.
     */
    private void initAsync(Options options) {
        ReactApplicationContext reactContext = getReactApplicationContext();

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(reactContext)
                .createInitializationOptions());

        AudioDeviceModule adm = null;
        VideoEncoderFactory encoderFactory = null;
        VideoDecoderFactory decoderFactory = null;

        if (options != null) {
            adm = options.audioDeviceModule;
            encoderFactory = options.videoEncoderFactory;
            decoderFactory = options.videoDecoderFactory;
        }

        if (encoderFactory == null || decoderFactory == null) {
            // Initialize EGL context required for HW acceleration.
            EglBase.Context eglContext = EglUtils.getRootEglBaseContext();

            if (eglContext != null) {
                encoderFactory
                    = new DefaultVideoEncoderFactory(
                    eglContext,
                    /* enableIntelVp8Encoder */ true,
                    /* enableH264HighProfile */ false);
                decoderFactory = new DefaultVideoDecoderFactory(eglContext);
            } else {
                encoderFactory = new SoftwareVideoEncoderFactory();
                decoderFactory = new SoftwareVideoDecoderFactory();
            }
        }

        if (adm == null) {
            adm = JavaAudioDeviceModule.builder(reactContext).createAudioDeviceModule();
        }

        mFactory
            = PeerConnectionFactory.builder()
                .setAudioDeviceModule(adm)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();

        getUserMediaImpl = new GetUserMediaImpl(this, reactContext);
    }

    @Override
    public String getName() {
        return "WebRTCModule";
    }

    private PeerConnection getPeerConnection(int id) {
        PeerConnectionObserver pco = mPeerConnectionObservers.get(id);

        return (pco == null) ? null : pco.getPeerConnection();
    }

    void sendEvent(String eventName, @Nullable WritableMap params) {
        getReactApplicationContext()
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(eventName, params);
    }

    private PeerConnection.IceServer createIceServer(String url) {
        return PeerConnection.IceServer.builder(url).createIceServer();
    }

    private PeerConnection.IceServer createIceServer(String url, String username, String credential) {
        return PeerConnection.IceServer.builder(url)
            .setUsername(username)
            .setPassword(credential)
            .createIceServer();
    }

    private List<PeerConnection.IceServer> createIceServers(ReadableArray iceServersArray) {
        final int size = (iceServersArray == null) ? 0 : iceServersArray.size();
        List<PeerConnection.IceServer> iceServers = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ReadableMap iceServerMap = iceServersArray.getMap(i);
            boolean hasUsernameAndCredential = iceServerMap.hasKey("username") && iceServerMap.hasKey("credential");
            if (iceServerMap.hasKey("url")) {
                if (hasUsernameAndCredential) {
                    iceServers.add(createIceServer(iceServerMap.getString("url"), iceServerMap.getString("username"), iceServerMap.getString("credential")));
                } else {
                    iceServers.add(createIceServer(iceServerMap.getString("url")));
                }
            } else if (iceServerMap.hasKey("urls")) {
                switch (iceServerMap.getType("urls")) {
                    case String:
                        if (hasUsernameAndCredential) {
                            iceServers.add(createIceServer(iceServerMap.getString("urls"), iceServerMap.getString("username"), iceServerMap.getString("credential")));
                        } else {
                            iceServers.add(createIceServer(iceServerMap.getString("urls")));
                        }
                        break;
                    case Array:
                        ReadableArray urls = iceServerMap.getArray("urls");
                        for (int j = 0; j < urls.size(); j++) {
                            String url = urls.getString(j);
                            if (hasUsernameAndCredential) {
                                iceServers.add(createIceServer(url,iceServerMap.getString("username"), iceServerMap.getString("credential")));
                            } else {
                                iceServers.add(createIceServer(url));
                            }
                        }
                        break;
                }
            }
        }
        return iceServers;
    }

    private PeerConnection.RTCConfiguration parseRTCConfiguration(ReadableMap map) {
        ReadableArray iceServersArray = null;
        if (map != null) {
            iceServersArray = map.getArray("iceServers");
        }
        List<PeerConnection.IceServer> iceServers = createIceServers(iceServersArray);
        PeerConnection.RTCConfiguration conf = new PeerConnection.RTCConfiguration(iceServers);
        if (map == null) {
            return conf;
        }

        // iceTransportPolicy (public api)
        if (map.hasKey("iceTransportPolicy")
                && map.getType("iceTransportPolicy") == ReadableType.String) {
            final String v = map.getString("iceTransportPolicy");
            if (v != null) {
                switch (v) {
                case "all": // public
                    conf.iceTransportsType = PeerConnection.IceTransportsType.ALL;
                    break;
                case "relay": // public
                    conf.iceTransportsType = PeerConnection.IceTransportsType.RELAY;
                    break;
                case "nohost":
                    conf.iceTransportsType = PeerConnection.IceTransportsType.NOHOST;
                    break;
                case "none":
                    conf.iceTransportsType = PeerConnection.IceTransportsType.NONE;
                    break;
                }
            }
        }

        // bundlePolicy (public api)
        if (map.hasKey("bundlePolicy")
                && map.getType("bundlePolicy") == ReadableType.String) {
            final String v = map.getString("bundlePolicy");
            if (v != null) {
                switch (v) {
                case "balanced": // public
                    conf.bundlePolicy = PeerConnection.BundlePolicy.BALANCED;
                    break;
                case "max-compat": // public
                    conf.bundlePolicy = PeerConnection.BundlePolicy.MAXCOMPAT;
                    break;
                case "max-bundle": // public
                    conf.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
                    break;
                }
            }
        }

        // rtcpMuxPolicy (public api)
        if (map.hasKey("rtcpMuxPolicy")
                && map.getType("rtcpMuxPolicy") == ReadableType.String) {
            final String v = map.getString("rtcpMuxPolicy");
            if (v != null) {
                switch (v) {
                case "negotiate": // public
                    conf.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.NEGOTIATE;
                    break;
                case "require": // public
                    conf.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
                    break;
                }
            }
        }

        // FIXME: peerIdentity of type DOMString (public api)
        // FIXME: certificates of type sequence<RTCCertificate> (public api)

        // iceCandidatePoolSize of type unsigned short, defaulting to 0
        if (map.hasKey("iceCandidatePoolSize")
                && map.getType("iceCandidatePoolSize") == ReadableType.Number) {
            final int v = map.getInt("iceCandidatePoolSize");
            if (v > 0) {
                conf.iceCandidatePoolSize = v;
            }
        }

        // === below is private api in webrtc ===

        // tcpCandidatePolicy (private api)
        if (map.hasKey("tcpCandidatePolicy")
                && map.getType("tcpCandidatePolicy") == ReadableType.String) {
            final String v = map.getString("tcpCandidatePolicy");
            if (v != null) {
                switch (v) {
                case "enabled":
                    conf.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED;
                    break;
                case "disabled":
                    conf.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
                    break;
                }
            }
        }

        // candidateNetworkPolicy (private api)
        if (map.hasKey("candidateNetworkPolicy")
                && map.getType("candidateNetworkPolicy") == ReadableType.String) {
            final String v = map.getString("candidateNetworkPolicy");
            if (v != null) {
                switch (v) {
                case "all":
                    conf.candidateNetworkPolicy = PeerConnection.CandidateNetworkPolicy.ALL;
                    break;
                case "low_cost":
                    conf.candidateNetworkPolicy = PeerConnection.CandidateNetworkPolicy.LOW_COST;
                    break;
                }
            }
        }

        // KeyType (private api)
        if (map.hasKey("keyType")
                && map.getType("keyType") == ReadableType.String) {
            final String v = map.getString("keyType");
            if (v != null) {
                switch (v) {
                case "RSA":
                    conf.keyType = PeerConnection.KeyType.RSA;
                    break;
                case "ECDSA":
                    conf.keyType = PeerConnection.KeyType.ECDSA;
                    break;
                }
            }
        }

        // continualGatheringPolicy (private api)
        if (map.hasKey("continualGatheringPolicy")
                && map.getType("continualGatheringPolicy") == ReadableType.String) {
            final String v = map.getString("continualGatheringPolicy");
            if (v != null) {
                switch (v) {
                case "gather_once":
                    conf.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE;
                    break;
                case "gather_continually":
                    conf.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
                    break;
                }
            }
        }

        // audioJitterBufferMaxPackets (private api)
        if (map.hasKey("audioJitterBufferMaxPackets")
                && map.getType("audioJitterBufferMaxPackets") == ReadableType.Number) {
            final int v = map.getInt("audioJitterBufferMaxPackets");
            if (v > 0) {
                conf.audioJitterBufferMaxPackets = v;
            }
        }

        // iceConnectionReceivingTimeout (private api)
        if (map.hasKey("iceConnectionReceivingTimeout")
                && map.getType("iceConnectionReceivingTimeout") == ReadableType.Number) {
            final int v = map.getInt("iceConnectionReceivingTimeout");
            conf.iceConnectionReceivingTimeout = v;
        }

        // iceBackupCandidatePairPingInterval (private api)
        if (map.hasKey("iceBackupCandidatePairPingInterval")
                && map.getType("iceBackupCandidatePairPingInterval") == ReadableType.Number) {
            final int v = map.getInt("iceBackupCandidatePairPingInterval");
            conf.iceBackupCandidatePairPingInterval = v;
        }

        // audioJitterBufferFastAccelerate (private api)
        if (map.hasKey("audioJitterBufferFastAccelerate")
                && map.getType("audioJitterBufferFastAccelerate") == ReadableType.Boolean) {
            final boolean v = map.getBoolean("audioJitterBufferFastAccelerate");
            conf.audioJitterBufferFastAccelerate = v;
        }

        // pruneTurnPorts (private api)
        if (map.hasKey("pruneTurnPorts")
                && map.getType("pruneTurnPorts") == ReadableType.Boolean) {
            final boolean v = map.getBoolean("pruneTurnPorts");
            conf.pruneTurnPorts = v;
        }

        // presumeWritableWhenFullyRelayed (private api)
        if (map.hasKey("presumeWritableWhenFullyRelayed")
                && map.getType("presumeWritableWhenFullyRelayed") == ReadableType.Boolean) {
            final boolean v = map.getBoolean("presumeWritableWhenFullyRelayed");
            conf.presumeWritableWhenFullyRelayed = v;
        }
        // --添加代码 sdpSemantics
        if (map.hasKey("sdpSemantics")
                && map.getType("sdpSemantics") == ReadableType.String) {
            final String v = map.getString("sdpSemantics");
            if (v != null) {
                switch (v) {
                    case "unified-plan":
                        conf.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
                        break;
                    case "plan-b":
                        conf.sdpSemantics = PeerConnection.SdpSemantics.PLAN_B;
                }

            }
        }
        //end
        return conf;
    }

    @ReactMethod
    public void peerConnectionInit(ReadableMap configuration, int id) {
        PeerConnection.RTCConfiguration rtcConfiguration
            = parseRTCConfiguration(configuration);

        ThreadUtils.runOnExecutor(() ->
            peerConnectionInitAsync(rtcConfiguration, id));
    }

    private void peerConnectionInitAsync(
            PeerConnection.RTCConfiguration configuration,
            int id) {
        //--修改代码
        PeerConnectionObserver observer = new PeerConnectionObserver(this,
                id, configuration.sdpSemantics == PeerConnection.SdpSemantics.UNIFIED_PLAN);
        PeerConnection peerConnection
            = mFactory.createPeerConnection(configuration, observer);

        observer.setPeerConnection(peerConnection);
        mPeerConnectionObservers.put(id, observer);
    }

    MediaStream getStreamForReactTag(String streamReactTag) {
        MediaStream stream = localStreams.get(streamReactTag);

        if (stream == null) {
            for (int i = 0, size = mPeerConnectionObservers.size(); i < size; i++) {
                PeerConnectionObserver pco = mPeerConnectionObservers.valueAt(i);
                stream = pco.remoteStreams.get(streamReactTag);
                if (stream != null) {
                    break;
                }
            }
        }

        return stream;
    }

    private MediaStreamTrack getTrack(String trackId) {
        MediaStreamTrack track = getLocalTrack(trackId);

        if (track == null) {
            for (int i = 0, size = mPeerConnectionObservers.size(); i < size; i++) {
                PeerConnectionObserver pco = mPeerConnectionObservers.valueAt(i);
                track = pco.remoteTracks.get(trackId);
                if (track != null) {
                    break;
                }
            }
        }

        return track;
    }

    MediaStreamTrack getLocalTrack(String trackId) {
        return getUserMediaImpl.getTrack(trackId);
    }

    MediaStreamTrack getLocalTrackByType(String type) {
        return getUserMediaImpl.getTrackByType(type);
    }

    private static MediaStreamTrack getLocalTrack(
            MediaStream localStream,
            String trackId) {
        for (AudioTrack track : localStream.audioTracks) {
            if (track.id().equals(trackId)) {
                return track;
            }
        }
        for (VideoTrack track : localStream.videoTracks) {
            if (track.id().equals(trackId)) {
                return track;
            }
        }
        return null;
    }

    /**
     * Turns an "options" <tt>ReadableMap</tt> into a <tt>MediaConstraints</tt> object.
     *
     * @param options A <tt>ReadableMap</tt> which represents a JavaScript
     * object specifying the options to be parsed into a
     * <tt>MediaConstraints</tt> instance.
     * @return A new <tt>MediaConstraints</tt> instance initialized with the
     * mandatory keys and values specified by <tt>options</tt>.
     */
    MediaConstraints constraintsForOptions(ReadableMap options) {
        MediaConstraints mediaConstraints = new MediaConstraints();
        ReadableMapKeySetIterator keyIterator = options.keySetIterator();

        while (keyIterator.hasNextKey()) {
            String key = keyIterator.nextKey();
            String value = ReactBridgeUtil.getMapStrValue(options, key);

            mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(key, value));
        }

        return mediaConstraints;
    }

    @ReactMethod
    public void getUserMedia(ReadableMap constraints,
                             Callback    successCallback,
                             Callback    errorCallback) {
        ThreadUtils.runOnExecutor(() ->
            getUserMediaImpl.getUserMedia(constraints, successCallback, errorCallback));
    }

    @ReactMethod
    public void enumerateDevices(Callback callback) {
        ThreadUtils.runOnExecutor(() ->
            callback.invoke(getUserMediaImpl.enumerateDevices()));
    }

    @ReactMethod
    public void mediaStreamCreate(String id) {
        ThreadUtils.runOnExecutor(() -> mediaStreamCreateAsync(id));
    }

    private void mediaStreamCreateAsync(String id) {
        MediaStream mediaStream = mFactory.createLocalMediaStream(id);
        localStreams.put(id, mediaStream);
    }

    @ReactMethod
    public void mediaStreamAddTrack(String streamId, String trackId) {
        ThreadUtils.runOnExecutor(() ->
            mediaStreamAddTrackAsync(streamId, trackId));
    }

    private void mediaStreamAddTrackAsync(String streamId, String trackId) {
        MediaStream stream = localStreams.get(streamId);
        MediaStreamTrack track = getTrack(trackId);

        if (stream == null || track == null) {
            Log.d(TAG, "mediaStreamAddTrack() stream || track is null");
            return;
        }

        String kind = track.kind();
        if ("audio".equals(kind)) {
            stream.addTrack((AudioTrack)track);
        } else if ("video".equals(kind)) {
            stream.addTrack((VideoTrack)track);
        }
    }

    @ReactMethod
    public void mediaStreamRemoveTrack(String streamId, String trackId) {
        ThreadUtils.runOnExecutor(() ->
            mediaStreamRemoveTrackAsync(streamId, trackId));
    }

    private void mediaStreamRemoveTrackAsync(String streamId, String trackId) {
        MediaStream stream = localStreams.get(streamId);
        MediaStreamTrack track = getTrack(trackId);

        if (stream == null || track == null) {
            Log.d(TAG, "mediaStreamRemoveTrack() stream || track is null");
            return;
        }

        String kind = track.kind();
        if ("audio".equals(kind)) {
            stream.removeTrack((AudioTrack)track);
        } else if ("video".equals(kind)) {
            stream.removeTrack((VideoTrack)track);
        }
    }

    @ReactMethod
    public void mediaStreamRelease(String id) {
        ThreadUtils.runOnExecutor(() -> mediaStreamReleaseAsync(id));
    }

    private void mediaStreamReleaseAsync(String id) {
        MediaStream stream = localStreams.get(id);
        if (stream == null) {
            Log.d(TAG, "mediaStreamRelease() stream is null");
            return;
        }

        // Remove and dispose any tracks ourselves before calling stream.dispose().
        // We need to remove the extra objects (TrackPrivate) we create.

        List<AudioTrack> audioTracks = new ArrayList<>(stream.audioTracks);
        for (AudioTrack track : audioTracks) {
            track.setEnabled(false);
            stream.removeTrack(track);
            getUserMediaImpl.disposeTrack(track.id());
        }

        List<VideoTrack> videoTracks = new ArrayList<>(stream.videoTracks);
        for (VideoTrack track : videoTracks) {
            track.setEnabled(false);
            stream.removeTrack(track);
            getUserMediaImpl.disposeTrack(track.id());
        }

        localStreams.remove(id);

        // MediaStream.dispose() may be called without an exception only if
        // it's no longer added to any PeerConnection.
        for (int i = 0, size = mPeerConnectionObservers.size(); i < size; i++) {
            mPeerConnectionObservers.valueAt(i).removeStream(stream);
        }

        stream.dispose();
    }

    @ReactMethod
    public void mediaStreamTrackRelease(String id) {
        ThreadUtils.runOnExecutor(() ->
            mediaStreamTrackReleaseAsync(id));
    }

    private void mediaStreamTrackReleaseAsync(String id) {
        MediaStreamTrack track = getLocalTrack(id);
        if (track == null) {
            Log.d(TAG, "mediaStreamTrackRelease() track is null");
            return;
        }
        track.setEnabled(false);
        getUserMediaImpl.disposeTrack(id);
    }

    @ReactMethod
    public void mediaStreamTrackSetEnabled(String id, boolean enabled) {
        ThreadUtils.runOnExecutor(() ->
            mediaStreamTrackSetEnabledAsync(id, enabled));
    }

    private void mediaStreamTrackSetEnabledAsync(String id, boolean enabled) {
        MediaStreamTrack track = getTrack(id);
        if (track == null) {
            Log.d(TAG, "mediaStreamTrackSetEnabled() track is null");
            return;
        } else if (track.enabled() == enabled) {
            return;
        }
        track.setEnabled(enabled);
        getUserMediaImpl.mediaStreamTrackSetEnabled(id, enabled);
    }

    @ReactMethod
    public void mediaStreamTrackSwitchCamera(String id) {
        MediaStreamTrack track = getLocalTrack(id);
        if (track != null) {
            getUserMediaImpl.switchCamera(id);
        }
    }

    @ReactMethod
    public void peerConnectionSetConfiguration(ReadableMap configuration,
                                               int id) {
        ThreadUtils.runOnExecutor(() ->
            peerConnectionSetConfigurationAsync(configuration, id));
    }

    private void peerConnectionSetConfigurationAsync(ReadableMap configuration,
                                                     int id) {
        PeerConnection peerConnection = getPeerConnection(id);
        if (peerConnection == null) {
            Log.d(TAG, "peerConnectionSetConfiguration() peerConnection is null");
            return;
        }
        peerConnection.setConfiguration(parseRTCConfiguration(configuration));
    }

    @ReactMethod
    public void peerConnectionAddStream(String streamId, int id, Callback callback) {
        ThreadUtils.runOnExecutor(() ->
            peerConnectionAddStreamAsync(streamId, id, callback));
    }

    private void peerConnectionAddStreamAsync(String streamId, int id, Callback callback) {
        MediaStream mediaStream = localStreams.get(streamId);

        if (mediaStream == null) {
            Log.d(TAG, "peerConnectionAddStream() mediaStream is null");
            return;
        }
        PeerConnectionObserver pco = mPeerConnectionObservers.get(id);
        if(pco != null){
            if(pco.isUnifiedPlan == false){
                boolean result = pco.addStream(mediaStream);
                if(result == true){
                    callback.invoke(true);
                }else {
                    callback.invoke(false,"add stream failed");
                    Log.e(TAG, "peerConnectionAddStream() failed");
                }
            }else {
                callback.invoke(false,"Unified Plan mode does not allow AddStream");
                Log.e(TAG, "Unified Plan mode does not allow AddStream");
            }
        }else {
            callback.invoke(false,"pco == null");
            Log.e(TAG, "peerConnectionAddStream() failed");
        }
    }

    @ReactMethod
    public void peerConnectionRemoveStream(String streamId, int id) {
        ThreadUtils.runOnExecutor(() ->
            peerConnectionRemoveStreamAsync(streamId, id));
    }

    private void peerConnectionRemoveStreamAsync(String streamId, int id) {
        MediaStream mediaStream = localStreams.get(streamId);
        if (mediaStream == null) {
            Log.d(TAG, "peerConnectionRemoveStream() mediaStream is null");
            return;
        }
        PeerConnectionObserver pco = mPeerConnectionObservers.get(id);
        if (pco == null || !pco.removeStream(mediaStream)) {
            Log.e(TAG, "peerConnectionRemoveStream() failed");
        }
    }

    //--添加代码
    @ReactMethod
    public void peerConnectionAddTrack(String trackId, int id, Callback callback) {
        ThreadUtils.runOnExecutor(() ->
                peerConnectionAddTrackAsync(trackId, id, callback));
    }

    private void peerConnectionAddTrackAsync(String trackId, int id, Callback callback){
        PeerConnectionObserver pco = mPeerConnectionObservers.get(id);

        if(pco != null){
            if(pco.isUnifiedPlan == true){
                //只能添加本地轨道
                MediaStreamTrack mediaStreamTrack = getLocalTrack(trackId);
                if (mediaStreamTrack == null) {
                    Log.d(TAG, "peerConnectionAddTrack() mediaStreamTrack is null(local)");
                    return;
                }

                try{
                    //查找轨道是否有对应的（RtpSender）了
                    String addTrackId = mediaStreamTrack.id(); RtpSender sender = null;
                    for (RtpSender rtpSender : pco.getPeerConnection().getSenders()){
                        if(rtpSender.track() != null){
                            if(rtpSender.track().id().equalsIgnoreCase(addTrackId)){
                                sender = rtpSender;
                                break;
                            }
                        }
                    }

                    if(sender == null){
                        //没有创建收发器，就添加轨道，有收发器，轨道就添加到 RtpSender 里
                        if(pco.getPeerConnection().getTransceivers().size() <= 0){
                            sender = pco.addTrack(mediaStreamTrack);
                        }else {
                            for (RtpTransceiver transceiver : pco.getPeerConnection().getTransceivers()){
                                if(transceiver.getReceiver().track() != null){
                                    if(transceiver.getSender().track() == null && transceiver.getReceiver().track().kind().equalsIgnoreCase(mediaStreamTrack.kind())){
                                        transceiver.getSender().setTrack(mediaStreamTrack,false);
                                        transceiver.setDirection(RtpTransceiver.RtpTransceiverDirection.SEND_RECV);
                                        sender = transceiver.getSender();
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    if(sender != null){
                        WritableMap map = Arguments.createMap();
                        WritableMap subMap = Arguments.createMap();
                        map.putString("id", sender.id());


                        subMap.putString("id", sender.track().id());
                        subMap.putString("kind", sender.track().kind());
                        subMap.putString("readyState", (sender.track().state() == MediaStreamTrack.State.LIVE)?"live":"ended");
                        subMap.putBoolean("enabled", sender.track().enabled());
                        subMap.putBoolean("remote", false); //只能添加本地轨道，远程轨道 RTC 自动添加到收发器（RtpReceiver 里)

                        map.putMap("track", subMap);

                        callback.invoke(true, map);
                    }else {
                        callback.invoke(false,"add track failed");
                        Log.e(TAG, "peerConnectionAddTrack() failed");
                    }
                }catch (Exception e){
                    callback.invoke(false,"add track failed");
                    Log.e(TAG, "peerConnectionAddTrack() failed");
                }
            }else {
                callback.invoke(false,"Plan-B mode does not allow AddTrack");
                Log.e(TAG, "Plan-B mode does not allow AddTrack");
            }
        }else {
            callback.invoke(false,"pco == null");
            Log.e(TAG, "peerConnectionAddTrack() failed");
        }
    }

    @ReactMethod
    public void peerConnectionRemoveTrack(String senderId, int id, Callback callback) {
        ThreadUtils.runOnExecutor(() ->
                peerConnectionRemoveTrackAsync(senderId, id, callback));
    }

    private void peerConnectionRemoveTrackAsync(String senderId, int id, Callback callback) {
        PeerConnectionObserver pco = mPeerConnectionObservers.get(id);
        RtpSender rtpSender = null;

        if(pco != null){
            for (int i = 0; i < pco.getPeerConnection().getSenders().size(); i++) {
                if(pco.getPeerConnection().getSenders().get(i).id().equalsIgnoreCase(senderId)){
                    rtpSender = pco.getPeerConnection().getSenders().get(i);
                    break;
                }
            }

            if(rtpSender != null){
                boolean result = pco.removeTrack(rtpSender);
                if(result == true){
                    callback.invoke(true);
                }else {
                    Log.e(TAG, "peerConnectionRemoveTrack() failed");
                    callback.invoke(false, "peerConnectionRemoveTrack() failed");
                }
            }else {
                Log.e(TAG, "peerConnectionRemoveTrack() rtpSender is null");
                callback.invoke(false, "rtpSender == null");
            }
        }else {
            Log.e(TAG, "peerConnectionRemoveTrack() failed");
            callback.invoke(false, "pco == null");
        }
    }

    @ReactMethod
    public void peerConnectionGetRtpSenders(int id, Callback callback) {
        ThreadUtils.runOnExecutor(() ->
                peerConnectionGetRtpSendersAsync(id, callback));
    }

    private void peerConnectionGetRtpSendersAsync(int id, Callback callback){
        PeerConnectionObserver pco = mPeerConnectionObservers.get(id);
        if(pco != null){
            WritableArray array = Arguments.createArray();
            for (int i = 0; i < pco.getPeerConnection().getSenders().size(); i++) {
                RtpSender sender = pco.getPeerConnection().getSenders().get(i);
                if(sender.track() == null){ continue; } //因移除了轨道，导致 RtpSender 存在，而 MediaStreamTrack 为 null

                WritableMap map = Arguments.createMap();
                WritableMap subMap = Arguments.createMap();
                map.putString("id", sender.id());

                subMap.putString("id", sender.track().id());
                subMap.putString("kind", sender.track().kind());
                subMap.putString("readyState", (sender.track().state() == MediaStreamTrack.State.LIVE)?"live":"ended");
                subMap.putBoolean("enabled", sender.track().enabled());
                subMap.putBoolean("remote", false);

                map.putMap("track", subMap);
                array.pushMap(map);
            }
            callback.invoke(true, array);
        }else {
            Log.e(TAG, "peerConnectionRemoveTrack() failed");
            callback.invoke(false, "pco == null");
        }
    }

    private ReadableMap serializeState(int id) {
        PeerConnection peerConnection = getPeerConnection(id);
        PeerConnectionObserver pco = mPeerConnectionObservers.get(id);
        WritableArray transceivers = Arguments.createArray();
        if (pco.isUnifiedPlan){
            for (RtpTransceiver transceiver : peerConnection.getTransceivers()) {
                transceivers.pushMap(serializeTransceiver(pco.resolveTransceiverId(transceiver), transceiver));
            }
        }
        WritableMap res = Arguments.createMap();
        res.putArray("transceivers", transceivers);
        return res;
    }
    //end

    @ReactMethod
    public void peerConnectionCreateOffer(int id,
                                          ReadableMap options,
                                          Callback callback) {
        ThreadUtils.runOnExecutor(() ->
            peerConnectionCreateOfferAsync(id, options, callback));
    }

    private void peerConnectionCreateOfferAsync(int id,
                                                ReadableMap options,
                                                final Callback callback) {
        PeerConnection peerConnection = getPeerConnection(id);

        if (peerConnection != null) {
            peerConnection.createOffer(new SdpObserver() {
                @Override
                public void onCreateFailure(String s) {
                    callback.invoke(false, s);
                }

                @Override
                public void onCreateSuccess(SessionDescription sdp) {
                    WritableMap params = Arguments.createMap();
                    params.putString("sdp", sdp.description);
                    params.putString("type", sdp.type.canonicalForm());
                    //--添加代码
                    WritableMap res = Arguments.createMap();
                    res.putMap("session", params);
                    res.putMap("state", serializeState(id));
                    //end
                    callback.invoke(true, res); //--修改代码
                }

                @Override
                public void onSetFailure(String s) {}

                @Override
                public void onSetSuccess() {}
            }, constraintsForOptions(options));
        } else {
            Log.d(TAG, "peerConnectionCreateOffer() peerConnection is null");
            callback.invoke(false, "peerConnection is null");
        }
    }

    @ReactMethod
    public void peerConnectionCreateAnswer(int id,
                                           ReadableMap options,
                                           Callback callback) {
        ThreadUtils.runOnExecutor(() ->
            peerConnectionCreateAnswerAsync(id, options, callback));
    }

    private void peerConnectionCreateAnswerAsync(int id,
                                                 ReadableMap options,
                                                 final Callback callback) {
        PeerConnection peerConnection = getPeerConnection(id);

        if (peerConnection != null) {
            peerConnection.createAnswer(new SdpObserver() {
                @Override
                public void onCreateFailure(String s) {
                    callback.invoke(false, s);
                }

                @Override
                public void onCreateSuccess(SessionDescription sdp) {
                    WritableMap params = Arguments.createMap();
                    params.putString("sdp", sdp.description);
                    params.putString("type", sdp.type.canonicalForm());
                    //--添加代码
                    WritableMap res = Arguments.createMap();
                    res.putMap("session", params);
                    res.putMap("state", serializeState(id));
                    //end
                    callback.invoke(true, res);
                }

                @Override
                public void onSetFailure(String s) {}

                @Override
                public void onSetSuccess() {}
            }, constraintsForOptions(options));
        } else {
            Log.d(TAG, "peerConnectionCreateAnswer() peerConnection is null");
            callback.invoke(false, "peerConnection is null");
        }
    }

    @ReactMethod
    public void peerConnectionSetLocalDescription(ReadableMap sdpMap,
                                                  int id,
                                                  Callback callback) {
        ThreadUtils.runOnExecutor(() ->
            peerConnectionSetLocalDescriptionAsync(sdpMap, id, callback));
    }

    private void peerConnectionSetLocalDescriptionAsync(ReadableMap sdpMap,
                                                        int id,
                                                        final Callback callback) {
        PeerConnection peerConnection = getPeerConnection(id);

        Log.d(TAG, "peerConnectionSetLocalDescription() start");
        if (peerConnection != null) {
            SessionDescription sdp = new SessionDescription(
                SessionDescription.Type.fromCanonicalForm(sdpMap.getString("type")),
                sdpMap.getString("sdp")
            );

            peerConnection.setLocalDescription(new SdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sdp) {
                }

                @Override
                public void onSetSuccess() {
                    //--添加代码
                    WritableMap res = Arguments.createMap();
                    res.putMap("state", serializeState(id));
                    //end
                    callback.invoke(true, res);
                }

                @Override
                public void onCreateFailure(String s) {
                }

                @Override
                public void onSetFailure(String s) {
                    callback.invoke(false, s);
                }
            }, sdp);
        } else {
            Log.d(TAG, "peerConnectionSetLocalDescription() peerConnection is null");
            callback.invoke(false, "peerConnection is null");
        }
        Log.d(TAG, "peerConnectionSetLocalDescription() end");
    }

    @ReactMethod
    public void peerConnectionSetRemoteDescription(ReadableMap sdpMap,
                                                   int id,
                                                   Callback callback) {
        ThreadUtils.runOnExecutor(() ->
            peerConnectionSetRemoteDescriptionAsync(sdpMap, id, callback));
    }

    private void peerConnectionSetRemoteDescriptionAsync(ReadableMap sdpMap,
                                                         int id,
                                                         final Callback callback) {
        PeerConnection peerConnection = getPeerConnection(id);

        Log.d(TAG, "peerConnectionSetRemoteDescription() start");
        if (peerConnection != null) {
            SessionDescription sdp = new SessionDescription(
                SessionDescription.Type.fromCanonicalForm(sdpMap.getString("type")),
                sdpMap.getString("sdp")
            );

            peerConnection.setRemoteDescription(new SdpObserver() {
                @Override
                public void onCreateSuccess(final SessionDescription sdp) {
                }

                @Override
                public void onSetSuccess() {
                    WritableMap res = Arguments.createMap();
                    res.putMap("state", serializeState(id));
                    callback.invoke(true, res);
                }

                @Override
                public void onCreateFailure(String s) {
                }

                @Override
                public void onSetFailure(String s) {
                    callback.invoke(false, s);
                }
            }, sdp);
        } else {
            Log.d(TAG, "peerConnectionSetRemoteDescription() peerConnection is null");
            callback.invoke(false, "peerConnection is null");
        }
        Log.d(TAG, "peerConnectionSetRemoteDescription() end");
    }

    @ReactMethod
    public void peerConnectionAddICECandidate(ReadableMap candidateMap,
                                              int id,
                                              Callback callback) {
        ThreadUtils.runOnExecutor(() ->
            peerConnectionAddICECandidateAsync(candidateMap, id, callback));
    }

    private void peerConnectionAddICECandidateAsync(ReadableMap candidateMap,
                                                    int id,
                                                    Callback callback) {
        boolean result = false;
        PeerConnection peerConnection = getPeerConnection(id);
        Log.d(TAG, "peerConnectionAddICECandidate() start");
        if (peerConnection != null) {
            IceCandidate candidate = new IceCandidate(
                candidateMap.getString("sdpMid"),
                candidateMap.getInt("sdpMLineIndex"),
                candidateMap.getString("candidate")
            );
            result = peerConnection.addIceCandidate(candidate);
        } else {
            Log.d(TAG, "peerConnectionAddICECandidate() peerConnection is null");
        }
        callback.invoke(result);
        Log.d(TAG, "peerConnectionAddICECandidate() end");
    }

    @ReactMethod
    public void peerConnectionGetStats(String trackId, int id, Callback cb) {
        ThreadUtils.runOnExecutor(() ->
            peerConnectionGetStatsAsync(trackId, id, cb));
    }

    private void peerConnectionGetStatsAsync(String trackId,
                                             int id,
                                             Callback cb) {
        PeerConnectionObserver pco = mPeerConnectionObservers.get(id);
        if (pco == null || pco.getPeerConnection() == null) {
            Log.d(TAG, "peerConnectionGetStats() peerConnection is null");
            cb.invoke(false, "PeerConnection ID not found");
        } else {
            pco.getStats(trackId, cb);
        }
    }

    @ReactMethod
    public void peerConnectionClose(int id) {
        ThreadUtils.runOnExecutor(() -> peerConnectionCloseAsync(id));
    }

    private void peerConnectionCloseAsync(int id) {
        PeerConnectionObserver pco = mPeerConnectionObservers.get(id);
        if (pco == null || pco.getPeerConnection() == null) {
            Log.d(TAG, "peerConnectionClose() peerConnection is null");
        } else {
            pco.close();
            mPeerConnectionObservers.remove(id);
        }
    }

    @ReactMethod
    public void createDataChannel(int peerConnectionId,
                                  String label,
                                  ReadableMap config) {
        ThreadUtils.runOnExecutor(() ->
            createDataChannelAsync(peerConnectionId, label, config));
    }

    private void createDataChannelAsync(int peerConnectionId,
                                        String label,
                                        ReadableMap config) {
        // Forward to PeerConnectionObserver which deals with DataChannels
        // because DataChannel is owned by PeerConnection.
        PeerConnectionObserver pco
            = mPeerConnectionObservers.get(peerConnectionId);
        if (pco == null || pco.getPeerConnection() == null) {
            Log.d(TAG, "createDataChannel() peerConnection is null");
        } else {
            pco.createDataChannel(label, config);
        }
    }

    @ReactMethod
    public void dataChannelClose(int peerConnectionId, int dataChannelId) {
        ThreadUtils.runOnExecutor(() ->
            dataChannelCloseAsync(peerConnectionId, dataChannelId));
    }

    private void dataChannelCloseAsync(int peerConnectionId,
                                       int dataChannelId) {
        // Forward to PeerConnectionObserver which deals with DataChannels
        // because DataChannel is owned by PeerConnection.
        PeerConnectionObserver pco
            = mPeerConnectionObservers.get(peerConnectionId);
        if (pco == null || pco.getPeerConnection() == null) {
            Log.d(TAG, "dataChannelClose() peerConnection is null");
        } else {
            pco.dataChannelClose(dataChannelId);
        }
    }

    @ReactMethod
    public void dataChannelSend(int peerConnectionId,
                                int dataChannelId,
                                String data,
                                String type) {
        ThreadUtils.runOnExecutor(() ->
            dataChannelSendAsync(peerConnectionId, dataChannelId, data, type));
    }

    private void dataChannelSendAsync(int peerConnectionId,
                                      int dataChannelId,
                                      String data,
                                      String type) {
        // Forward to PeerConnectionObserver which deals with DataChannels
        // because DataChannel is owned by PeerConnection.
        PeerConnectionObserver pco
            = mPeerConnectionObservers.get(peerConnectionId);
        if (pco == null || pco.getPeerConnection() == null) {
            Log.d(TAG, "dataChannelSend() peerConnection is null");
        } else {
            pco.dataChannelSend(dataChannelId, data, type);
        }
    }
    //--添加代码
    // Transceivers API
    private String serializeDirection(RtpTransceiver.RtpTransceiverDirection src) {
        if (src == RtpTransceiver.RtpTransceiverDirection.INACTIVE) {
            return "inactive";
        } else if (src == RtpTransceiver.RtpTransceiverDirection.RECV_ONLY) {
            return "recvonly";
        } else if (src == RtpTransceiver.RtpTransceiverDirection.SEND_ONLY) {
            return "sendonly";
        } else if (src == RtpTransceiver.RtpTransceiverDirection.SEND_RECV) {
            return "sendrecv";
        } else {
            throw new Error("Invalid direction");
        }
    }

    private RtpTransceiver.RtpTransceiverDirection parseDirection(String src) throws Error{
        switch (src) {
            case "sendrecv":
                return RtpTransceiver.RtpTransceiverDirection.SEND_RECV;
            case "sendonly":
                return RtpTransceiver.RtpTransceiverDirection.SEND_ONLY;
            case "recvonly":
                return RtpTransceiver.RtpTransceiverDirection.RECV_ONLY;
            case "inactive":
                return RtpTransceiver.RtpTransceiverDirection.INACTIVE;
        }
        throw new Error("Invalid direction");
    }

    private RtpTransceiver.RtpTransceiverInit parseTransceiverOptions(ReadableMap map) {
        RtpTransceiver.RtpTransceiverDirection direction = RtpTransceiver.RtpTransceiverDirection.SEND_RECV;
        ArrayList<String> streamIds = new ArrayList<>();
        if (map != null) {
            if (map.hasKey("direction")) {
                String directionRaw = map.getString("direction");
                if (directionRaw != null) {
                    direction = this.parseDirection(directionRaw);
                }
            }
            if (map.hasKey("streamIds")) {
                ReadableArray rawStreamIds = map.getArray("streamIds");
                if (rawStreamIds != null) {
                    for (int i = 0; i < rawStreamIds.size(); i++) {
                        streamIds.add(rawStreamIds.getString(i));
                    }
                }
            }
        }

        return new RtpTransceiver.RtpTransceiverInit(direction, streamIds);
    }

    private ReadableMap serializeTrack(MediaStreamTrack track) {
        WritableMap trackInfo = Arguments.createMap();
        trackInfo.putString("id", track.id());
        if (track.kind().equals("video")) {
            trackInfo.putString("label", "Video");
        } else if (track.kind().equals("audio")) {
            trackInfo.putString("label", "Aideo");
        } else {
            throw new Error("Unknown kind: " + track.kind());
        }
        trackInfo.putString("kind", track.kind());
        trackInfo.putBoolean("enabled", track.enabled());
        trackInfo.putString("readyState", track.state().toString());
        trackInfo.putBoolean("remote", true);
        return trackInfo;
    }

    private ReadableMap serializeReceiver(RtpReceiver receiver) {
        WritableMap res = Arguments.createMap();
        res.putString("id", receiver.id());
        res.putMap("track", serializeTrack(receiver.track()));
        return res;
    }

    private ReadableMap serializeTransceiver(String id, RtpTransceiver transceiver) {
        WritableMap res = Arguments.createMap();
        res.putString("id", id);
        String mid = transceiver.getMid();
        if (mid != null) {
            res.putString("mid", mid);
        }
        res.putString("direction", serializeDirection(transceiver.getDirection()));
        RtpTransceiver.RtpTransceiverDirection currentDirection = transceiver.getCurrentDirection();
        if (currentDirection != null) {
            res.putString("currentDirection", serializeDirection(transceiver.getCurrentDirection()));
        }
        res.putBoolean("isStopped", transceiver.isStopped());
        res.putMap("receiver", serializeReceiver(transceiver.getReceiver()));
        return res;
    }

    @ReactMethod
    public void peerConnectionAddTransceiver(int id,
                                             ReadableMap options,
                                             final Callback callback) {
        ThreadUtils.runOnExecutor(() ->
                this.peerConnectionAddTransceiverAsync(id, options, callback));
    }

    private void peerConnectionAddTransceiverAsync(int id,
                                                   ReadableMap options,
                                                   final Callback callback) {
        PeerConnectionObserver pco = mPeerConnectionObservers.get(id);

        if (pco != null) {
            String transceiverId;
            if (options.hasKey("type")) {
                String kind = options.getString("type");
                MediaStreamTrack.MediaType type;
                if (kind != null && kind.equals("audio")) {
                    type = MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO;
                } else if (kind != null && kind.equals("video")) {
                    type = MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO;
                } else {
                    callback.invoke(false, "invalid type");
                    return;
                }

                transceiverId = pco.addTransceiver(type, parseTransceiverOptions(options.getMap("init")));
            } else if (options.hasKey("trackId")) {
                String trackId = options.getString("trackId");
                if (trackId == null) {
                    callback.invoke(false, "invalid trackId");
                    return;
                }
                MediaStreamTrack track = getTrack(trackId);
                transceiverId = pco.addTransceiver(track, parseTransceiverOptions(options.getMap("init")));
            } else {
                callback.invoke(false, "invalid trackId and type");
                return;
            }

            WritableMap res = Arguments.createMap();
            res.putString("id", transceiverId);
            res.putMap("state", this.serializeState(id));
            callback.invoke(true, res);
        } else {
            Log.d(TAG, "peerConnectionAddTransceiver() peerConnection is null");
            callback.invoke(false, "peerConnection is null");
        }
    }

    @ReactMethod
    public void peerConnectionTransceiverStop(int id,
                                              String transceiverId,
                                              final Callback callback) {
        ThreadUtils.runOnExecutor(() ->
                this.peerConnectionTransceiverStopAsync(id, transceiverId, callback));
    }

    private void peerConnectionTransceiverStopAsync(int id,
                                                    String transceiverId,
                                                    final Callback callback) {
        PeerConnectionObserver pco = mPeerConnectionObservers.get(id);
        if (pco != null) {
            RtpTransceiver transceiver = pco.getTransceiver(transceiverId);
            transceiver.stop();
            WritableMap res = Arguments.createMap();
            res.putString("id", transceiverId);
            res.putMap("state", this.serializeState(id));
            callback.invoke(true, res);
        } else {
            Log.d(TAG, "peerConnectionAddTransceiver() peerConnection is null");
            callback.invoke(false, "peerConnection is null");
        }
    }

    @ReactMethod
    public void peerConnectionTransceiverReplaceTrack(int id,
                                                      String transceiverId,
                                                      String trackId,
                                                      final Callback callback) {
        ThreadUtils.runOnExecutor(() ->
                this.peerConnectionTransceiverReplaceTrackAsync(id, transceiverId, trackId, callback));
    }

    private void peerConnectionTransceiverReplaceTrackAsync(int id,
                                                            String transceiverId,
                                                            String trackId,
                                                            final Callback callback) {
        PeerConnectionObserver pco = mPeerConnectionObservers.get(id);
        if (pco != null) {
            RtpTransceiver transceiver = pco.getTransceiver(transceiverId);
            RtpSender sender = transceiver.getSender();
            MediaStreamTrack track = getTrack(trackId);
            sender.setTrack(track, false);
            //重置收发器（SEND_RECV）状态
            transceiver.setDirection(RtpTransceiver.RtpTransceiverDirection.SEND_RECV);

            WritableMap res = Arguments.createMap();
            res.putString("id", transceiverId);
            res.putMap("state", this.serializeState(id));
            callback.invoke(true, res);
        } else {
            Log.d(TAG, "peerConnectionTransceiverReplaceTrack() peerConnection is null");
            callback.invoke(false, "peerConnection is null");
        }
    }

    @ReactMethod
    public void peerConnectionTransceiverSetDirection(int id,
                                                      String transceiverId,
                                                      String direction,
                                                      String kind,
                                                      boolean isShow,
                                                      final Callback callback) {
            ThreadUtils.runOnExecutor(() ->
                    this.peerConnectionTransceiverSetDirectionAsync(id, transceiverId, direction, kind, isShow, callback));
    }

    private void peerConnectionTransceiverSetDirectionAsync(int id,
                                                            String transceiverId,
                                                            String direction,
                                                            String kind,
                                                            boolean isShow,
                                                            final Callback callback) {
        synchronized (this) {
            PeerConnectionObserver pco = mPeerConnectionObservers.get(id);
            int size = mPeerConnectionObservers.size();

            if (pco != null) {
                RtpTransceiver transceiver = null;
                try {
                    transceiver = pco.getTransceiver(transceiverId);
                    //显示进度框，延迟等待（2 秒）
                    if(isShow == true){  this.showProgressDialog(kind, direction); }

                    RtpTransceiver.RtpTransceiverDirection tempDirection = this.parseDirection(direction);
                    transceiver.setDirection(tempDirection);

                    WritableMap res = Arguments.createMap();
                    res.putString("id", transceiverId);
                    res.putMap("state", this.serializeState(id));
                    callback.invoke(true, res);

                    if(isShow == true){ this.delayedClose(); }
                }catch (Error e){
                    if(transceiver == null){
                        callback.invoke(false, "transceiver not found");
                    }else {
                        callback.invoke(false, this.serializeDirection(transceiver.getDirection()));
                    }
                    if(isShow == true){ this.delayedClose(); }
                }
            } else {
                Log.d(TAG, "peerConnectionTransceiverSetDirection() peerConnection is null");
                callback.invoke(false, "peerConnection is null");
                if(isShow == true){ this.delayedClose(); }
            }
        }
    }

    /**
     * 显示 进度对话框
     * @param direction 方向
     */
    private void showProgressDialog(String type, String direction){
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();//增加部分
                String str = "音频";
                if(type.equalsIgnoreCase("video")){
                    str = "视频";
                }
                progressDialog = ProgressDialog.show(context.getCurrentActivity(), str + "：" + direction, "设置中，请稍后...", true, false);
                progressDialog.setIcon(R.drawable.webrtc);
                progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER); //转盘
                Looper.loop();//增加部分
            }
        });
        thread.start();
    }

    /**
     * 延迟关闭 -> 进度对话框
     */
    private void delayedClose(){
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if(progressDialog != null){
                    progressDialog.dismiss();
                }
            }
        },1000L);
    }
    //end
}
