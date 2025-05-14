package com.aurora.bedrocktest;

import android.content.Context;

import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;

import java.util.Random;

public class WebRTC {
    public static SessionDescription createSdp(long sessionId, String fingerprint) {
        // 创建 SDP 内容，作为字符串构造 SessionDescription
        String sdpString = "v=0\n" +
                "o=- " + sessionId + " 2 IN IP4 127.0.0.1\n" +
                "s=-\n" +
                "t=0 0\n" +
                "a=group:BUNDLE 0\n" +
                "a=extmap-allow-mixed:\n" +
                "a=msid-semantic: WMS\n" +
                "m=application 9 UDP DTLS SCTP webrtc-datachannel\n" +
                "c=IN IP4 0.0.0.0\n" +
                "a=ice-ufrag:mc\n" +
                "a=ice-pwd:minecraft_bedrock_aurora\n" +
                "a=ice-options:trickle\n" +
                "a=fingerprint:sha-256 "+ fingerprint +"\n" +
                "a=setup:actpass\n" +
                "a=mid:0\n" +
                "a=sctp-port:5000\n" +
                "a=max-message-size:1024";

        // 创建 SessionDescription（Offer 类型）
        return new SessionDescription(SessionDescription.Type.OFFER, sdpString);
    }

    // 随机生成 Session ID
    public static long randSessionId() {
        return new Random().nextLong();
    }
}
