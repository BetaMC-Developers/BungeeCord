package net.md_5.bungee.connection;

import com.google.common.base.Preconditions;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.UserConnection;
import net.md_5.bungee.Util;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.config.ListenerInfo;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ProxyPingEvent;
import net.md_5.bungee.netty.HandlerBoss;
import net.md_5.bungee.packet.Packet1Login;
import net.md_5.bungee.packet.Packet2Handshake;
import net.md_5.bungee.packet.PacketFFKick;
import net.md_5.bungee.packet.PacketHandler;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Random;

@RequiredArgsConstructor
public class InitialHandler extends PacketHandler implements PendingConnection {

    private static final Random random = new Random(); // BMC

    private final ProxyServer bungee;
    private Channel ch;
    @Getter
    private final ListenerInfo listener;
    private String serverId = ""; // BMC
    private Packet2Handshake handshake;
    private State thisState = State.HANDSHAKE;

    // BMC start
    @Getter
    private boolean requestedStatus;
    private int requestVersion = 14;
    // BMC end

    private enum State {
        HANDSHAKE,
        LOGIN, // BMC
        FINISHED
    }

    @Override
    public void connected(Channel channel) throws Exception {
        this.ch = channel;
    }

    @Override
    public void exception(Throwable t) throws Exception {
        disconnect(ChatColor.RED + Util.exception(t));
    }

    @Override
    public void handle(Packet2Handshake handshake) throws Exception {
        Preconditions.checkState(thisState == State.HANDSHAKE, "Not expecting HANDSHAKE");
        // BMC - correctly limit length of handshake username
        Preconditions.checkArgument(handshake.username.length() <= 32, "Handshake username cannot be longer than 32 characters");

        int limit = BungeeCord.getInstance().config.getPlayerLimit();
        Preconditions.checkState(limit <= 0 || bungee.getPlayers().size() < limit, "Server is full!");

        this.handshake = handshake;

        // BMC start - restore online mode
        if (BungeeCord.getInstance().config.isOnlineMode()) {
            this.serverId = Long.toHexString(random.nextLong());
            this.ch.writeAndFlush(new Packet2Handshake(this.serverId));
        } else {
            this.ch.writeAndFlush(new Packet2Handshake("-"));
        }

        thisState = State.LOGIN;
        throw new CancelSendSignal();
    }

    @Override
    public void handle(Packet1Login login) throws Exception {
        Preconditions.checkState(thisState == State.LOGIN, "Not expecting LOGIN");
        Preconditions.checkArgument(login.username.length() <= 16, "Login username cannot be longer than 16 characters");

        UserConnection userCon = new UserConnection((BungeeCord) bungee, ch, this, handshake, login);
        if (!BungeeCord.getInstance().addConnection(userCon)) {
            disconnect("A player with your name is already connected");
            return;
        }

        bungee.getPluginManager().callEvent(new PostLoginEvent(userCon));

        ch.pipeline().get(HandlerBoss.class).setHandler(new UpstreamBridge(bungee, userCon));

        ServerInfo server = bungee.getReconnectHandler().getServer(userCon);
        userCon.connect(server, true);

        thisState = State.FINISHED;
        throw new CancelSendSignal();
    }
    // BMC end

    // BMC start - modern query protocol
    public void handleQuery(ByteBufInputStream in) {
        try {
            int length = Util.readVarInt(in);
            int id = Util.readVarInt(in);
            if (id == 0) {
                if (length != 1) {
                    requestVersion = Util.readVarInt(in);
                    Util.readUTF8(in);
                    in.readUnsignedShort();
                    Util.readVarInt(in);
                    requestedStatus = true;
                } else {
                    doStatusResponse();
                }
            } else if (id == 1) {
                long time = in.readLong();
                doPongResponse(time);
            }
        } catch (IOException e) {
        }
    }

    private void doStatusResponse() throws IOException {
        ServerPing data = new ServerPing();
        data.setVersion(new ServerPing.Version(bungee.getGameVersion(), requestVersion));
        data.setPlayers(new ServerPing.Players(listener.getMaxPlayers(), bungee.getPlayers().size()));
        data.setDescription(new ServerPing.Description(listener.getMotd()));
        if (bungee.getIcon().getRawData() != null) {
            data.setFavicon("data:image/png;base64," + new String(bungee.getIcon().getRawData(), StandardCharsets.ISO_8859_1));
        }

        data = bungee.getPluginManager().callEvent(new ProxyPingEvent(this, data)).getResponse();

        ByteBufOutputStream out = new ByteBufOutputStream(Unpooled.buffer());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Util.writeVarInt(0, bytes);
        Util.writeUTF8(data.toJson(), bytes);
        Util.writeVarInt(bytes.size(), out);
        bytes.writeTo(out);
        ch.writeAndFlush(out.buffer());
    }

    private void doPongResponse(long time) throws IOException {
        ByteBufOutputStream out = new ByteBufOutputStream(Unpooled.buffer());
        ByteArrayOutputStream pong = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(pong);
        Util.writeVarInt(1, data);
        data.writeLong(time);
        Util.writeVarInt(pong.size(), out);
        pong.writeTo(out);
        ch.writeAndFlush(out.buffer()).addListener(future -> ch.close());
    }
    // BMC end

    @Override
    public synchronized void disconnect(String reason) {
        if (ch.isActive()) {
            ch.writeAndFlush(new PacketFFKick(reason)); // BMC - writeAndFlush
            ch.close();
        }
    }

    @Override
    public String getName() {
        return (handshake == null) ? null : handshake.username;
    }

    @Override
    public byte getVersion() {
        return (handshake == null) ? -1 : BungeeCord.PROTOCOL_VERSION;
    }

    @Override
    public InetSocketAddress getVirtualHost() {
        return (handshake == null) ? null : new InetSocketAddress("localhost", 1234);
    }

    @Override
    public InetSocketAddress getAddress() {
        return (InetSocketAddress) ch.remoteAddress();
    }

    @Override
    public String toString() {
        return "[" + ((getName() != null) ? getName() : getAddress()) + "] <-> InitialHandler";
    }
}
