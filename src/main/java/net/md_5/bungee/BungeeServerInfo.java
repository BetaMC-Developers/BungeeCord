package net.md_5.bungee;

import lombok.Getter;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.packet.DefinedPacket;
import net.md_5.bungee.packet.PacketFAPluginMessage;

import java.net.InetSocketAddress;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BungeeServerInfo extends ServerInfo {

    @Getter
    private final Queue<DefinedPacket> packetQueue = new ConcurrentLinkedQueue<>();

    public BungeeServerInfo(String name, InetSocketAddress address, boolean restricted) {
        super(name, address, restricted);
    }

    @Override
    public void sendData(String channel, byte[] data) {
        Server server = ProxyServer.getInstance().getServer(getName());
        if (server != null) {
            server.sendData(channel, data);
        } else {
            packetQueue.add(new PacketFAPluginMessage(channel, data));
        }
    }

}
