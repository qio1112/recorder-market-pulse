package com.yipeng.recorder.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;

public class IPUtil {

    public static String getPrivateIP() {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            return localHost.getHostAddress();
        } catch (UnknownHostException e) {
            return "";
        }
    }

    public static String getPublicIP() {
        try {
            URL url = new URL("https://checkip.amazonaws.com");
            BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
            return reader.readLine();
        } catch (IOException e) {
            return "";
        }
    }
}
