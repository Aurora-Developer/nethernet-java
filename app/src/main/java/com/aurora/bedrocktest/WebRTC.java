package com.aurora.bedrocktest;

import android.util.Log;

import org.webrtc.*;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Random;

public class WebRTC {
    private static final int DTLS_PORT = 19133;
    private static final String LOCAL_IP = "192.168.1.22";
    public static String createSdp(long sessionId, String fingerprint) {
        // 创建 SDP 内容，作为字符串构造 SessionDescription
        String sdpString = "v=0\r\n" +
                "o=- " + sessionId + " 2 IN IP4 127.0.0.1\n" +
                "s=-\r\n" +
                "t=0 0\r\n" +
                "a=group:BUNDLE 0\r\n" +
                "a=extmap-allow-mixed:\n" +
                "a=msid-semantic:WMS\n" +
                "m=application 9 UDP DTLS SCTP webrtc-datachannel\r\n" +
                "c=IN IP4 "+LOCAL_IP+"\r\n" +
                "a=ice-ufrag:mc\r\n" +
                "a=ice-pwd:minecraft_bedrock_aurora\r\n" +
                "a=ice-options:trickle\r\n" +
                "a=fingerprint:sha-256 "+ fingerprint +"\r\n" +
                "a=setup:actpass\r\n" +
                "a=mid:0\r\n" +
                "a=sctp-port:5000\r\n" +
                "a=max-message-size:262144\r\n";

        return sdpString;
    }

    // 随机生成 Session ID
    public static long randSessionId() {
        return new Random().nextLong();
    }

    public static String createCandidate(String ufrag){
        return "candidate:0 1 udp 10 "+LOCAL_IP+" "+ DTLS_PORT +" typ host generation 0 ufrag "+ ufrag +" network-id 1 network-cost 10";
    }
}
