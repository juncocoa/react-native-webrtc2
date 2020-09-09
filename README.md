# react-native-webrtc2（Support transceiver APIs - unified-plan mode）

[![React Native Version](https://img.shields.io/badge/react--native-latest-blue.svg?style=flat-square)](http://facebook.github.io/react-native/releases)
[![npm version](https://badge.fury.io/js/react-native-webrtc2.svg)](https://badge.fury.io/js/react-native-webrtc2)
[![npm downloads](https://img.shields.io/npm/dm/react-native-webrtc2.svg?maxAge=2592000)](https://img.shields.io/npm/dm/react-native-webrtc.svg2?maxAge=2592000)

用于 React Native 的 WebRTC 模块
- 支持 iOS / Android（plan-b 模式：W3C 建议弃用）.
- 支持 Video / Audio / Data Channels.
- 仅 Android 版支持: Transceiver API（unified-plan 模式：W3C 建议使用）.

**NOTE** 对于 Expo 用户：除非您退出，否则此插件将不起作用。
## 感谢开源项目
- 感谢 [react-native-webrtc](https://github.com/react-native-webrtc/react-native-webrtc) 提供基础架构 [M84](https://github.com/jitsi/webrtc/commit/dc40d5cc81e8fe9aa1cd78a38ee8bb9e91ec49a0) 版本
- 感谢 openland 作者 [react-native-webrtc：openland](https://github.com/openland/react-native-webrtc) 提供收发器 API 支持
- 修正了作者 openland 收发器，解决一些存在的 bug，添加了一些功能。

## 社区

欢迎大家光临我们的 [Discourse community](https://react-native-webrtc.discourse.group/) 讨论任何与 React Native 和 WebRTC 相关的主题。

## WebRTC 修订版
- 当前使用版本: [M84](https://github.com/jitsi/webrtc/commit/dc40d5cc81e8fe9aa1cd78a38ee8bb9e91ec49a0)
- 支持的架构
  * Android: armeabi-v7a, arm64-v8a, x86, x86_64
  * iOS: arm64, x86_64

## WebRTC 修订版（旧）

| react-native-webrtc | WebRTC Version | arch(ios) | arch(android)  | npm published | notes |
| :-------------: | :-------------:| :-----: | :-----: | :-----: | :-----: |
| 1.75.2 | [M75](https://github.com/jitsi/webrtc/commit/0cd6ce4de669bed94ba47b88cb71b9be0341bb81) | x86_64<br>i386<br>armv7<br>arm64 | armeabi-v7a<br>arm64-v8a<br>x86<br>x86_64 | :heavy_check_mark: | |
| 1.75.1 | [M75](https://github.com/jitsi/webrtc/commit/0cd6ce4de669bed94ba47b88cb71b9be0341bb81) | x86_64<br>i386<br>armv7<br>arm64 | armeabi-v7a<br>arm64-v8a<br>x86<br>x86_64 | :heavy_check_mark: | |
| 1.75.0 | [M75](https://github.com/jitsi/webrtc/commit/0cd6ce4de669bed94ba47b88cb71b9be0341bb81) | x86_64<br>i386<br>armv7<br>arm64 | armeabi-v7a<br>arm64-v8a<br>x86<br>x86_64 | :heavy_check_mark: | |
| 1.69.2 | [M69](https://github.com/jitsi/webrtc/tree/cb536cf7a368e77ec29a6779de7fbebf2c300b70) | x86_64<br>i386<br>armv7<br>arm64 | armeabi-v7a<br>arm64-v8a<br>x86<br>x86_64 | :heavy_check_mark: |  |
| 1.69.1 | [M69](https://chromium.googlesource.com/external/webrtc/+/branch-heads/69)<br>[commit](https://chromium.googlesource.com/external/webrtc/+/9110a54a60d9e0c69128338fc250319ddb751b5a)<br>(24012)<br>(+16-24348) | x86_64<br>i386<br>armv7<br>arm64 | armeabi-v7a<br>arm64-v8a<br>x86<br>x86_64 | :heavy_check_mark: |  |
| 1.69.0 | [M69](https://chromium.googlesource.com/external/webrtc/+/branch-heads/69)<br>[commit](https://chromium.googlesource.com/external/webrtc/+/9110a54a60d9e0c69128338fc250319ddb751b5a)<br>(24012)<br>(+16-24348) | x86_64<br>i386<br>armv7<br>arm64 | armeabi-v7a<br>x86 | :heavy_check_mark: |  |
| master | [M75](https://github.com/jitsi/webrtc/commit/0cd6ce4de669bed94ba47b88cb71b9be0341bb81) | x86_64<br>i386<br>armv7<br>arm64 | armeabi-v7a<br>arm64-v8a<br>x86<br>x86_64 | :warning: | Please test! |

请参见 [wiki page](https://github.com/react-native-webrtc/react-native-webrtc/wiki) 关于修订历史。

## 安装

- [iOS](https://github.com/react-native-webrtc/react-native-webrtc/blob/master/Documentation/iOSInstallation.md)
- [Android](https://github.com/react-native-webrtc/react-native-webrtc/blob/master/Documentation/AndroidInstallation.md)

## 使用
现在，请先看下图，WebRTC 认证、协议握手流程图（其中需要 信令服务器，媒体服务器[ STUN、TURN ]）
![image](http://z1.027cgb.com/632122/communicationTopology.png)

开发、测试 可以用 [信令服务器](https://github.com/juncocoa/react-native-webrtc-server)，使用socket.io 加房间方式。（仅限开发测试，正式运营，需要自己写信令服务器 和 压力测试）

现在，您可以像在浏览器中一样使用WebRTC。在你的 `index.ios.js`/`index.android.js`, 您可以在 WebRTC 项目中 导入 RTCPeerConnection，RTCSessionDescription 等。

```javascript
import {
  RTCPeerConnection,
  RTCIceCandidate,
  RTCSessionDescription,
  RTCRtpTransceiver,
  RTCRtpReceiver,
  RTCRtpSender,
  RTCView,
  MediaStream,
  MediaStreamTrack,
  mediaDevices,
  registerGlobals
} from 'react-native-webrtc';
```
关于使用 RTCPeerConnection，RTCSessionDescription 和 RTCIceCandidate 的任何内容都类似于浏览器。 支持大多数 WebRTC API，请参阅 [Mozilla Document](https://developer.mozilla.org/en-US/docs/Web/API/RTCPeerConnection).

```javascript
var sdpSemantics = "plan-b"; //unified-plan: 收发器，plan-b: 常规
const configuration = {"iceServers": [{"url": "stun:stun.l.google.com:19302"}], sdpSemantics: sdpSemantics};
const pc = new RTCPeerConnection(configuration);

let isFront = true;
mediaDevices.enumerateDevices().then(sourceInfos => {
  console.log(sourceInfos);
  let videoSourceId;
  for (let i = 0; i < sourceInfos.length; i++) {
    const sourceInfo = sourceInfos[i];
    if(sourceInfo.kind == "videoinput" && sourceInfo.facing == (isFront ? "front" : "environment")) {
      videoSourceId = sourceInfo.deviceId;
    }
  }
  mediaDevices.getUserMedia({
    audio: true,
    video: {
      mandatory: {
        minWidth: 500, // Provide your own width, height and frame rate here
        minHeight: 300,
        minFrameRate: 30
      },
      facingMode: (isFront ? "user" : "environment"),
      optional: (videoSourceId ? [{sourceId: videoSourceId}] : [])
    }
  })
  .then(stream => {
    // Got stream!
  })
  .catch(error => {
    // Log error
  });
});

pc.createOffer().then(desc => {
  pc.setLocalDescription(desc).then(() => {
    // Send pc.localDescription to peer
  });
});

pc.onicecandidate = function (event) {
  // send event.candidate to peer
};

// 也支持 setRemoteDescription, createAnswer, addIceCandidate, addTransceiver, onnegotiationneeded, oniceconnectionstatechange, onsignalingstatechange, onaddstream , ontrack

```
### 收发器介绍（ Transceiver API ）
收发器方向，一共有四种：sendrecv、sendonly、recvonly、inactive( ``提示：会导致 C++ 销毁收发器，销毁后不能使用 setDirection(方向)`` )
```js
pc.addTransceiver('audio' | 'video' | MediaStreamTrack, {direction: 'sendrecv'})
.then((res)=>{
  that.state.videoTransceiver = res;
})

//控制收发器，工作模式（音频流 只接收、只发送 、视频流 只接收、只发送）
//从而控制：麦克风静音，关闭远程摄像头 等
this.state.videoTransceiver.setDirection("recvonly")
```
![image](http://z1.027cgb.com/632122/direction.jpg)
### RTCView

但是，渲染视频流应以 React 方式使用。

渲染 RTCView.

```javascript
//使用 sdpSemantics: plan-b -> addStream(MediaStream) 下的 toURL 方法(是一个 UUID)
<RTCView streamURL={this.state.stream.toURL()}/>

//使用 sdpSemantics: unified-plan -> addTrack(MediaStreamTrack) outStream 是一个 UUID)
//推荐使用（实验室功能，可控制 收发器 方向）
<RTCView streamURL={this.state.outStream}/>
`or 通用写法（Universal）`
<RTCView streamURL={(typeof(this.state.outStream) === "string")?this.state.outStream:this.state.outStream.toURL()}/>
```

| 属性名称                           | 类型             | 默认值                   | 描述                                                                                                                                |
| ------------------------------ | ---------------- | ------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| mirror                         | boolean          | false               | 指在渲染过程中 “streamURL” 指定的视频是否是镜像。通常，应用程序选择镜像启用手机相机。                                                                                                                       |
| objectFit                      | string           | 'contain'           | 可选择 （contain） 或者 （cover）                                                                                                |
| streamURL                      | string           | ''                  | 必须的（本地视频轨道 UUID 或者远程视频轨道 UUID)                                                                                                                      |
| zOrder                         | number           | 0                   | 类似于 zIndex                                                                                              |


### 自定义 APIs

#### registerGlobals()

通过调用此方法，JavaScript 可注册全局对象，并增加以下内容：

* `navigator.mediaDevices.getUserMedia()`
* `navigator.mediaDevices.enumerateDevices()`
* `window.RTCPeerConnection`
* `window.RTCIceCandidate`
* `window.RTCSessionDescription`
* `window.MediaStream`
* `window.MediaStreamTrack`
* `-------以下对象（新增）--------`
* `window.RTCRtpTransceiver`
* `window.RTCRtpReceiver`
* `window.RTCRtpSender`

这儿提供了一些全局变量，以帮助你像 Web 浏览器一样使用 WebRTC，同时 WebRTC JavaScript libraries 将更好的和 react-native-webrtc (native code) 通讯。

#### MediaStreamTrack.prototype.switchCamera()

此功能允许在视频轨道中即时切换 前置、后置 摄像机，而无需 添加、删除 轨道或重新商定协议。

#### VideoTrack.enabled
`此项不适用于 unified-plan 模式`

从版本 1.67 开始，将本地视频轨道的启用状态设置为 `false`, t时，摄像机将关闭，但该轨道将保持活动状态。 将其重新设置为 `true` 将重新启用相机。

#### RTCPeerConnection.addTransceiver('audio' | 'video' | MediaStreamTrack, init)
添加收发器，会自动添加轨道（addTrack），不需手动添加，一个收发器，对应一个轨道，同时拥有（RTCRtpSender 和 RTCRtpReceiver）两个对象，可以使用 <font color=#e06459>setDirection</font> 方法设置收发方向。
* （Promise）返回：收发器对象（RTCRtpTransceiver）
```js
for (var i = 0; i < stream.getTracks().length; i++) {
    var track = stream.getTracks()[i]
    var info = {
      constraints: track.getConstraints(),
      enabled: track.enabled,
      id: track.id,
      kind: track.kind,
      label: track.label,
      muted: track.muted,
      remote: track.remote,
      readyState: track.readyState,
    }
    var mediaStreamTrack = new MediaStreamTrack(info);

    //direction：sendrecv 发送接收，sendonly 只发送，recvonly 只接收，inactive 不发送不接收，未激活
    pc.addTransceiver(mediaStreamTrack, {direction: 'sendrecv'}).then((rtpTransceiver)=>{
      this.state.rtpTransceiver = rtpTransceiver;
      //this.state.rtpTransceiver.setDirection("sendrecv")
      console.log("收发器（RTCRtpTransceiver）：", rtpTransceiver);
    })
    'or' //audio, video
    pc.addTransceiver('audio', {direction: 'sendrecv'})
}
```

#### RTCRtpTransceiver.stop()
停止收发器：<font color=#e06459>此项是单向不可逆的，停止后将永不能使用。</font>

#### RTCRtpTransceiver.isStopped: boolean
获取此收发器，是否停止（<font color=#e06459>true</font>, <font color=#e06459>false</font>）

#### RTCRtpSender.replaceTrack
将一个新的 MediaStreamTrack 对象，作为参数，交给 RTCRtpSender 发送者，可替换当前轨道（Audio、Video）。入参：MediaStreamTrack  
提示：<font color=#e06459>替换后，程序智能将收发器重置为 direction = sendrecv，确保 RTC 通讯正常。</font>

#### * 新增 RTCPeerConnection.addTrack: RtpSender
为 RTCPeerConnection 增加 addTrack 方式，将满足 Transceiver API 规范。
入参：MediaStreamTrack  
提示：<font color=#e06459>智能判断是否存在收发器，存在，轨道将添加到收发器 RtpSender 里（同时设置：direction = sendrecv），否则 直接添加轨道到 RTCPeerConnection 中，支持通讯中，动态 removeTrack 和 addTrack 满足产品需求。（ e.g ）</font>
* （Promise）返回：RtpSender 对象
```js
var that = this;
that.getLocalStream(false, (stream) => {
    that.state.isOffer = true;
    pc = new RTCPeerConnection(configuration);

    if(sdpSemantics === "unified-plan"){
        //这个循环可省略，写了也不会出错
        for (const track of stream.getTracks()) {
          pc.addTrack(track).then((result) => {
            console.log("RtpSender：", result);
          });
        }
        //添加收发器，会自动添加轨道
        pc.addTransceiver("audio", {direction: 'sendrecv'})
        .then((res)=>{
          that.state.audioTransceiver = res;
          console.log("添加收发器（音频轨）：", res);
        })
        pc.addTransceiver("video", {direction: 'sendrecv'})
        .then((res)=>{
          that.state.videoTransceiver = res;
          console.log("添加收发器（视频轨）：", res);
        })
    }
})
```

#### * 新增 RTCPeerConnection.removeTrack
为 RTCPeerConnection 增加 removeTrack 方式，将满足 Transceiver API 规范。
入参：RtpSender(可以从 RTCPeerConnection -> getRtpSenders 获取 Array<RtpSender> 列表，指定移除某个轨道，RtpSender -> track() 可以查看 MediaStreamTrack 对象)
* （Promise）返回：boolean 成功、失败

#### * 新增 RTCPeerConnection.getRtpSenders: Array< RtpSender >
获取 RTCPeerConnection 托管的，所有 RtpSenders 对象集，这些对象是使用 RTCPeerConnection.addTrack 添加的(会调用 android 本地代码取值)，所以是异步的。
* （Promise）返回：Array< RtpSender >，建议使用 async 和 await 关键字。

#### * 为 setDirection 设置进度对话框（Progress Dialog）
为解决快速 setDirection 设置方向舵， C++ 报错导致 APP 崩溃，新增弹出进度对话框，延迟 2 秒关闭。
* 传入参数
  - direction (String：<font color=#e06459>sendrecv，sendonly，recvonly ，inactive</font>)
  - isShow （boolean：可选，默认 - <font color=#e06459>true</font>）是否显示，进度对话框（Progress Dialog）

#### * 重要提示：
客户端 A createOffer，setLocalDescription 后，连接信令服务器，客户端 B 在连接信令服务器，此时客户端 A 需要重新 和 客户端 B 商定 SDP（再次 createOffer，setLocalDescription）通过信令，告诉客户端 B，其中 SDP 包含，transceiver（收发器）相关重要信息 direction：（sendrecv  sendonly  recvonly  inactive。如果不重新商定，告诉客户端 B，收发器将失效。

## 如果打开混淆 WebRTC，请配置
``` js
在文件 proguard-rules.pro 中配置
-keep class org.webrtc.** { *; }
```

## 相关项目

### react-native-incall-manager

使用 [react-native-incall-manager](https://github.com/react-native-webrtc/react-native-incall-manager) 使屏幕保持打开状态，使麦克风静音等。

### react-native-callkeep

使用 [react-native-callkeep](https://github.com/react-native-webrtc/react-native-callkeep) 在 iOS 上使用 Callkit 或在 Android 上使用连接服务，以使 webrtc 应用程序具有本机拨号程序。

## 赞助商
该存储库没有获得赞助的计划。（以后可以由合作者讨论）。 如果您想为解决某些错误或获得某些功能而赏金，可以随意打开一个添加 `[BOUNTY]` 标题中的类别。 添加其他赏金网站链接，例如 [this](https://www.bountysource.com) 会更好。

## 创作者
该代码库最初是由创建的 [Wan Huang Yang](https://github.com/react-native-webrtc/react-native-webrtc)，后经过 [Openland 组织](https://github.com/openland/react-native-webrtc) 加入收发器，再由作者本人，修复 bug 添加一些 API，单元测试。

### 协议（License）

MIT
