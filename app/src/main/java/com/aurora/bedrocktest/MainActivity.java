package com.aurora.bedrocktest;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.ice4j.ice.Candidate;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    private WebRTC webRTC;
    private Packet packet;
    private Thread discoveryThread;
    private Boolean running;
    private long senderId;
    private long serverId;
    private long connectId;
    private String serverIp;
    private boolean inICE = false;

    private SessionDescription remoteSdp;
    private DataChannel dataChannel;
    private Socket socket;

    private PeerConnection peerConnection;
    private boolean sendRequest = true;

    private boolean sendSdp = false;
    private byte[] localSdpData;
    private ArrayList<IceCandidate> candidates = new ArrayList<>();

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
        socket = new Socket();

        startSocket();

    }

    private void startSocket() {
        byte[] emptyPayload = new byte[0];
        byte[] requestPacket = new Packet.DiscoveryPacket((short) 0x00, senderId, emptyPayload).pack();
        running = true;
        discoveryThread = new Thread(() -> {
            while (running) {
                if (peerConnection != null){
                    Log.d("ICEConnectionStatus", peerConnection.iceConnectionState().toString());
                    Log.d("SignalConnectionStatus", peerConnection.signalingState().toString());
                }
                try {
                    if (sendRequest) {
                        socket.broadcast(requestPacket);
                    }
                    if (sendSdp){
                        Log.d("SendLocalSdp", "send");
                        socket.send(localSdpData, serverIp);
                        sendSdp = false;
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
                        if (type == 1) {
                            Packet.ResponsePacket responsePacket = new Packet.ResponsePacket(discoveredPacket.getData());
                            Log.d("ResponsePacketContent", responsePacket.string());
                            //开始交换ICE候选
                            if (!inICE) {
                                Log.d("TryICE", "开始ICE候选协商");
                                inICE = true;
                                peerConnection = startConnect();
                            }else{
                                sendSdp = true;
                            }
                        } else if (type == 2) {
                            Log.d("ReceiveMessagePacket", "收到0x02MessagePacket");
                            byte[] data = discoveredPacket.getData();
                            Packet.MessagePacket messagePacket = new Packet.MessagePacket(data);
                            //比对己方SenderID和返回包里的Recipient ID
                            Log.d("Recipient Id", messagePacket.getRecipientId() + "|" + senderId);
                            //输出SDP信息
                            String sdp = new String(messagePacket.getMessage());
                            Log.d("sdp", sdp);
                            if (sdp.equals("Ping")) {
                                sendSdp = true;
                            } else {
                                String[] parts = sdp.split(" ");
                                String iceType = parts[0]; // 按空格切分，取第一个部分
                                Log.d("ICEType", iceType);
                                switch (iceType) {
                                    case Packet.connectRequest:
                                        //需要返回ConnectResponse
                                        break;
                                    case Packet.connectResponse:
                                        //获取SDP Answer
                                        String serverConnectId = parts[1];
                                        int sdpStartIndex = sdp.indexOf("v=0");
                                        String answer = sdp.substring(sdpStartIndex);
                                        Log.d("ANSWER", answer);
                                        remoteSdp = new SessionDescription(SessionDescription.Type.ANSWER, answer);
                                        peerConnection.setRemoteDescription(new SdpAdapter("setRemoteSdp"), remoteSdp);
                                        sendCandidate();
                                        break;
                                    case Packet.candidateadd:
                                        String regex = "candidate:[^\n]+";

                                        Pattern pattern = Pattern.compile(regex);
                                        Matcher matcher = pattern.matcher(sdp);
                                        if (matcher.find()) {
                                            // 输出匹配到的候选信息
                                            String candidateInfo = matcher.group(0);
                                            IceCandidate candidate = new IceCandidate("application", 9, candidateInfo);
                                            peerConnection.addIceCandidate(candidate);
                                        }
                                        break;
                                }
                            }
                        }
                    } else {
                        //未知包
                        Log.d("UnknownPacket", Arrays.toString(receivePacket));
                    }
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Log.e("DiscoveryUnknownError", e.getMessage() != null ? e.getMessage() : "Unknown error");
                }
            }
        });
        discoveryThread.start();
    }

    private void createRequestMessage(SessionDescription localSdp) {
        connectId = WebRTC.randSessionId();
        String sdpData = Packet.connectRequest + " " + connectId + " " + localSdp.description;
        Log.d("SDPData", sdpData);
        Packet.MessagePacket messagePacket = new Packet.MessagePacket(serverId, sdpData);
        byte[] messagePacketPayload = messagePacket.pack();
        localSdpData = new Packet.DiscoveryPacket((short) 0x02, senderId, messagePacketPayload).pack();
        sendSdp = true;
    }
    private void storeCandidate(IceCandidate candidate){
        candidates.add(candidate);
    }
    private void sendCandidate(){
        sendRequest = false;
        sendSdp = false;
        for (IceCandidate candidate: candidates) {
            String candidateData = Packet.candidateadd + " " + connectId + " " + candidate.sdp;
            Log.d("Candidate", candidateData);
            Packet.MessagePacket messagePacket = new Packet.MessagePacket(serverId, candidateData);
            byte[] messagePacketPayload = messagePacket.pack();
            byte[] finalPacket = new Packet.DiscoveryPacket((short) 0x02, senderId, messagePacketPayload).pack();
            socket.send(finalPacket, serverIp);
        }
    }

    private PeerConnection startConnect() {
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
        config.iceTransportsType = PeerConnection.IceTransportsType.ALL;
        config.enableDtlsSrtp = true;

        PeerConnection peerConnection = factory.createPeerConnection(config, new CustomPeerConnectionObserver());

        DataChannel.Init init = new DataChannel.Init();
        dataChannel = peerConnection.createDataChannel("myChannel", init);
        dataChannel.registerObserver(new CustomDataChannelObserver());

        peerConnection.createOffer(new SdpAdapter("createLocalSdp"), new MediaConstraints());

        return peerConnection;
    }

    // 监听连接状态变化
    private class CustomPeerConnectionObserver implements PeerConnection.Observer {
        @Override
        public void onSignalingChange(PeerConnection.SignalingState newState) {
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {
            Log.d("WebRTC", "ICE Connection State: " + newState);
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
        }

        @Override
        public void onIceCandidate(IceCandidate candidate) {
            Log.d("LocalCandidate", candidate.sdp);
            //发送本地候选
            storeCandidate(candidate);
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] candidates) {
        }

        @Override
        public void onDataChannel(DataChannel dc) {
            Log.d("WebRTC", "DataChannel received");
            dc.registerObserver(new CustomDataChannelObserver());
        }

        @Override
        public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
            Log.d("WebRTC", "Connection State: " + newState);
        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {
        }

        @Override
        public void onAddStream(MediaStream stream) {
        }

        @Override
        public void onRemoveStream(MediaStream stream) {
        }

        @Override
        public void onRenegotiationNeeded() {
        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {

        }

        @Override
        public void onTrack(RtpTransceiver transceiver) {
        }
    }

    // 数据通道事件
    private class CustomDataChannelObserver implements DataChannel.Observer {
        @Override
        public void onBufferedAmountChange(long previousAmount) {
        }

        @Override
        public void onStateChange() {
            Log.d("WebRTC", "DataChannel state: " + dataChannel.state());
        }

        @Override
        public void onMessage(DataChannel.Buffer buffer) {
            Log.d("WebRTC", "Received message");
        }
    }

    // SDP 事件适配器
    private class SdpAdapter implements SdpObserver {
        private final String tag;

        public SdpAdapter(String tag) {
            this.tag = tag;
        }

        @Override
        public void onCreateSuccess(SessionDescription sdp) {
            if (peerConnection != null) {
                peerConnection.setLocalDescription(new SdpAdapter("setLocalSdp"), sdp);
            }
            createRequestMessage(sdp);
        }

        @Override
        public void onSetSuccess() {
        }

        @Override
        public void onCreateFailure(String error) {
            Log.e(tag, "Create fail: " + error);
        }

        @Override
        public void onSetFailure(String error) {
            Log.e(tag, "Set fail: " + error);
        }
    }
}