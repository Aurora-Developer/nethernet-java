package com.aurora.bedrocktest;

import android.app.Activity;
import android.graphics.pdf.LoadParams;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtcCertificatePem;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SSLCertificateVerifier;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;

public class MainActivity extends AppCompatActivity {
    private Packet packet;
    private WebRTC webRTC;
    private Thread discoveryThread;
    private Thread iceThread;
    private Boolean running;
    private long senderId;
    private long serverId;
    private long sessionId;
    private long connectId;
    private String serverIp;
    private boolean inICE = false;

    private KeyPair keyPair;
    private X509Certificate certificate;

    private SessionDescription localSdp;
    private SessionDescription remoteSdp;
    private IceCandidate candidate;
    private DataChannel dataChannel;

    private String localCandidate;
    private String remoteCandidate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        Random random = new Random();
        senderId = random.nextLong();
        packet = new Packet();
        webRTC = new WebRTC();
        startSocket();
    }

    private void startSocket() {
        byte[] emptyPayload = new byte[0];
        byte[] requestPacket = new Packet.DiscoveryPacket((short) 0x00, senderId, emptyPayload).pack();
        Socket socket = new Socket();
        running = true;
        discoveryThread = new Thread(() -> {
            byte[] candidate = new byte[0];
            boolean sendCandidate = false;
            boolean stopRequest = false;
            while (running) {
                try {
                    if (!stopRequest) {
                        socket.broadcast(requestPacket);
                    }
                    if (sendCandidate){
                        Log.d("SendCandidate", "send");
                        socket.send(candidate, serverIp);
                    }
                    Map<String, Object> receive = socket.receive();
                    byte[] receivePacket = (byte[]) receive.get("data");
                    serverIp = (String) receive.get("ip");
                    Log.d("ReceivePacket", receivePacket.length + "\nip:" + serverIp);
                    Packet.DiscoveryPacket discoveredPacket = packet.decodeDiscoveryPacket(receivePacket);
                    if (discoveredPacket != null) {
                        short type = discoveredPacket.getType();
                        serverId = discoveredPacket.getSenderId();
                        Log.d("ReceivePacketType", "type:" + type + " serverId:" + serverId);
                        if (type == 1){
                            Packet.ResponsePacket responsePacket = new Packet.ResponsePacket(discoveredPacket.getData());
                            Log.d("ResponsePacketContent", responsePacket.string());
                            //开始交换ICE候选
                            if (!inICE) {
                                Log.d("TryICE", "开始ICE候选协商");
                                inICE = true;
                                startICE(socket);
                            }
                        }
                        else if (type == 2){
                            Log.d("ReceiveMessagePacket", "收到0x02MessagePacket");
                            byte[] data = discoveredPacket.getData();
                            Packet.MessagePacket messagePacket = new Packet.MessagePacket(data);
                            //比对己方SenderID和返回包里的Recipient ID
                            Log.d("Recipient Id", messagePacket.getRecipientId() + "|" + senderId);
                            //输出SDP信息
                            String sdp = new String(messagePacket.getMessage());
                            Log.d("sdp", sdp);
                            if (sdp.equals("Ping")){
                                inICE = false;
                            }else{
                                String[] parts = sdp.split(" ");
                                String iceType = parts[0]; // 按空格切分，取第一个部分
                                Log.d("ICEType", iceType);
                                switch (iceType){
                                    case Packet.connectRequest:
                                        //需要返回ConnectResponse
                                        break;
                                    case Packet.connectResponse:
                                        //获取SDP Answer
                                        String serverConnectId = parts[1];
                                        int sdpStartIndex = sdp.indexOf("v=0");
                                        String answer = sdp.substring(sdpStartIndex);
                                        remoteSdp = new SessionDescription(SessionDescription.Type.ANSWER, answer);
                                        //发送候选
                                        String pattern = "a=ice-ufrag:([^\r\n]*)"; // 匹配 ice-ufrag 后的值
                                        Pattern r = Pattern.compile(pattern);
                                        Matcher m = r.matcher(answer);
                                        if (m.find()){
                                            localCandidate = WebRTC.createCandidate(m.group(1));
                                            Log.d("LocalCandidate", localCandidate);
                                            String candidateData = Packet.candidateadd + " " + connectId + " " + localCandidate;
                                            Packet.MessagePacket candidateMessagePacket = new Packet.MessagePacket(serverId, candidateData);
                                            byte[] messagePacketPayload = candidateMessagePacket.pack();
                                            byte[] finalPacket = new Packet.DiscoveryPacket((short) 0x02, senderId, messagePacketPayload).pack();
                                            candidate = finalPacket;
                                            sendCandidate = true;
                                            stopRequest = true;
                                        }
                                        break;
                                    case Packet.candidateadd:
                                        sendCandidate = false;
                                        break;
                                }
                            }
                        }
                    }else{
                        //未知包
                        Log.d("UnknownPacket", Arrays.toString(receivePacket));
                    }
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Log.e("DiscoveryUnknownError", e.getMessage()!=null?e.getMessage():"Unknown error");
                }
            }
        });
        discoveryThread.start();
    }
    private void startICE(Socket socket){
        try {
            //生成密钥
            keyPair = Crypto.generateKeyPair();
            certificate = Crypto.generateSelfSignedCertificate(keyPair);
            String fingerprint = Crypto.getFingerprint(certificate);
            sessionId = WebRTC.randSessionId();
            connectId = WebRTC.randSessionId();
            String session = WebRTC.createSdp(sessionId, fingerprint);
            localSdp = new SessionDescription(SessionDescription.Type.OFFER, session);
            String sdpData = Packet.connectRequest + " " + connectId + " " + session;
            Log.d("SDPData", sdpData);
            Packet.MessagePacket messagePacket = new Packet.MessagePacket(serverId, sdpData);
            byte[] messagePacketPayload = messagePacket.pack();
            byte[] finalPacket = new Packet.DiscoveryPacket((short) 0x02, senderId, messagePacketPayload).pack();
            socket.send(finalPacket, serverIp);
        }catch (Exception e){
            Log.e("ICEUnknownError", e.getMessage()!=null?e.getMessage():"Unknown error");
        }
    }

    private void startConnect(SessionDescription sdpOffer, SessionDescription sdpAnswer, IceCandidate iceCandidate){
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .setEnableInternalTracer(true)
                        .createInitializationOptions()
        );

        // 创建工厂
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        PeerConnectionFactory factory = PeerConnectionFactory.builder()
                .setOptions(options)
                .createPeerConnectionFactory();

        PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(new ArrayList<>());
        try {
            config.certificate = new RtcCertificatePem(Crypto.convertPrivateKeyToPEM(keyPair.getPrivate()), Crypto.convertCertificateToPEM(certificate));
        }catch (Exception e){
            Log.d("SetCert", e.getMessage()!=null?e.getMessage():"Unknown error");
        }
        config.iceTransportsType = PeerConnection.IceTransportsType.NOHOST;
        config.enableDtlsSrtp = true;

        PeerConnection peerConnection = factory.createPeerConnection(config, new CustomPeerConnectionObserver());

        DataChannel.Init init = new DataChannel.Init();
        dataChannel = peerConnection.createDataChannel("myChannel", init);
        dataChannel.registerObserver(new CustomDataChannelObserver());

        peerConnection.setLocalDescription(new SdpAdapter("setLocalDesc"), sdpOffer);
        peerConnection.setRemoteDescription(new SdpAdapter("setRemoteDesc"), sdpAnswer);
        peerConnection.addIceCandidate(iceCandidate);
    }

    // 监听连接状态变化
    private class CustomPeerConnectionObserver implements PeerConnection.Observer {
        @Override public void onSignalingChange(PeerConnection.SignalingState newState) {}
        @Override public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {
            Log.d("WebRTC", "ICE Connection State: " + newState);
        }
        @Override public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {}
        @Override public void onIceCandidate(IceCandidate candidate) {}
        @Override public void onIceCandidatesRemoved(IceCandidate[] candidates) {}
        @Override public void onDataChannel(DataChannel dc) {
            Log.d("WebRTC", "DataChannel received");
            dc.registerObserver(new CustomDataChannelObserver());
        }
        @Override public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
            Log.d("WebRTC", "Connection State: " + newState);
        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {
        }

        @Override public void onAddStream(MediaStream stream) {}
        @Override public void onRemoveStream(MediaStream stream) {}
        @Override public void onRenegotiationNeeded() {}

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {

        }

        @Override public void onTrack(RtpTransceiver transceiver) {}
    }

    // 数据通道事件
    private class CustomDataChannelObserver implements DataChannel.Observer {
        @Override public void onBufferedAmountChange(long previousAmount) {}
        @Override public void onStateChange() {
            Log.d("WebRTC", "DataChannel state: " + dataChannel.state());
        }
        @Override public void onMessage(DataChannel.Buffer buffer) {
            Log.d("WebRTC", "Received message");
        }
    }

    // SDP 事件适配器
    private class SdpAdapter implements SdpObserver {
        private final String tag;
        public SdpAdapter(String tag) { this.tag = tag; }
        @Override public void onCreateSuccess(SessionDescription sdp) {}
        @Override public void onSetSuccess() {}
        @Override public void onCreateFailure(String error) { Log.e(tag, "Create fail: " + error); }
        @Override public void onSetFailure(String error) { Log.e(tag, "Set fail: " + error); }
    }
}