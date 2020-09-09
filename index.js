'use strict';

import RTCPeerConnection from './RTCPeerConnection';
import RTCIceCandidate from './RTCIceCandidate';
import RTCSessionDescription from './RTCSessionDescription';
//--添加代码
import RTCRtpTransceiver from './RTCRtpTransceiver';
import RTCRtpSender from './RTCRtpSender';
import RTCRtpReceiver from './RTCRtpReceiver';
//end
import RTCView from './RTCView';
import MediaStream from './MediaStream';
import MediaStreamTrack from './MediaStreamTrack';
import mediaDevices from './MediaDevices';
import permissions from './Permissions';

export {
  RTCPeerConnection,
  RTCIceCandidate,
  RTCSessionDescription,
  RTCRtpTransceiver, //--添加代码
  RTCRtpReceiver, //--添加代码
  RTCRtpSender, //--添加代码
  RTCView,
  MediaStream,
  MediaStreamTrack,
  mediaDevices,
  permissions,
  registerGlobals
};

function registerGlobals() {
	// Should not happen. React Native has a global navigator object.
	if (typeof navigator !== 'object') {
		throw new Error('navigator is not an object');
	}

	if (!navigator.mediaDevices) {
		navigator.mediaDevices = {};
	}

	navigator.mediaDevices.getUserMedia =
		mediaDevices.getUserMedia.bind(mediaDevices);

	navigator.mediaDevices.enumerateDevices =
		mediaDevices.enumerateDevices.bind(mediaDevices);

	global.RTCPeerConnection     = RTCPeerConnection;
	global.RTCIceCandidate       = RTCIceCandidate;
	global.RTCSessionDescription = RTCSessionDescription;
	global.MediaStream           = MediaStream;
	global.MediaStreamTrack      = MediaStreamTrack;
  //新增代码
  global.RTCRtpTransceiver     = RTCRtpTransceiver;
	global.RTCRtpReceiver        = RTCRtpReceiver;
	global.RTCRtpSender          = RTCRtpSender;
}
