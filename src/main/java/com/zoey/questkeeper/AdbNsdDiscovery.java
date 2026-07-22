package com.zoey.questkeeper;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class AdbNsdDiscovery {
    interface Callback {
        void onStatus(String status);
        void onFound(String host, int port, String serviceType);
        void onFinished(boolean found);
    }

    private static final String[] SERVICE_TYPES = {
            "_adb-tls-connect._tcp.",
            "_adb_secure_connect._tcp."
    };

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<NsdManager.DiscoveryListener> listeners = new ArrayList<>();
    private NsdManager nsdManager;
    private WifiManager.MulticastLock multicastLock;
    private final AtomicBoolean finished = new AtomicBoolean(false);

    AdbNsdDiscovery(Context context) {
        this.context = context.getApplicationContext();
    }

    void discover(long timeoutMs, Callback callback) {
        nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        if (nsdManager == null) {
            callback.onStatus("NSD service unavailable");
            callback.onFinished(false);
            return;
        }
        acquireMulticastLock(callback);
        InetAddress localIp = getWifiAddress();
        callback.onStatus("Local Wi-Fi IP: " + (localIp == null ? "unknown" : localIp.getHostAddress()));

        for (String type : SERVICE_TYPES) startDiscovery(type, localIp, callback);
        main.postDelayed(() -> finish(false, callback), timeoutMs);
    }

    private void startDiscovery(final String serviceType, final InetAddress localIp, final Callback callback) {
        NsdManager.DiscoveryListener listener = new NsdManager.DiscoveryListener() {
            @Override public void onDiscoveryStarted(String regType) {
                callback.onStatus("mDNS discovery started for " + regType);
            }

            @Override public void onServiceFound(NsdServiceInfo serviceInfo) {
                String foundType = serviceInfo.getServiceType();
                callback.onStatus("mDNS candidate: " + serviceInfo.getServiceName() + " " + foundType);
                try {
                    nsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                        @Override public void onResolveFailed(NsdServiceInfo info, int errorCode) {
                            callback.onStatus("mDNS resolve failed: " + errorCode);
                        }

                        @Override public void onServiceResolved(NsdServiceInfo info) {
                            InetAddress hostAddr = host(info);
                            int port = info.getPort();
                            if (hostAddr == null || port <= 0) return;
                            String host = hostAddr.getHostAddress();
                            boolean localMatch = localIp == null || host.equals(localIp.getHostAddress());
                            if (!localMatch) {
                                callback.onStatus("Ignoring non-local ADB service at " + host + ":" + port);
                                return;
                            }
                            if (finished.compareAndSet(false, true)) {
                                callback.onFound(host, port, serviceType);
                                stopAll();
                                callback.onFinished(true);
                            }
                        }
                    });
                } catch (Exception e) {
                    callback.onStatus("mDNS resolve exception: " + e.getMessage());
                }
            }

            @Override public void onServiceLost(NsdServiceInfo serviceInfo) {
                callback.onStatus("mDNS service lost: " + serviceInfo.getServiceName());
            }

            @Override public void onDiscoveryStopped(String serviceType) {
                callback.onStatus("mDNS discovery stopped for " + serviceType);
            }

            @Override public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                callback.onStatus("mDNS start failed for " + serviceType + ": " + errorCode);
                try { nsdManager.stopServiceDiscovery(this); } catch (Exception ignored) {}
            }

            @Override public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                callback.onStatus("mDNS stop failed for " + serviceType + ": " + errorCode);
                try { nsdManager.stopServiceDiscovery(this); } catch (Exception ignored) {}
            }
        };
        listeners.add(listener);
        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener);
        } catch (Exception e) {
            callback.onStatus("Could not start mDNS for " + serviceType + ": " + e.getMessage());
        }
    }

    private void finish(boolean found, Callback callback) {
        if (finished.compareAndSet(false, true)) {
            stopAll();
            callback.onFinished(found);
        }
    }

    private void stopAll() {
        if (nsdManager != null) {
            for (NsdManager.DiscoveryListener listener : listeners) {
                try { nsdManager.stopServiceDiscovery(listener); } catch (Exception ignored) {}
            }
        }
        listeners.clear();
        releaseMulticastLock();
    }

    private void acquireMulticastLock(Callback callback) {
        try {
            WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi == null) return;
            multicastLock = wifi.createMulticastLock("QuestKeeper:adb_mdns");
            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();
        } catch (Exception e) {
            callback.onStatus("Could not acquire multicast lock: " + e.getMessage());
        }
    }

    private void releaseMulticastLock() {
        try {
            if (multicastLock != null && multicastLock.isHeld()) multicastLock.release();
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("deprecation")
    private InetAddress getWifiAddress() {
        try {
            WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi == null || wifi.getConnectionInfo() == null) return null;
            int ipInt = wifi.getConnectionInfo().getIpAddress();
            byte[] ipBytes = new byte[]{
                    (byte) (ipInt & 0xff),
                    (byte) ((ipInt >> 8) & 0xff),
                    (byte) ((ipInt >> 16) & 0xff),
                    (byte) ((ipInt >> 24) & 0xff)
            };
            return InetAddress.getByAddress(ipBytes);
        } catch (Exception ignored) {
            return null;
        }
    }

    private InetAddress host(NsdServiceInfo info) {
        if (Build.VERSION.SDK_INT >= 34) {
            List<InetAddress> hosts = info.getHostAddresses();
            return hosts == null || hosts.isEmpty() ? null : hosts.get(0);
        }
        return info.getHost();
    }
}
