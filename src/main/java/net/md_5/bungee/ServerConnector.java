package net.md_5.bungee;

import com.google.common.base.Preconditions;
import io.netty.channel.Channel;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.connection.CancelSendSignal;
import net.md_5.bungee.connection.DownstreamBridge;
import net.md_5.bungee.netty.HandlerBoss;
import net.md_5.bungee.packet.DefinedPacket;
import net.md_5.bungee.packet.Packet1Login;
import net.md_5.bungee.packet.Packet9Respawn;
import net.md_5.bungee.packet.PacketFFKick;
import net.md_5.bungee.packet.PacketHandler;

import java.util.Queue;

@RequiredArgsConstructor
public class ServerConnector extends PacketHandler {

    private final ProxyServer bungee;
    private Channel ch;
    private final UserConnection user;
    private final ServerInfo target;
    private State thisState = State.LOGIN;
    private final static int MAGIC_HEADER = 2;

    private enum State {
        LOGIN, FINISHED
    }

    @Override
    public void connected(Channel channel) throws Exception {
        this.ch = channel;
        channel.writeAndFlush(user.handshake); // BMC - writeAndFlush
        // IP Forwarding
        boolean flag = BungeeCord.getInstance().config.isIpForwarding();
        long address = flag ? Util.serializeAddress(user.getAddress().getAddress().getHostAddress()) : 0;
        byte header = (byte) (flag ? MAGIC_HEADER : 0);
        // end

        // BMC - writeAndFlush, handshake -> login
        channel.writeAndFlush(new Packet1Login(BungeeCord.PROTOCOL_VERSION, user.login.username, address, header));
    }

    @Override
    public void handle(Packet1Login login) throws Exception {
        Preconditions.checkState(thisState == State.LOGIN, "Not exepcting LOGIN");

        ServerConnection server = new ServerConnection(ch, target, login);
        ServerConnectedEvent event = new ServerConnectedEvent(user, server);
        bungee.getPluginManager().callEvent(event);

        ch.write(BungeeCord.getInstance().registerChannels()); // BMC - restore plugin messaging

        // TODO: Race conditions with many connects
        Queue<DefinedPacket> packetQueue = ((BungeeServerInfo) target).getPacketQueue();
        while (!packetQueue.isEmpty()) {
            ch.writeAndFlush(packetQueue.poll()); // BMC - writeAndFlush
        }

        synchronized (user.getSwitchMutex()) {
            if (user.getServer() == null) {
                BungeeCord.getInstance().connections.put(user.getName(), user);
                // Once again, first connection
                user.clientEntityId = login.entityId;
                user.serverEntityId = login.entityId;

                Packet1Login modLogin = new Packet1Login(
                        login.entityId,
                        login.username,
                        login.seed,
                        login.dimension
                );
                user.ch.writeAndFlush(modLogin); // BMC - writeAndFlush
            } else {
                byte oppositeDimension = (byte) (login.dimension >= 0 ? -1 : 0);

                user.serverEntityId = login.entityId;

                // BMC start - option to send login packet when switching servers
                if (BungeeCord.getInstance().config.isSendLoginPacketOnServerSwitch()) {
                    Packet1Login modLogin = new Packet1Login(
                            login.entityId,
                            login.username,
                            login.seed,
                            login.dimension
                    );
                    user.ch.writeAndFlush(modLogin);
                }
                // BMC end

                user.ch.writeAndFlush(new Packet9Respawn(oppositeDimension)); // BMC - writeAndFlush
                user.ch.writeAndFlush(new Packet9Respawn(login.dimension)); // BMC - writeAndFlush

                // Remove from old servers
                user.getServer().setObsolete(true);
                user.getServer().disconnect("Quitting");
            }

            // TODO: Fix this?
            /*if ( !user.ch.isActive() )
            {
                server.disconnect( "Quitting" );
                throw new IllegalStateException( "No client connected for pending server!" );
            }*/

            // Add to new server
            // TODO: Move this to the connected() method of DownstreamBridge
            target.addPlayer(user);

            user.setServer(server);
            ch.pipeline().get(HandlerBoss.class).setHandler(new DownstreamBridge(bungee, user, server));
        }

        thisState = State.FINISHED;

        throw new CancelSendSignal();
    }

    @Override
    public void handle(PacketFFKick kick) throws Exception {
        // BMC - only send raw message when being disconnected
        if (user.getServer() == null) {
            user.disconnect(kick.message);
        } else {
            user.sendMessage(ChatColor.RED + "Kicked whilst connecting to " + target.getName() + ": " + kick.message);
        }
    }

    @Override
    public String toString() {
        return "[" + user.getName() + "] <-> ServerConnector [" + target.getName() + "]";
    }
}
